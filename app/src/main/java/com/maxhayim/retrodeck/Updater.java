package com.maxhayim.retrodeck;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.maxhayim.retrodeck.core.Tls12;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

/**
 * Updates from GitHub Releases. Off: never contacts GitHub. Notify only: checks every few hours while
 * RetroDeck is open and says when there's a new version. Automatic: also downloads it, and installs it
 * when the stereo is switched off, never while music plays, since installing closes RetroDeck. Silent on
 * Android 12+ once RetroDeck has installed itself; older Android asks for one tap.
 */
final class Updater {

    static final String REPO = "maxhayim/RetroDeck";
    static final String OFF = "off", NOTIFY = "notify", AUTO = "auto";
    static final String[] MODES = {OFF, NOTIFY, AUTO};
    private static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long CHECK_EVERY_MS = 6 * 60 * 60 * 1000L;
    private static final int MAX_APK = 200 << 20;
    private static final String PREFS = "updater";

    /** Activities on screen; an install that needs a tap is only started while this is above 0. */
    static int visible;
    /** The stereo is switched off (PWR, or the head unit's screen went off) and no music is playing. */
    static volatile boolean standby;
    /** Called on the main thread whenever the status changes, so Settings can show it. */
    static Runnable onChange;
    private static boolean busy;

    static final class Release {
        final String version, apkUrl;

        Release(String version, String apkUrl) {
            this.version = version;
            this.apkUrl = apkUrl;
        }
    }

    // --- Settings and state ----------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String mode(Context c) { return prefs(c).getString("mode", NOTIFY); }

    static void setMode(Context c, String mode) { prefs(c).edit().putString("mode", mode).apply(); }

    static String label(String mode) {
        return AUTO.equals(mode) ? "Automatic" : NOTIFY.equals(mode) ? "Notify only" : "Off";
    }

    /** The newer version found by the last check, or null. */
    static Release available(Context c) {
        SharedPreferences p = prefs(c);
        String v = p.getString("version", null), url = p.getString("url", null);
        if (v == null || url == null || compare(v, BuildConfig.VERSION_NAME) <= 0) return null;
        return new Release(v, url);
    }

    /** What happened last ("Up to date", "Update failed: …"). */
    static String status(Context c) { return prefs(c).getString("status", ""); }

    static void setStatus(Context c, String s) {
        prefs(c).edit().putString("status", s).apply();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (onChange != null) onChange.run();
        });
    }

    /**
     * Once per new version, the line to show on the stereo, or null. Automatic installs on its own, so it
     * only speaks up when an install is waiting for a tap that couldn't be asked for at the time.
     */
    static String announce(Context c) {
        Release r = available(c);
        if (r == null || OFF.equals(mode(c)) || r.version.equals(prefs(c).getString("told", null))) return null;
        boolean waiting = r.version.equals(prefs(c).getString("waiting", null));
        if (AUTO.equals(mode(c)) && !waiting) return null;
        prefs(c).edit().putString("told", r.version).apply();
        return "RetroDeck " + r.version + (waiting ? " is ready to install" : " is out")
                + ": Settings → Updates";
    }

    // --- Checking --------------------------------------------------------------------------

    /** Checks in the background if the last check is old. Cheap to call often. */
    static void checkIfDue(Context c) {
        if (OFF.equals(mode(c)) || busy) return;
        if (System.currentTimeMillis() - prefs(c).getLong("checked", 0) < CHECK_EVERY_MS) return;
        start(c, false);
    }

    /** Checks now, in the background, whatever the mode: Settings → Check for updates now. */
    static void checkNow(Context c) {
        if (busy) return;
        setStatus(c, "Checking…");
        start(c, true);
    }

    private static synchronized void start(Context c, boolean userAsked) {
        if (busy) return;
        busy = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                run(app, userAsked);
            } finally {
                busy = false;
            }
        }, "retrodeck-update").start();
    }

    /** Asks GitHub for the latest release. Blocking. In Automatic a newer release is downloaded too. */
    private static void run(Context c, boolean userAsked) {
        if (OFF.equals(mode(c)) && !userAsked) return;
        Release r;
        try {
            r = latest();
        } catch (IOException e) {
            setStatus(c, "Couldn't reach GitHub");
            return;
        }
        prefs(c).edit().putLong("checked", System.currentTimeMillis())
                .putString("version", r.version).putString("url", r.apkUrl).apply();
        if (compare(r.version, BuildConfig.VERSION_NAME) <= 0) {
            setStatus(c, "Up to date");
            return;
        }
        setStatus(c, "Version " + r.version + " is available");
        if (AUTO.equals(mode(c)) && ready(c) == null && fetch(c, r) && standby) installReady(c);
    }

    private static Release latest() throws IOException {
        JSONObject rel;
        try {
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            get(LATEST, 1 << 20, reply);
            rel = new JSONObject(reply.toString("UTF-8"));
        } catch (JSONException e) {
            throw new IOException("unexpected reply");
        }
        String version = rel.optString("tag_name").replaceFirst("^v", "");
        JSONArray assets = rel.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && a.optString("name").endsWith(".apk")) {
                return new Release(version, a.optString("browser_download_url"));
            }
        }
        throw new IOException("release " + version + " has no APK");
    }

    /** Compares "1.10.0" with "1.9.2" by number, part by part. */
    static int compare(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- Downloading -----------------------------------------------------------------------

    /**
     * Where the APK goes. Android 4.4's installer is another app and reads it by path, so there it goes
     * in the app's own folder on shared storage.
     */
    private static File apkFile(Context c) {
        File dir = Build.VERSION.SDK_INT < 21 ? c.getExternalCacheDir() : null;
        return new File(dir != null ? dir : c.getCacheDir(), "update.apk");
    }

    /** The downloaded, checked APK of the available version, or null. */
    static File ready(Context c) {
        Release r = available(c);
        File apk = apkFile(c);
        if (r == null || !apk.isFile() || !r.version.equals(prefs(c).getString("ready", null))) return null;
        return apk;
    }

    /** Downloads and checks the APK. Blocking. */
    private static boolean fetch(Context c, Release r) {
        File apk = apkFile(c);
        try {
            setStatus(c, "Downloading " + r.version + "…");
            try (OutputStream out = new FileOutputStream(apk)) {
                get(r.apkUrl, MAX_APK, out);
            }
            String problem = verify(c, apk);
            if (problem != null) {
                apk.delete();
                setStatus(c, "Update refused: " + problem);
                return false;
            }
            prefs(c).edit().putString("ready", r.version).apply();
            setStatus(c, "Version " + r.version + " is downloaded"
                    + (AUTO.equals(mode(c)) ? " and installs when the stereo is switched off" : ""));
            return true;
        } catch (IOException e) {
            apk.delete();
            setStatus(c, "Update failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * A GET over TLS 1.2 into out, following GitHub's redirects to its download servers by hand, since a redirect
     * Android follows itself would drop the TLS 1.2 socket factory on Android 4.4.
     */
    private static void get(String url, int max, OutputStream out) throws IOException {
        for (int hops = 0; hops < 5; hops++) {
            if (!url.startsWith("https://")) throw new IOException("not a secure link");
            HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
            c.setSSLSocketFactory(Tls12.factory());
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            c.setRequestProperty("User-Agent", "RetroDeck/" + BuildConfig.VERSION_NAME);
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String next = c.getHeaderField("Location");
                    if (next == null) throw new IOException("HTTP " + code);
                    url = new URL(new URL(url), next).toString();
                    continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[64 * 1024];
                    long total = 0;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        if ((total += n) > max) throw new IOException("download too large");
                        out.write(buf, 0, n);
                    }
                    return;
                }
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    /**
     * The APK must be RetroDeck, newer, and signed with the same key as this install. Android enforces
     * the key too; checking first gives a clear message instead of a failed install.
     */
    @SuppressWarnings("deprecation") // GET_SIGNATURES is the flag that reads an APK file on every API level
    private static String verify(Context c, File apk) {
        PackageManager pm = c.getPackageManager();
        PackageInfo update = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        if (update == null) return "not a valid APK";
        if (!c.getPackageName().equals(update.packageName)) return "wrong app";
        if (compare(update.versionName, BuildConfig.VERSION_NAME) <= 0) return "not newer";
        try {
            Signature[] mine = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            if (update.signatures != null && update.signatures.length > 0 && !Arrays.equals(mine, update.signatures)) {
                return "signed with a different key";
            }
        } catch (PackageManager.NameNotFoundException e) {
            return "can't read this app's key";
        }
        return null;
    }

    /** An install needs a tap, and RetroDeck wasn't on screen to ask: say so next time it is. */
    static void needsTap(Context c) {
        prefs(c).edit().putString("waiting", prefs(c).getString("ready", "")).remove("told").apply();
        setStatus(c, "Update downloaded: open Settings → Updates to install");
    }

    // --- Installing ------------------------------------------------------------------------

    /** Settings → Install: downloads if needed, then installs. In the background. */
    static void installNow(Context c) {
        final Context app = c.getApplicationContext();
        final Release r = available(app);
        if (r == null || busy) return;
        busy = true;
        new Thread(() -> {
            try {
                if (ready(app) != null || fetch(app, r)) installReady(app);
            } finally {
                busy = false;
            }
        }, "retrodeck-install").start();
    }

    /** The stereo was just switched off: in Automatic, installs a downloaded update. */
    static void onStandby(Context c) {
        if (!AUTO.equals(mode(c)) || ready(c) == null || busy) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> installReady(app), "retrodeck-install").start();
    }

    /** Hands the downloaded APK to Android's installer. Blocking. */
    private static void installReady(Context c) {
        File apk = ready(c);
        if (apk == null) return;
        setStatus(c, "Installing " + prefs(c).getString("ready", "") + "…");
        try {
            if (Build.VERSION.SDK_INT >= 21) commit(c, apk);
            else legacyInstall(c, apk);
        } catch (IOException | RuntimeException e) {
            setStatus(c, "Update failed: " + e.getMessage());
        }
    }

    @TargetApi(21)
    private static void commit(Context c, File apk) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) {
            // Silent when RetroDeck is the installer of record of itself; otherwise Android asks.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream out = s.openWrite("base.apk", 0, apk.length());
                 InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent done = PendingIntent.getBroadcast(c, id, new Intent(c, UpdateReceiver.class), flags);
            s.commit(done.getIntentSender());
        }
    }

    /** Android 4.4 has no PackageInstaller for apps: its installer screen asks, so only while on screen. */
    @SuppressWarnings("deprecation") // ACTION_INSTALL_PACKAGE is the way on Android 4.4
    private static void legacyInstall(Context c, File apk) {
        if (visible <= 0) {
            needsTap(c);
            return;
        }
        if (c.getExternalCacheDir() == null) {
            setStatus(c, "Update failed: the installer can't read it without shared storage");
            return;
        }
        Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromFile(apk));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        setStatus(c, "Waiting for you to confirm the install");
    }
}
