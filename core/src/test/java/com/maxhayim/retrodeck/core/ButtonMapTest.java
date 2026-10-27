package com.maxhayim.retrodeck.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ButtonMapTest {

    @Test
    public void defaultsCoverMediaKeysAndPresets() {
        ButtonMap m = ButtonMap.defaults();
        assertEquals(ButtonMap.PLAY_PAUSE, m.action(ButtonMap.KEY_PLAY_PAUSE));
        assertEquals(ButtonMap.NEXT, m.action(ButtonMap.KEY_NEXT));
        assertEquals(ButtonMap.MENU, m.action(ButtonMap.KEY_MENU));
        assertEquals("preset_1", m.action(ButtonMap.KEY_1));
        assertEquals("preset_6", m.action(ButtonMap.KEY_1 + 5));
        assertNull(m.action(24));
    }

    @Test
    public void roundTrips() {
        ButtonMap m = ButtonMap.defaults();
        m.set(24, ButtonMap.VOL_UP);
        m.set(ButtonMap.KEY_NEXT, null);
        ButtonMap back = ButtonMap.parse(m.toString());
        assertEquals(m.toString(), back.toString());
        assertEquals(ButtonMap.VOL_UP, back.action(24));
        assertNull(back.action(ButtonMap.KEY_NEXT));
    }

    @Test
    public void emptyIsNotDefaults() {
        // Someone who cleared every key keeps none.
        assertTrue(ButtonMap.parse("").codes().isEmpty());
        assertFalse(ButtonMap.parse(null).codes().isEmpty());
    }

    @Test
    public void ignoresWhatItCannotUse() {
        ButtonMap m = ButtonMap.parse("4=next,3=menu,abc=play,87=launch_missiles,88=prev,=,99999=play");
        assertEquals(1, m.codes().size());
        assertEquals(ButtonMap.PREV, m.action(88));
        m.set(4, ButtonMap.NEXT);
        assertNull("Back stays with Android", m.action(4));
    }

    @Test
    public void everyOnScreenControlDoesSomething() {
        String[] controls = {Skin.POWER, Skin.SOURCE, Skin.PREV, Skin.PLAY, Skin.NEXT, Skin.DISPLAY, Skin.MENU,
                Skin.VOL_DOWN, Skin.VOL_UP};
        for (String c : controls) assertTrue(c, ButtonMap.ACTIONS.contains(ButtonMap.forControl(c, 0)));
        for (int i = 1; i <= 6; i++) assertEquals(i, ButtonMap.preset(ButtonMap.forControl(Skin.PRESET, i)));
        assertNull(ButtonMap.forControl(Skin.PRESET, 7));
    }

    @Test
    public void everyActionHasALabel() {
        for (String a : ButtonMap.ACTIONS) assertFalse(a, ButtonMap.label(a).equals("Nothing"));
    }
}
