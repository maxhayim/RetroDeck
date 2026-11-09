package com.maxhayim.retrodeck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OtherAppsTest {

    @Test
    public void findsRadiosAndPlayersByName() {
        assertTrue(OtherApps.isMusicName("com.example.radio", "Radio"));
        assertTrue(OtherApps.isMusicName("com.example.btmusic", "BT Music"));
        assertTrue(OtherApps.isMusicName("com.example.player", "Music"));
        assertTrue(OtherApps.isMusicName("com.example.fm", "FM"));
        assertFalse(OtherApps.isMusicName("com.example.video", "Video Player"));
        assertFalse(OtherApps.isMusicName("com.example.dvr", "Dash Cam Recorder"));
        assertFalse(OtherApps.isMusicName("com.example.maps", "Maps"));
    }

    @Test
    public void sourceNamesAreShortCapitals() {
        assertEquals("RADIO", MainActivity.sourceName("Radio"));
        assertEquals("RADIO", MainActivity.sourceName("FM Radio"));
        assertEquals("BT", MainActivity.sourceName("Bluetooth Music"));
        assertEquals("AUX", MainActivity.sourceName(""));
        assertEquals("PODCASTS", MainActivity.sourceName("Podcasts"));
        assertEquals("MUSIC", MainActivity.sourceName("Music Player"));
    }
}
