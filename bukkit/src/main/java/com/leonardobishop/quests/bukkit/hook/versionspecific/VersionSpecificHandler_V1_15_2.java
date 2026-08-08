package com.leonardobishop.quests.bukkit.hook.versionspecific;

import com.leonardobishop.quests.common.versioning.Version;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

@NullMarked
public class VersionSpecificHandler_V1_15_2 extends VersionSpecificHandler_V1_14 {

    static @Nullable Method getItemStackMethod;

    static {
        try {
            getItemStackMethod = AbstractArrow.class.getMethod("getItemStack");
        } catch (NoSuchMethodException e) {
            // supported only on Paper
        }
    }

    @Override
    public Version getMinecraftVersion() {
        return Version.V1_15_2;
    }

    @Override
    public ItemStack getItemInEquipmentSlot(PlayerInventory inventory, EquipmentSlot slot) {
        return inventory.getItem(slot);
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public @Nullable ItemStack getAbstractArrowItem(Entity entity) {
        if (getItemStackMethod == null) {
            return null;
        }

        if (!(entity instanceof AbstractArrow arrow)) {
            return null;
        }

        try {
            return (ItemStack) getItemStackMethod.invoke(arrow);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
