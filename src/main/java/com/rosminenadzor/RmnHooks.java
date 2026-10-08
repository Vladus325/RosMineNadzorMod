package com.rosminenadzor;

import com.rosminenadzor.RmnBan.Kind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.common.Tags;

/**
 * Серверные хуки «РосМайнНадзора»: ломание/установка блоков, прыжок по запретной
 * поверхности, клики (лодки, лошади, двери, сон, эндер-жемчуг), PvP-экипировка
 * и запрещённые буквы в чате. «Глупые» запреты (шифт/спринт) ловит тик
 * {@link RmnOverwatch}. Нарушение = отмена действия + {@link RmnOverwatch#onViolation}.
 */
public final class RmnHooks {
    private RmnHooks() {
    }

    /**
     * Запрет на ломание: блок ЛОМАЕТСЯ как обычно (дроп обычный), но нарушение
     * фиксируется и запускает лестницу наказаний — надзор карает, а не делает
     * игрока неуязвимым для собственных запретов.
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        RmnBan ban = RmnOverwatch.stateBan(Kind.BREAK, event.getState());
        if (ban == null) return;
        RmnOverwatch.onViolation(player, ban);
    }

    /**
     * Запрет на установку: блок СТАВИТСЯ как обычно, но нарушение фиксируется
     * и запускает лестницу наказаний (на этапах 1–2 запрещённые блоки из
     * инвентаря конфискуются). Надзор карает, а не блокирует.
     */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RmnBan ban = RmnOverwatch.stateBan(Kind.PLACE, event.getPlacedBlock());
        if (ban == null) return;
        RmnOverwatch.onViolation(player, ban);
    }

    /** Запрет на прыжки по поверхности: прыжок не отменить, но надзор всё видел. */
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var support = player.mainSupportingBlockPos;
        if (support.isEmpty()) return;
        RmnBan ban = RmnOverwatch.stateBan(Kind.JUMP_ON,
                player.level().getBlockState(support.get()));
        if (ban == null) return;
        RmnOverwatch.onViolation(player, ban);
    }

    /** Клики по блокам: лодки (спуск на воду), кровати ночью, эндер-жемчуг, двери. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        if (RmnOverwatch.banActive(RmnBan.NO_BOAT.id) && held.is(ItemTags.BOATS)) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(player, RmnBan.NO_BOAT);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_BED.id) && held.is(ItemTags.BEDS)
                && player.level().isNight()) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(player, RmnBan.NO_BED);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_PEARL.id) && held.is(Items.ENDER_PEARL)) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(player, RmnBan.NO_PEARL);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_DOOR.id)
                && RmnBan.isDoorBlock(event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(player, RmnBan.NO_DOOR);
        }
    }

    /** Клики «в воздух»: бросок эндер-жемчуга. */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!RmnOverwatch.banActive(RmnBan.NO_PEARL.id)) return;
        if (!event.getItemStack().is(Items.ENDER_PEARL)) return;
        event.setCanceled(true);
        RmnOverwatch.onViolation(player, RmnBan.NO_PEARL);
    }

    /** Клики по сущностям: плавсредства и верховые животные. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var target = event.getTarget();
        if (RmnOverwatch.banActive(RmnBan.NO_BOAT.id)
                && (target instanceof net.minecraft.world.entity.vehicle.Boat
                    || target.getType().is(Tags.EntityTypes.BOATS))) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(player, RmnBan.NO_BOAT);
            return;
        }
        // AbstractHorse: лошади, ослы, мулы, скелетные/зомби-лошади, ламы, верблюды
        if (RmnOverwatch.banActive(RmnBan.NO_HORSE.id)
                && target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(player, RmnBan.NO_HORSE);
        }
    }

    /** PvP-запреты: оружие в руке атакующего и броня на жертве (по рукам атакующего). */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!(event.getEntity() instanceof ServerPlayer target)) return;
        if (attacker == target) return;

        ItemStack weapon = attacker.getMainHandItem();
        if (RmnBan.isWeapon(weapon)) {
            if (RmnOverwatch.banActive(RmnBan.NO_WEAPON.id)) {
                event.setCanceled(true);
                RmnOverwatch.onViolation(attacker, RmnBan.NO_WEAPON);
                return;
            }
            boolean enchanted = weapon.isEnchanted();
            if (enchanted && RmnOverwatch.banActive(RmnBan.NO_ENCH_WEAPON.id)) {
                event.setCanceled(true);
                RmnOverwatch.onViolation(attacker, RmnBan.NO_ENCH_WEAPON);
                return;
            }
            if (!enchanted && RmnOverwatch.banActive(RmnBan.NO_UNENCH_WEAPON.id)) {
                event.setCanceled(true);
                RmnOverwatch.onViolation(attacker, RmnBan.NO_UNENCH_WEAPON);
                return;
            }
        }

        if (!hasArmor(target)) return;
        if (RmnOverwatch.banActive(RmnBan.NO_ENCH_ARMOR.id) && isFullyEnchantedArmor(target)) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(attacker, RmnBan.NO_ENCH_ARMOR);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_UNENCH_ARMOR.id) && hasUnenchantedArmorPiece(target)) {
            event.setCanceled(true);
            RmnOverwatch.onViolation(attacker, RmnBan.NO_UNENCH_ARMOR);
        }
    }

    /** Запрещённые буквы в чате: сообщение конфискуется, нарушение фиксируется
     *  (букв может быть несколько — встроенные и кастомные). */
    public static void onChat(ServerChatEvent event) {
        if (!RmnOverwatch.isActive()) return;
        String raw = event.getRawText();
        for (RmnBan letter : RmnOverwatch.activeLetterBans()) {
            if (!letter.containsChar(raw)) continue;
            event.setCanceled(true);
            RmnOverwatch.onViolation(event.getPlayer(), letter);
            return;
        }
    }

    /** Есть ли на игроке хоть какая-то броня. */
    private static boolean hasArmor(ServerPlayer player) {
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (RmnBan.isArmor(player.getItemBySlot(slot))) return true;
        }
        return false;
    }

    /** Вся надетая броня зачарована (для запрета зачарованной). */
    private static boolean isFullyEnchantedArmor(ServerPlayer player) {
        boolean any = false;
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack piece = player.getItemBySlot(slot);
            if (!RmnBan.isArmor(piece)) continue;
            any = true;
            if (!piece.isEnchanted()) return false;
        }
        return any;
    }

    /** Есть ли хоть одна незачарованная часть брони (для запрета незачарованной). */
    private static boolean hasUnenchantedArmorPiece(ServerPlayer player) {
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack piece = player.getItemBySlot(slot);
            if (RmnBan.isArmor(piece) && !piece.isEnchanted()) return true;
        }
        return false;
    }
}
