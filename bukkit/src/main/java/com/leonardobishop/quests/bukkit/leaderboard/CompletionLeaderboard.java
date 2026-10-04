package com.leonardobishop.quests.bukkit.leaderboard;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.QPlayerData;
import com.leonardobishop.quests.common.player.questprogressfile.filters.QuestProgressFilter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Ranks every player known to the storage provider by the number of quests they have completed at least
 * once, as used by {@code /quests top}.
 * <p>
 * Reading the data of every player is far too expensive to do per command, so the ranking is built
 * asynchronously, at most once per {@code options.leaderboard-cache-time} seconds, and all requests made
 * while a build is running are answered by that one build.
 */
public class CompletionLeaderboard {

    /** How many of the top entries get a resolved player name, as resolving may hit the Mojang API. */
    private static final int NAMED_ENTRIES = 50;

    private final BukkitQuestsPlugin plugin;

    private final Object lock = new Object();
    private final List<Consumer<List<Entry>>> waiting = new ArrayList<>();

    private @Nullable List<Entry> entries;
    private long builtAt;
    private boolean building;

    public CompletionLeaderboard(@NotNull BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Pass the current ranking to the given callback, rebuilding it first if it has gone stale. The callback
     * always runs on the main thread, either immediately or once the ranking has been built.
     * <p>
     * Must be called from the main thread, as the progress of the players who are online is read directly.
     *
     * @param callback the callback to pass the ranking to
     */
    public void request(@NotNull Consumer<List<Entry>> callback) {
        final List<Entry> cached;

        synchronized (lock) {
            cached = isFresh() ? entries : null;

            if (cached == null) {
                waiting.add(callback);

                if (building) {
                    // A build is already running - it will answer this request as well
                    return;
                }

                building = true;
            }
        }

        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // Read the players who are online here, on the main thread, as their progress is being written to
        // as they play and is newer than whatever is currently on disk for them
        final Map<UUID, Integer> online = new HashMap<>();
        for (final QPlayer qPlayer : plugin.getPlayerManager().getQPlayers()) {
            online.put(qPlayer.getPlayerUUID(),
                    qPlayer.getQuestProgressFile().getAllQuestsFromProgressCount(QuestProgressFilter.COMPLETED_BEFORE_COUNT));
        }

        plugin.getScheduler().doAsync(() -> build(online));
    }

    /**
     * Drop the ranking currently held, so the next request builds a new one. Called when the quests
     * themselves are reloaded, as that changes what the counts mean.
     */
    public void invalidate() {
        synchronized (lock) {
            entries = null;
            builtAt = 0L;
        }
    }

    private void build(Map<UUID, Integer> online) {
        final List<Entry> built = new ArrayList<>();
        boolean failed = false;

        try {
            final Map<UUID, Integer> counts = new HashMap<>(online);

            for (final QPlayerData playerData : plugin.getStorageProvider().loadAllPlayerData()) {
                // Players who are online were counted from memory already
                counts.putIfAbsent(playerData.playerUUID(),
                        playerData.questProgressFile().getAllQuestsFromProgressCount(QuestProgressFilter.COMPLETED_BEFORE_COUNT));
            }

            for (final Map.Entry<UUID, Integer> count : counts.entrySet()) {
                if (count.getValue() > 0) {
                    built.add(new Entry(count.getKey(), null, count.getValue()));
                }
            }

            built.sort(Comparator.comparingInt(Entry::completed).reversed());

            // Only the entries which are actually shown are worth a name lookup
            for (int i = 0; i < Math.min(NAMED_ENTRIES, built.size()); i++) {
                final Entry entry = built.get(i);
                built.set(i, new Entry(entry.uuid(), name(entry.uuid()), entry.completed()));
            }
        } catch (final Exception e) {
            failed = true;
            plugin.getQuestsLogger().severe("An error occurred building the quest completion leaderboard: " + e.getMessage());
            e.printStackTrace();
        }

        final List<Entry> result = List.copyOf(built);
        final List<Consumer<List<Entry>>> callbacks;

        synchronized (lock) {
            // A ranking which could not be read is not worth keeping around - the next request tries again
            entries = failed ? null : result;
            builtAt = failed ? 0L : System.currentTimeMillis();
            building = false;

            callbacks = new ArrayList<>(waiting);
            waiting.clear();
        }

        plugin.getScheduler().doSync(() -> {
            for (final Consumer<List<Entry>> callback : callbacks) {
                callback.accept(result);
            }
        });
    }

    private @Nullable String name(UUID uuid) {
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        return offlinePlayer.getName();
    }

    private boolean isFresh() {
        if (entries == null) {
            return false;
        }

        final long cacheTime = plugin.getQuestsConfig().getInt("options.leaderboard-cache-time", 300) * 1000L;
        return cacheTime > 0 && System.currentTimeMillis() - builtAt < cacheTime;
    }

    /**
     * A single place in the ranking.
     *
     * @param uuid the UUID of the player
     * @param name the name of the player, or null if it was not looked up or is not known to the server
     * @param completed how many quests which count towards completion the player has completed at least once
     */
    public record Entry(@NotNull UUID uuid, @Nullable String name, int completed) {
    }
}
