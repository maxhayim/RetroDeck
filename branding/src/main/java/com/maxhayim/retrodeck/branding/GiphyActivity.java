package com.maxhayim.retrodeck.branding;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.maxhayim.retrodeck.core.Gif;
import com.maxhayim.retrodeck.core.Ui;
import com.maxhayim.retrodeck.core.Visualizers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Vehicle Branding → Animation → Search GIPHY. Picks a GIF for the Animation visualizer of the active
 * profile. Needs a GIPHY API key: one built into this copy of RetroDeck, or one the user pastes in.
 */
public class GiphyActivity extends Activity {

    private static final String PREFS = "giphy", KEY = "apiKey";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newFixedThreadPool(3);
    private final List<Giphy.Result> results = new ArrayList<>();
    private final LruCache<String, Bitmap> stills = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getRowBytes() * b.getHeight();
        }
    };
    private EditText query;
    private TextView status;
    private Adapter adapter;
    private int searchId;
    private boolean loading, more = true;
    private String searched = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Search GIPHY", "Vehicle Branding  ›  Animation"));

        LinearLayout bar = Ui.horizontal(this);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Search animations, e.g. equalizer, neon, retro car");
        query.setTextColor(Ui.TEXT);
        query.setHintTextColor(Ui.SUB);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });
        bar.addView(query, Ui.weight(1));
        bar.addView(Ui.button(this, "Search", true, v -> search()), Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        root.addView(bar);

        status = Ui.text(this, "", 14, Ui.SUB, false);
        status.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 4));
        root.addView(status);

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(this, 150));
        grid.setVerticalSpacing(Ui.dp(this, 8));
        grid.setHorizontalSpacing(Ui.dp(this, 8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> choose(results.get(pos)));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView v, int s) {}

            @Override
            public void onScroll(AbsListView v, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 4) load(false);
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        // GIPHY's terms ask for this wherever their results are shown.
        TextView powered = Ui.text(this, "Powered by GIPHY", 13, Ui.SUB, true);
        powered.setGravity(Gravity.CENTER);
        powered.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        root.addView(powered);
        setContentView(root);

        if (key().isEmpty()) askForKey();
        else load(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        net.shutdownNow();
    }

    // ---- the API key ----

    /** The key built into this copy, unless the user pasted one of their own. */
    private String key() {
        String own = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, "");
        return own.isEmpty() ? BuildConfig.GIPHY_API_KEY : own;
    }

    private void askForKey() {
        final EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint("Your GIPHY API key");
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(e);
        Ui.dialog(this).setTitle("GIPHY API key")
                .setMessage("This copy of RetroDeck has no GIPHY key built in. Make a free one at developers.giphy.com "
                        + "(Create an App, then API) and paste it here. It's kept on this device.\n\n"
                        + "Or download a GIF on another device and use Import a GIF file instead.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String k = e.getText().toString().trim();
                    if (!k.matches("[A-Za-z0-9]{8,64}")) {
                        status.setText("That doesn't look like a GIPHY key.");
                        return;
                    }
                    SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                    ed.putString(KEY, k).apply();
                    load(true);
                })
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    // ---- searching ----

    private void search() {
        searched = query.getText().toString();
        load(true);
    }

    private void load(boolean fresh) {
        if (key().isEmpty()) return;
        if (fresh) {
            searchId++;
            results.clear();
            adapter.notifyDataSetChanged();
            more = true;
            loading = false;
        }
        if (loading || !more) return;
        loading = true;
        final int id = searchId;
        final String q = searched, k = key();
        final int offset = results.size();
        status.setText(offset == 0 ? (q.trim().isEmpty() ? "Loading what's trending…" : "Searching…") : "Loading more…");
        net.execute(() -> {
            List<Giphy.Result> page = null;
            String error = null;
            try {
                page = Giphy.search(k, q, offset);
            } catch (Exception e) {
                error = e.getMessage() == null ? "Couldn't reach GIPHY. Is the head unit online?" : e.getMessage();
            }
            final List<Giphy.Result> got = page;
            final String err = error;
            main.post(() -> {
                if (id != searchId || isFinishing()) return;
                loading = false;
                if (err != null) {
                    status.setText(err);
                    if (err.contains("API key")) askForKey();
                    return;
                }
                more = got.size() >= Giphy.PAGE / 2;
                results.addAll(got);
                adapter.notifyDataSetChanged();
                status.setText(results.isEmpty() ? "Nothing found." : (q.trim().isEmpty() ? "Trending on GIPHY" : "Results for “" + q.trim() + "”")
                        + "  ·  choose one for the visualizer");
            });
        });
    }

    // ---- choosing ----

    private void choose(final Giphy.Result r) {
        final ProgressDialog progress = ProgressDialog.show(this, null, "Downloading animation…", true, false);
        final ProfileStore store = ProfileStore.get(this);
        final String profileId = store.activeId();
        net.execute(() -> {
            String error = null;
            File dest = store.newAnimationFile(profileId);
            try {
                Animations.save(Giphy.download(r.gif, Gif.MAX_BYTES), dest);
            } catch (Animations.ImportException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = e.getMessage() == null ? "Couldn't download it." : e.getMessage();
            }
            final String err = error;
            main.post(() -> {
                try {
                    progress.dismiss();
                } catch (IllegalArgumentException ignored) {
                    // Gone already.
                }
                if (err != null) {
                    store.discard(dest);
                    status.setText(err);
                    return;
                }
                VehicleProfile p = store.profile(profileId);
                if (p == null) {
                    store.discard(dest);
                    finish();
                    return;
                }
                p.animationFile = dest.getName();
                p.visualizer = Visualizers.ANIMATION;
                store.save(p);
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    // ---- the grid ----

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return results.size(); }
        @Override public Object getItem(int i) { return results.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            ImageView v = (ImageView) convert;
            if (v == null) {
                v = new ImageView(GiphyActivity.this);
                v.setScaleType(ImageView.ScaleType.CENTER_CROP);
                v.setLayoutParams(new AbsListView.LayoutParams(-1, Ui.dp(GiphyActivity.this, 100)));
                v.setBackground(Ui.pressable(GiphyActivity.this, Ui.ROW, Ui.ROW_DOWN, 6));
                v.setFocusable(false);
            }
            Giphy.Result r = results.get(i);
            v.setContentDescription(r.title);
            Bitmap b = stills.get(r.still);
            v.setImageBitmap(b);
            v.setTag(r.still);
            if (b == null) fetchStill(r.still, v);
            return v;
        }
    }

    private void fetchStill(final String url, final ImageView v) {
        net.execute(() -> {
            Bitmap b = null;
            try {
                byte[] data = Giphy.download(url, 512 * 1024);
                b = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception ignored) {
                // Leave the tile blank.
            }
            final Bitmap got = b;
            main.post(() -> {
                if (got == null) return;
                stills.put(url, got);
                if (url.equals(v.getTag())) v.setImageBitmap(got);
            });
        });
    }
}
