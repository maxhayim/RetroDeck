package com.maxhayim.retrodeck.branding;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vehicle profiles on this device: files/branding/profiles.json plus one folder of optimised logos per
 * profile. Nothing leaves the device unless the user exports a pack.
 */
public final class ProfileStore {

    private static final String TAG = "RetroDeck";
    private static final int VERSION = 1;
    private static ProfileStore instance;

    private final File dir;
    private final AtomicFile file;
    private final List<VehicleProfile> profiles = new ArrayList<>();
    private String activeId;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static synchronized ProfileStore get(Context context) {
        if (instance == null) instance = new ProfileStore(new File(context.getApplicationContext().getFilesDir(), "branding"));
        return instance;
    }

    ProfileStore(File dir) {
        this.dir = dir;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        file = new AtomicFile(new File(dir, "profiles.json"));
        load();
        if (profiles.isEmpty()) {
            VehicleProfile p = newProfile();
            profiles.add(p);
            activeId = p.id;
            persist();
        }
    }

    private void load() {
        try {
            String s = new String(file.readFully(), StandardCharsets.UTF_8);
            JSONObject o = new JSONObject(s);
            JSONArray list = o.optJSONArray("profiles");
            for (int i = 0; list != null && i < list.length(); i++) {
                VehicleProfile p = ProfileJson.read(list.getJSONObject(i));
                if (p.id.isEmpty() || find(p.id) != null) continue;
                // Forget logos whose files have gone missing.
                for (int r = 0; r < 2; r++) if (p.logoFile[r] != null && !logo(p, r).exists()) p.logoFile[r] = null;
                if (p.animationFile != null && !animation(p).exists()) p.animationFile = null;
                profiles.add(p);
            }
            activeId = o.optString("active", null);
        } catch (java.io.FileNotFoundException e) {
            // First run.
        } catch (IOException | JSONException e) {
            Log.w(TAG, "profiles.json unreadable, starting fresh", e);
        }
        if (find(activeId) == null && !profiles.isEmpty()) activeId = profiles.get(0).id;
    }

    private synchronized void persist() {
        FileOutputStream out = null;
        try {
            JSONObject o = new JSONObject();
            o.put("version", VERSION);
            o.put("active", activeId);
            JSONArray list = new JSONArray();
            for (VehicleProfile p : profiles) list.put(ProfileJson.write(p));
            o.put("profiles", list);
            out = file.startWrite();
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (IOException | JSONException e) {
            if (out != null) file.failWrite(out);
            Log.e(TAG, "Couldn't save profiles", e);
        }
    }

    // ---- reading ----

    public synchronized List<VehicleProfile> profiles() {
        List<VehicleProfile> out = new ArrayList<>();
        for (VehicleProfile p : profiles) out.add(p.copy());
        return out;
    }

    /** A copy of the active profile; change it and pass it to save(). */
    public synchronized VehicleProfile active() {
        VehicleProfile p = find(activeId);
        return p == null ? profiles.get(0).copy() : p.copy();
    }

    public synchronized String activeId() {
        return activeId;
    }

    public synchronized VehicleProfile profile(String id) {
        VehicleProfile p = find(id);
        return p == null ? null : p.copy();
    }

    private VehicleProfile find(String id) {
        if (id == null) return null;
        for (VehicleProfile p : profiles) if (p.id.equals(id)) return p;
        return null;
    }

    /** The folder holding a profile's logos. */
    public File folder(String profileId) {
        return new File(dir, ProfileJson.safeId(profileId).isEmpty() ? "_" : profileId);
    }

    /** The logo file for a role, which may not exist. */
    public File logo(VehicleProfile p, int role) {
        return p.logoFile[role] == null ? null : new File(folder(p.id), p.logoFile[role]);
    }

    /** A fresh file name for a newly imported logo, so a cancelled edit never overwrites the old one. */
    public File newLogoFile(String profileId, int role) {
        File f = folder(profileId);
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return new File(f, VehicleProfile.ROLES[role] + "-" + System.currentTimeMillis() + ".png");
    }

    /** The profile's animation GIF, which may not exist; null when it has none. */
    public File animation(VehicleProfile p) {
        return p.animationFile == null ? null : new File(folder(p.id), p.animationFile);
    }

    /** A fresh file name for a newly chosen animation. */
    public File newAnimationFile(String profileId) {
        File f = folder(profileId);
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return new File(f, "animation-" + System.nanoTime() + ".gif");
    }

    // ---- changing ----

    public VehicleProfile newProfile() {
        VehicleProfile p = new VehicleProfile(UUID.randomUUID().toString());
        p.updatedAt = System.currentTimeMillis();
        return p;
    }

    public synchronized void add(VehicleProfile p, boolean makeActive) {
        if (p.id == null || p.id.isEmpty() || find(p.id) != null) p.id = UUID.randomUUID().toString();
        profiles.add(p.copy());
        if (makeActive) activeId = p.id;
        commit();
    }

    /** Saves changes to an existing profile, and removes logo files it no longer uses. */
    public synchronized void save(VehicleProfile p) {
        p.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(p.id)) {
                profiles.set(i, p.copy());
                cleanFolder(p);
                commit();
                return;
            }
        }
        add(p, false);
    }

    public synchronized void setActive(String id) {
        if (find(id) == null || id.equals(activeId)) return;
        activeId = id;
        commit();
    }

    /** A copy of a profile, with copies of its logos. */
    public synchronized VehicleProfile duplicate(String id) {
        VehicleProfile src = find(id);
        if (src == null) return null;
        VehicleProfile p = src.copy();
        p.id = UUID.randomUUID().toString();
        p.trim = p.trim.isEmpty() ? "Copy" : VehicleProfile.cleanName(p.trim + " copy");
        for (int r = 0; r < 2; r++) {
            File from = logo(src, r);
            if (from == null || !from.exists()) {
                p.logoFile[r] = null;
                continue;
            }
            File to = newLogoFile(p.id, r);
            if (copy(from, to)) p.logoFile[r] = to.getName();
            else p.logoFile[r] = null;
        }
        File anim = animation(src);
        p.animationFile = null;
        if (anim != null && anim.exists()) {
            File to = newAnimationFile(p.id);
            if (copy(anim, to)) p.animationFile = to.getName();
        }
        add(p, false);
        return p.copy();
    }

    public synchronized void delete(String id) {
        VehicleProfile p = find(id);
        if (p == null) return;
        profiles.remove(p);
        deleteFolder(folder(id));
        if (profiles.isEmpty()) profiles.add(newProfile());
        if (id.equals(activeId)) activeId = profiles.get(0).id;
        commit();
    }

    /** Puts a profile's layout, colours, skin and start-up back to defaults. Names, logos and its animation stay. */
    public synchronized VehicleProfile restoreDefaults(String id) {
        VehicleProfile p = find(id);
        if (p == null) return null;
        VehicleProfile fresh = new VehicleProfile(p.id);
        fresh.manufacturer = p.manufacturer;
        fresh.model = p.model;
        fresh.year = p.year;
        fresh.trim = p.trim;
        for (int r = 0; r < 2; r++) {
            fresh.logoFile[r] = p.logoFile[r];
            fresh.logoColor[r] = p.logoColor[r];
        }
        fresh.animationFile = p.animationFile;
        save(fresh);
        return fresh.copy();
    }

    /** Deletes every profile and logo and starts with one empty profile. */
    public synchronized void resetAll() {
        for (VehicleProfile p : profiles) deleteFolder(folder(p.id));
        profiles.clear();
        VehicleProfile p = newProfile();
        profiles.add(p);
        activeId = p.id;
        commit();
    }

    private void cleanFolder(VehicleProfile p) {
        File[] files = folder(p.id).listFiles();
        if (files == null) return;
        Set<String> keep = new HashSet<>();
        for (String f : p.logoFile) if (f != null) keep.add(f);
        if (p.animationFile != null) keep.add(p.animationFile);
        // Only one editor works on a profile at a time, and it saves the file it imported, so anything
        // unreferenced now is a logo that was replaced.
        for (File f : files) {
            if (!keep.contains(f.getName())) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    /** Removes a logo file an editor imported and then threw away. */
    public void discard(File f) {
        if (f != null && f.getParentFile() != null && f.getParentFile().getParentFile() != null
                && f.getParentFile().getParentFile().equals(dir)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static void deleteFolder(File f) {
        File[] files = f.listFiles();
        if (files != null) for (File c : files) {
            //noinspection ResultOfMethodCallIgnored
            c.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static boolean copy(File from, File to) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ---- listeners ----

    public synchronized void addListener(Runnable r) {
        listeners.add(r);
    }

    public synchronized void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void commit() {
        persist();
        final List<Runnable> copy = new ArrayList<>(listeners);
        main.post(() -> {
            for (Runnable r : copy) r.run();
        });
    }
}
