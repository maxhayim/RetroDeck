package com.maxhayim.retrodeck.core;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * A feature module plugged into RetroDeck. The app registers its modules at start-up; each one adds its
 * own rows to Settings, so the app shell doesn't need to know what a module does.
 */
public interface RetroDeckModule {

    /** One row in Settings, under a section such as "Personalization". */
    final class SettingsEntry {
        public final String section, title, summary;
        public final Intent intent;

        public SettingsEntry(String section, String title, String summary, Intent intent) {
            this.section = section;
            this.title = title;
            this.summary = summary;
            this.intent = intent;
        }
    }

    String id();

    void onCreate(Application app);

    List<SettingsEntry> settings(Context context);
}
