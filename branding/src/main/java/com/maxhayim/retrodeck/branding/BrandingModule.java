package com.maxhayim.retrodeck.branding;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.maxhayim.retrodeck.core.RetroDeckModule;

import java.util.Collections;
import java.util.List;

/** The Vehicle Branding & Customization module. */
public final class BrandingModule implements RetroDeckModule {

    public static final String ID = "vehicle-branding";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onCreate(Application app) {
        // Loads profiles once, off the first screen's critical path.
        ProfileStore.get(app);
    }

    @Override
    public List<SettingsEntry> settings(Context c) {
        VehicleProfile p = ProfileStore.get(c).active();
        return Collections.singletonList(new SettingsEntry("Personalization", "Vehicle Branding",
                p.displayName() + " · logos, colours, startup", new Intent(c, VehicleBrandingActivity.class)));
    }
}
