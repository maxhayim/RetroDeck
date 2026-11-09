package com.maxhayim.retrodeck.branding;

import com.maxhayim.retrodeck.core.Gif;
import com.maxhayim.retrodeck.core.Tls12;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

/**
 * Searches GIPHY for animations, only while the search screen is open: the search words and the API key go to api.giphy.com, and the chosen GIF is
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
}
