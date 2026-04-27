package core;

import net.HttpRequest;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Peer — represents a single connected client socket.
 * Holds per-connection state: inbound request accumulator,
 * pending write buffer, activity timestamp, and the local port
 * used to resolve which virtual host should handle this connection.
 */
public class Peer {

    public final SocketChannel channel;
    public final int           localPort;
    public long                lastActivity;
    public boolean             dropped = false;

    /** Accumulates raw bytes into a parsed HTTP request. */
    public HttpRequest  inboundRequest;

    /** Fully-serialized HTTP response waiting to be flushed to the socket. */
    public ByteBuffer   pendingWrite;

    public Peer(SocketChannel channel, int localPort) {
        this.channel      = channel;
        this.localPort    = localPort;
        this.lastActivity = System.currentTimeMillis();
        this.inboundRequest = new HttpRequest();
    }

    /** Updates the idle-timeout timestamp. */
    public void touch() {
        lastActivity = System.currentTimeMillis();
    }

    /** Resets state after a response is fully sent (keep-alive). */
    public void reset() {
        inboundRequest = new HttpRequest();
        pendingWrite   = null;
    }
}
