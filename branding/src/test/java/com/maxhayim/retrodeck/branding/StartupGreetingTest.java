package com.maxhayim.retrodeck.branding;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StartupGreetingTest {

    @Test
    public void blankGreetingIsHelloAndTheCar() {
        assertEquals("HELLO  1989 Halcyon Mesa GT", new StartupConfig().greetingFor("1989 Halcyon Mesa GT"));
        assertEquals("HELLO", new StartupConfig().greetingFor(""));
    }

    @Test
    public void ownGreetingInAnyLanguageWithTheCarsName() {
        StartupConfig c = new StartupConfig();
        c.greeting = "おかえり {car}";
        assertEquals("おかえり Mesa GT", c.greetingFor("Mesa GT"));
        c.greeting = "ברוך הבא";
        assertEquals("ברוך הבא", c.greetingFor("Mesa GT"));
    }

    @Test
    public void greetingIsCleanedAndKeptInPacks() throws Exception {
        assertEquals("Привет", StartupConfig.cleanGreeting("  При\nвет \t"));
        VehicleProfile p = ProfileJsonTest.sample();
        p.startup.greeting = "Добро пожаловать";
        VehicleProfile back = ProfileJson.read(ProfileJson.write(p));
        assertEquals("Добро пожаловать", back.startup.greeting);
    }
}
