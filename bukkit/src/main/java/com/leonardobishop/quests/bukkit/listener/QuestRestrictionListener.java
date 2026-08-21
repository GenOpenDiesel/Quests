package com.leonardobishop.quests.bukkit.listener;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.api.event.PlayerFinishQuestEvent;
import com.leonardobishop.quests.bukkit.api.event.PlayerStartQuestEvent;
import com.leonardobishop.quests.bukkit.restriction.QuestRestrictionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps the multi-account data of {@link QuestRestrictionManager} up to date.
 */
public class QuestRestrictionListener implements Listener {

    private final BukkitQuestsPlugin plugin;

    public QuestRestrictionListener(BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getQuestRestrictionManager().recordAddress(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getQuestRestrictionManager().invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuestStart(PlayerStartQuestEvent event) {
        plugin.getQuestRestrictionManager().recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuestFinish(PlayerFinishQuestEvent event) {
        plugin.getQuestRestrictionManager().recordActivity(event.getPlayer());
    }
}
