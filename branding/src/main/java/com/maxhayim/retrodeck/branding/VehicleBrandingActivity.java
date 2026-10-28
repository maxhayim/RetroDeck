package com.maxhayim.retrodeck.branding;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.maxhayim.retrodeck.core.AspectFrame;
import com.maxhayim.retrodeck.core.Illumination;
import com.maxhayim.retrodeck.core.KeyLights;
import com.maxhayim.retrodeck.core.Pickers;
import com.maxhayim.retrodeck.core.Skin;
import com.maxhayim.retrodeck.core.Skins;
import com.maxhayim.retrodeck.core.StereoView;
import com.maxhayim.retrodeck.core.Ui;
import com.maxhayim.retrodeck.core.Visualizers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Settings → Personalization → Vehicle Branding. Rows on the left, a live preview of the stereo on
 * the right that follows every change.
 */
public class VehicleBrandingActivity extends Activity {

    static final String[] IMAGE_TYPES = {"image/png", "image/jpeg", "image/webp", "image/svg+xml"};
    private static final String[] PACK_TYPES = {"application/zip", "application/x-zip-compressed", "application/octet-stream"};
    private static final String[] GIF_TYPES = {"image/gif"};
    private static final int PICK_LOGO = 1, PICK_BADGE = 2, EXPORT_PACK = 3, IMPORT_PACK = 4, PICK_GIF = 5;

    private ProfileStore store;
    private VehicleProfile profile;
    private StereoView preview;
    private BrandingRenderer renderer;
    private TextView previewTitle;
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<VehicleProfile> pendingExport;

    private Ui.Row rowProfiles, rowManufacturer, rowModel, rowYear, rowTrim, rowLogo, rowBadge, rowEditLogo,
            rowPlacement, rowSkin, rowVisualizer, rowAnimation, rowIllumination, rowAccent, rowVisColour, rowScreen, rowBody, rowKeys, rowStartup;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = ProfileStore.get(this);
        profile = store.active();

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.header(this, "Vehicle Branding", "Settings  ›  Personalization"));
        LinearLayout body = Ui.horizontal(this);
        body.setGravity(0);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout list = Ui.vertical(this);
        list.setBackgroundColor(Ui.PANEL);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));
        buildRows(list);
        body.addView(Ui.scroll(this, list), Ui.weight(0.44f));

        LinearLayout right = Ui.vertical(this);
        right.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        previewTitle = Ui.text(this, "", 16, Ui.TEXT, true);
        right.addView(previewTitle);
        right.addView(Ui.text(this, "Live preview", 13, Ui.SUB, false));
        AspectFrame frame = new AspectFrame(this, 1024f / 600f);
        preview = new StereoView(this);
        preview.setState(Previews.state());
        renderer = new BrandingRenderer(store, profile);
        preview.setOverlay(renderer);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        right.addView(frame, Ui.margins(new LinearLayout.LayoutParams(-1, 0, 1), this, 0, 10, 0, 10));
        LinearLayout buttons = Ui.horizontal(this);
        buttons.addView(Ui.button(this, "Play startup", true, v -> openPreview()));
        buttons.addView(Ui.button(this, "Edit placement", false, v -> startActivity(new Intent(this, PlacementEditorActivity.class))),
                Ui.margins(Ui.wrap(), this, 10, 0, 0, 0));
        right.addView(buttons);
        body.addView(right, Ui.weight(0.56f));

        setContentView(root);
    }

    private void buildRows(LinearLayout l) {
        l.addView(Ui.section(this, "Vehicle"));
        rowProfiles = add(l, "Vehicle Profiles", v -> showProfiles());
        rowManufacturer = add(l, "Manufacturer", v -> editText("Manufacturer", "e.g. Jeep, Toyota, Nissan", profile.manufacturer, false,
                s -> profile.manufacturer = VehicleProfile.cleanName(s)));
        rowModel = add(l, "Model", v -> editText("Model", "e.g. Wrangler YJ, Supra, 300ZX", profile.model, false,
                s -> profile.model = VehicleProfile.cleanName(s)));
        rowYear = add(l, "Model Year", v -> editText("Model year", "e.g. 1989", profile.year, true, s -> {
            String y = VehicleProfile.cleanYear(s);
            if (!s.trim().isEmpty() && y.isEmpty()) toast("Enter a four-digit year from 1886 to 2100.");
            profile.year = y;
        }));
        rowTrim = add(l, "Trim or Edition", v -> editText("Trim or edition (optional)", "e.g. Sahara, Turbo, GT", profile.trim, false,
                s -> profile.trim = VehicleProfile.cleanName(s)));

        l.addView(Ui.section(this, "Logos"));
        rowLogo = add(l, "Import Manufacturer Logo", v -> pickLogo(VehicleProfile.MANUFACTURER));
        rowBadge = add(l, "Import Model Badge", v -> pickLogo(VehicleProfile.MODEL));
        rowEditLogo = add(l, "Adjust Logo Image", v -> chooseLogoToEdit());
        rowPlacement = add(l, "Edit Logo Placement", v -> startActivity(new Intent(this, PlacementEditorActivity.class)));

        l.addView(Ui.section(this, "Look"));
        rowSkin = add(l, "Radio Skin", v -> chooseSkin());
        rowVisualizer = add(l, "Visualizer", v -> chooseVisualizer());
        rowAnimation = add(l, "Animation", v -> chooseAnimation());
        rowIllumination = add(l, "Dashboard Illumination", v -> chooseIllumination());
        rowAccent = add(l, "Accent Colour", v -> chooseAccent());
        rowVisColour = add(l, "Visualizer Colour", v -> chooseOwnColour(OWN_VISUALIZER));
        rowScreen = add(l, "Visualizer Background", v -> chooseOwnColour(OWN_SCREEN));
        rowBody = add(l, "Radio Colour", v -> chooseOwnColour(OWN_BODY));
        rowKeys = add(l, "Key Lighting", v -> chooseKeyLighting());
        rowStartup = add(l, "Startup Animation", v -> startActivity(new Intent(this, StartupEditorActivity.class)));
        add(l, "Preview", v -> openPreview()).value("Startup, stereo and standby, full screen");

        l.addView(Ui.section(this, "Share and reset"));
        add(l, "Import/Export Branding Pack", v -> showPacks()).value("ZIP files, works offline");
        add(l, "Restore Defaults", v -> restoreDefaults()).value("Layout, colours and startup for this profile");
    }

    private Ui.Row add(LinearLayout l, String title, View.OnClickListener click) {
        Ui.Row r = Ui.row(this, title, "", click);
        l.addView(r);
        l.addView(Ui.divider(this));
        return r;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        profile = store.active();
        refresh();
    }

    private void refresh() {
        renderer.setProfile(profile);
        Previews.apply(preview, profile);
        previewTitle.setText(profile.displayName());
        int count = store.profiles().size();
        rowProfiles.value(profile.displayName() + "  ·  " + count + (count == 1 ? " profile" : " profiles"));
        rowManufacturer.value(orNotSet(profile.manufacturer));
        rowModel.value(orNotSet(profile.model));
        rowYear.value(orNotSet(profile.year));
        rowTrim.value(orNotSet(profile.trim));
        rowLogo.value(logoValue(VehicleProfile.MANUFACTURER));
        rowBadge.value(logoValue(VehicleProfile.MODEL));
        rowEditLogo.value("Crop, rotate, opacity, brightness, tint");
        rowPlacement.value(placementSummary());
        rowSkin.value(Skins.get(profile.skin).name);
        rowVisualizer.value(Visualizers.label(profile.visualizer));
        rowAnimation.value(animationValue());
        rowIllumination.value(Illumination.label(profile.illumination) + "  ·  " + Illumination.hex(profile.illuminationColor()));
        rowAccent.value(Illumination.hex(profile.accentColor));
        rowVisColour.value(profile.visualizerColor != 0 ? Illumination.hex(profile.visualizerColor) : "The display's own colour");
        rowScreen.value(profile.screenColor != 0 ? Illumination.hex(profile.screenColor)
                : "Black  ·  full screen, and the screen above a single-DIN radio");
        rowBody.value(profile.bodyColor != 0 ? Illumination.hex(profile.bodyColor) : "The skin's own");
        rowKeys.value(KeyLights.label(profile.keyLighting) + "  ·  " + KeyLights.describe(profile.keyLighting));
        rowStartup.value(profile.startup.enabled
                ? StartupConfig.label(profile.startup.preset) + (StartupConfig.INSTANT.equals(profile.startup.preset) ? ""
                : String.format(Locale.US, "  ·  %.1f s", profile.startup.durationMs / 1000f))
                : "Off");
    }

    private String logoValue(int role) {
        if (!profile.hasLogo(role)) return "Not set  ·  PNG, JPEG, WebP or SVG";
        File f = store.logo(profile, role);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
        return "Imported  ·  " + o.outWidth + " × " + o.outHeight + "  ·  tap to replace or remove";
    }

    private String placementSummary() {
        Skin skin = Skins.get(profile.skin);
        int n = 0;
        for (String z : com.maxhayim.retrodeck.core.Zones.ALL) {
            if (skin.zone(z) == null) continue;
            for (int r = 0; r < 2; r++) if (profile.placement(z, r).visible) n++;
        }
        return n + (n == 1 ? " placement" : " placements") + " on " + skin.name;
    }

    private static String orNotSet(String s) {
        return s == null || s.isEmpty() ? "Not set" : s;
    }

    private void save() {
        store.save(profile);
        profile = store.active();
        refresh();
    }

    // ---- names ----

    private interface TextResult {
        void set(String s);
    }

    private void editText(String title, String hint, String current, boolean number, TextResult result) {
        final EditText e = new EditText(this);
        e.setText(current);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(number ? 4 : VehicleProfile.MAX_NAME)});
        FrameLayout box = new FrameLayout(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(e);
        AlertDialog d = Ui.dialog(this).setTitle(title).setView(box)
                .setPositiveButton("Save", (di, w) -> {
                    result.set(e.getText().toString());
                    save();
                })
                .setNegativeButton("Cancel", null)
                .create();
        e.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        d.show();
    }

    // ---- profiles ----

    private void showProfiles() {
        final List<VehicleProfile> all = store.profiles();
        String[] names = new String[all.size()];
        int checked = 0;
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).displayName();
            if (all.get(i).id.equals(store.activeId())) checked = i;
        }
        final int[] sel = {checked};
        Ui.dialog(this).setTitle("Vehicle profiles")
                .setSingleChoiceItems(names, checked, (d, w) -> sel[0] = w)
                .setPositiveButton("Use", (d, w) -> {
                    store.setActive(all.get(sel[0]).id);
                    profile = store.active();
                    refresh();
                })
                .setNeutralButton("New", (d, w) -> newProfile())
                .setNegativeButton("More…", (d, w) -> profileActions(all.get(sel[0])))
                .show();
    }

    private void newProfile() {
        VehicleProfile p = store.newProfile();
        p.skin = profile.skin;
        store.add(p, true);
        profile = store.active();
        refresh();
        editText("New vehicle: manufacturer", "e.g. Jeep, Toyota, Nissan", "", false,
                s -> profile.manufacturer = VehicleProfile.cleanName(s));
    }

    private void profileActions(final VehicleProfile p) {
        Ui.dialog(this).setTitle(p.displayName())
                .setItems(new String[]{"Duplicate", "Delete"}, (d, w) -> {
                    if (w == 0) {
                        VehicleProfile copy = store.duplicate(p.id);
                        if (copy != null) toast("Made a copy: " + copy.displayName());
                        refresh();
                    } else {
                        Ui.dialog(this).setTitle("Delete " + p.displayName() + "?")
                                .setMessage("Its logos and settings are removed from this device.")
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    store.delete(p.id);
                                    profile = store.active();
                                    refresh();
                                })
                                .setNegativeButton("Cancel", null).show();
                    }
                }).show();
    }

    // ---- logos ----

    private void pickLogo(final int role) {
        if (!profile.hasLogo(role)) {
            chooseImage(role);
            return;
        }
        Ui.dialog(this).setTitle(role == VehicleProfile.MANUFACTURER ? "Manufacturer logo" : "Model badge")
                .setItems(new String[]{"Replace with another image", "Adjust image", "Remove"}, (d, w) -> {
                    if (w == 0) chooseImage(role);
                    else if (w == 1) editLogo(role, null, 0);
                    else {
                        profile.logoFile[role] = null;
                        profile.logoColor[role] = 0;
                        profile.adjust[role] = new LogoAdjust();
                        save();
                    }
                }).show();
    }

    /**
     * Offers the file picker plus any images in RetroDeck's import folder. Some head units and TVs
     * have no usable picker, or one that only shows photos, which leaves SVG logos unreachable.
     */
    private void chooseImage(final int role) {
        final int request = role == VehicleProfile.MANUFACTURER ? PICK_LOGO : PICK_BADGE;
        final File[] files = importFolder().listFiles((dir, n) -> n.toLowerCase(Locale.US).matches(".+\\.(png|jpe?g|webp|svg)"));
        if (files == null || files.length == 0) {
            folderOrPicker("image (PNG, JPEG, WebP or SVG)", () -> {
                if (!Pickers.open(this, IMAGE_TYPES, request)) explainFolder("image (PNG, JPEG, WebP or SVG)");
            });
            return;
        }
        java.util.Arrays.sort(files);
        String[] names = new String[files.length + 1];
        names[0] = "Choose with the file picker…";
        for (int i = 0; i < files.length; i++) names[i + 1] = files[i].getName();
        Ui.dialog(this).setTitle(role == VehicleProfile.MANUFACTURER ? "Import manufacturer logo" : "Import model badge")
                .setItems(names, (d, w) -> {
                    if (w == 0) {
                        if (!Pickers.open(this, IMAGE_TYPES, request)) explainFolder("image (PNG, JPEG, WebP or SVG)");
                    } else {
                        importLogo(role, Uri.fromFile(files[w - 1]));
                    }
                }).show();
    }

    /** Files copied here appear in the import lists; it needs no storage permission. */
    private File importFolder() {
        File f = getExternalFilesDir("import");
        if (f == null) f = new File(getFilesDir(), "import");
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        // Folders the app makes are private on newer Android; open this one so files can be copied in.
        //noinspection ResultOfMethodCallIgnored
        f.setReadable(true, false);
        //noinspection ResultOfMethodCallIgnored
        f.setWritable(true, false);
        //noinspection ResultOfMethodCallIgnored
        f.setExecutable(true, false);
        return f;
    }

    /**
     * Nothing in the import folder yet. Some devices have a picker that can't open the file, or one
     * that only says there is no app, so the folder is always offered alongside it.
     */
    private void folderOrPicker(String what, Runnable picker) {
        Ui.dialog(this).setTitle("Choose a file")
                .setMessage("Open the file picker, or copy the " + what + " into RetroDeck's import folder with a file "
                        + "manager, a USB cable or adb, and it will be listed here:\n\n" + importFolder().getPath())
                .setPositiveButton("File picker", (d, w) -> picker.run())
                .setNegativeButton("OK", null)
                .show();
    }

    private void explainFolder(String what) {
        error("No file picker", "This device has no file picker for this kind of file. Copy the " + what
                + " into this folder with a file manager, a USB cable or adb, then try again:\n\n" + importFolder().getPath());
    }

    private void chooseLogoToEdit() {
        List<String> names = new ArrayList<>();
        final List<Integer> roles = new ArrayList<>();
        if (profile.hasLogo(VehicleProfile.MANUFACTURER)) {
            names.add("Manufacturer logo");
            roles.add(VehicleProfile.MANUFACTURER);
        }
        if (profile.hasLogo(VehicleProfile.MODEL)) {
            names.add("Model badge");
            roles.add(VehicleProfile.MODEL);
        }
        if (roles.isEmpty()) {
            toast("Import a manufacturer logo or model badge first.");
            return;
        }
        if (roles.size() == 1) {
            editLogo(roles.get(0), null, 0);
            return;
        }
        Ui.dialog(this).setTitle("Adjust which image?")
                .setItems(names.toArray(new String[0]), (d, w) -> editLogo(roles.get(w), null, 0)).show();
    }

    private void editLogo(int role, File newFile, int color) {
        Intent i = new Intent(this, LogoEditorActivity.class).putExtra(LogoEditorActivity.EXTRA_ROLE, role);
        if (newFile != null) {
            i.putExtra(LogoEditorActivity.EXTRA_NEW_FILE, newFile.getName());
            i.putExtra(LogoEditorActivity.EXTRA_NEW_COLOR, color);
        }
        startActivity(i);
    }

    private void importLogo(final int role, final Uri uri) {
        final ProgressDialog progress = ProgressDialog.show(this, null, "Optimising image…", true, false);
        final String profileId = profile.id;
        new Thread(() -> {
            File dest = store.newLogoFile(profileId, role);
            try {
                LogoImporter.Result r = LogoImporter.fromUri(this, uri, dest);
                main.post(() -> {
                    dismiss(progress);
                    editLogo(role, dest, r.color);
                });
            } catch (LogoImporter.ImportException e) {
                store.discard(dest);
                main.post(() -> {
                    dismiss(progress);
                    error("Couldn't import that image", e.getMessage());
                });
            }
        }, "logo-import").start();
    }

    // ---- look ----

    private void chooseSkin() {
        final List<Skin> skins = Skins.all();
        String[] names = new String[skins.size()];
        int checked = 0;
        for (int i = 0; i < skins.size(); i++) {
            Skin k = skins.get(i);
            names[i] = k.name + "  ·  " + (k.din == 1 ? "Single DIN" : "Double DIN") + ", " + k.era;
            if (skins.get(i).id.equals(profile.skin)) checked = i;
        }
        Ui.dialog(this).setTitle("Radio skin").setSingleChoiceItems(names, checked, (d, w) -> {
            profile.skin = skins.get(w).id;
            save();
            d.dismiss();
        }).show();
    }

    private void chooseVisualizer() {
        String[] names = new String[Visualizers.ALL.length];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = Visualizers.label(Visualizers.ALL[i]);
            if (Visualizers.ALL[i].equals(profile.visualizer)) checked = i;
        }
        Ui.dialog(this).setTitle("Visualizer").setSingleChoiceItems(names, checked, (d, w) -> {
            profile.visualizer = Visualizers.ALL[w];
            save();
            d.dismiss();
        }).show();
    }

    // ---- animation ----

    private String animationValue() {
        File f = store.animation(profile);
        if (f == null) return "None  ·  a GIF from GIPHY or a file, for the Animation visualizer";
        return "GIF  ·  " + (profile.animationTint ? "display colour" : "own colours") + "  ·  "
                + (profile.animationFull ? "whole display" : "visualizer strip");
    }

    private void chooseAnimation() {
        boolean has = store.animation(profile) != null;
        List<String> items = new ArrayList<>();
        items.add("Search GIPHY");
        items.add("Import a GIF file");
        if (has) {
            items.add(profile.animationTint ? "Colours: display colour (switch to the GIF's own)" : "Colours: the GIF's own (switch to display colour)");
            items.add(profile.animationFull ? "Size: whole display (switch to visualizer strip)" : "Size: visualizer strip (switch to whole display)");
            items.add("Remove");
        }
        Ui.dialog(this).setTitle("Animation").setItems(items.toArray(new String[0]), (d, w) -> {
            switch (w) {
                case 0:
                    startActivity(new Intent(this, GiphyActivity.class));
                    break;
                case 1:
                    chooseGif();
                    break;
                case 2:
                    profile.animationTint = !profile.animationTint;
                    save();
                    break;
                case 3:
                    profile.animationFull = !profile.animationFull;
                    save();
                    break;
                default:
                    profile.animationFile = null;
                    if (Visualizers.ANIMATION.equals(profile.visualizer)) profile.visualizer = Visualizers.BARS;
                    save();
                    break;
            }
        }).show();
    }

    /** GIFs in the import folder, or the file picker, as for logos. */
    private void chooseGif() {
        final File[] files = importFolder().listFiles((dir, n) -> n.toLowerCase(Locale.US).endsWith(".gif"));
        if (files == null || files.length == 0) {
            folderOrPicker("GIF", () -> {
                if (!Pickers.open(this, GIF_TYPES, PICK_GIF)) explainFolder("GIF");
            });
            return;
        }
        java.util.Arrays.sort(files);
        String[] names = new String[files.length + 1];
        names[0] = "Choose with the file picker…";
        for (int i = 0; i < files.length; i++) names[i + 1] = files[i].getName();
        Ui.dialog(this).setTitle("Import a GIF").setItems(names, (d, w) -> {
            if (w == 0) {
                if (!Pickers.open(this, GIF_TYPES, PICK_GIF)) explainFolder("GIF");
            } else {
                importGif(Uri.fromFile(files[w - 1]));
            }
        }).show();
    }

    private void importGif(final Uri uri) {
        final ProgressDialog progress = ProgressDialog.show(this, null, "Checking animation…", true, false);
        final String profileId = profile.id;
        new Thread(() -> {
            File dest = store.newAnimationFile(profileId);
            String err = null;
            try {
                Animations.fromUri(this, uri, dest);
            } catch (Animations.ImportException e) {
                store.discard(dest);
                err = e.getMessage();
            }
            final String error = err;
            main.post(() -> {
                dismiss(progress);
                if (error != null) {
                    error("Couldn't use that GIF", error);
                    return;
                }
                profile.animationFile = dest.getName();
                profile.visualizer = Visualizers.ANIMATION;
                save();
            });
        }, "gif-import").start();
    }

    /** Backlit legends at night, printed ones by day, like keys that light with the headlights. */
    private void chooseKeyLighting() {
        String[] names = new String[KeyLights.ALL.length];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = KeyLights.label(KeyLights.ALL[i]) + ": " + KeyLights.describe(KeyLights.ALL[i]);
            if (KeyLights.ALL[i].equals(profile.keyLighting)) checked = i;
        }
        Ui.dialog(this).setTitle("Key lighting").setSingleChoiceItems(names, checked, (d, w) -> {
            profile.keyLighting = KeyLights.ALL[w];
            save();
            d.dismiss();
        }).show();
    }

    private void chooseIllumination() {
        final VehicleProfile before = profile.copy();
        final String[] mode = {profile.illumination};
        LinearLayout box = Ui.vertical(this);
        final LinearLayout chips = Ui.horizontal(this);
        chips.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 4));
        final TextView note = Ui.text(this, "", 13, Ui.SUB, false);
        note.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), 0);
        final ColorPicker picker = new ColorPicker(this, profile.customColor, c -> {
            mode[0] = Illumination.CUSTOM;
            profile.customColor = c;
            profile.illumination = Illumination.CUSTOM;
            Previews.apply(preview, profile);
        });
        final Runnable[] drawChips = new Runnable[1];
        drawChips[0] = () -> {
            chips.removeAllViews();
            for (final String m : Illumination.MODES) {
                TextView chip = Ui.chip(this, Illumination.label(m), m.equals(mode[0]), v -> {
                    mode[0] = m;
                    profile.illumination = m;
                    Previews.apply(preview, profile);
                    drawChips[0].run();
                });
                chips.addView(chip, Ui.margins(Ui.wrap(), this, 4, 0, 4, 0));
            }
            int logo = profile.logoColor[0] != 0 ? profile.logoColor[0] : profile.logoColor[1];
            note.setText(Illumination.AUTO.equals(mode[0]) && logo == 0
                    ? "No clear colour in your logos, so amber is used. Import a coloured logo or pick Custom RGB."
                    : "Lights the buttons, LCD, visualizer and volume dial. Now " + Illumination.hex(profile.illuminationColor()) + ".");
        };
        drawChips[0].run();
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.addView(chips);
        box.addView(hs);
        box.addView(note);
        box.addView(Ui.section(this, "Custom RGB"));
        box.addView(picker);
        AlertDialog dialog = Ui.dialog(this).setTitle("Dashboard illumination").setView(Ui.scroll(this, box))
                .setPositiveButton("Save", (d, w) -> save())
                .setNegativeButton("Cancel", (d, w) -> {
                    profile = before;
                    refresh();
                })
                .setOnCancelListener(d -> {
                    profile = before;
                    refresh();
                })
                .create();
        dialog.show();
        Ui.beside(dialog);
    }

    private void chooseAccent() {
        final VehicleProfile before = profile.copy();
        LinearLayout box = Ui.vertical(this);
        TextView note = Ui.text(this, "Your vehicle's second colour, used for visualizer peaks and the startup glow.", 13, Ui.SUB, false);
        note.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), 0);
        box.addView(note);
        final ColorPicker picker = new ColorPicker(this, profile.accentColor, c -> {
            profile.accentColor = c;
            Previews.apply(preview, profile);
        });
        final int logo = profile.logoColor[0] != 0 ? profile.logoColor[0] : profile.logoColor[1];
        if (logo != 0) {
            LinearLayout row = Ui.horizontal(this);
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), 0, 0);
            row.addView(Ui.chip(this, "Use logo colour " + Illumination.hex(logo), false, v -> {
                picker.set(logo);
                profile.accentColor = logo;
                Previews.apply(preview, profile);
            }));
            box.addView(row);
        }
        box.addView(picker);
        AlertDialog dialog = Ui.dialog(this).setTitle("Accent colour").setView(Ui.scroll(this, box))
                .setPositiveButton("Save", (d, w) -> save())
                .setNegativeButton("Cancel", (d, w) -> {
                    profile = before;
                    refresh();
                })
                .setOnCancelListener(d -> {
                    profile = before;
                    refresh();
                })
                .create();
        dialog.show();
        Ui.beside(dialog);
    }

    private static final int OWN_VISUALIZER = 0, OWN_SCREEN = 1, OWN_BODY = 2;

    /** The visualizer's colour, the screen behind it or the radio's body: the vehicle's own, or the skin's. */
    private void chooseOwnColour(final int which) {
        final VehicleProfile before = profile.copy();
        final String[] titles = {"Visualizer colour", "Visualizer background", "Radio colour"};
        final String[] notes = {
                "The visualizer's bars, ladder and wave. Peaks stay in the accent colour.",
                "Behind the visualizer, in full screen and on the screen above a single-DIN radio.",
                "The radio's body, keeping its shading: dark plastic takes a dark shade of it, metal a bright one, and the print stays light."};
        final String[] skins = {"Display's own", "Black", "Skin's own"};
        final int[] start = {profile.illuminationColor(), 0xFF101826, 0xFF8A1020};
        int now = own(which);
        LinearLayout box = Ui.vertical(this);
        TextView note = Ui.text(this, notes[which], 13, Ui.SUB, false);
        note.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), 0);
        box.addView(note);
        final ColorPicker picker = new ColorPicker(this, now != 0 ? now : start[which], c -> {
            setOwn(which, c);
            Previews.apply(preview, profile);
        });
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), 0, 0);
        row.addView(Ui.chip(this, skins[which], now == 0, v -> {
            setOwn(which, 0);
            Previews.apply(preview, profile);
        }));
        final int logo = profile.logoColor[0] != 0 ? profile.logoColor[0] : profile.logoColor[1];
        if (logo != 0) {
            row.addView(Ui.chip(this, "Logo colour " + Illumination.hex(logo), false, v -> {
                picker.set(logo);
                setOwn(which, logo);
                Previews.apply(preview, profile);
            }));
        }
        box.addView(row);
        box.addView(picker);
        AlertDialog dialog = Ui.dialog(this).setTitle(titles[which]).setView(Ui.scroll(this, box))
                .setPositiveButton("Save", (d, w) -> save())
                .setNegativeButton("Cancel", (d, w) -> {
                    profile = before;
                    refresh();
                })
                .setOnCancelListener(d -> {
                    profile = before;
                    refresh();
                })
                .create();
        dialog.show();
        Ui.beside(dialog);
    }

    private int own(int which) {
        return which == OWN_VISUALIZER ? profile.visualizerColor : which == OWN_SCREEN ? profile.screenColor : profile.bodyColor;
    }

    private void setOwn(int which, int color) {
        if (which == OWN_VISUALIZER) profile.visualizerColor = color;
        else if (which == OWN_SCREEN) profile.screenColor = color;
        else profile.bodyColor = color;
    }

    private void openPreview() {
        startActivity(new Intent(this, BrandingPreviewActivity.class));
    }

    // ---- packs ----

    private void showPacks() {
        Ui.dialog(this).setTitle("Branding packs")
                .setItems(new String[]{"Export this profile", "Export all profiles", "Import a pack"}, (d, w) -> {
                    if (w == 2) {
                        importPack();
                        return;
                    }
                    pendingExport = w == 0 ? Collections.singletonList(profile) : store.profiles();
                    String name = (w == 0 ? safeFileName(profile.displayName()) : "RetroDeck-vehicles") + ".zip";
                    if (!Pickers.create(this, BrandingPack.MIME, name, EXPORT_PACK)) exportToAppFolder(name);
                }).show();
    }

    private static String safeFileName(String s) {
        String out = s.replaceAll("[^A-Za-z0-9 ._-]", "").trim().replace(' ', '-');
        return out.isEmpty() ? "RetroDeck-vehicle" : out;
    }

    /** For head units without a document picker, exported packs go to RetroDeck's own folder. */
    private File exportFolder() {
        File f = getExternalFilesDir("export");
        if (f == null) f = new File(getFilesDir(), "export");
        //noinspection ResultOfMethodCallIgnored
        f.mkdirs();
        return f;
    }

    private void exportToAppFolder(final String name) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // Android 10+ keeps app folders private; Download is reachable by USB, file managers and adb.
            runExport(() -> downloadsStream(name), "Saved to Download/RetroDeck/" + name
                    + "\n\nThis device has no file picker, so the pack went to the Download folder.");
            return;
        }
        final File out = new File(exportFolder(), name);
        runExport(() -> new FileOutputStream(out), "Saved to " + out.getPath()
                + "\n\nThis device has no file picker, so packs are kept in RetroDeck's folder. Copy them with a USB cable or file manager.");
    }

    @android.annotation.TargetApi(29)
    private OutputStream downloadsStream(String name) throws java.io.IOException {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, BrandingPack.MIME);
        v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/RetroDeck");
        Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        OutputStream out = uri == null ? null : getContentResolver().openOutputStream(uri);
        if (out == null) throw new java.io.IOException("The Download folder isn't available.");
        return out;
    }

    private interface Opener<T> {
        T open() throws Exception;
    }

    /** Logos are usually someone's trademark and artwork: fine on your own car, not to hand out. */
    private static final String SHARE_NOTE = "This pack contains your logos. Only share it if you have the right to share them.";

    private static boolean hasLogos(List<VehicleProfile> profiles) {
        for (VehicleProfile p : profiles) if (p.hasLogo(VehicleProfile.MANUFACTURER) || p.hasLogo(VehicleProfile.MODEL)) return true;
        return false;
    }

    private void runExport(final Opener<OutputStream> opener, final String doneMessage) {
        final List<VehicleProfile> profiles = pendingExport;
        if (profiles == null) return;
        final ProgressDialog progress = ProgressDialog.show(this, null, "Packing…", true, false);
        new Thread(() -> {
            String error = null;
            try (OutputStream out = opener.open()) {
                PackIO.export(store, profiles, out, "RetroDeck " + version());
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String err = error;
            main.post(() -> {
                dismiss(progress);
                if (err != null) error("Couldn't export", err);
                else Ui.dialog(this).setTitle("Branding pack exported")
                        .setMessage(doneMessage + (hasLogos(profiles) ? "\n\n" + SHARE_NOTE : ""))
                        .setPositiveButton("OK", null).show();
            });
        }, "pack-export").start();
    }

    private void importPack() {
        final File[] files = importFolder().listFiles((dir, n) -> n.toLowerCase(Locale.US).endsWith(".zip"));
        if (files == null || files.length == 0) {
            folderOrPicker("branding pack (.zip)", () -> {
                if (!Pickers.open(this, PACK_TYPES, IMPORT_PACK)) explainFolder("branding pack (.zip)");
            });
            return;
        }
        java.util.Arrays.sort(files);
        String[] names = new String[files.length + 1];
        names[0] = "Choose with the file picker…";
        SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        for (int i = 0; i < files.length; i++) names[i + 1] = files[i].getName() + "  ·  " + f.format(new Date(files[i].lastModified()));
        Ui.dialog(this).setTitle("Import a branding pack")
                .setItems(names, (d, w) -> {
                    if (w > 0) runImport(() -> new FileInputStream(files[w - 1]));
                    else if (!Pickers.open(this, PACK_TYPES, IMPORT_PACK)) explainFolder("branding pack (.zip)");
                }).show();
    }

    private void runImport(final Opener<InputStream> opener) {
        final ProgressDialog progress = ProgressDialog.show(this, null, "Checking pack…", true, false);
        new Thread(() -> {
            String error = null;
            PackIO.ImportResult result = null;
            try (InputStream in = opener.open()) {
                result = PackIO.importPack(this, store, in);
            } catch (BrandingPack.InvalidPackException | LogoImporter.ImportException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "Couldn't read that file.";
            }
            final String err = error;
            final PackIO.ImportResult res = result;
            main.post(() -> {
                dismiss(progress);
                if (err != null) {
                    error("Pack not imported", err);
                    return;
                }
                profile = store.active();
                refresh();
                StringBuilder b = new StringBuilder();
                for (VehicleProfile p : res.added) b.append("• ").append(p.displayName()).append('\n');
                Ui.dialog(this).setTitle(res.added.size() == 1 ? "Imported 1 vehicle" : "Imported " + res.added.size() + " vehicles")
                        .setMessage(b.toString().trim() + "\n\nThe first one is now active.").setPositiveButton("OK", null).show();
            });
        }, "pack-import").start();
    }

    // ---- reset ----

    private void restoreDefaults() {
        Ui.dialog(this).setTitle("Restore defaults")
                .setItems(new String[]{"Reset this profile's layout, colours, skin and startup",
                        "Delete all profiles and logos"}, (d, w) -> {
                    if (w == 0) {
                        store.restoreDefaults(profile.id);
                        profile = store.active();
                        refresh();
                        toast("Restored defaults. Names and logos were kept.");
                    } else {
                        Ui.dialog(this).setTitle("Delete everything?")
                                .setMessage("Every vehicle profile and imported logo is removed from this device. Export a pack first if you want to keep them.")
                                .setPositiveButton("Delete all", (d2, w2) -> {
                                    store.resetAll();
                                    LogoCache.get().clear();
                                    profile = store.active();
                                    refresh();
                                })
                                .setNegativeButton("Cancel", null).show();
                    }
                }).show();
    }

    // ---- results ----

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        switch (request) {
            case PICK_LOGO:
                importLogo(VehicleProfile.MANUFACTURER, uri);
                break;
            case PICK_BADGE:
                importLogo(VehicleProfile.MODEL, uri);
                break;
            case EXPORT_PACK:
                runExport(() -> getContentResolver().openOutputStream(uri), "Your branding pack was saved.");
                break;
            case IMPORT_PACK:
                runImport(() -> getContentResolver().openInputStream(uri));
                break;
            case PICK_GIF:
                importGif(uri);
                break;
        }
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void error(String title, String message) {
        Ui.dialog(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private static void dismiss(ProgressDialog d) {
        try {
            d.dismiss();
        } catch (IllegalArgumentException ignored) {
            // The activity went away while working.
        }
    }
}
