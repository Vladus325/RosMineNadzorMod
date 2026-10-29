package com.rosminenadzor;

import com.rosminenadzor.RmnBan.Kind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Серверные хуки «РосМайнНадзора» (NeoForge 26.1): ломание/установка блоков,
 * прыжок по запретной поверхности, использование запретных инструментов/оружия,
 * клики (лодки, лошади, двери, сон, эндер-жемчуг, USEBLOCK-блоки) и запрещённые
 * буквы в чате. «Глупые» запреты (шифт/спринт) и запрет ношения брони ловит тик
 * {@link RmnOverwatch}. Нарушение = {@link RmnOverwatch#onViolation} (действие
 * НЕ отменяется — надзор карает, а не блокирует).
 */
public final class RmnHooks {
    private RmnHooks() {
    }

    /**
     * Запрет на ломание: 26.1 убрал BreakEvent — нарушение фиксируется по
     * BreakBlockEvent (наследник его семантики: стреляет на попытке сломать при
     * ЛЮБОМ режиме игры, включая креатив, где BlockDropsEvent мог не дойти без
     * дропа). Событие клиент-серверное — фильтр по ServerPlayer. Отдельно —
     * использование запретного инструмента в руке (USE).
     */
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        RmnBan ban = RmnOverwatch.stateBan(Kind.BREAK, event.getState());
        if (ban != null) RmnOverwatch.onViolation(player, ban);
        checkUse(player);
    }

    /**
     * Запрет на установку: блок СТАВИТСЯ как обычно, но нарушение фиксируется
     * (на этапах 1–2 запрещённые блоки из инвентаря конфискуются).
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

    /** Клики по блокам: лодки (спуск на воду), кровати ночью, эндер-жемчуг, двери, USEBLOCK-блоки. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        if (RmnOverwatch.banActive(RmnBan.NO_BOAT.id) && held.is(ItemTags.BOATS)) {
            RmnOverwatch.onViolation(player, RmnBan.NO_BOAT);
            return;
        }
        if (RmnOverwatch.banActive(RmnBan.NO_BED.id)
                && event.getLevel().getBlockState(event.getPos()).is(BlockTags.BEDS)
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

    /**
     * Клики «в воздух» и ПО ЖИДКОСТИ: спуск лодки на воду и бросок эндер-жемчуга.
     * Клик по БЛОКУ с лодкой в руке ловит onRightClickBlock, а вода мимо raytrace
     * по блокам — срабатывает RightClickItem; без этой проверки запрет лодки
     * не стрелял ровно в главном случае (спуск на воду).
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack held = event.getItemStack();
        if (RmnOverwatch.banActive(RmnBan.NO_BOAT.id) && held.is(ItemTags.BOATS)) {
            RmnOverwatch.onViolation(player, RmnBan.NO_BOAT);
            return;
        }
        if (!RmnOverwatch.banActive(RmnBan.NO_PEARL.id)) return;
        if (!held.is(Items.ENDER_PEARL)) return;
        RmnOverwatch.onViolation(player, RmnBan.NO_PEARL);
    }

    /** Клики по сущностям: плавсредства и верховые животные. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!RmnOverwatch.isActive()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        checkUse(attacker);
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

    /** Ночь по времени суток (26.1 убрал Level.isNight; ванильное окно ночи 12542..23459). */
    private static boolean isNight(ServerPlayer player) {
        long t = player.level().getDefaultClockTime() % 24000L;
        return t >= 12542L && t <= 23459L;
    }
}
