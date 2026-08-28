package com.leonardobishop.quests.bukkit.tasktype.type;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentBlockPlacementTrackerTest {

    private static final long WINDOW = 10_000L;
    private final UUID player = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();
    private final RecentBlockPlacementTracker tracker = new RecentBlockPlacementTracker();

    @Test
    void consumesOnlyTheSamePlayersBlockAtExactCoordinates() {
        tracker.record(player, world, 10, 64, -5, "STONE", 1_000L, WINDOW);

        assertFalse(tracker.consume(player, world, 11, 64, -5, "STONE", 2_000L, WINDOW));
        assertTrue(tracker.consume(player, world, 10, 64, -5, "STONE", 2_000L, WINDOW));
        assertFalse(tracker.consume(player, world, 10, 64, -5, "STONE", 2_001L, WINDOW));
    }

    @Test
    void doesNotMatchAnotherPlayerWorldOrMaterial() {
        tracker.record(player, world, 10, 64, -5, "STONE", 1_000L, WINDOW);
        assertFalse(tracker.consume(UUID.randomUUID(), world, 10, 64, -5, "STONE", 2_000L, WINDOW));

        tracker.record(player, world, 10, 64, -5, "STONE", 1_000L, WINDOW);
        assertFalse(tracker.consume(player, UUID.randomUUID(), 10, 64, -5, "STONE", 2_000L, WINDOW));

        tracker.record(player, world, 10, 64, -5, "STONE", 1_000L, WINDOW);
        assertFalse(tracker.consume(player, world, 10, 64, -5, "DIRT", 2_000L, WINDOW));
    }

    @Test
    void forgetsPlacementAfterWindow() {
        tracker.record(player, world, 10, 64, -5, "STONE", 1_000L, WINDOW);

        assertFalse(tracker.consume(player, world, 10, 64, -5, "STONE", 11_001L, WINDOW));
    }
}
