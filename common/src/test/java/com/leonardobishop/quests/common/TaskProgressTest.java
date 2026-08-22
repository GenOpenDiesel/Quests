package com.leonardobishop.quests.common;

import com.leonardobishop.quests.common.player.questprogressfile.QuestProgress;
import com.leonardobishop.quests.common.player.questprogressfile.QuestProgressFile;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.plugin.Quests;
import com.leonardobishop.quests.common.quest.QuestCompleter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TaskProgressTest {

    @Test
    void completionNotificationIsRaisedOncePerTransition() {
        QuestCompleter completer = new QuestCompleter() {
            @Override
            public void queueSingular(QuestProgress questProgress) { }

            @Override
            public void queueFullCheck(QuestProgressFile questProgressFile) { }
        };

        Quests plugin = (Quests) Proxy.newProxyInstance(
                Quests.class.getClassLoader(),
                new Class<?>[]{Quests.class},
                (proxy, method, args) -> method.getName().equals("getQuestCompleter") ? completer : null
        );

        UUID playerId = UUID.randomUUID();
        QuestProgress questProgress = new QuestProgress(plugin, "test", playerId,
                true, 0L, false, false, 0L);
        TaskProgress taskProgress = questProgress.getTaskProgress("mining");

        assertFalse(taskProgress.consumeCompletionNotification());

        taskProgress.setCompleted(true);
        assertTrue(taskProgress.consumeCompletionNotification());
        assertFalse(taskProgress.consumeCompletionNotification());

        taskProgress.setCompleted(true);
        assertFalse(taskProgress.consumeCompletionNotification());

        taskProgress.setCompleted(false);
        taskProgress.setCompleted(true);
        assertTrue(taskProgress.consumeCompletionNotification());
    }
}
