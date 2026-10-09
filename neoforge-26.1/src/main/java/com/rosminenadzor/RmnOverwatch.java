package com.rosminenadzor;

import com.rosminenadzor.RmnBan.Kind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Экстра-событие «РМН — РосМайнНадзор» ({@code /rmn start}): глобальный
 * надзорный орган. После активации каждые {@code banIntervalSeconds} (config.json,
 * по умолчанию один майнкрафт-день) вводится новый случайный запрет
 * (накопительно, без снятия и потолка — весь каталог {@link RmnBan}
 * — на прыжки/ломание/установку групп блоков, буквы алфавита, «глупые» действия
 * и PvP-экипировку (каталог — {@link RmnBan}). Нарушения караются по нарастающей:
 * <ol>
 *   <li>нарушения 1–2 — предупреждение + устранение (телепортация/конфискация);</li>
 *   <li>нарушения 3–5 — то же + блокировка одной из «кнопок» (WASD, SPACE, SHIFT,
 *       ЛКМ, ПКМ — блокируется действие, а не клавиша);</li>
 *   <li>нарушение 6+ — «суд»: рулетка с шансом бана 20% (+20% за каждое следующее).</li>
 * </ol>
 * Помилование: день без единого нарушения возвращает одну «кнопку» и снижает
 * шанс бана на 20% (минус одно нарушение из счётчика).
 * Работает до конца сессии сервера (состояние в памяти). Объявление нового запрета —
 * полноэкранный баннер «РосМайнНадзор запретил …» (S2C {@code rosminenadzor:rmn_announce})
 * плюс чат всем игрокам.
 */
public final class RmnOverwatch {
    /** id «кнопок», которые может блокировать надзор (действия, не клавиши). */
    private static final String[] CONTROL_IDS = {"forward", "back", "left", "right", "jump", "sneak", "attack", "use"};

    private static boolean active;
    private static int dayCount;
    private static long lastCycleMs;
    /** Время суток на прошлом тике (для детекции wrap'а суток). */
    private static long lastDayTime = -1;
    /** Абсолютное overworld-время на прошлом цикле: 24000 тиков = игровой день.
     *  Спасает, когда календари модов берут время суток на себя. */
    private static long lastCycleGameTime = -1;
    /** Минимальная пауза между циклами в дневном режиме — календари модов (TFC)
     *  дёргают время при загрузке мира пачкой переходов. */
    private static final long MIN_CYCLE_GAP_MS = 2000;
    /** Длина игровых суток в тиках. */
    private static final long DAY_TICKS = 24000;
    private static final List<RmnBan> BANS = new ArrayList<>();
    /** Нарушения по игрокам — счётчик единый на все запреты. */
    private static final Map<UUID, Integer> VIOLATIONS = new HashMap<>();
    /** Нарушавшие в текущем цикле — остальным на смене цикла положено помилование. */
    private static final Set<UUID> VIOLATED_TODAY = new HashSet<>();
    /**
     * Последнее нарушение по паре «игрок|запрет» — троттлинт спама ОДНОГО запрета.
     * Общий ключ (по игроку) нельзя: пассивные нарушения («только приседая», спринт)
     * забивали окно и молча глушили нарушения других запретов (ловили на буквах).
     */
    private static final Map<String, Long> LAST_VIOLATION_MS = new HashMap<>();
    /** Заблокированные «кнопки» по игрокам (до конца работы надзора). */
    private static final Map<UUID, Set<String>> BLOCKED_CONTROLS = new HashMap<>();

    private RmnOverwatch() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Переключение надзора печатью (ПКМ по предмету). */
    public static void toggle(MinecraftServer server, ServerPlayer feedback) {
        if (active) stop(server);
        else start(server, feedback, true);
    }

    /**
     * Остановка: всё состояние очищается, клиентам уходит пустая синхронизация —
     * цензор гаснет, блокировки «кнопок» снимаются. Повторный запуск
     * (печатью или /rmn start) начинает накопление запретов заново.
     */
    public static void stop(MinecraftServer server) {
        if (!active) return;
        active = false;
        BANS.clear();
        VIOLATIONS.clear();
        LAST_VIOLATION_MS.clear();
        VIOLATED_TODAY.clear();
        BLOCKED_CONTROLS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            pushControls(player);
            PacketDistributor.sendToPlayer(player, payloadFor(""));
            player.sendSystemMessage(Component.translatable("rosminenadzor.chat.rmn_stopped")
                    .withStyle(ChatFormatting.GREEN));
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8F, 0.9F);
        }
        RosMineNadzor.LOGGER.info("РМН: надзор остановлен");
    }

    /** Запуск: надзор активируется и со следующего дня начинает вводить запреты. */
    public static void start(MinecraftServer server, ServerPlayer anchor, boolean allPlayers) {
        if (active) {
            if (anchor != null) {
                anchor.sendSystemMessage(Component.translatable("rosminenadzor.chat.rmn_already")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        active = true;
        BANS.clear();
        VIOLATIONS.clear();
        LAST_VIOLATION_MS.clear();
        VIOLATED_TODAY.clear();
        BLOCKED_CONTROLS.clear();
        RmnConfig.load();
        RmnBan.setCustom(RmnCustomBans.load()); // конструктор запретов: свежий каталог при каждом запуске
        lastCycleMs = System.currentTimeMillis();
        ServerLevel overworld = server.overworld();
        lastDayTime = overworld != null ? overworld.getDefaultClockTime() : -1;
        lastCycleGameTime = overworld != null ? overworld.getOverworldClockTime() : -1;
        dayCount = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("rosminenadzor.chat.rmn_started")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1.0F, 0.7F);
        }
        RosMineNadzor.LOGGER.info("РМН: надзор начал работу");
    }

    /** Серверный тик: цикл настал (игровые сутки или интервал) → помилование + новый запрет. */
    public static void tick(MinecraftServer server) {
        if (!active) return;
        long now = System.currentTimeMillis();
        if (RmnConfig.banIntervalSeconds > 0) {
            // режим реального времени: каждые N секунд
            if (now - lastCycleMs >= RmnConfig.banIntervalSeconds * 1000L) {
                lastCycleMs = now;
                onCycle(server);
            }
        } else {
            // режим игровых суток: детекция ДВУМЯ способами —
            // (1) wrap времени суток: рассвет или time set;
            // (2) 24000 монотонных тиков overworld-времени: работает, когда календарь
            //     модов ведёт время суток сам и ванильная смена суток не наступает
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                long dayTime = overworld.getDefaultClockTime();
                long gameTime = overworld.getOverworldClockTime();
                boolean dayWrapped = lastDayTime >= 0
                        && (dayTime < lastDayTime || dayTime - lastDayTime > 12000);
                boolean ticksElapsed = lastCycleGameTime >= 0
                        && gameTime - lastCycleGameTime >= DAY_TICKS;
                if ((dayWrapped || ticksElapsed) && now - lastCycleMs >= MIN_CYCLE_GAP_MS) {
                    lastCycleMs = now;
                    onCycle(server);
                }
                lastDayTime = dayTime;
                lastCycleGameTime = gameTime;
            }
        }
        enforceSilly(server);
        enforceEquip(server);
    }

    /**
     * Периодическая проверка запретов ношения (EQUIP): запретная броня на игроке
     * снимается (autoFix) и карается. Раз в полсекунды — достаточно быстро,
     * чтобы поймать переодевание, и не дорого по CPU.
     */
    private static void enforceEquip(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        boolean anyEquip = false;
        for (RmnBan ban : BANS) {
            if (ban.kind == Kind.EQUIP) {
                anyEquip = true;
                break;
            }
        }
        if (!anyEquip) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{
                        net.minecraft.world.entity.EquipmentSlot.HEAD,
                        net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS,
                        net.minecraft.world.entity.EquipmentSlot.FEET}) {
                    ItemStack piece = player.getItemBySlot(slot);
                    if (piece.isEmpty()) continue;
                    for (RmnBan ban : BANS) {
                        if (ban.kind == Kind.EQUIP && ban.matchesItem(piece)) {
                            onViolation(player, ban); // autoFix снимет броню
                            break;
                        }
                    }
                }
            }
        }
    }

    /** Смена цикла: помилование за чистый период + новый накопительный запрет. */
    private static void onCycle(MinecraftServer server) {
        dayCount++;
        grantClemency(server);
        rollNewBan(server);
    }

    /**
     * Новый накопительный запрет: 1-й — блоки, 2-й — странные, дальше — весь
     * каталог (включая кастомные из custom_bans.json, распределённые по своим
     * пулам). Виды, выключенные в config.json, из розыгрыша исключаются.
     * false — ввести запрет не удалось (потолок/пустой каталог).
     */
    private static boolean rollNewBan(MinecraftServer server) {
        if (RmnConfig.maxActiveBans > 0 && BANS.size() >= RmnConfig.maxActiveBans) return false;
        List<RmnBan> pool = new ArrayList<>();
        if (RmnConfig.builtInEnabled) {
            pool.addAll(switch (BANS.size()) {
                case 0 -> RmnBan.POOL_MAJOR;
                case 1 -> concat(RmnBan.POOL_MAJOR, RmnBan.POOL_WEIRD);
                default -> RmnBan.BUILT_IN;
            });
        }
        pool.addAll(customsForPhase(BANS.size()));
        List<RmnBan> candidates = RmnBan.unbanned(BANS);
        candidates.retainAll(pool);
        candidates.removeIf(b -> !kindEnabled(b.kind));
        if (candidates.isEmpty()) {
            // фолбэк по всему каталогу, но с уважением к тем же переключателям
            candidates = RmnBan.unbanned(BANS);
            if (!RmnConfig.builtInEnabled) candidates.removeIf(b -> RmnBan.BUILT_IN.contains(b));
            candidates.removeIf(b -> !kindEnabled(b.kind));
        }
        if (candidates.isEmpty()) {
            if (!RmnConfig.builtInEnabled && RmnBan.customs().isEmpty()) {
                RosMineNadzor.LOGGER.warn("РМН: встроенные запреты выключены, а custom_bans.json пуст — день без нового запрета");
            }
            return false;
        }
        RmnBan ban = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        BANS.add(ban);
        broadcastBan(server, ban);
        return true;
    }

    /** Внеочередной запрет (команда /rmn now); false — некого запретить. */
    public static boolean rollNewBanNow(MinecraftServer server) {
        if (!active) return false;
        return rollNewBan(server);
    }

    /** Сброс таймера цикла (после /rmn interval): отсчёт заново с текущего момента. */
    public static void resetCycleTimer(MinecraftServer server) {
        lastCycleMs = System.currentTimeMillis();
        ServerLevel overworld = server.overworld();
        lastDayTime = overworld != null ? overworld.getDefaultClockTime() : -1;
        lastCycleGameTime = overworld != null ? overworld.getOverworldClockTime() : -1;
    }

    /** Выключен ли вид запрета в config.json. */
    private static boolean kindEnabled(RmnBan.Kind kind) {
        return switch (kind) {
            case LETTER -> RmnConfig.lettersEnabled;
            case SILLY -> RmnConfig.sillyEnabled;
            case USE -> RmnConfig.useEnabled;
            case EQUIP -> RmnConfig.equipEnabled;
            case USEBLOCK -> RmnConfig.useBlockEnabled;
            case INTERACT -> RmnConfig.interactEnabled;
            default -> true;
        };
    }

    /** Кастомные запреты, доступные на текущей фазе накопления. */
    private static List<RmnBan> customsForPhase(int phase) {
        List<RmnBan> out = new ArrayList<>();
        for (RmnBan ban : RmnBan.all()) {
            boolean isCustom = ban.titleOverride() != null || !RmnBan.BUILT_IN.contains(ban);
            if (!isCustom) continue;
            if (phase == 0 && ban.pool.equals("MAJOR")) out.add(ban);
            else if (phase == 1 && (ban.pool.equals("MAJOR") || ban.pool.equals("WEIRD"))) out.add(ban);
            else if (phase >= 2) out.add(ban);
        }
        return out;
    }

    /** Объявление запрета всем: payload с описаниями, чат списком, звук. */
    private static void broadcastBan(MinecraftServer server, RmnBan ban) {
        RmnAnnouncePayload payload = payloadFor(ban.id);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
            player.sendSystemMessage(Component.translatable("rosminenadzor.chat.rmn_day")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            for (RmnBan b : BANS) {
                player.sendSystemMessage(Component.literal(" — ").withStyle(ChatFormatting.DARK_RED)
                        .append(b.title().copy().withStyle(ChatFormatting.RED)));
            }
            player.sendSystemMessage(Component.translatable("rosminenadzor.chat.rmn_day_hint")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.8F, 0.7F);
        }
        RosMineNadzor.LOGGER.info("РМН: запретил «{}» (активных: {})", ban.id, BANS.size());
    }

    /** Принудительный запрет по id каталога (команда /rmn ban) — для отладки и демо. */
    public static void debugAddBan(MinecraftServer server, String banId) {
        if (!active) return;
        RmnBan ban = RmnBan.byId(banId);
        if (ban == null || BANS.contains(ban)) return;
        BANS.add(ban);
        broadcastBan(server, ban);
    }

    // ---------------------------------------------------------------- нарушения

    /** Фиксация нарушения: предупреждение → блокировка контроля → «суд» с шансом бана. */
    public static void onViolation(ServerPlayer player, RmnBan ban) {
        if (!active || ban == null) return;
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        String throttleKey = uuid + "|" + ban.id;
        Long last = LAST_VIOLATION_MS.get(throttleKey);
        if (last != null && now - last < RmnConfig.violationSpacingSeconds * 1000L) return;
        LAST_VIOLATION_MS.put(throttleKey, now);
        int count = VIOLATIONS.merge(uuid, 1, Integer::sum);
        VIOLATED_TODAY.add(uuid);

        RosMineNadzor.LOGGER.info("RMN: violation #{} ({}) for {}",
                count, ban.id, player.getName());

        player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.violation", count, ban.title())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.5F, 1.3F);

        Component fix = ban.autoFix(player);
        if (fix != null) {
            player.sendSystemMessage(fix.copy().withStyle(ChatFormatting.YELLOW));
        }

        if (count <= RmnConfig.warnViolations) {
            player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.warn",
                            Math.min(count, RmnConfig.warnViolations), RmnConfig.warnViolations)
                    .withStyle(ChatFormatting.RED));
        } else if (count <= RmnConfig.controlViolations) {
            String control = randomFreeControl(uuid);
            if (control != null) {
                BLOCKED_CONTROLS.computeIfAbsent(uuid, u -> new HashSet<>()).add(control);
                pushControls(player);
                player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.control_blocked",
                                Component.translatable("rosminenadzor.rmn.ctrl." + control))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            } else {
                player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.controls_exhausted")
                        .withStyle(ChatFormatting.RED));
            }
        } else {
            int chance = Math.min(100, RmnConfig.courtBaseChance
                    + (count - RmnConfig.controlViolations - 1) * RmnConfig.courtChanceStep);
            player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.court", chance)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.MASTER, 1.0F, 0.6F);
            if (ThreadLocalRandom.current().nextInt(100) < chance) {
                banPlayer(player.level().getServer(), player);
                player.level().getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                                "rosminenadzor.rmn.ban_broadcast", player.getName())
                        .withStyle(ChatFormatting.DARK_RED), false);
            } else {
                player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.acquitted")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** Случайная ещё не заблокированная «кнопка»; null — все восемь уже изъяты. */
    private static String randomFreeControl(UUID uuid) {
        Set<String> blocked = BLOCKED_CONTROLS.getOrDefault(uuid, Set.of());
        List<String> free = new ArrayList<>();
        for (String control : CONTROL_IDS) {
            if (!blocked.contains(control)) free.add(control);
        }
        return free.isEmpty() ? null : free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }

    /**
     * Помилование на смене цикла: если игрок весь цикл соблюдал запреты, ему
     * возвращаются изъятые «кнопки» (clemencyControls) и счётчик нарушений
     * снижается (clemencyMercyViolations → минус шанс бана). Выключается
     * clemencyEnabled; работает и для офлайн-игроков.
     */
    private static void grantClemency(MinecraftServer server) {
        VIOLATED_TODAY.clear();
        if (!RmnConfig.clemencyEnabled) return;
        Set<UUID> tracked = new HashSet<>(VIOLATIONS.keySet());
        tracked.addAll(BLOCKED_CONTROLS.keySet());
        for (UUID uuid : tracked) {
            boolean reduced = false;
            int count = VIOLATIONS.getOrDefault(uuid, 0);
            if (count > 0) {
                int next = Math.max(0, count - RmnConfig.clemencyMercyViolations);
                if (next == 0) VIOLATIONS.remove(uuid);
                else VIOLATIONS.put(uuid, next);
                reduced = true;
            }
            boolean unlocked = false;
            Set<String> unlockedControls = new LinkedHashSet<>();
            Set<String> blocked = BLOCKED_CONTROLS.get(uuid);
            int toUnlock = Math.min(RmnConfig.clemencyControls, blocked == null ? 0 : blocked.size());
            for (int i = 0; i < toUnlock; i++) {
                String control = blocked.iterator().next();
                blocked.remove(control);
                unlockedControls.add(control);
                unlocked = true;
            }
            if (blocked != null && blocked.isEmpty()) BLOCKED_CONTROLS.remove(uuid);
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || (!reduced && !unlocked)) continue;
            if (unlocked) {
                pushControls(player);
                for (String control : unlockedControls) {
                    player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.clemency_unlock",
                                    Component.translatable("rosminenadzor.rmn.ctrl." + control))
                            .withStyle(ChatFormatting.GREEN));
                }
            }
            if (reduced) {
                player.sendSystemMessage(Component.translatable("rosminenadzor.rmn.clemency_mercy")
                        .withStyle(ChatFormatting.GREEN));
            }
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8F, 1.2F);
            RosMineNadzor.LOGGER.info("RMN: clemency for {} (unlocked={}, mercy={})",
                    player.getName(), unlocked, reduced);
        }
    }

    /** «Суд» постановил: бан через серверную команду (переживает рестарт). */
    private static void banPlayer(MinecraftServer server, ServerPlayer player) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS).withSuppressedOutput(),
                "ban " + player.getName() + " РосМайнНадзор");
    }

    // ---------------------------------------------------------------- слежка за «глупыми»

    /** Периодическая проверка «глупых» запретов (шифт/спринт/жидкости) для всех игроков. */
    private static void enforceSilly(MinecraftServer server) {
        boolean anySilly = false;
        for (RmnBan ban : BANS) {
            if (ban.kind == Kind.SILLY) {
                anySilly = true;
                break;
            }
        }
        if (!anySilly) return;
        int tick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if ((tick + player.getId()) % 20 != 0) continue; // раз в секунду, рассинхронно
                for (RmnBan ban : BANS) {
                    if (ban.kind != Kind.SILLY) continue;
                    if (sillyViolated(player, ban.id)) onViolation(player, ban);
                }
            }
        }
    }

    private static boolean sillyViolated(ServerPlayer player, String id) {
        return switch (id) {
            case "no_shift" -> player.isShiftKeyDown();
            // «перемещаться только приседая»: в прыжке и стоя на месте — можно
            case "force_shift" -> !player.isShiftKeyDown() && player.onGround() && isMoving(player);
            case "no_sprint" -> player.isSprinting();
            case "no_shift_lava" -> player.isShiftKeyDown() && nearFluid(player, net.minecraft.tags.FluidTags.LAVA);
            case "no_shift_water" -> player.isShiftKeyDown() && nearFluid(player, net.minecraft.tags.FluidTags.WATER);
            default -> false;
        };
    }

    private static boolean isMoving(ServerPlayer player) {
        var d = player.getDeltaMovement();
        return d.x * d.x + d.z * d.z > 1.0E-4;
    }

    /** Жидкость указанного тега у ног или под ногами. */
    private static boolean nearFluid(ServerPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tag) {
        var level = player.level();
        return level.getFluidState(player.blockPosition()).is(tag)
                || level.getFluidState(player.blockPosition().below()).is(tag);
    }

    // ---------------------------------------------------------------- доступ хуков

    /** Активный запрет нужного вида, задевающий этот блок; null — нарушения нет. */
    public static RmnBan stateBan(Kind kind, BlockState state) {
        for (RmnBan ban : BANS) {
            if (ban.kind == kind && ban.matchesState(state)) return ban;
        }
        return null;
    }

    /** Активен ли запрет с этим id каталога. */
    public static boolean banActive(String id) {
        if (!active) return false;
        for (RmnBan ban : BANS) {
            if (ban.id.equals(id)) return true;
        }
        return false;
    }

    /** Активные запреты букв (встроенные и кастомные) — для фильтра чата. */
    public static List<RmnBan> activeLetterBans() {
        List<RmnBan> out = new ArrayList<>();
        for (RmnBan ban : BANS) {
            if (ban.kind == Kind.LETTER) out.add(ban);
        }
        return out;
    }

    /** Активные запреты использования (встроенные и кастомные) — для хуков USE. */
    public static List<RmnBan> activeUseBans() {
        List<RmnBan> out = new ArrayList<>();
        for (RmnBan ban : BANS) {
            if (ban.kind == Kind.USE) out.add(ban);
        }
        return out;
    }

    // ---------------------------------------------------------------- синхронизация клиента

    /** Полные описания активных запретов для клиента (цензор, баннер, заголовки). */
    private static RmnAnnouncePayload payloadFor(String newBanId) {
        List<RmnBanInfo> infos = new ArrayList<>();
        for (RmnBan b : BANS) {
            String title = b.titleOverride() != null
                    ? b.titleOverride()
                    : "rosminenadzor.rmn.ban." + b.id;
            String chars = b.kind == Kind.LETTER ? new String(b.maskChars()) : "";
            infos.add(new RmnBanInfo(b.id, b.kind.name(), title, chars));
        }
        return new RmnAnnouncePayload(newBanId, List.copyOf(infos));
    }

    /** Отправить игроку его заблокированные «кнопки» (S2C rosminenadzor:rmn_controls). */
    public static void pushControls(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RmnControlsPayload(
                List.copyOf(BLOCKED_CONTROLS.getOrDefault(player.getUUID(), Set.of()))));
    }

    /** Вошедшему игроку — тихая синхронизация запретов (цензор букв на клиенте),
     *  его заблокированных «кнопок» (даже пустых — сброс устаревшего состояния)
     *  и напоминание списком. Без баннера: это не утреннее объявление. */
    public static void onPlayerLogin(ServerPlayer player) {
        if (!active) return;
        pushControls(player);
        if (!BANS.isEmpty()) {
            PacketDistributor.sendToPlayer(player, payloadFor(""));
            player.sendSystemMessage(Component.translatable("rosminenadzor.chat.rmn_day")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            for (RmnBan b : BANS) {
                player.sendSystemMessage(Component.literal(" — ").withStyle(ChatFormatting.DARK_RED)
                        .append(b.title().copy().withStyle(ChatFormatting.RED)));
            }
        }
    }

    /** Перечитать config.json и custom_bans.json (команда /rmn reload). */
    public static void reloadCustomBans() {
        RmnConfig.load();
        RmnBan.setCustom(RmnCustomBans.load());
    }

    // ---------------------------------------------------------------- утилиты

    private static List<RmnBan> concat(List<RmnBan> a, List<RmnBan> b) {
        List<RmnBan> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
