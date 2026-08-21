package com.leonardobishop.quests.bukkit.command;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.restriction.AltAccountRecord;
import com.leonardobishop.quests.bukkit.restriction.CategoryRequirement;
import com.leonardobishop.quests.bukkit.restriction.QuestRestrictionManager;
import com.leonardobishop.quests.bukkit.util.Messages;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shows why a player is (not) allowed to play quests: their playtime, the addresses they have
 * connected from and the accounts sharing them. Also manages the exemptions which let specific
 * accounts (siblings, for example) share an address without blocking each other.
 */
public class AdminRestrictionsCommandHandler implements CommandHandler {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private static final List<String> SUBCOMMANDS = List.of("exempt", "unexempt", "exemptions");

    private final BukkitQuestsPlugin plugin;

    public AdminRestrictionsCommandHandler(BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        QuestRestrictionManager manager = plugin.getQuestRestrictionManager();

        if (args.length == 2) {
            sender.sendMessage(ChatColor.GRAY + "Categories with a playtime requirement:");
            for (CategoryRequirement requirement : manager.getCategoryRequirements().values()) {
                sender.sendMessage(ChatColor.DARK_GRAY + " * " + ChatColor.RED + requirement.categoryId()
                        + ChatColor.GRAY + ": " + requirement.requiredPlaytime() + " minutes");
            }
            sender.sendMessage(ChatColor.GRAY + "Multi-account protection: " + ChatColor.RED
                    + manager.getMultiAccountPeriodDays() + " days");
            sender.sendMessage(ChatColor.GRAY + "Accounts allowed per address: " + ChatColor.RED
                    + manager.getMaxAccountsPerAddress());
            sender.sendMessage(ChatColor.GRAY + "Tracked accounts: " + ChatColor.RED + manager.getStore().getRecords().size());
            sender.sendMessage(ChatColor.GRAY + "Exempt pairs: " + ChatColor.RED + manager.getExemptions().getPairs().size());
            sender.sendMessage(ChatColor.DARK_GRAY + "View a player using /q a restrictions [player].");
            sender.sendMessage(ChatColor.DARK_GRAY + "Pair two accounts using /q a restrictions exempt <player> <player>.");
            return;
        }

        switch (args[2].toLowerCase()) {
            case "exempt" -> handleExempt(sender, args, true);
            case "unexempt" -> handleExempt(sender, args, false);
            case "exemptions" -> handleList(sender, args);
            default -> handlePlayer(sender, args[2]);
        }
    }

    private void handleExempt(CommandSender sender, String[] args, boolean add) {
        if (args.length != 5) {
            sender.sendMessage(ChatColor.RED + "Usage: /q a restrictions " + args[2] + " <player> <player>");
            return;
        }

        String one = args[3];
        String other = args[4];

        if (one.equalsIgnoreCase(other)) {
            sender.sendMessage(ChatColor.RED + "An account cannot be paired with itself.");
            return;
        }

        QuestRestrictionManager manager = plugin.getQuestRestrictionManager();
        boolean changed = add
                ? manager.getExemptions().addPair(one, other)
                : manager.getExemptions().removePair(one, other);

        if (!changed) {
            sender.sendMessage(ChatColor.RED + "'" + one + "' and '" + other + "' are "
                    + (add ? "already" : "not") + " paired.");
            return;
        }

        // a pair can unblock (or re-block) accounts on any address
        manager.invalidateAll();
        plugin.getScheduler().doAsync(() -> manager.getExemptions().save());

        sender.sendMessage(ChatColor.GRAY + (add
                ? "'" + one + "' and '" + other + "' may now both play quests from the same address."
                : "'" + one + "' and '" + other + "' are no longer paired."));
    }

    private void handleList(CommandSender sender, String[] args) {
        QuestRestrictionManager manager = plugin.getQuestRestrictionManager();

        if (args.length >= 4) {
            String name = args[3];
            sender.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "Accounts paired with '" + name + "'");
            if (manager.getExemptions().getPartners(name).isEmpty()) {
                sender.sendMessage(ChatColor.DARK_GRAY + " * " + ChatColor.GRAY + "none");
                return;
            }
            for (String partner : manager.getExemptions().getPartners(name)) {
                sender.sendMessage(ChatColor.DARK_GRAY + " * " + ChatColor.GRAY + partner);
            }
            return;
        }

        List<String[]> pairs = manager.getExemptions().getPairs();
        sender.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "Exempt pairs (" + pairs.size() + ")");
        if (pairs.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_GRAY + " * " + ChatColor.GRAY + "none");
            return;
        }
        for (String[] pair : pairs) {
            sender.sendMessage(ChatColor.DARK_GRAY + " * " + ChatColor.GRAY + pair[0]
                    + ChatColor.DARK_GRAY + " <-> " + ChatColor.GRAY + pair[1]);
        }
    }

    private void handlePlayer(CommandSender sender, String name) {
        QuestRestrictionManager manager = plugin.getQuestRestrictionManager();

        Player player = plugin.getServer().getPlayerExact(name);
        if (player == null) {
            Messages.COMMAND_QUEST_ADMIN_PLAYERNOTFOUND.send(sender, "{player}", name);
            return;
        }

        sender.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "Restrictions for '" + player.getName() + "'");

        long playtime = manager.getPlaytime(player);
        sender.sendMessage(ChatColor.RED + "Playtime: " + ChatColor.GRAY
                + (playtime < 0 ? "unknown" : playtime + " minutes"));
        sender.sendMessage(ChatColor.RED + "Bypasses playtime: " + ChatColor.GRAY
                + player.hasPermission(QuestRestrictionManager.BYPASS_PLAYTIME_PERMISSION));
        sender.sendMessage(ChatColor.RED + "Bypasses multi-account: " + ChatColor.GRAY
                + player.hasPermission(QuestRestrictionManager.BYPASS_MULTI_ACCOUNT_PERMISSION));

        java.util.Set<String> partners = manager.getExemptions().getPartners(player.getName());
        sender.sendMessage(ChatColor.RED + "Paired with: " + ChatColor.GRAY
                + (partners.isEmpty() ? "nobody" : String.join(", ", partners)));

        AltAccountRecord record = manager.getStore().getRecord(player.getUniqueId());
        sender.sendMessage(ChatColor.RED + "Addresses: " + ChatColor.GRAY
                + (record == null ? "none" : String.join(", ", record.getAddresses())));
        sender.sendMessage(ChatColor.RED + "Last quest activity: " + ChatColor.GRAY
                + (record == null || record.getLastActivity() == 0 ? "never" : DATE_FORMAT.format(new Date(record.getLastActivity()))));

        QuestRestrictionManager.AltAccountBlock block = manager.getMultiAccountBlock(player);
        if (block == null) {
            sender.sendMessage(ChatColor.RED + "Blocked by other accounts: " + ChatColor.GRAY + "no");
        } else {
            sender.sendMessage(ChatColor.RED + "Blocked by other accounts: " + ChatColor.GRAY
                    + block.accounts() + "/" + manager.getMaxAccountsPerAddress() + " slots taken");
            sender.sendMessage(ChatColor.RED + "First slot frees up in: " + ChatColor.GRAY
                    + TimeUnit.MILLISECONDS.toMinutes(block.getRemainingTime()) + " minutes ("
                    + block.name() + ")");
        }

        if (record != null) {
            sender.sendMessage(ChatColor.RED.toString() + ChatColor.UNDERLINE + "Accounts sharing an address");
            boolean found = false;
            for (String address : record.getAddresses()) {
                for (AltAccountRecord other : manager.getStore().getRecordsForAddress(address, player.getUniqueId())) {
                    found = true;
                    boolean exempt = manager.getExemptions().isExempt(player.getName(), other.getName());
                    sender.sendMessage(ChatColor.DARK_GRAY + " * " + ChatColor.GRAY + other.getName()
                            + (exempt ? ChatColor.GREEN + " [exempt]" : "")
                            + ChatColor.DARK_GRAY + " (" + address + ", last activity: "
                            + (other.getLastActivity() == 0 ? "never" : DATE_FORMAT.format(new Date(other.getLastActivity()))) + ")");
                }
            }
            if (!found) {
                sender.sendMessage(ChatColor.DARK_GRAY + " * " + ChatColor.GRAY + "none");
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 3) {
            List<String> ret = new ArrayList<>(SUBCOMMANDS);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                ret.add(player.getName());
            }
            return ret;
        }

        if (args.length == 4 && SUBCOMMANDS.contains(args[2].toLowerCase())) {
            if (args[2].equalsIgnoreCase("unexempt")) {
                List<String> ret = new ArrayList<>();
                for (String[] pair : plugin.getQuestRestrictionManager().getExemptions().getPairs()) {
                    ret.add(pair[0]);
                }
                return ret;
            }
            return null; // default to online player names
        }

        if (args.length == 5 && args[2].equalsIgnoreCase("unexempt")) {
            return new ArrayList<>(plugin.getQuestRestrictionManager().getExemptions().getPartners(args[3]));
        }

        if (args.length == 5 && args[2].equalsIgnoreCase("exempt")) {
            return null; // default to online player names
        }

        return Collections.emptyList();
    }

    @Override
    public @Nullable String getPermission() {
        return "quests.admin";
    }
}
