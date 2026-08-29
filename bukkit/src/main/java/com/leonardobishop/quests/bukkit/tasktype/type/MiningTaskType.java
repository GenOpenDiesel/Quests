package com.leonardobishop.quests.bukkit.tasktype.type;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.hook.coreprotect.AbstractCoreProtectHook;
import com.leonardobishop.quests.bukkit.hook.playerblocktracker.AbstractPlayerBlockTrackerHook;
import com.leonardobishop.quests.bukkit.item.QuestItem;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.Messages;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.constraint.TaskConstraintSet;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public final class MiningTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;
    private final Table<String, String, QuestItem> fixedQuestItemCache = HashBasedTable.create();
    private final RepeatedBlockCycleTracker repeatedBlockCycles = new RepeatedBlockCycleTracker();
    private final AntiFarmViolationLogger violationLogger;

    public MiningTaskType(BukkitQuestsPlugin plugin) {
        super("blockbreak", TaskUtils.TASK_ATTRIBUTION_STRING, "Break a set amount of a block.", "blockbreakcertain");
        this.plugin = plugin;
        this.violationLogger = new AntiFarmViolationLogger(plugin.getDataFolder().toPath().resolve("logi.txt"));

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useMaterialListConfigValidator(this, TaskUtils.MaterialListConfigValidatorMode.BLOCK, "block", "blocks"));
        super.addConfigValidator(TaskUtils.useItemStackConfigValidator(this, "item"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "data"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "check-playerblocktracker"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "check-coreprotect"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "check-coreprotect-time"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "reverse-if-placed"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "allow-negative-progress"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "allow-silk-touch"));
    }

    @Override
    public void onReady() {
        fixedQuestItemCache.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("NPC")) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        Block block = event.getBlock();
        ItemStack item = plugin.getVersionSpecificHandler().getItemInMainHand(player);
        boolean silkTouchPresent = item != null && item.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        int allowedCycles = getAllowedPlaceBreakCycles();
        long antiFarmReset = getAntiFarmResetMillis();
        RepeatedBlockCycleTracker.CycleResult cycleResult = allowedCycles > 0
                ? repeatedBlockCycles.registerBreak(
                        player.getUniqueId(), block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                        block.getType().name(), System.currentTimeMillis(), antiFarmReset, allowedCycles)
                : new RepeatedBlockCycleTracker.CycleResult(false, 0);
        boolean antiFarmActionTaken = false;

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player mined block " + block.getType(), quest.getId(), task.getId(), player.getUniqueId());

            boolean allowSilkTouch = TaskUtils.getConfigBoolean(task, "allow-silk-touch", true);
            if (!allowSilkTouch && silkTouchPresent) {
                super.debug("allow-silk-touch is enabled, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            super.debug("allow-silk-touch is disabled, checking block", quest.getId(), task.getId(), player.getUniqueId());

            if (!TaskUtils.matchBlock(this, pendingTask, block, player.getUniqueId())) {
                super.debug("Continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            if (task.hasConfigKey("item")) {
                if (item == null) {
                    super.debug("Specific item is required, player has no item in hand; continuing...", quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                }

                super.debug("Specific item is required; player held item is of type '" + item.getType() + "'", quest.getId(), task.getId(), player.getUniqueId());

                QuestItem qi;
                if ((qi = fixedQuestItemCache.get(quest.getId(), task.getId())) == null) {
                    QuestItem fetchedItem = TaskUtils.getConfigQuestItem(task, "item", "data");
                    fixedQuestItemCache.put(quest.getId(), task.getId(), fetchedItem);
                    qi = fetchedItem;
                }

                boolean exactMatch = TaskUtils.getConfigBoolean(task, "exact-match", true);
                if (!qi.compareItemStack(item, exactMatch)) {
                    super.debug("Item does not match required item, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                    continue;
                } else {
                    super.debug("Item matches required item", quest.getId(), task.getId(), player.getUniqueId());
                }
            }

            if (cycleResult.blocked()) {
                super.debug("Anti-farm protection ignored repeated place/break cycles at the same coordinates",
                        quest.getId(), task.getId(), player.getUniqueId());
                if (!antiFarmActionTaken) {
                    handleAntiFarmViolation(player, block, quest, task, cycleResult.completedCycles());
                    antiFarmActionTaken = true;
                }
                continue;
            }

            boolean playerBlockTrackerEnabled = TaskUtils.getConfigBoolean(task, "check-playerblocktracker");

            if (playerBlockTrackerEnabled) {
                AbstractPlayerBlockTrackerHook playerBlockTrackerHook = plugin.getPlayerBlockTrackerHook();
                if (playerBlockTrackerHook != null) {
                    super.debug("Running PlayerBlockTracker lookup", quest.getId(), task.getId(), player.getUniqueId());

                    boolean result = playerBlockTrackerHook.checkBlock(block);
                    if (result) {
                        super.debug("PlayerBlockTracker lookup indicates this is a player placed block, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                        continue;
                    }

                    super.debug("PlayerBlockTracker lookup OK", quest.getId(), task.getId(), player.getUniqueId());
                } else {
                    super.debug("check-playerblocktracker is enabled, but PlayerBlockTracker is not detected on the server", quest.getId(), task.getId(), player.getUniqueId());
                    continue; // we want to prevent progressing in quest if PBT failed to start and was expected to
                }
            }

            Runnable increment = () -> {
                int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress);
                super.debug("Incrementing task progress (now " + progress + ")", quest.getId(), task.getId(), player.getUniqueId());

                int amount = (int) task.getConfigValue("amount");
                if (progress >= amount) {
                    super.debug("Marking task as complete", quest.getId(), task.getId(), player.getUniqueId());
                    taskProgress.setCompleted(true);
                }

                TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, amount);
            };

            boolean coreProtectEnabled = TaskUtils.getConfigBoolean(task, "check-coreprotect");
            int coreProtectTime = (int) task.getConfigValue("check-coreprotect-time", 3600);

            if (coreProtectEnabled) {
                AbstractCoreProtectHook coreProtectHook = plugin.getCoreProtectHook();
                if (coreProtectHook != null) {
                    super.debug("Running CoreProtect lookup (may take a while)", quest.getId(), task.getId(), player.getUniqueId());

                    // Run CoreProtect lookup
                    plugin.getCoreProtectHook().checkBlock(block, coreProtectTime).thenAccept(result -> {
                        if (result) {
                            super.debug("CoreProtect lookup indicates this is a player placed block, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                        } else {
                            super.debug("CoreProtect lookup OK", quest.getId(), task.getId(), player.getUniqueId());
                            increment.run();
                        }
                    }).exceptionally(throwable -> {
                        super.debug("CoreProtect lookup failed: " + throwable.getMessage(), quest.getId(), task.getId(), player.getUniqueId());
                        throwable.printStackTrace();
                        return null;
                    });

                    continue;
                }

                super.debug("check-coreprotect is enabled, but CoreProtect is not detected on the server", quest.getId(), task.getId(), player.getUniqueId());
                continue; // we want to prevent progressing in quest if CoreProtect failed to start and was expected to
            }

            increment.run();
        }
    }

    // Count complete place/break cycles at exact coordinates. A few corrections are harmless;
    // only repeatedly recycling the same block in one spot is ignored by onBlockBreak.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("NPC")) {
            return;
        }

        if (plugin.getPlayerManager().getPlayer(player.getUniqueId()) == null) {
            return;
        }

        Block block = event.getBlock();
        int allowedCycles = getAllowedPlaceBreakCycles();
        if (allowedCycles > 0) {
            repeatedBlockCycles.recordPlacement(player.getUniqueId(), block.getWorld().getUID(),
                    block.getX(), block.getY(), block.getZ(), block.getType().name(),
                    System.currentTimeMillis(), getAntiFarmResetMillis());
        }
    }

    private int getAllowedPlaceBreakCycles() {
        return Math.max(0, plugin.getQuestsConfig().getInt(
                "options.antifarm-place-break-max-cycles-per-location", 5));
    }

    private long getAntiFarmResetMillis() {
        int seconds = plugin.getQuestsConfig().getInt("options.antifarm-place-break-reset-seconds", 600);
        return Math.max(1L, seconds) * 1_000L;
    }

    private void handleAntiFarmViolation(Player player, Block block, Quest quest, Task task, int cycles) {
        String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now());
        String playerName = player.getName();
        String playerId = player.getUniqueId().toString();
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        String material = block.getType().name();
        String questId = quest.getId();
        String taskId = task.getId();

        plugin.getScheduler().doAsync(() -> {
            try {
                violationLogger.append(timestamp, playerName, playerId, world, x, y, z,
                        material, cycles, questId, taskId);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not append mining anti-farm incident to logi.txt", exception);
            }
        });

        String kickMessage = Messages.TASK_ANTIFARM_KICK.getMessageLegacyColor()
                .replace("{cycles}", Integer.toString(cycles))
                .replace("{world}", world)
                .replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y))
                .replace("{z}", Integer.toString(z));
        kickMessage = plugin.applyPlayerAndPAPI(BukkitQuestsPlugin.PAPIType.QUESTS, player, kickMessage);
        String finalKickMessage = kickMessage;
        plugin.getScheduler().runTaskAtEntity(player, () -> {
            if (player.isOnline()) {
                player.kickPlayer(finalKickMessage);
            }
        });
    }
}
