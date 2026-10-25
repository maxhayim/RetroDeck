package com.maxhayim.retrodeck;

import android.app.Application;

import com.maxhayim.retrodeck.branding.BrandingModule;
import com.maxhayim.retrodeck.core.RetroDeckModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Starts RetroDeck's feature modules. New modules are added to this list and nowhere else. */
public class RetroDeckApp extends Application {

    private static final List<RetroDeckModule> MODULES = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        MODULES.add(new BrandingModule());
        for (RetroDeckModule m : MODULES) m.onCreate(this);
    }

    public static List<RetroDeckModule> modules() {
        return Collections.unmodifiableList(MODULES);
    }
}
