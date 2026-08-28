package com.leonardobishop.quests.bukkit.tasktype.type;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player counter of complete place/break cycles at exact block coordinates.
 * The values contain no Bukkit objects, so stale entries cannot retain worlds.
 */
final class RepeatedBlockCycleTracker {

    private static final int CLEANUP_THRESHOLD = 4_096;
    private final Map<BlockKey, CycleState> cycles = new ConcurrentHashMap<>();

    void recordPlacement(UUID playerId, UUID worldId, int x, int y, int z, String material,
                         long now, long resetMillis) {
        BlockKey key = new BlockKey(playerId, worldId, x, y, z);
        cycles.compute(key, (ignored, existing) -> {
            if (existing == null || existing.isStale(now, resetMillis)
                    || !existing.material.equals(material)) {
                return new CycleState(material, now);
            }

            existing.awaitingBreak = true;
            existing.lastActivity = now;
            return existing;
        });

        if (cycles.size() > CLEANUP_THRESHOLD) {
            cycles.values().removeIf(state -> state.isStale(now, resetMillis));
        }
    }

    boolean registerBreak(UUID playerId, UUID worldId, int x, int y, int z, String material,
                          long now, long resetMillis, int allowedCycles) {
        BlockKey key = new BlockKey(playerId, worldId, x, y, z);
        boolean[] blocked = {false};

        cycles.computeIfPresent(key, (ignored, state) -> {
            if (state.isStale(now, resetMillis)) {
                return null;
            }
            if (!state.awaitingBreak || !state.material.equals(material)) {
                return state;
            }

            state.awaitingBreak = false;
            state.completedCycles++;
            state.lastActivity = now;
            blocked[0] = state.completedCycles > allowedCycles;
            return state;
        });

        return blocked[0];
    }

    private static final class CycleState {

        private final String material;
        private boolean awaitingBreak = true;
        private int completedCycles;
        private long lastActivity;

        private CycleState(String material, long now) {
            this.material = material;
            this.lastActivity = now;
        }

        private boolean isStale(long now, long resetMillis) {
            return now - lastActivity < 0 || now - lastActivity > resetMillis;
        }
    }

    private static final class BlockKey {

        private final UUID playerId;
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(UUID playerId, UUID worldId, int x, int y, int z) {
            this.playerId = playerId;
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof BlockKey other)) {
                return false;
            }
            return x == other.x && y == other.y && z == other.z
                    && playerId.equals(other.playerId) && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, worldId, x, y, z);
        }
    }
}
