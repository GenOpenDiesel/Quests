package com.leonardobishop.quests.bukkit.tasktype.type;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedBlockCycleTrackerTest {

    private static final long RESET = 600_000L;
    private static final int ALLOWED_CYCLES = 5;
    private final UUID player = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();
    private final RepeatedBlockCycleTracker tracker = new RepeatedBlockCycleTracker();

    @Test
    void blocksTheSixthAndLaterCycleAtTheSameCoordinates() {
        long now = 1_000L;
        for (int cycle = 1; cycle <= ALLOWED_CYCLES; cycle++) {
            tracker.recordPlacement(player, world, 10, 64, -5, "STONE", now++, RESET);
            assertFalse(tracker.registerBreak(player, world, 10, 64, -5, "STONE",
                    now++, RESET, ALLOWED_CYCLES));
        }

        tracker.recordPlacement(player, world, 10, 64, -5, "STONE", now++, RESET);
        assertTrue(tracker.registerBreak(player, world, 10, 64, -5, "STONE",
                now++, RESET, ALLOWED_CYCLES));

        tracker.recordPlacement(player, world, 10, 64, -5, "STONE", now++, RESET);
        assertTrue(tracker.registerBreak(player, world, 10, 64, -5, "STONE",
                now, RESET, ALLOWED_CYCLES));
    }

    @Test
    void countsOnlyCompleteCyclesForTheSamePlayerWorldCoordinatesAndMaterial() {
        tracker.recordPlacement(player, world, 10, 64, -5, "STONE", 1_000L, RESET);

        assertFalse(tracker.registerBreak(UUID.randomUUID(), world, 10, 64, -5, "STONE",
                2_000L, RESET, 0));
        assertFalse(tracker.registerBreak(player, UUID.randomUUID(), 10, 64, -5, "STONE",
                2_000L, RESET, 0));
        assertFalse(tracker.registerBreak(player, world, 11, 64, -5, "STONE",
                2_000L, RESET, 0));
        assertFalse(tracker.registerBreak(player, world, 10, 64, -5, "DIRT",
                2_000L, RESET, 0));
        assertTrue(tracker.registerBreak(player, world, 10, 64, -5, "STONE",
                2_000L, RESET, 0));

        // A second break without another placement is not another cycle.
        assertFalse(tracker.registerBreak(player, world, 10, 64, -5, "STONE",
                2_001L, RESET, 0));
    }

    @Test
    void resetsTheCounterAfterAProperBreakInActivity() {
        long now = 1_000L;
        for (int cycle = 0; cycle < ALLOWED_CYCLES; cycle++) {
            tracker.recordPlacement(player, world, 10, 64, -5, "STONE", now++, RESET);
            tracker.registerBreak(player, world, 10, 64, -5, "STONE",
                    now++, RESET, ALLOWED_CYCLES);
        }

        now += RESET + 1;
        tracker.recordPlacement(player, world, 10, 64, -5, "STONE", now++, RESET);
        assertFalse(tracker.registerBreak(player, world, 10, 64, -5, "STONE",
                now, RESET, ALLOWED_CYCLES));
    }
}
