package com.leonardobishop.quests.bukkit.hook.versionspecific;

import com.leonardobishop.quests.common.versioning.Version;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

@NullMarked
public class VersionSpecificHandler_V1_20_4 extends VersionSpecificHandler_V1_20 {

    static @Nullable Method getItemMethod;

    static {
        try {
            getItemMethod = AbstractArrow.class.getMethod("getItem");
        } catch (NoSuchMethodException e) {
            // supported only on Spigot
        }
    }

    @Override
    public Version getMinecraftVersion() {
        return Version.V1_20_4;
    }

    @Override
    public @Nullable Player getDamager(@Nullable EntityDamageEvent event) {
        if (event == null) {
            return null;
        }

        DamageSource source = event.getDamageSource();
        Entity causingEntity = source.getCausingEntity();

        if (causingEntity instanceof Player) {
            return (Player) causingEntity;
        }

        return null;
    }

    @Override
    public @Nullable Entity getDirectSource(@Nullable EntityDamageEvent event) {
        if (event == null) {
            return null;
        }

        DamageSource source = event.getDamageSource();
        return source.getDirectEntity();
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public @Nullable ItemStack getAbstractArrowItem(Entity entity) {
        // We prefer the super return value, as Paper implementations usually work better
        if (getItemStackMethod != null) {
            return super.getAbstractArrowItem(entity);
        }

        if (getItemMethod == null) {
            return null;
        }

        if (!(entity instanceof AbstractArrow arrow)) {
            return null;
        }

        try {
            return (ItemStack) getItemMethod.invoke(arrow);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
