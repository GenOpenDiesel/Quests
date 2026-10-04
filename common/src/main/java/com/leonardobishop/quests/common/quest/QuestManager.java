package com.leonardobishop.quests.common.quest;

import com.leonardobishop.quests.common.util.Modern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The quests manager stores all present Quests and Categories on the server and is used as a registry.
 */
@Modern(type = Modern.Type.FULL)
@NullMarked
public final class QuestManager {

    private final Map<String, Quest> questMap;
    private final List<Category> categories;

    /** Lazily computed by {@link QuestManager#getCompletionCountingQuestCount()}, -1 when it has to be recomputed. */
    private int completionCountingQuestCount = -1;

    /**
     * Constructs a QuestManager.
     */
    public QuestManager() {
        // Specify expected size as people tend to add horrendous amounts of quests
        this.questMap = LinkedHashMap.newLinkedHashMap(1024);
        this.categories = new ArrayList<>();
    }

    /**
     * Register a quest with the quest manager
     *
     * @param quest the category to register
     */
    public void registerQuest(final Quest quest) {
        Objects.requireNonNull(quest, "quest cannot be null");

        this.questMap.put(quest.getId(), quest);
        this.completionCountingQuestCount = -1;
    }

    /**
     * Get how many of the registered quests count towards completion, as per the
     * {@code options.counts-towards-completed} option of a quest. This is the number of quests a player
     * has to have completed to have completed every quest currently on the server.
     * <p>
     * The result is cached and only recomputed after the registry has changed, as this is read on every
     * placeholder request.
     *
     * @return count of registered quests which count towards completion
     */
    public int getCompletionCountingQuestCount() {
        int count = this.completionCountingQuestCount;

        if (count == -1) {
            count = 0;

            for (final Quest quest : this.questMap.values()) {
                if (quest.doesCountTowardsCompleted()) {
                    count++;
                }
            }

            this.completionCountingQuestCount = count;
        }

        return count;
    }

    /**
     * @param id id to match
     * @return {@link Quest}, or null
     */
    @Contract(pure = true)
    public @Nullable Quest getQuestById(final String id) {
        Objects.requireNonNull(id, "id cannot be null");

        return this.questMap.get(id);
    }

    /**
     * Get a map of id-quest of all quests registered
     *
     * @return immutable map of all {@link Quest}
     */
    @Contract(pure = true)
    public @UnmodifiableView Map<String, Quest> getQuestMap() {
        return Collections.unmodifiableMap(this.questMap);
    }

    /**
     * Register a category with the quest manager
     *
     * @param category the category to register
     */
    public void registerCategory(final Category category) {
        Objects.requireNonNull(category, "category cannot be null");

        this.categories.add(category);
    }

    /**
     * Get a specific category by id
     *
     * @param id the id
     * @return {@link Category}, or null
     */
    @Contract(pure = true)
    public @Nullable Category getCategoryById(final String id) {
        Objects.requireNonNull(id, "id cannot be null");

        for (final Category category : this.categories) {
            if (category.getId().equals(id)) {
                return category;
            }
        }

        return null;
    }

    /**
     * Get a map of id-category of all categories registered
     *
     * @return immutable map of all {@link Quest}
     */
    @Contract(pure = true)
    public @UnmodifiableView List<Category> getCategories() {
        return Collections.unmodifiableList(this.categories);
    }

    /**
     * Reset the quest manager and clears all registered quests and categories
     */
    public void clear() {
        this.questMap.clear();
        this.categories.clear();
        this.completionCountingQuestCount = -1;
    }

    // DEPRECATED AND FOR REMOVAL

    /**
     * Get a map of id-quest of all quests registered
     *
     * @return immutable map of all {@link Quest}
     * @see QuestManager#getQuestMap()
     */
    @Deprecated(forRemoval = true)
    @Contract(pure = true)
    public @UnmodifiableView Map<String, Quest> getQuests() {
        return this.getQuestMap();
    }
}
