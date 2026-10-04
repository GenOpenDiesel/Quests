package com.leonardobishop.quests.bukkit.command;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.leaderboard.CompletionLeaderboard;
import com.leonardobishop.quests.bukkit.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TopCommandHandler implements CommandHandler {

    /** How many places are shown to the player. */
    private static final int SHOWN_PLACES = 10;

    private final BukkitQuestsPlugin plugin;

    public TopCommandHandler(BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        final int total = plugin.getQuestManager().getCompletionCountingQuestCount();
        if (total == 0) {
            Messages.COMMAND_TOP_EMPTY.send(sender);
            return;
        }

        Messages.COMMAND_TOP_LOADING.send(sender);
        plugin.getCompletionLeaderboard().request(entries -> show(sender, entries, total));
    }

    private void show(CommandSender sender, List<CompletionLeaderboard.Entry> entries, int total) {
        if (sender instanceof Player player && !player.isOnline()) {
            return;
        }

        if (entries.isEmpty()) {
            Messages.COMMAND_TOP_EMPTY.send(sender);
            return;
        }

        Messages.COMMAND_TOP_HEADER.send(sender, "{total}", String.valueOf(total));

        for (int i = 0; i < Math.min(SHOWN_PLACES, entries.size()); i++) {
            final CompletionLeaderboard.Entry entry = entries.get(i);
            final String name = entry.name() != null ? entry.name() : entry.uuid().toString().substring(0, 8);
            final int position = i + 1;

            if (entry.completed() >= total) {
                Messages.COMMAND_TOP_ENTRY_COMPLETE.send(sender,
                        "{position}", String.valueOf(position),
                        "{player}", name,
                        "{completed}", String.valueOf(entry.completed()),
                        "{total}", String.valueOf(total));
            } else {
                Messages.COMMAND_TOP_ENTRY.send(sender,
                        "{position}", String.valueOf(position),
                        "{player}", name,
                        "{completed}", String.valueOf(entry.completed()),
                        "{total}", String.valueOf(total),
                        "{remaining}", String.valueOf(total - entry.completed()));
            }
        }

        if (sender instanceof Player player) {
            showOwnPlace(player, entries, total);
        }
    }

    private void showOwnPlace(Player player, List<CompletionLeaderboard.Entry> entries, int total) {
        final UUID uuid = player.getUniqueId();

        for (int i = 0; i < entries.size(); i++) {
            final CompletionLeaderboard.Entry entry = entries.get(i);
            if (!entry.uuid().equals(uuid)) {
                continue;
            }

            if (i < SHOWN_PLACES) {
                // The player is in the list above already
                return;
            }

            Messages.COMMAND_TOP_SELF.send(player,
                    "{position}", String.valueOf(i + 1),
                    "{completed}", String.valueOf(entry.completed()),
                    "{total}", String.valueOf(total),
                    "{remaining}", String.valueOf(Math.max(0, total - entry.completed())));
            return;
        }

        Messages.COMMAND_TOP_SELF_NONE.send(player, "{total}", String.valueOf(total));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable String getPermission() {
        return "quests.command.top";
    }
}
