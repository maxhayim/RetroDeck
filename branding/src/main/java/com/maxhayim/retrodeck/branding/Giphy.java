package com.maxhayim.retrodeck.branding;

import com.maxhayim.retrodeck.core.Gif;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Searches GIPHY for animations. The only part of RetroDeck that goes online, and only while the
 * search screen is open: the search words and the API key go to api.giphy.com, and the chosen GIF is
 * downloaded from GIPHY's media servers. Nothing else is sent.
 */
final class Giphy {

    // Large pages: the free key allows 100 calls an hour, and only searches count, not downloads.
    static final int PAGE = 48;
    private static final String API = "https://api.giphy.com/v1/gifs/";

    /** One search result. */
    static final class Result {
        String title = "";
        /** A small still picture, for the results grid. */
        String still;
        /** The animation itself, 200 pixels tall. */
        String gif;
        int gifBytes;
    }

    private Giphy() {}

    /** Searches, or lists what's trending when the query is blank. */
    static List<Result> search(String key, String query, int offset) throws IOException {
        String q = query == null ? "" : query.trim();
        String url = API + (q.isEmpty() ? "trending?" : "search?q=" + URLEncoder.encode(q, "UTF-8") + "&")
                + "api_key=" + URLEncoder.encode(key, "UTF-8") + "&limit=" + PAGE + "&offset=" + offset
                + "&rating=pg&lang=" + Locale.getDefault().getLanguage();
        byte[] body = get(url, 512 * 1024);
        try {
            return parse(new JSONObject(new String(body, "UTF-8")));
        } catch (JSONException e) {
            throw new IOException("GIPHY sent something RetroDeck couldn't read.");
        }
    }

    static List<Result> parse(JSONObject o) throws JSONException {
        List<Result> out = new ArrayList<>();
        JSONArray data = o.optJSONArray("data");
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject d = data.optJSONObject(i);
            JSONObject images = d == null ? null : d.optJSONObject("images");
            if (images == null) continue;
            JSONObject still = images.optJSONObject("fixed_width_small_still");
            if (still == null) still = images.optJSONObject("fixed_height_small_still");
            JSONObject gif = images.optJSONObject("fixed_height");
            JSONObject small = images.optJSONObject("fixed_height_downsampled");
            if (gif != null && small != null && gif.optInt("size", 0) > Gif.MAX_BYTES) gif = small;
            if (still == null || gif == null) continue;
            Result r = new Result();
            r.title = d.optString("title", "");
            r.still = still.optString("url", null);
            r.gif = gif.optString("url", null);
            r.gifBytes = gif.optInt("size", 0);
            if (isGiphyMedia(r.still) && isGiphyMedia(r.gif)) out.add(r);
        }
        return out;
    }

    /** Only HTTPS links to GIPHY's own servers are followed. */
    static boolean isGiphyMedia(String url) {
        if (url == null) return false;
        try {
            URL u = new URL(url);
            String host = u.getHost().toLowerCase(Locale.US);
            return "https".equals(u.getProtocol()) && (host.equals("giphy.com") || host.endsWith(".giphy.com"));
        } catch (IOException e) {
            return false;
        }
    }

    /** Downloads a GIPHY picture, at most max bytes. */
    static byte[] download(String url, int max) throws IOException {
        if (!isGiphyMedia(url)) throw new IOException("That link isn't GIPHY's.");
        return get(url, max);
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        c.setSSLSocketFactory(Tls12.factory());
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(false);
        try {
            int code = c.getResponseCode();
            if (code == 401 || code == 403) throw new IOException("GIPHY didn't accept the API key.");
            if (code == 429) throw new IOException("Too many GIPHY searches for now. Try again in a while.");
            if (code != 200) throw new IOException("GIPHY answered " + code + ".");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + n > max) throw new IOException("That GIF is larger than " + (max >> 20) + " MB.");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Android 4.4 supports TLS 1.2 but doesn't switch it on, and GIPHY accepts nothing older. This
     * turns it on for our connections; newer Android has it on already.
     */
    static final class Tls12 extends SSLSocketFactory {
        private static SSLSocketFactory instance;
        private final SSLSocketFactory base;

        static synchronized SSLSocketFactory factory() throws IOException {
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
}
