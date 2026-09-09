package com.leonardobishop.quests.bukkit.tasktype.type;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks block placements which actually increased building-task progress.
 * Entries contain no Bukkit objects, so stale credits cannot retain worlds.
 */
final class CreditedBlockPlacementTracker {

    private static final int CLEANUP_THRESHOLD = 4_096;
    private final Map<BlockKey, PlacementCredit> credits = new ConcurrentHashMap<>();

    void recordPlacement(UUID playerId, UUID worldId, int x, int y, int z, String material,
                         Collection<TaskKey> tasks, long now, long resetMillis) {
        if (tasks.isEmpty()) {
            return;
        }

        BlockKey key = new BlockKey(playerId, worldId, x, y, z);
        credits.put(key, new PlacementCredit(material, Set.copyOf(tasks), now));

        if (credits.size() > CLEANUP_THRESHOLD) {
            credits.values().removeIf(credit -> credit.isStale(now, resetMillis));
        }
    }

    Set<TaskKey> consumeBreak(UUID playerId, UUID worldId, int x, int y, int z, String material,
                              long now, long resetMillis) {
        BlockKey key = new BlockKey(playerId, worldId, x, y, z);
        AtomicReference<Set<TaskKey>> result = new AtomicReference<>(Set.of());

        credits.computeIfPresent(key, (ignored, credit) -> {
            if (credit.isStale(now, resetMillis)) {
                return null;
            }
            if (!credit.material.equals(material)) {
                return credit;
            }

            result.set(credit.tasks);
            return null;
        });

        return result.get();
    }

    record TaskKey(String questId, String taskId) { }

    private record PlacementCredit(String material, Set<TaskKey> tasks, long timestamp) {

        private boolean isStale(long now, long resetMillis) {
            return now - timestamp < 0 || now - timestamp > resetMillis;
        }
    }

    private record BlockKey(UUID playerId, UUID worldId, int x, int y, int z) {

        private BlockKey {
            Objects.requireNonNull(playerId);
            Objects.requireNonNull(worldId);
        }
    }
}
