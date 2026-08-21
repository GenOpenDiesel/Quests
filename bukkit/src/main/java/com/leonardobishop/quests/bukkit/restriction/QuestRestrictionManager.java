package com.leonardobishop.quests.bukkit.restriction;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.scheduler.WrappedTask;
import com.leonardobishop.quests.common.enums.QuestStartResult;
import com.leonardobishop.quests.common.quest.Quest;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enforces the two server-side restrictions on top of the regular quest requirements:
 * <ul>
 *     <li>quests of the categories in {@link #RESTRICTED_CATEGORIES} require
 *     {@link #REQUIRED_PLAYTIME} minutes of playtime before they can be played,</li>
 *     <li>at most {@link #MAX_ACCOUNTS_PER_ADDRESS} accounts from the same IP address may have
 *     played quests within the last {@link #MULTI_ACCOUNT_PERIOD}.</li>
 * </ul>
 * Both restrictions are intentionally hardcoded - there is nothing to configure and
 * nothing to add to an existing config.yml. Only the two bypass permissions and the
 * per-account exemptions of {@link ExemptionStore} apply.
 */
public class QuestRestrictionManager {

    public static final String BYPASS_PLAYTIME_PERMISSION = "quests.bypass.playtime";
    public static final String BYPASS_MULTI_ACCOUNT_PERMISSION = "quests.bypass.multiaccount";

    /** Categories which require playtime, mapped to the name shown to players. */
    private static final Map<String, String> RESTRICTED_CATEGORIES = Map.of(
            "sredni", "&eSrednie",
            "trudny", "&cTrudne"
    );

    /** Playtime, in minutes, required to play quests of a restricted category. */
    private static final int REQUIRED_PLAYTIME = 180; // 3 hours

    /** How long an account blocks the other accounts of its IP address after playing quests. */
    private static final long MULTI_ACCOUNT_PERIOD = TimeUnit.DAYS.toMillis(30);

    /** How many accounts of one IP address may play quests within {@link #MULTI_ACCOUNT_PERIOD}. */
    private static final int MAX_ACCOUNTS_PER_ADDRESS = 2;

    /** Whether being blocked also stops progress in quests which are already started. */
    private static final boolean BLOCK_TASK_PROGRESS = true;

    /** How long the result of a multi-account check is reused for. */
    private static final long CHECK_CACHE_TIME = TimeUnit.SECONDS.toMillis(60);

    /** How long an account is remembered in alt-accounts.yml after its last quest activity. */
    private static final long DATA_RETENTION = TimeUnit.DAYS.toMillis(90);

    /** How many of the most recent addresses are remembered per account. */
    private static final int ADDRESS_LIMIT = 5;

    /** Addresses which never block anyone (local testing, same-machine connections). */
    private static final Set<String> IGNORED_ADDRESSES = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1");

    /** How often alt-accounts.yml is written to disk, in ticks. */
    private static final long SAVE_INTERVAL = 20L * 300; // 5 minutes

    private static final long ACTIVITY_THROTTLE = TimeUnit.MINUTES.toMillis(1);

    private static final Statistic PLAYTIME_STATISTIC;

    static {
        Statistic playtimeStatistic = null;
        // PLAY_ONE_TICK was renamed to PLAY_ONE_MINUTE, both count ticks
        for (final String name : new String[]{"PLAY_ONE_MINUTE", "PLAY_ONE_TICK", "PLAY_TIME"}) {
            try {
                playtimeStatistic = Statistic.valueOf(name);
                break;
            } catch (final IllegalArgumentException ignored) { }
        }
        PLAYTIME_STATISTIC = playtimeStatistic;
    }

    private final BukkitQuestsPlugin plugin;
    private final AltAccountStore store;
    private final ExemptionStore exemptions;
    private final Map<String, CategoryRequirement> categoryRequirements;
    private final Map<UUID, CachedCheck> checkCache = new ConcurrentHashMap<>();

    private WrappedTask saveTask;

    public QuestRestrictionManager(final @NotNull BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
        this.store = new AltAccountStore(plugin, new File(plugin.getDataFolder(), "alt-accounts.yml"));
        this.exemptions = new ExemptionStore(plugin, new File(plugin.getDataFolder(), "quest-exemptions.yml"));

        final Map<String, CategoryRequirement> categoryRequirements = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : RESTRICTED_CATEGORIES.entrySet()) {
            categoryRequirements.put(entry.getKey(), new CategoryRequirement(entry.getKey(), entry.getValue(), REQUIRED_PLAYTIME));
        }
        this.categoryRequirements = Collections.unmodifiableMap(categoryRequirements);
    }

    /**
     * Read the stored multi-account data from disk and start the autosave task.
     * Should only be called once, on startup.
     */
    public void start() {
        plugin.getScheduler().doAsync(() -> {
            store.load(DATA_RETENTION);
            exemptions.load();
        });
        scheduleSaveTask();
    }

    /**
     * Save the multi-account data and stop the autosave task.
     */
    public void shutdown() {
        if (saveTask != null && !saveTask.isCancelled()) {
            saveTask.cancel();
            saveTask = null;
        }

        store.saveIfDirty();
        exemptions.saveIfDirty();
    }

    public @NotNull AltAccountStore getStore() {
        return store;
    }

    public @NotNull ExemptionStore getExemptions() {
        return exemptions;
    }

    public int getMaxAccountsPerAddress() {
        return MAX_ACCOUNTS_PER_ADDRESS;
    }

    public @NotNull Map<String, CategoryRequirement> getCategoryRequirements() {
        return categoryRequirements;
    }

    public long getMultiAccountPeriodDays() {
        return TimeUnit.MILLISECONDS.toDays(MULTI_ACCOUNT_PERIOD);
    }

    public @Nullable CategoryRequirement getCategoryRequirement(final @Nullable String categoryId) {
        return categoryId == null ? null : categoryRequirements.get(categoryId);
    }

    /**
     * Get the requirement which applies to a quest, based on the category it is in.
     *
     * @param quest the quest
     * @return the requirement, or null if the category of the quest has none
     */
    public @Nullable CategoryRequirement getRequirement(final @NotNull Quest quest) {
        return getCategoryRequirement(quest.getCategoryId());
    }

    /**
     * Get the playtime of a player, in minutes.
     *
     * @param player the player
     * @return the playtime in minutes, or -1 if the server does not expose the statistic
     */
    public long getPlaytime(final @NotNull Player player) {
        if (PLAYTIME_STATISTIC == null) {
            return -1;
        }

        try {
            return player.getStatistic(PLAYTIME_STATISTIC) / 1200L; // ticks -> minutes
        } catch (final Exception e) {
            return -1;
        }
    }

    /**
     * Get how much longer a player has to play before they can play a quest.
     *
     * @param player the player
     * @param quest the quest
     * @return the remaining playtime in minutes, or 0 if the requirement is already met
     */
    public long getRemainingPlaytime(final @NotNull Player player, final @NotNull Quest quest) {
        final CategoryRequirement requirement = getRequirement(quest);
        if (requirement == null || !requirement.hasPlaytimeRequirement()) {
            return 0;
        }

        final long playtime = getPlaytime(player);
        if (playtime < 0) {
            return 0;
        }

        return Math.max(0, requirement.requiredPlaytime() - playtime);
    }

    /**
     * Check whether a player is blocked because too many other accounts from one of their
     * addresses have recently played quests.
     *
     * @param player the player
     * @return the block, or null if the player is not blocked
     */
    public @Nullable AltAccountBlock getMultiAccountBlock(final @NotNull Player player) {
        if (player.hasPermission(BYPASS_MULTI_ACCOUNT_PERMISSION)) {
            return null;
        }

        final UUID uuid = player.getUniqueId();
        final long now = System.currentTimeMillis();

        final CachedCheck cached = checkCache.get(uuid);
        if (cached != null && cached.expiry() > now) {
            return cached.block();
        }

        final AltAccountBlock block = computeMultiAccountBlock(player, now);
        checkCache.put(uuid, new CachedCheck(block, now + CHECK_CACHE_TIME));
        return block;
    }

    private @Nullable AltAccountBlock computeMultiAccountBlock(final @NotNull Player player, final long now) {
        final UUID uuid = player.getUniqueId();
        final AltAccountRecord self = store.getRecord(uuid);

        Set<String> addresses = self != null ? self.getAddresses() : Set.of();
        if (addresses.isEmpty()) {
            final String address = getAddress(player);
            addresses = address != null ? Set.of(address) : Set.of();
        }

        final long threshold = now - MULTI_ACCOUNT_PERIOD;
        final String name = player.getName();

        // an account which shares several addresses with the player must only be counted once
        final Map<UUID, AltAccountRecord> occupants = new HashMap<>();

        for (final String address : addresses) {
            if (IGNORED_ADDRESSES.contains(address)) {
                continue;
            }

            for (final AltAccountRecord other : store.getRecordsForAddress(address, uuid)) {
                if (other.getLastActivity() < threshold) {
                    continue;
                }

                // siblings and other explicitly paired accounts do not take up a slot
                if (exemptions.isExempt(name, other.getName())) {
                    continue;
                }

                occupants.put(other.getUuid(), other);
            }
        }

        // the player takes up a slot themselves, so the others may fill up all but one
        if (occupants.size() < MAX_ACCOUNTS_PER_ADDRESS) {
            return null;
        }

        // report the account which has to expire first before a slot frees up
        AltAccountRecord oldest = null;
        for (final AltAccountRecord occupant : occupants.values()) {
            if (oldest == null || occupant.getLastActivity() < oldest.getLastActivity()) {
                oldest = occupant;
            }
        }

        final long lastActivity = oldest.getLastActivity();
        return new AltAccountBlock(oldest.getUuid(), oldest.getName(), lastActivity,
                lastActivity + MULTI_ACCOUNT_PERIOD, occupants.size());
    }

    /**
     * Check whether the restrictions allow a player to start a quest.
     *
     * @param player the player, or null if they are offline
     * @param quest the quest
     * @return {@code QuestStartResult.QUEST_SUCCESS} if the quest is not restricted
     */
    public @NotNull QuestStartResult checkQuest(final @Nullable Player player, final @NotNull Quest quest) {
        if (player == null) {
            // playtime and addresses cannot be resolved reliably for offline players
            return QuestStartResult.QUEST_SUCCESS;
        }

        if (getMultiAccountBlock(player) != null) {
            return QuestStartResult.QUEST_MULTI_ACCOUNT;
        }

        // the permission is only looked up when the quest would actually be blocked
        if (getRemainingPlaytime(player, quest) > 0 && !player.hasPermission(BYPASS_PLAYTIME_PERMISSION)) {
            return QuestStartResult.QUEST_PLAYTIME_TOO_LOW;
        }

        return QuestStartResult.QUEST_SUCCESS;
    }

    /**
     * Check whether the restrictions allow a player to progress the tasks of a quest they
     * have already started.
     *
     * @param player the player
     * @param quest the quest
     * @return true if task progress is allowed
     */
    public boolean canProgressQuest(final @NotNull Player player, final @NotNull Quest quest) {
        if (getRemainingPlaytime(player, quest) > 0 && !player.hasPermission(BYPASS_PLAYTIME_PERMISSION)) {
            return false;
        }

        return !BLOCK_TASK_PROGRESS || getMultiAccountBlock(player) == null;
    }

    /**
     * Remember the address a player connected from.
     *
     * @param player the player
     */
    public void recordAddress(final @NotNull Player player) {
        final String address = getAddress(player);
        if (store.recordAddress(player.getUniqueId(), player.getName(), address, ADDRESS_LIMIT)) {
            // a new address means everyone sharing it may now be blocked (or unblocked)
            invalidateAddress(address);
        }

        checkCache.remove(player.getUniqueId());
    }

    /**
     * Remember that a player has played quests right now.
     *
     * @param player the player
     */
    public void recordActivity(final @NotNull Player player) {
        if (player.hasPermission(BYPASS_MULTI_ACCOUNT_PERMISSION)) {
            return;
        }

        final long now = System.currentTimeMillis();
        final AltAccountRecord existing = store.getRecord(player.getUniqueId());

        // activity is recorded on every completed task, once a minute is more than enough
        if (existing != null && now - existing.getLastActivity() < ACTIVITY_THROTTLE) {
            return;
        }

        store.recordActivity(player.getUniqueId(), player.getName(), now);

        final AltAccountRecord record = store.getRecord(player.getUniqueId());
        if (record != null) {
            for (final String address : record.getAddresses()) {
                invalidateAddress(address);
            }
        }
    }

    /**
     * Drop the cached check result of every account which uses the given address.
     *
     * @param address the address, or null to do nothing
     */
    public void invalidateAddress(final @Nullable String address) {
        if (address == null) {
            return;
        }

        for (final AltAccountRecord record : store.getRecordsForAddress(address, null)) {
            checkCache.remove(record.getUuid());
        }
    }

    /**
     * Drop the cached check result of a single account.
     *
     * @param uuid the account
     */
    public void invalidate(final @NotNull UUID uuid) {
        checkCache.remove(uuid);
    }

    /**
     * Drop every cached check result. Used after the exemptions have been changed, as a single
     * pair can affect accounts on any address.
     */
    public void invalidateAll() {
        checkCache.clear();
    }

    public @Nullable String getAddress(final @NotNull Player player) {
        final InetSocketAddress socketAddress = player.getAddress();
        if (socketAddress == null || socketAddress.getAddress() == null) {
            return null;
        }

        return socketAddress.getAddress().getHostAddress();
    }

    private void scheduleSaveTask() {
        try {
            if (saveTask != null && !saveTask.isCancelled()) {
                saveTask.cancel();
            }

            saveTask = plugin.getScheduler().runTaskTimerAsynchronously(() -> {
                store.saveIfDirty();
                exemptions.saveIfDirty();
            }, SAVE_INTERVAL, SAVE_INTERVAL);
        } catch (final Exception e) {
            plugin.getQuestsLogger().severe("Could not schedule the multi-account data save task!");
            e.printStackTrace();
        }
    }

    /**
     * The reason an account on a full address cannot play quests. It refers to the account whose
     * activity expires first, since that is the one which frees up a slot.
     *
     * @param uuid the uuid of the offending account
     * @param name the name of the offending account, may be null if it was never seen
     * @param lastActivity when the offending account last played quests
     * @param unlockTime when the block expires
     * @param accounts how many other accounts of the address are taking up a slot
     */
    public record AltAccountBlock(@NotNull UUID uuid, @Nullable String name, long lastActivity, long unlockTime, int accounts) {

        public long getRemainingTime() {
            return Math.max(0, unlockTime - System.currentTimeMillis());
        }
    }

    private record CachedCheck(@Nullable AltAccountBlock block, long expiry) { }
}
