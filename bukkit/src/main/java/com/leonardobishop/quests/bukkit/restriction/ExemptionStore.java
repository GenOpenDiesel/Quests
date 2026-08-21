package com.leonardobishop.quests.bukkit.restriction;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds - and persists to {@code quest-exemptions.yml} - the pairs of accounts which are allowed
 * to play quests from the same address, for example siblings sharing a household connection.
 * <p>
 * Pairs are explicit and symmetric: exempting A with B and B with C does <b>not</b> exempt A with C.
 * Accounts are matched by name (case insensitive) rather than by UUID, so an exemption can be added
 * before the account has ever joined.
 */
public class ExemptionStore {

    private final BukkitQuestsPlugin plugin;
    private final File file;

    /** lowercase name -> the names it is paired with, in the casing they were added with */
    private final Map<String, Set<String>> pairs = new ConcurrentHashMap<>();
    /** lowercase name -> the casing it was added with, for display only */
    private final Map<String, String> displayNames = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public ExemptionStore(@NotNull BukkitQuestsPlugin plugin, @NotNull File file) {
        this.plugin = plugin;
        this.file = file;
    }

    /**
     * Read all exemptions from disk, replacing everything currently held in memory.
     */
    public void load() {
        pairs.clear();
        displayNames.clear();

        if (!file.exists()) {
            save(); // write the file with its header so it can be edited by hand
            return;
        }

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (final Exception e) {
            plugin.getQuestsLogger().severe("Failed to load " + file.getName() + " - no account will be exempt from the multi-account protection!");
            e.printStackTrace();
            return;
        }

        final ConfigurationSection section = configuration.getConfigurationSection("exemptions");
        if (section == null) {
            return;
        }

        for (final String key : section.getKeys(false)) {
            for (final String other : section.getStringList(key)) {
                link(key, other);
            }
        }

        plugin.getQuestsLogger().debug("Loaded exemptions for " + pairs.size() + " accounts");
    }

    /**
     * Write all exemptions to disk. Safe to call asynchronously.
     */
    public void save() {
        dirty.set(false);

        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().header("Accounts which are allowed to play quests from the same IP address.\n"
                + "Every pair is symmetric: if Kowalski is paired with Kowalska, neither blocks the other.\n"
                + "Pairs are NOT transitive - pairing A with B and B with C does not pair A with C.\n"
                + "Managed by /quests admin restrictions exempt <player> <player>. It can also be edited by hand,\n"
                + "but only while the server is stopped - the file is read on startup and overwritten on shutdown.");

        for (final Map.Entry<String, Set<String>> entry : pairs.entrySet()) {
            configuration.set("exemptions." + getDisplayName(entry.getKey()), new ArrayList<>(entry.getValue()));
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
     * Write all exemptions to disk, but only if something has changed since the last save.
     */
    public void saveIfDirty() {
        if (dirty.get()) {
            save();
        }
    }

    /**
     * Check whether two accounts are explicitly allowed to share an address.
     *
     * @param one the name of the first account, may be null if it is unknown
     * @param other the name of the second account, may be null if it is unknown
     * @return true if the two accounts do not block each other
     */
    public boolean isExempt(final String one, final String other) {
        if (one == null || other == null) {
            return false;
        }

        final Set<String> partners = pairs.get(one.toLowerCase());
        if (partners == null) {
            return false;
        }

        for (final String partner : partners) {
            if (partner.equalsIgnoreCase(other)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Allow two accounts to play quests from the same address.
     *
     * @param one the name of the first account
     * @param other the name of the second account
     * @return true if the pair did not already exist
     */
    public boolean addPair(final @NotNull String one, final @NotNull String other) {
        if (one.equalsIgnoreCase(other)) {
            return false;
        }

        if (isExempt(one, other)) {
            return false;
        }

        link(one, other);
        dirty.set(true);
        return true;
    }

    /**
     * Stop allowing two accounts to play quests from the same address.
     *
     * @param one the name of the first account
     * @param other the name of the second account
     * @return true if the pair existed
     */
    public boolean removePair(final @NotNull String one, final @NotNull String other) {
        if (!isExempt(one, other)) {
            return false;
        }

        unlink(one, other);
        unlink(other, one);
        dirty.set(true);
        return true;
    }

    /**
     * Get every account a given account is paired with.
     *
     * @param name the name of the account
     * @return the names of the paired accounts, never null
     */
    public @NotNull Set<String> getPartners(final String name) {
        if (name == null) {
            return Collections.emptySet();
        }

        final Set<String> partners = pairs.get(name.toLowerCase());
        return partners == null ? Collections.emptySet() : new LinkedHashSet<>(partners);
    }

    /**
     * @return every pair exactly once, as {@code [name, name]} arrays
     */
    public @NotNull List<String[]> getPairs() {
        final List<String[]> ret = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> entry : pairs.entrySet()) {
            for (final String other : entry.getValue()) {
                // only emit the pair from the alphabetically first side to avoid duplicates
                if (entry.getKey().compareTo(other.toLowerCase()) < 0) {
                    ret.add(new String[]{getDisplayName(entry.getKey()), other});
                }
            }
        }
        return ret;
    }

    /**
     * @return the amount of accounts which have at least one exemption
     */
    public int size() {
        return pairs.size();
    }

    private @NotNull String getDisplayName(final @NotNull String lowercaseName) {
        return displayNames.getOrDefault(lowercaseName, lowercaseName);
    }

    private void link(final @NotNull String one, final @NotNull String other) {
        if (one.equalsIgnoreCase(other)) {
            return;
        }

        displayNames.putIfAbsent(one.toLowerCase(), one);
        displayNames.putIfAbsent(other.toLowerCase(), other);

        pairs.computeIfAbsent(one.toLowerCase(), k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(other);
        pairs.computeIfAbsent(other.toLowerCase(), k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(one);
    }

    private void unlink(final @NotNull String from, final @NotNull String target) {
        final Set<String> partners = pairs.get(from.toLowerCase());
        if (partners == null) {
            return;
        }

        partners.removeIf(partner -> partner.equalsIgnoreCase(target));
        if (partners.isEmpty()) {
            pairs.remove(from.toLowerCase());
            displayNames.remove(from.toLowerCase());
        }
    }
}
