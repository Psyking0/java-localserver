package core;

import model.VirtualHost;
import net.HttpRequest;
import net.HttpResponse;
import net.StatusCode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HttpEngine — the single-threaded, non-blocking NIO event loop.
 * Accepts connections, reads requests, dispatches to RequestDispatcher,
 * and writes responses. Never exits; catches all exceptions internally.
 */
public class HttpEngine {

    // ── Server-wide metrics (used by /api/metrics endpoint) ──────────────────
    public static final AtomicInteger totalRequests    = new AtomicInteger(0);
    public static final AtomicInteger liveConnections  = new AtomicInteger(0);
    public static final AtomicLong    bytesIn          = new AtomicLong(0);
    public static final AtomicLong    bytesOut         = new AtomicLong(0);
    public static final AtomicInteger count404         = new AtomicInteger(0);
    public static final AtomicInteger count500         = new AtomicInteger(0);
    public static final long          bootTime         = System.currentTimeMillis();

    // ── Configuration ─────────────────────────────────────────────────────────
    private static final int SELECTOR_TIMEOUT_MS  = 5_000;   // how long select() blocks
    private static final int CONNECTION_TIMEOUT_MS = 60_000;  // idle connection TTL
    private static final int READ_BUFFER_BYTES    = 16_384;   // 16 KB per read cycle

    // ── Internal state ────────────────────────────────────────────────────────
    private Selector               selector;
    private final RequestDispatcher dispatcher;
    private final List<VirtualHost> virtualHosts;

    public HttpEngine(List<VirtualHost> virtualHosts) {
        this.virtualHosts = virtualHosts;
        this.dispatcher   = new RequestDispatcher(virtualHosts);
    }

    /** Binds all ports and enters the event loop. Blocks indefinitely. */
    public void run() throws IOException {
        selector = Selector.open();
        bindPorts();
        System.out.println("[INFO] WebServ ready — awaiting connections.");
        eventLoop();
    }

    // ── Port binding ──────────────────────────────────────────────────────────

    private void bindPorts() {
        // Detect and warn about duplicate port declarations across virtual hosts
        java.util.Map<Integer, String> portOwners = new java.util.LinkedHashMap<>();
        for (VirtualHost vh : virtualHosts) {
            for (int port : vh.listenPorts) {
                String tag = vh.bindAddress + ":" + port;
                if (portOwners.containsKey(port)) {
                    System.err.printf("[WARN] Port %d declared more than once (first owner: %s). " +
                        "One socket will be opened; virtual-host routing by Host header applies.%n",
                        port, portOwners.get(port));
                } else {
                    portOwners.put(port, tag);
                }
            }
        }

        // Bind exactly one ServerSocketChannel per unique port
        Set<Integer> bound = new HashSet<>();
        for (VirtualHost vh : virtualHosts) {
            for (int port : vh.listenPorts) {
                if (!bound.add(port)) continue;
                try {
                    ServerSocketChannel ssc = ServerSocketChannel.open();
                    ssc.configureBlocking(false);
                    ssc.bind(new InetSocketAddress(vh.bindAddress, port));
                    ssc.register(selector, SelectionKey.OP_ACCEPT);
                    System.out.printf("[INFO] Listening on %s:%d%n", vh.bindAddress, port);
                } catch (IOException ex) {
                    System.err.printf("[ERROR] Could not bind %s:%d — %s " +
                        "(port may already be in use by another process)%n",
                        vh.bindAddress, port, ex.getMessage());
                }
            }
        }
    }

    // ── Event loop ────────────────────────────────────────────────────────────

    private void eventLoop() {
        ByteBuffer readBuf = ByteBuffer.allocateDirect(READ_BUFFER_BYTES);

        while (true) {
            try {
                selector.select(SELECTOR_TIMEOUT_MS);
                reapTimedOutConnections();

                Set<SelectionKey> ready = selector.selectedKeys();
                Iterator<SelectionKey> it = ready.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) continue;
                    try {
                        if (key.isAcceptable()) onAccept(key);
                        else if (key.isReadable())  onRead(key, readBuf);
                        else if (key.isWritable()) onWrite(key);
                    } catch (Exception ex) {
                        System.err.println(ex.getClass().getName());
                        System.err.println(ex.getMessage());
                        System.err.println("[ERROR] Key handler: " + ex.getMessage());
                        dropKey(key);
                    }
                }
            } catch (Exception ex) {
                // Never crash the event loop
                System.err.println("[ERROR] Event loop: " + ex.getMessage());
            }
        }
    }

    // ── Accept ────────────────────────────────────────────────────────────────

    private void onAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssc.accept();
        if (sc == null) return;
        sc.configureBlocking(false);
        int localPort = ssc.socket().getLocalPort();
        Peer peer = new Peer(sc, localPort);
        sc.register(selector, SelectionKey.OP_READ, peer);
        liveConnections.incrementAndGet();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    private void onRead(SelectionKey key, ByteBuffer readBuf) throws IOException {
        Peer peer = (Peer) key.attachment();
        peer.touch();
        readBuf.clear();

        int n;
        try {
            n = peer.channel.read(readBuf);
        } catch (java.net.SocketException e) {
            dropKey(key);
            return;
        }   

        n = peer.channel.read(readBuf);
        if (n == -1) { dropKey(key); return; }
        if (n > 0) bytesIn.addAndGet(n);

        readBuf.flip();
        peer.inboundRequest.ingest(readBuf);

        // Body-size guard (checked early so we don't buffer huge payloads)
        VirtualHost vh = dispatcher.resolveHost(
                peer.inboundRequest.header("host"), peer.localPort);
        if (vh != null && peer.inboundRequest.bodyLength() > vh.maxBodyBytes) {
            peer.inboundRequest.forceError();
        }

        HttpRequest.Phase phase = peer.inboundRequest.phase();
        if (phase == HttpRequest.Phase.COMPLETE || phase == HttpRequest.Phase.BROKEN) {
            HttpResponse resp;
            if (phase == HttpRequest.Phase.BROKEN) {
                resp = HttpResponse.plainError(StatusCode.BAD_REQUEST);
            } else {
                resp = dispatcher.dispatch(peer.inboundRequest, peer.localPort);
            }
            peer.pendingWrite = resp.serialize();
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    private void onWrite(SelectionKey key) throws IOException {
        Peer peer = (Peer) key.attachment();
        peer.touch();

        if (peer.pendingWrite != null && peer.pendingWrite.hasRemaining()) {
            int n = peer.channel.write(peer.pendingWrite);
            if (n > 0) bytesOut.addAndGet(n);
        }

        if (peer.pendingWrite == null || !peer.pendingWrite.hasRemaining()) {
            // Keep-alive: reset state and wait for next request
            peer.reset();
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    // ── Housekeeping ──────────────────────────────────────────────────────────

    private void reapTimedOutConnections() {
        long now = System.currentTimeMillis();
        for (SelectionKey key : selector.keys()) {
            if (!key.isValid()) continue;
            if (!(key.attachment() instanceof Peer)) continue;
            Peer peer = (Peer) key.attachment();
            if (now - peer.lastActivity > CONNECTION_TIMEOUT_MS) {
                System.out.println("[INFO] Connection timed out, closing.");
                dropKey(key);
            }
        }
    }

    private void dropKey(SelectionKey key) {
        try {
            if (key.attachment() instanceof Peer) {
                Peer peer = (Peer) key.attachment();
                if (!peer.dropped) {
                    peer.dropped = true;
                    liveConnections.decrementAndGet();
                }
            }
            key.cancel();
            key.channel().close();
        } catch (IOException ignored) {}
    }
}
