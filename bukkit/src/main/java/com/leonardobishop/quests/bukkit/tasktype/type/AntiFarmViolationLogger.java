package com.leonardobishop.quests.bukkit.tasktype.type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Appends machine-readable anti-farm incidents to plugins/Quests/logi.txt. */
final class AntiFarmViolationLogger {

    private final Path logFile;

    AntiFarmViolationLogger(Path logFile) {
        this.logFile = logFile;
    }

    synchronized void append(String timestamp, String playerName, String playerId, String world,
                             int x, int y, int z, String material, int cycles,
                             String questId, String taskId) throws IOException {
        Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String line = sanitize(timestamp)
                + " | player=" + sanitize(playerName)
                + " | uuid=" + sanitize(playerId)
                + " | world=" + sanitize(world)
                + " | x=" + x + " | y=" + y + " | z=" + z
                + " | material=" + sanitize(material)
                + " | cycles=" + cycles
                + " | quest=" + sanitize(questId)
                + " | task=" + sanitize(taskId)
                + System.lineSeparator();

        Files.write(logFile, line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String sanitize(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replace('|', '/');
    }
}
