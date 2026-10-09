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
        if (ban != null) RmnOverwatch.onViolation(player, ban);
        checkUse(player);
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
            return;
        }
        // Запрет взаимодействия с блоками (USEBLOCK): открытие проходит, но карается
        RmnBan useBlock = RmnOverwatch.stateBan(Kind.USEBLOCK,
                event.getLevel().getBlockState(event.getPos()));
        if (useBlock != null) RmnOverwatch.onViolation(player, useBlock);
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
     * Удар: атака ПРОХОДИТ, но если в руке запретное оружие (USE) — нарушение.
     * PvP-запреты удалены: боя без запретов больше не ограничиваем.
     */
    public static void onIncomingDamage(LivingAttackEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        checkUse(attacker);
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

    /** Нарушение за использование запретного предмета в главной руке (USE). */
    private static void checkUse(ServerPlayer player) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) return;
        for (RmnBan ban : RmnOverwatch.activeUseBans()) {
            if (ban.matchesItem(hand)) {
                RmnOverwatch.onViolation(player, ban);
                return;
            }
        }
    }
}
