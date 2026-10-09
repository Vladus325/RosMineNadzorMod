package com.rosminenadzor;

import com.rosminenadzor.RmnBan.Kind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Серверные хуки «РосМайнНадзора» (Forge 26.1): ломание/установка блоков,
 * прыжок по запретной поверхности, клики (лодки, лошади, двери, сон, эндер-жемчуг),
 * PvP-экипировка и запрещённые буквы в чате. «Глупые» запреты (шифт/спринт)
 * ловит тик {@link RmnOverwatch}. Нарушение = {@link RmnOverwatch#onViolation}
 * (действие НЕ отменяется — надзор карает, а не блокирует).
 * <p>
 * PlayerInteractEvent в Forge 26 — один общий BUS на все под-ивенты, поэтому
 * весь интеракт ловится в onPlayerInteract и разбирается по instanceof.
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

    /** Общий обработчик кликов: разобрать под-ивент и проверить запреты. */
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event instanceof PlayerInteractEvent.RightClickBlock rightClickBlock) {
            onRightClickBlock(player, rightClickBlock);
        } else if (event instanceof PlayerInteractEvent.RightClickItem rightClickItem) {
            onRightClickItem(player, rightClickItem);
        } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific entityInteract) {
            onEntityInteract(player, entityInteract);
        }
    }

    private static void onRightClickBlock(ServerPlayer player, PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (RmnOverwatch.banActive(RmnBan.NO_BOAT.id) && held.is(ItemTags.BOATS)) {
            RmnOverwatch.onViolation(player, RmnBan.NO_BOAT);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_BED.id) && held.is(ItemTags.BEDS)
                && isNight(player)) {
            RmnOverwatch.onViolation(player, RmnBan.NO_BED);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_PEARL.id) && held.is(Items.ENDER_PEARL)) {
            RmnOverwatch.onViolation(player, RmnBan.NO_PEARL);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_DOOR.id)
                && RmnBan.isDoorBlock(event.getLevel().getBlockState(event.getPos()))) {
            RmnOverwatch.onViolation(player, RmnBan.NO_DOOR);
        }
    }

    private static void onRightClickItem(ServerPlayer player, PlayerInteractEvent.RightClickItem event) {
        if (!RmnOverwatch.banActive(RmnBan.NO_PEARL.id)) return;
        if (!event.getItemStack().is(Items.ENDER_PEARL)) return;
        RmnOverwatch.onViolation(player, RmnBan.NO_PEARL);
    }

    private static void onEntityInteract(ServerPlayer player, PlayerInteractEvent.EntityInteractSpecific event) {
        var target = event.getTarget();
        if (RmnOverwatch.banActive(RmnBan.NO_BOAT.id)
                && target instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) {
            RmnOverwatch.onViolation(player, RmnBan.NO_BOAT);
            return;
        }
        // AbstractHorse (equine): лошади, ослы, мулы, скелетные/зомби-лошади, ламы, верблюды
        if (RmnOverwatch.banActive(RmnBan.NO_HORSE.id)
                && target instanceof net.minecraft.world.entity.animal.equine.AbstractHorse) {
            RmnOverwatch.onViolation(player, RmnBan.NO_HORSE);
        }
    }

    /**
     * PvP-запреты: оружие в руке атакующего и броня на жертве (по рукам атакующего).
     * EventBus 7: отмена — возврат true из листенера (Predicate), setCanceled удалён.
     */
    public static boolean onIncomingDamage(LivingAttackEvent event) {
        if (!RmnOverwatch.isActive()) return false;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return false;
        if (!(event.getEntity() instanceof ServerPlayer target)) return false;
        if (attacker == target) return false;

        ItemStack weapon = attacker.getMainHandItem();
        if (RmnBan.isWeapon(weapon)) {
            if (RmnOverwatch.banActive(RmnBan.NO_WEAPON.id)) {
                RmnOverwatch.onViolation(attacker, RmnBan.NO_WEAPON);
                return true;
            }
            boolean enchanted = weapon.isEnchanted();
            if (enchanted && RmnOverwatch.banActive(RmnBan.NO_ENCH_WEAPON.id)) {
                RmnOverwatch.onViolation(attacker, RmnBan.NO_ENCH_WEAPON);
                return true;
            }
            if (!enchanted && RmnOverwatch.banActive(RmnBan.NO_UNENCH_WEAPON.id)) {
                RmnOverwatch.onViolation(attacker, RmnBan.NO_UNENCH_WEAPON);
                return true;
            }
        }

        if (!hasArmor(target)) return false;
        if (RmnOverwatch.banActive(RmnBan.NO_ENCH_ARMOR.id) && isFullyEnchantedArmor(target)) {
            RmnOverwatch.onViolation(attacker, RmnBan.NO_ENCH_ARMOR);
            return true;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_UNENCH_ARMOR.id) && hasUnenchantedArmorPiece(target)) {
            RmnOverwatch.onViolation(attacker, RmnBan.NO_UNENCH_ARMOR);
            return true;
        }
        return false;
    }

    /** Запрещённые буквы в чате: сообщение конфискуется (возврат true = отмена),
     *  нарушение фиксируется (букв может быть несколько — встроенные и кастомные). */
    public static boolean onChat(ServerChatEvent event) {
        if (!RmnOverwatch.isActive()) return false;
        String raw = event.getRawText();
        for (RmnBan letter : RmnOverwatch.activeLetterBans()) {
            if (!letter.containsChar(raw)) continue;
            RmnOverwatch.onViolation(event.getPlayer(), letter);
            return true;
        }
        return false;
    }

    /** Ночь по времени суток (26.1 убрал Level.isNight; ванильное окно ночи 12542..23459). */
    private static boolean isNight(ServerPlayer player) {
        long t = player.level().getDefaultClockTime() % 24000L;
        return t >= 12542L && t <= 23459L;
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
