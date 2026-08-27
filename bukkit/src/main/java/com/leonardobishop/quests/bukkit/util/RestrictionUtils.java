package com.leonardobishop.quests.bukkit.util;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.config.BukkitQuestsConfig;
import com.leonardobishop.quests.bukkit.restriction.CategoryRequirement;
import com.leonardobishop.quests.bukkit.restriction.QuestRestrictionManager;
import com.leonardobishop.quests.bukkit.util.chat.Chat;
import com.leonardobishop.quests.common.quest.Quest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Placeholders for the messages and GUI items of the playtime and multi-account restrictions.
 */
public final class RestrictionUtils {

    private static final List<String> DEFAULT_PLAYTIME_LORE = List.of(
            "&7Zadania z kategorii &e{category}",
            "&7wymagaja &e{required-playtime} &7gry.",
            "",
            "&7Twoj czas gry: &e{playtime}",
            "&7Brakuje ci: &e{time}"
    );

    private static final List<String> DEFAULT_MULTI_ACCOUNT_LORE = List.of(
            "&7Z jednego adresu IP zadania",
            "&7moga wykonywac &c{limit} &7konta.",
            "&7Limit zajmuje juz m.in. &c{alt}&7.",
            "",
            "&7Odblokowanie za: &c{time}"
    );

    private RestrictionUtils() { }

    /**
     * Get the item shown in the GUI for a quest which is blocked by a restriction.
     * The item can be overridden in the configuration ({@code gui.quest-playtime-display} and
     * {@code gui.quest-multi-account-display}); if it is not, a built-in item is used so that
     * the restrictions also work on configurations which predate them.
     *
     * @param plugin the plugin
     * @param playtime true for the playtime item, false for the multi-account item
     * @return the item to display
     */
    public static @NotNull ItemStack getRestrictionItem(final @NotNull BukkitQuestsPlugin plugin, final boolean playtime) {
        final BukkitQuestsConfig config = (BukkitQuestsConfig) plugin.getQuestsConfig();
        final String path = playtime ? "gui.quest-playtime-display" : "gui.quest-multi-account-display";

        if (config.getConfig().isConfigurationSection(path)) {
            return config.getItem(path);
        }

        return buildItem(playtime
                        ? new String[]{"CLOCK", "WATCH"}
                        : new String[]{"BARRIER", "RED_STAINED_GLASS_PANE"},
                playtime ? "&e&lZa maly czas gry" : "&c&lZablokowane (multikonto)",
                playtime ? DEFAULT_PLAYTIME_LORE : DEFAULT_MULTI_ACCOUNT_LORE);
    }

    private static @NotNull ItemStack buildItem(final String[] materials, final String name, final List<String> lore) {
        Material material = null;
        for (final String candidate : materials) {
            material = Material.matchMaterial(candidate);
            if (material != null) {
                break;
            }
        }

        final ItemStack itemStack = new ItemStack(material != null ? material : Material.STONE);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Chat.legacyColor(name));
            meta.setLore(Chat.legacyColor(lore));
            itemStack.setItemMeta(meta);
        }

        return itemStack;
    }

    /**
     * Get the placeholders describing why a player cannot play a quest of a restricted category.
     *
     * @param plugin the plugin
     * @param player the player
     * @param quest the quest
     * @return a map of placeholder to replacement
     */
    public static @NotNull Map<String, String> getPlaytimePlaceholders(final @NotNull BukkitQuestsPlugin plugin,
                                                                      final @NotNull Player player,
                                                                      final @NotNull Quest quest) {
        final QuestRestrictionManager manager = plugin.getQuestRestrictionManager();
        final CategoryRequirement requirement = manager.getRequirement(quest);

        final long required = requirement != null ? requirement.requiredPlaytime() : 0;
        final long playtime = Math.max(0, manager.getPlaytime(player));
        final long remaining = manager.getRemainingPlaytime(player, quest);

        final String category = requirement != null ? requirement.displayName() : String.valueOf(quest.getCategoryId());
        final String categoryColored = Chat.legacyColor(category);

        final Map<String, String> placeholders = new HashMap<>();
        // The placeholders are substituted into text which has already been coloured (the GUI item is
        // built and coloured before the substitution happens), so the colour codes of the category
        // have to be translated here - otherwise they are shown literally, as "&cTrudne".
        placeholders.put("{category}", categoryColored);
        placeholders.put("{categoryplain}", Chat.legacyStrip(categoryColored));
        placeholders.put("{categoryid}", String.valueOf(quest.getCategoryId()));
        placeholders.put("{required-playtime}", FormatUtils.time(TimeUnit.MINUTES.toSeconds(required)));
        placeholders.put("{playtime}", FormatUtils.time(TimeUnit.MINUTES.toSeconds(playtime)));
        placeholders.put("{time}", FormatUtils.time(TimeUnit.MINUTES.toSeconds(remaining)));
        return placeholders;
    }

    /**
     * Get the placeholders describing which account is blocking a player from playing quests.
     *
     * @param plugin the plugin
     * @param player the player
     * @return a map of placeholder to replacement
     */
    public static @NotNull Map<String, String> getMultiAccountPlaceholders(final @NotNull BukkitQuestsPlugin plugin,
                                                                          final @NotNull Player player) {
        final QuestRestrictionManager manager = plugin.getQuestRestrictionManager();
        final QuestRestrictionManager.AltAccountBlock block = manager.getMultiAccountBlock(player);

        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{alt}", block != null && block.name() != null ? block.name() : "?");
        placeholders.put("{limit}", String.valueOf(manager.getMaxAccountsPerAddress()));
        placeholders.put("{accounts}", String.valueOf(block != null ? block.accounts() + 1 : 1));
        placeholders.put("{time}", FormatUtils.time(block != null ? TimeUnit.MILLISECONDS.toSeconds(block.getRemainingTime()) : 0));
        return placeholders;
    }

    public static @Nullable String applyPlaytimePlaceholders(final @NotNull BukkitQuestsPlugin plugin,
                                                             final @NotNull Player player,
                                                             final @NotNull Quest quest,
                                                             final @Nullable String message) {
        return apply(message, getPlaytimePlaceholders(plugin, player, quest));
    }

    public static @Nullable String applyMultiAccountPlaceholders(final @NotNull BukkitQuestsPlugin plugin,
                                                                 final @NotNull Player player,
                                                                 final @Nullable String message) {
        return apply(message, getMultiAccountPlaceholders(plugin, player));
    }

    private static @Nullable String apply(final @Nullable String message, final @NotNull Map<String, String> placeholders) {
        if (message == null) {
            return null;
        }

        String ret = message;
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            ret = ret.replace(entry.getKey(), entry.getValue());
        }

        return ret;
    }
}
