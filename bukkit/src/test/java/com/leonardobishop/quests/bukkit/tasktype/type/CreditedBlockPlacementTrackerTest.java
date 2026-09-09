package com.leonardobishop.quests.bukkit.tasktype.type;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditedBlockPlacementTrackerTest {

    private static final long RESET = 600_000L;
    private final UUID player = UUID.randomUUID();
    private final UUID world = UUID.randomUUID();
    private final CreditedBlockPlacementTracker tracker = new CreditedBlockPlacementTracker();
    private final CreditedBlockPlacementTracker.TaskKey grassTask =
            new CreditedBlockPlacementTracker.TaskKey("trudny51krecik", "postaw_trawe");

    @Test
    void returnsOnlyTasksCreditedAtTheExactCoordinates() {
        CreditedBlockPlacementTracker.TaskKey otherTask =
                new CreditedBlockPlacementTracker.TaskKey("another-quest", "another-task");
        Set<CreditedBlockPlacementTracker.TaskKey> tasks = Set.of(grassTask, otherTask);
        tracker.recordPlacement(player, world, 10, 64, -5, "GRASS_BLOCK", tasks, 1_000L, RESET);

        assertTrue(tracker.consumeBreak(UUID.randomUUID(), world, 10, 64, -5,
                "GRASS_BLOCK", 2_000L, RESET).isEmpty());
        assertTrue(tracker.consumeBreak(player, UUID.randomUUID(), 10, 64, -5,
                "GRASS_BLOCK", 2_000L, RESET).isEmpty());
        assertTrue(tracker.consumeBreak(player, world, 11, 64, -5,
                "GRASS_BLOCK", 2_000L, RESET).isEmpty());
        assertTrue(tracker.consumeBreak(player, world, 10, 64, -5,
                "DIRT", 2_000L, RESET).isEmpty());

        assertEquals(tasks, tracker.consumeBreak(player, world, 10, 64, -5,
                "GRASS_BLOCK", 2_000L, RESET));

        // The credited placement can reverse progress only once.
        assertTrue(tracker.consumeBreak(player, world, 10, 64, -5,
                "GRASS_BLOCK", 2_001L, RESET).isEmpty());
    }

    @Test
    void doesNotReverseAnExpiredPlacementCredit() {
        tracker.recordPlacement(player, world, 10, 64, -5, "GRASS_BLOCK",
                Set.of(grassTask), 1_000L, RESET);

        assertTrue(tracker.consumeBreak(player, world, 10, 64, -5,
                "GRASS_BLOCK", 1_000L + RESET + 1, RESET).isEmpty());
    }

    @Test
    void replacesCreditWhenAnotherBlockIsPlacedAtTheSameCoordinates() {
        CreditedBlockPlacementTracker.TaskKey dirtTask =
                new CreditedBlockPlacementTracker.TaskKey("another-quest", "postaw_dirta");
        tracker.recordPlacement(player, world, 10, 64, -5, "GRASS_BLOCK",
                Set.of(grassTask), 1_000L, RESET);
        tracker.recordPlacement(player, world, 10, 64, -5, "DIRT",
                Set.of(dirtTask), 2_000L, RESET);

        assertTrue(tracker.consumeBreak(player, world, 10, 64, -5,
                "GRASS_BLOCK", 3_000L, RESET).isEmpty());
        assertEquals(Set.of(dirtTask), tracker.consumeBreak(player, world, 10, 64, -5,
                "DIRT", 3_000L, RESET));
    }
}
