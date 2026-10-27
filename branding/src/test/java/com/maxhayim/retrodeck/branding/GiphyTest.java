package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.maxhayim.retrodeck.core.Gif;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class GiphyTest {

    private static JSONObject item(String title, String still, String gif, int size, String small) throws Exception {
        JSONObject images = new JSONObject()
                .put("fixed_width_small_still", new JSONObject().put("url", still))
                .put("fixed_height", new JSONObject().put("url", gif).put("size", String.valueOf(size)));
        if (small != null) images.put("fixed_height_downsampled", new JSONObject().put("url", small).put("size", "1000"));
        return new JSONObject().put("title", title).put("images", images);
    }

    @Test
    public void readsResultsAndKeepsOnlyGiphyLinks() throws Exception {
        JSONArray data = new JSONArray()
                .put(item("Neon", "https://media1.giphy.com/s.gif", "https://media1.giphy.com/a.gif", 5000, null))
                .put(item("Elsewhere", "https://example.com/s.gif", "https://media1.giphy.com/b.gif", 5000, null))
                .put(item("Plain http", "https://media1.giphy.com/s.gif", "http://media1.giphy.com/c.gif", 5000, null))
                .put(item("Huge", "https://media2.giphy.com/s.gif", "https://media2.giphy.com/big.gif", Gif.MAX_BYTES + 1,
                        "https://media2.giphy.com/small.gif"))
                .put(new JSONObject().put("title", "No images"));
        List<Giphy.Result> r = Giphy.parse(new JSONObject().put("data", data));
        assertEquals(2, r.size());
        assertEquals("Neon", r.get(0).title);
        assertEquals("https://media1.giphy.com/a.gif", r.get(0).gif);
        // Too big to play: the smaller version instead.
        assertEquals("https://media2.giphy.com/small.gif", r.get(1).gif);
    }

    @Test
    public void giphyHostsOnly() {
        assertTrue(Giphy.isGiphyMedia("https://media.giphy.com/media/x/200.gif"));
        assertTrue(Giphy.isGiphyMedia("https://giphy.com/x.gif"));
        assertFalse(Giphy.isGiphyMedia("https://giphy.com.evil.example/x.gif"));
        assertFalse(Giphy.isGiphyMedia("https://evilgiphy.com/x.gif"));
        assertFalse(Giphy.isGiphyMedia("http://media.giphy.com/x.gif"));
        assertFalse(Giphy.isGiphyMedia("file:///sdcard/x.gif"));
        assertFalse(Giphy.isGiphyMedia(null));
    }
}
