package com.leonardobishop.quests.bukkit.restriction;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds - and persists to {@code alt-accounts.yml} - which accounts connected from which addresses
 * and when they last progressed a quest.
 * <p>
 * All lookups are done in memory, the file is only touched when loading and saving.
 */
public class AltAccountStore {

    private final BukkitQuestsPlugin plugin;
    private final File file;

    private final Map<UUID, AltAccountRecord> records = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> addressIndex = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public AltAccountStore(@NotNull BukkitQuestsPlugin plugin, @NotNull File file) {
        this.plugin = plugin;
        this.file = file;
    }

    /**
     * Read all records from disk, discarding anything older than the retention period.
     * This replaces everything currently held in memory.
     *
     * @param retention the maximum age (in millis) of a record to keep, or 0 to keep everything
     */
    public void load(long retention) {
        records.clear();
        addressIndex.clear();

        if (!file.exists()) {
            return;
        }

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (final Exception e) {
            plugin.getQuestsLogger().severe("Failed to load " + file.getName() + " - multi-account protection will start with no data!");
            e.printStackTrace();
            return;
        }

        final ConfigurationSection playersSection = configuration.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        final long minimumLastSeen = retention > 0 ? System.currentTimeMillis() - retention : 0;
        int skipped = 0;

        for (final String key : playersSection.getKeys(false)) {
            final UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (final IllegalArgumentException e) {
                plugin.getQuestsLogger().warning("Skipping malformed UUID '" + key + "' in " + file.getName());
                continue;
            }

            final long lastActivity = playersSection.getLong(key + ".last-activity", 0);

            // records without recent activity cannot block anyone; they are recreated on the next join
            if (minimumLastSeen > 0 && lastActivity < minimumLastSeen) {
                skipped++;
                continue;
            }

            final AltAccountRecord record = new AltAccountRecord(uuid, playersSection.getString(key + ".name"), lastActivity);
            record.addAddresses(playersSection.getStringList(key + ".addresses"));

            records.put(uuid, record);
            for (final String address : record.getAddresses()) {
                indexAddress(address, uuid);
            }
        }

        plugin.getQuestsLogger().debug("Loaded " + records.size() + " multi-account records (" + skipped + " expired)");
    }

    /**
     * Write all records to disk. Safe to call asynchronously.
     */
    public void save() {
        dirty.set(false);

        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().header("This file is managed by Quests and is used to detect alternate accounts.\n"
                + "Do not edit it while the server is running.");

        for (final AltAccountRecord record : new ArrayList<>(records.values())) {
            final String key = "players." + record.getUuid();
            configuration.set(key + ".name", record.getName());
            configuration.set(key + ".last-activity", record.getLastActivity());
            configuration.set(key + ".addresses", new ArrayList<>(record.getAddresses()));
        }

        try {
            final File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            configuration.save(file);
        } catch (final Exception e) {
            plugin.getQuestsLogger().severe("Failed to save " + file.getName() + "!");
            e.printStackTrace();
        }
    }

    /**
     * Write all records to disk, but only if something has changed since the last save.
     */
    public void saveIfDirty() {
        if (dirty.get()) {
            save();
        }
    }

    public @Nullable AltAccountRecord getRecord(@NotNull UUID uuid) {
        return records.get(uuid);
    }

    public @NotNull Collection<AltAccountRecord> getRecords() {
        return Collections.unmodifiableCollection(records.values());
    }

    /**
     * Get all accounts (excluding the given one) which have connected from the given address.
     *
     * @param address the address to look up
     * @param excluding the account to leave out of the result
     * @return the records of all other accounts on this address
     */
    public @NotNull List<AltAccountRecord> getRecordsForAddress(@NotNull String address, @Nullable UUID excluding) {
        final Set<UUID> uuids = addressIndex.get(address);
        if (uuids == null || uuids.isEmpty()) {
            return Collections.emptyList();
        }

        final List<AltAccountRecord> ret = new ArrayList<>();
        for (final UUID uuid : new HashSet<>(uuids)) {
            if (uuid.equals(excluding)) {
                continue;
            }

            final AltAccountRecord record = records.get(uuid);
            if (record != null) {
                ret.add(record);
            }
        }

        return ret;
    }

    /**
     * Remember that an account connected from an address.
     *
     * @param uuid the account
     * @param name the name of the account
     * @param address the address, or null if it could not be resolved
     * @param addressLimit the maximum amount of addresses to remember per account
     * @return true if this address was not previously known for this account
     */
    public boolean recordAddress(@NotNull UUID uuid, @Nullable String name, @Nullable String address, int addressLimit) {
        final AltAccountRecord record = records.computeIfAbsent(uuid, k -> new AltAccountRecord(k, name, 0));
        record.setName(name);

        if (address == null) {
            return false;
        }

        if (!record.addAddress(address, addressLimit)) {
            return false;
        }

        indexAddress(address, uuid);
        dirty.set(true);
        return true;
    }

    /**
     * Remember that an account made quest progress right now.
     *
     * @param uuid the account
     * @param name the name of the account
     * @param time the time of the activity
     */
    public void recordActivity(@NotNull UUID uuid, @Nullable String name, long time) {
        final AltAccountRecord record = records.computeIfAbsent(uuid, k -> new AltAccountRecord(k, name, 0));
        record.setName(name);
        record.setLastActivity(time);
        dirty.set(true);
    }

    /**
     * Forget everything about an account.
     *
     * @param uuid the account
     * @return true if anything was removed
     */
    public boolean forget(@NotNull UUID uuid) {
        final AltAccountRecord record = records.remove(uuid);
        if (record == null) {
            return false;
        }

        for (final String address : record.getAddresses()) {
            final Set<UUID> uuids = addressIndex.get(address);
            if (uuids != null) {
                uuids.remove(uuid);
                if (uuids.isEmpty()) {
                    addressIndex.remove(address);
                }
            }
        }

        dirty.set(true);
        return true;
    }

    private void indexAddress(@NotNull String address, @NotNull UUID uuid) {
        addressIndex.computeIfAbsent(address, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }
}
