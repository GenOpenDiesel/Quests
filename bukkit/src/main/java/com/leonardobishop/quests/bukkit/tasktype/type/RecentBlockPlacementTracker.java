package com.leonardobishop.quests.bukkit.tasktype.type;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, per-player memory of placed blocks used by mining task anti-farming.
 * The values contain no Bukkit objects, so stale entries cannot retain worlds.
 */
final class RecentBlockPlacementTracker {

    private static final int CLEANUP_THRESHOLD = 4_096;
    private final Map<BlockKey, Placement> placements = new ConcurrentHashMap<>();

    void record(UUID playerId, UUID worldId, int x, int y, int z, String material,
                long now, long retentionMillis) {
        placements.put(new BlockKey(playerId, worldId, x, y, z), new Placement(material, now));

        if (placements.size() > CLEANUP_THRESHOLD) {
            placements.values().removeIf(placement -> now - placement.placedAt > retentionMillis);
        }
    }

    boolean consume(UUID playerId, UUID worldId, int x, int y, int z, String material,
                    long now, long retentionMillis) {
        Placement placement = placements.remove(new BlockKey(playerId, worldId, x, y, z));
        return placement != null
                && placement.material.equals(material)
                && now - placement.placedAt >= 0
                && now - placement.placedAt <= retentionMillis;
    }

    private static final class Placement {

        private final String material;
        private final long placedAt;

        private Placement(String material, long placedAt) {
            this.material = material;
            this.placedAt = placedAt;
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
