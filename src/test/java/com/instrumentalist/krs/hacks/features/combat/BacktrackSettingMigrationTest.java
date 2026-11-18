package com.instrumentalist.krs.hacks.features.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BacktrackSettingMigrationTest {
    private final Backtrack backtrack = new Backtrack();

    @Test
    void mapsOldLiquidLabelsToShortenedLabels() {
        assertEquals("Next Min Delay", backtrack.migrateSettingName("Min Next Backtrack Delay"));
        assertEquals("Next Max Delay", backtrack.migrateSettingName("Max Next Backtrack Delay"));
        assertEquals("Track Buffer", backtrack.migrateSettingName("Tracking Buffer"));
        assertEquals("Pause On Hurt", backtrack.migrateSettingName("Pause On Hurt Time"));
        assertEquals("Attack Window", backtrack.migrateSettingName("Last Attack Time To Work"));
    }

    @Test
    void mapsOldMyauLabelsToShortenedLabels() {
        assertEquals("Myau Hit Window", backtrack.migrateSettingName("Myau Last Attack Time"));
        assertEquals("Myau Hit Window", backtrack.migrateSettingName("Myau Attack Window"));
        assertEquals("Myau Adapt Delay", backtrack.migrateSettingName("Myau Adaptive Delay"));
        assertEquals("Myau Release Hit", backtrack.migrateSettingName("Myau Release On Hit"));
    }

    @Test
    void leavesUnknownAndCurrentNamesUntouched() {
        assertEquals("Chance", backtrack.migrateSettingName("Chance"));
        assertEquals("Some Future Setting", backtrack.migrateSettingName("Some Future Setting"));
    }
}
