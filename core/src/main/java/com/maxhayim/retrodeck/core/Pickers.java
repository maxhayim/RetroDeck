package com.maxhayim.retrodeck.core;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

/**
 * Opening and saving files on any head unit. Uses the system document picker where there is one,
 * falls back to any file manager, and reports when a device has neither, so callers can offer their
 * own folder instead. Some Android TV and head-unit builds register a do-nothing stub for the picker;
 * that counts as no picker.
 */
public final class Pickers {

    private Pickers() {}

    /** Asks the user for a file to open. False when the device has no picker; the caller explains what to do. */
    public static boolean open(Activity a, String[] mimeTypes, int requestCode) {
        Intent doc = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        doc.addCategory(Intent.CATEGORY_OPENABLE);
        doc.setType(mimeTypes.length == 1 ? mimeTypes[0] : "*/*");
        if (mimeTypes.length > 1) doc.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        if (usable(a, doc) && start(a, doc, requestCode)) return true;

        Intent any = new Intent(Intent.ACTION_GET_CONTENT);
        any.addCategory(Intent.CATEGORY_OPENABLE);
        any.setType(mimeTypes.length == 1 ? mimeTypes[0] : "*/*");
        return usable(a, any) && start(a, Intent.createChooser(any, null), requestCode);
    }

    /** Asks where to save a new file. False when the device can't; callers then save to their own folder. */
    public static boolean create(Activity a, String mimeType, String name, int requestCode) {
        Intent doc = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        doc.addCategory(Intent.CATEGORY_OPENABLE);
        doc.setType(mimeType);
        doc.putExtra(Intent.EXTRA_TITLE, name);
        return usable(a, doc) && start(a, doc, requestCode);
    }

    /** Keeps read access to an opened document across restarts, where Android allows it. */
    public static void keep(Activity a, Intent data) {
        Uri uri = data == null ? null : data.getData();
        if (uri == null) return;
        try {
            a.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException ignored) {
            // File managers using GET_CONTENT don't offer lasting access; the file is used right away.
        }
    }

    private static boolean usable(Activity a, Intent i) {
        ResolveInfo r = a.getPackageManager().resolveActivity(i, 0);
        if (r == null || r.activityInfo == null) return false;
        return !r.activityInfo.packageName.contains("frameworkpackagestubs");
    }

    private static boolean start(Activity a, Intent i, int requestCode) {
        try {
            a.startActivityForResult(i, requestCode);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
