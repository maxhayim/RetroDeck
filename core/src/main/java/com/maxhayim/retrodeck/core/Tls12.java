package com.maxhayim.retrodeck.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Android 4.4 supports TLS 1.2 but doesn't switch it on, and GIPHY and GitHub accept nothing older. This
 * turns it on for our connections; newer Android has it on already.
 */
public final class Tls12 extends SSLSocketFactory {
    private static SSLSocketFactory instance;
    private final SSLSocketFactory base;

    public static synchronized SSLSocketFactory factory() throws IOException {
        if (instance == null) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLSv1.2");
                ctx.init(null, null, null);
                instance = new Tls12(ctx.getSocketFactory());
            } catch (Exception e) {
                throw new IOException("This device can't make secure connections.");
            }
        }
        return instance;
    }

    private Tls12(SSLSocketFactory base) {
        this.base = base;
    }

    private Socket on(Socket s) {
        if (s instanceof SSLSocket) {
            List<String> want = new ArrayList<>();
            for (String p : ((SSLSocket) s).getSupportedProtocols()) if (p.equals("TLSv1.2") || p.equals("TLSv1.3")) want.add(p);
            if (!want.isEmpty()) ((SSLSocket) s).setEnabledProtocols(want.toArray(new String[0]));
        }
        return s;
    }

    @Override public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }
    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return on(base.createSocket(s, host, port, autoClose));
    }
    @Override public Socket createSocket(String host, int port) throws IOException { return on(base.createSocket(host, port)); }
    @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return on(base.createSocket(host, port, local, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException { return on(base.createSocket(host, port)); }
    @Override public Socket createSocket(InetAddress a, int port, InetAddress local, int localPort) throws IOException {
        return on(base.createSocket(a, port, local, localPort));
    }
}
