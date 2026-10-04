package com.leonardobishop.quests.common.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestManagerTest {

    private final QuestManager questManager = new QuestManager();

    private static Quest quest(String id, boolean countsTowardsCompleted) {
        return new Quest.Builder(id)
                .withCountsTowardsCompleted(countsTowardsCompleted)
                .build();
    }

    @Test
    void countsOnlyTheQuestsWhichCountTowardsCompletion() {
        questManager.registerQuest(quest("one", true));
        questManager.registerQuest(quest("two", true));
        questManager.registerQuest(quest("daily", false));

        assertEquals(2, questManager.getCompletionCountingQuestCount());
    }

    @Test
    void returnsZeroWithoutAnyQuest() {
        assertEquals(0, questManager.getCompletionCountingQuestCount());
    }

    @Test
    void recountsAfterAQuestIsRegistered() {
        questManager.registerQuest(quest("one", true));
        assertEquals(1, questManager.getCompletionCountingQuestCount());

        questManager.registerQuest(quest("two", true));
        assertEquals(2, questManager.getCompletionCountingQuestCount());
    }

    @Test
    void recountsAfterTheRegistryIsCleared() {
        questManager.registerQuest(quest("one", true));
        assertEquals(1, questManager.getCompletionCountingQuestCount());

        questManager.clear();
        assertEquals(0, questManager.getCompletionCountingQuestCount());
    }

    @Test
    void doesNotCountAQuestTwiceWhenItIsReRegistered() {
        questManager.registerQuest(quest("one", true));
        questManager.registerQuest(quest("one", true));

        assertEquals(1, questManager.getCompletionCountingQuestCount());
    }
}
