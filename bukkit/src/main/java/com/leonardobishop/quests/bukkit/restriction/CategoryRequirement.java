package com.leonardobishop.quests.bukkit.restriction;

import org.jetbrains.annotations.NotNull;

/**
 * The extra requirements a player has to meet before they can play quests of a category.
 * Defined in the main configuration under {@code options.category-requirements.categories}.
 *
 * @param categoryId the id of the category, as used in {@code options.category} of a quest
 * @param displayName the name of the category shown to players in messages
 * @param requiredPlaytime the minimum playtime, in minutes, needed to start or progress
 *                         quests of this category
 */
public record CategoryRequirement(@NotNull String categoryId, @NotNull String displayName, int requiredPlaytime) {

    public boolean hasPlaytimeRequirement() {
        return requiredPlaytime > 0;
    }
}
