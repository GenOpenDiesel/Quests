package com.leonardobishop.quests.bukkit.tasktype.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiFarmViolationLoggerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void appendsOneCompleteIncidentPerLine() throws Exception {
        Path log = temporaryDirectory.resolve("Quests").resolve("logi.txt");
        AntiFarmViolationLogger logger = new AntiFarmViolationLogger(log);

        logger.append("2026-08-28T21:00:00+02:00", "Player|One", "uuid-1", "world",
                10, 64, -5, "STONE", 6, "mining", "stone");
        logger.append("2026-08-28T21:01:00+02:00", "PlayerTwo", "uuid-2", "world_nether",
                -2, 70, 3, "NETHERRACK", 7, "mining", "nether");

        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("player=Player/One"));
        assertTrue(lines.get(0).contains("x=10 | y=64 | z=-5"));
        assertTrue(lines.get(0).contains("cycles=6"));
        assertTrue(lines.get(1).contains("material=NETHERRACK"));
    }
}
