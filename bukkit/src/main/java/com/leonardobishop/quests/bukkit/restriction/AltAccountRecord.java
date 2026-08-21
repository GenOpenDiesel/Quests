package com.leonardobishop.quests.bukkit.restriction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Quests knows about a single account for the purpose of multi-account detection:
 * the addresses it connected from and the last time it made any quest progress.
 */
public final class AltAccountRecord {

    private final UUID uuid;
    private final Set<String> addresses = Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile String name;
    private volatile long lastActivity;

    public AltAccountRecord(@NotNull UUID uuid, @Nullable String name, long lastActivity) {
        this.uuid = uuid;
        this.name = name;
        this.lastActivity = lastActivity;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    public @Nullable String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    /**
     * @return the last time (epoch millis) this account started or completed a quest, or 0 if never
     */
    public long getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }

    /**
     * @return a snapshot of all addresses this account has connected from
     */
    public @NotNull Set<String> getAddresses() {
        synchronized (addresses) {
            return new LinkedHashSet<>(addresses);
        }
    }

    public boolean hasAddress(@NotNull String address) {
        return addresses.contains(address);
    }

    /**
     * Add an address to this account, dropping the oldest ones if the limit is exceeded.
     *
     * @param address the address to add
     * @param limit the maximum amount of addresses to remember
     * @return true if the address was not known before
     */
    public boolean addAddress(@NotNull String address, int limit) {
        synchronized (addresses) {
            if (!addresses.add(address)) {
                return false;
            }

            while (limit > 0 && addresses.size() > limit) {
                final Iterator<String> iterator = addresses.iterator();
                iterator.next(); // oldest address, LinkedHashSet keeps insertion order
                iterator.remove();
            }

            return true;
        }
    }

    public void addAddresses(@NotNull Collection<String> addresses) {
        synchronized (this.addresses) {
            this.addresses.addAll(addresses);
        }
    }
}
