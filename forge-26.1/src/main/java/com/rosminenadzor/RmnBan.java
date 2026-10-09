package com.rosminenadzor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Один запрет «РосМайнНадзора» ({@code /rmn start}): тип, набор блоков
 * (теги + прямые), маскируемые буквы или id «глупого» запрета. Запреты выбираются
 * случайно и накапливаются с каждым майнкрафт-днём — см. {@link RmnOverwatch}.
 * Здесь же — «устранение нарушения» ({@link #autoFix}): телепортация от запретной
 * поверхности или конфискация запрещённых предметов.
 */
public final class RmnBan {
    /** Тип запрета — определяет, какой хук его проверяет. */
    public enum Kind {
        /** Нельзя прыгать, стоя на блоке из набора. */
        JUMP_ON,
        /** Нельзя ломать блоки из набора. */
        BREAK,
        /** Нельзя ставить блоки из набора (и держать их — конфискуются). */
        PLACE,
        /** Изгнание буквы: маскируется на клиенте, в чате — нарушение. */
        LETTER,
        /** Глупый запрет про управление (шифт/спринт/жидкости). */
        SILLY,
        /** Запрет на PvP-экипировку (зачарованная/незачарованная броня и оружие). */
        PVP,
        /** Запрет на взаимодействие (лодки, лошади, двери, сон, жемчуг). */
        INTERACT
    }

    public final String id;
    public final Kind kind;
    /** Пул розыгрыша первых дней: MAJOR (блоки), WEIRD (странные), PVP (PvP/взаимодействия). */
    public final String pool;
    private final Set<TagKey<Block>> blockTags;
    private final Set<Block> blocks;
    private final char[] maskChars;
    /** Свой заголовок из custom_bans.json; null для встроенных — перевод по ключу. */
    private final String titleOverride;

    private RmnBan(String id, Kind kind, String pool, Set<TagKey<Block>> blockTags, Set<Block> blocks,
                   char[] maskChars, String titleOverride) {
        this.id = id;
        this.kind = kind;
        this.pool = pool;
        this.blockTags = blockTags;
        this.blocks = blocks;
        this.maskChars = maskChars;
        this.titleOverride = titleOverride;
    }

    // ---------------------------------------------------------------- каталог

    /** Прыжки: по стандартным «мягким» поверхностям. */
    public static final RmnBan NO_JUMP_SAND = tags("no_jump_sand", Kind.JUMP_ON, "MAJOR", BlockTags.SAND);
    public static final RmnBan NO_JUMP_WOOL = tags("no_jump_wool", Kind.JUMP_ON, "MAJOR", BlockTags.WOOL);
    public static final RmnBan NO_JUMP_GLASS = direct("no_jump_glass", Kind.JUMP_ON, "MAJOR", Blocks.GLASS, Blocks.TINTED_GLASS);
    public static final RmnBan NO_JUMP_FARM = both("no_jump_farm", Kind.JUMP_ON, "MAJOR", BlockTags.CROPS,
            Blocks.FARMLAND, Blocks.DIRT_PATH);
    public static final RmnBan NO_JUMP_SLIME = direct("no_jump_slime", Kind.JUMP_ON, "MAJOR", Blocks.SLIME_BLOCK);
    public static final RmnBan NO_JUMP_HONEY = direct("no_jump_honey", Kind.JUMP_ON, "MAJOR", Blocks.HONEY_BLOCK);
    public static final RmnBan NO_JUMP_MAGMA = direct("no_jump_magma", Kind.JUMP_ON, "MAJOR", Blocks.MAGMA_BLOCK);

    /** Ломание: логически сгруппированные стандартные блоки. */
    public static final RmnBan NO_BREAK_POTTERY = both("no_break_pottery", Kind.BREAK, "MAJOR", BlockTags.TERRACOTTA,
            Blocks.CLAY);
    public static final RmnBan NO_BREAK_WOOD = tags("no_break_wood", Kind.BREAK, "MAJOR", BlockTags.LOGS, BlockTags.PLANKS);
    public static final RmnBan NO_BREAK_STONE = tags("no_break_stone", Kind.BREAK, "MAJOR",
            BlockTags.STONE_ORE_REPLACEABLES, BlockTags.BASE_STONE_OVERWORLD);
    public static final RmnBan NO_BREAK_DEEPSLATE = tags("no_break_deepslate", Kind.BREAK, "MAJOR",
            BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    public static final RmnBan NO_BREAK_QUARTZ = direct("no_break_quartz", Kind.BREAK, "MAJOR",
            Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.QUARTZ_BRICKS,
            Blocks.SMOOTH_QUARTZ, Blocks.CHISELED_QUARTZ_BLOCK);
    public static final RmnBan NO_BREAK_PURPUR = direct("no_break_purpur", Kind.BREAK, "MAJOR",
            Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR);
    public static final RmnBan NO_BREAK_ICE = tags("no_break_ice", Kind.BREAK, "MAJOR", BlockTags.ICE);
    public static final RmnBan NO_BREAK_LEAVES = tags("no_break_leaves", Kind.BREAK, "MAJOR", BlockTags.LEAVES);

    /** Установка: опасное/бессмысленное/декоративное. */
    public static final RmnBan NO_PLACE_TNT = direct("no_place_tnt", Kind.PLACE, "MAJOR", Blocks.TNT);
    public static final RmnBan NO_PLACE_FIRE = both("no_place_fire", Kind.PLACE, "MAJOR", BlockTags.CANDLES,
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE);
    public static final RmnBan NO_PLACE_CACTUS = direct("no_place_cactus", Kind.PLACE, "MAJOR", Blocks.CACTUS);
    public static final RmnBan NO_PLACE_CHEST = both("no_place_chest", Kind.PLACE, "MAJOR", BlockTags.SHULKER_BOXES,
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL);
    public static final RmnBan NO_PLACE_TORCH = direct("no_place_torch", Kind.PLACE, "MAJOR",
            Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
            Blocks.LANTERN, Blocks.SOUL_LANTERN);
    public static final RmnBan NO_PLACE_FLOWERS = tags("no_place_flowers", Kind.PLACE, "MAJOR", BlockTags.FLOWERS);

    /** Буквы: маскируются и латинские двойники (А/A, О/O/0, Е/E...). */
    public static final RmnBan NO_LETTER_A = letter("no_letter_a", 'А', 'а', 'A', 'a');
    public static final RmnBan NO_LETTER_O = letter("no_letter_o", 'О', 'о', 'O', 'o', '0');
    public static final RmnBan NO_LETTER_E = letter("no_letter_e", 'Е', 'е', 'E', 'e');
    public static final RmnBan NO_LETTER_I = letter("no_letter_i", 'И', 'и');
    public static final RmnBan NO_LETTER_N = letter("no_letter_n", 'Н', 'н', 'H', 'h');
    public static final RmnBan NO_LETTER_T = letter("no_letter_t", 'Т', 'т', 'T', 't');
    public static final RmnBan NO_LETTER_R = letter("no_letter_r", 'Р', 'р', 'P', 'p');
    public static final RmnBan NO_LETTER_S = letter("no_letter_s", 'С', 'с', 'C', 'c');
    public static final RmnBan NO_LETTER_K = letter("no_letter_k", 'К', 'к', 'K', 'k');
    public static final RmnBan NO_LETTER_M = letter("no_letter_m", 'М', 'м', 'M', 'm');

    /** Глупые запреты: проверяются тиканьем сервера (RmnOverwatch.enforceSilly). */
    public static final RmnBan NO_SHIFT = silly("no_shift");
    public static final RmnBan FORCE_SHIFT = silly("force_shift");
    public static final RmnBan NO_SPRINT = silly("no_sprint");
    public static final RmnBan NO_SHIFT_LAVA = silly("no_shift_lava");
    public static final RmnBan NO_SHIFT_WATER = silly("no_shift_water");

    /** PvP: проверяется хуком входящего урона между игроками. */
    public static final RmnBan NO_UNENCH_ARMOR = simple("no_unench_armor", Kind.PVP);
    public static final RmnBan NO_ENCH_ARMOR = simple("no_ench_armor", Kind.PVP);
    public static final RmnBan NO_WEAPON = simple("no_weapon", Kind.PVP);
    public static final RmnBan NO_UNENCH_WEAPON = simple("no_unench_weapon", Kind.PVP);
    public static final RmnBan NO_ENCH_WEAPON = simple("no_ench_weapon", Kind.PVP);

    /** Взаимодействия: проверяются клик-хуками. */
    public static final RmnBan NO_BOAT = simple("no_boat", Kind.INTERACT);
    public static final RmnBan NO_HORSE = simple("no_horse", Kind.INTERACT);
    public static final RmnBan NO_DOOR = simple("no_door", Kind.INTERACT);
    public static final RmnBan NO_BED = simple("no_bed", Kind.INTERACT);
    public static final RmnBan NO_PEARL = simple("no_pearl", Kind.INTERACT);

    /** Встроенный каталог. */
    public static final List<RmnBan> BUILT_IN = List.of(
            NO_JUMP_SAND, NO_JUMP_WOOL, NO_JUMP_GLASS, NO_JUMP_FARM, NO_JUMP_SLIME, NO_JUMP_HONEY, NO_JUMP_MAGMA,
            NO_BREAK_POTTERY, NO_BREAK_WOOD, NO_BREAK_STONE, NO_BREAK_DEEPSLATE,
            NO_BREAK_QUARTZ, NO_BREAK_PURPUR, NO_BREAK_ICE, NO_BREAK_LEAVES,
            NO_PLACE_TNT, NO_PLACE_FIRE, NO_PLACE_CACTUS, NO_PLACE_CHEST, NO_PLACE_TORCH, NO_PLACE_FLOWERS,
            NO_LETTER_A, NO_LETTER_O, NO_LETTER_E, NO_LETTER_I, NO_LETTER_N, NO_LETTER_T,
            NO_LETTER_R, NO_LETTER_S, NO_LETTER_K, NO_LETTER_M,
            NO_SHIFT, FORCE_SHIFT, NO_SPRINT, NO_SHIFT_LAVA, NO_SHIFT_WATER,
            NO_UNENCH_ARMOR, NO_ENCH_ARMOR, NO_WEAPON, NO_UNENCH_WEAPON, NO_ENCH_WEAPON,
            NO_BOAT, NO_HORSE, NO_DOOR, NO_BED, NO_PEARL);

    /** Пулы тяжести: 1-й запрет дня — блоки, 2-й — странные, 3-й — PvP/взаимодействия. */
    public static final List<RmnBan> POOL_MAJOR = List.of(
            NO_JUMP_SAND, NO_JUMP_WOOL, NO_JUMP_GLASS, NO_JUMP_FARM, NO_JUMP_SLIME, NO_JUMP_HONEY, NO_JUMP_MAGMA,
            NO_BREAK_POTTERY, NO_BREAK_WOOD, NO_BREAK_STONE, NO_BREAK_DEEPSLATE,
            NO_BREAK_QUARTZ, NO_BREAK_PURPUR, NO_BREAK_ICE, NO_BREAK_LEAVES,
            NO_PLACE_TNT, NO_PLACE_FIRE, NO_PLACE_CACTUS, NO_PLACE_CHEST, NO_PLACE_TORCH, NO_PLACE_FLOWERS);
    public static final List<RmnBan> POOL_WEIRD = List.of(
            NO_LETTER_A, NO_LETTER_O, NO_LETTER_E, NO_LETTER_I, NO_LETTER_N, NO_LETTER_T,
            NO_LETTER_R, NO_LETTER_S, NO_LETTER_K, NO_LETTER_M,
            NO_SHIFT, FORCE_SHIFT, NO_SPRINT, NO_SHIFT_LAVA, NO_SHIFT_WATER);
    public static final List<RmnBan> POOL_PVP = List.of(
            NO_UNENCH_ARMOR, NO_ENCH_ARMOR, NO_WEAPON, NO_UNENCH_WEAPON, NO_ENCH_WEAPON,
            NO_BOAT, NO_HORSE, NO_DOOR, NO_BED, NO_PEARL);

    /** Кастомные запреты из custom_bans.json (загружает RmnCustomBans). */
    private static volatile List<RmnBan> custom = List.of();

    public static void setCustom(List<RmnBan> bans) {
        custom = List.copyOf(bans);
    }

    /** Только кастомные запреты (из custom_bans.json). */
    public static List<RmnBan> customs() {
        return custom;
    }

    /** Встроенный каталог + кастомные запреты. */
    public static List<RmnBan> all() {
        if (custom.isEmpty()) return BUILT_IN;
        List<RmnBan> out = new ArrayList<>(BUILT_IN);
        out.addAll(custom);
        return out;
    }

    public static RmnBan byId(String id) {
        for (RmnBan ban : all()) {
            if (ban.id.equals(id)) return ban;
        }
        return null;
    }

    // ---------------------------------------------------------------- проверки

    public boolean matchesState(BlockState state) {
        for (TagKey<Block> tag : blockTags) {
            if (state.is(tag)) return true;
        }
        return blocks.contains(state.getBlock());
    }

    /** Предмет подпадает под запрет (для конфискации): тег предметов с тем же именем или прямой блок. */
    public boolean matchesStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (TagKey<Block> tag : blockTags) {
            if (stack.is(TagKey.create(Registries.ITEM, tag.location()))) return true;
        }
        return stack.getItem() instanceof BlockItem blockItem && blocks.contains(blockItem.getBlock());
    }

    /** Содержит ли текст запрещённую букву (для запрета букв в чате). */
    public boolean containsChar(String text) {
        for (char c : maskChars) {
            if (text.indexOf(c) >= 0) return true;
        }
        return false;
    }

    public char[] maskChars() {
        return maskChars;
    }

    public Component title() {
        return titleOverride != null
                ? Component.literal(titleOverride)
                : Component.translatable("rosminenadzor.rmn.ban." + id);
    }

    // ---------------------------------------------------------------- устранение нарушения

    /**
     * «Решение нарушения» при наказании: телепортация от запретной поверхности
     * или конфискация запрещённых предметов. null — устранять нечего.
     */
    public Component autoFix(ServerPlayer player) {
        return switch (kind) {
            case JUMP_ON -> teleportAway(player, this::matchesState);
            case PLACE -> confiscate(player, this::matchesStack);
            case SILLY -> switch (id) {
                case "no_shift_lava" -> teleportAway(player,
                        state -> state.getFluidState().is(FluidTags.LAVA));
                case "no_shift_water" -> teleportAway(player,
                        state -> state.getFluidState().is(FluidTags.WATER));
                default -> null;
            };
            case PVP -> switch (id) {
                case "no_weapon" -> confiscate(player, RmnBan::isWeapon);
                case "no_unench_weapon" -> confiscate(player, s -> isWeapon(s) && !s.isEnchanted());
                case "no_ench_weapon" -> confiscate(player, s -> isWeapon(s) && s.isEnchanted());
                case "no_unench_armor" -> confiscate(player, s -> isArmor(s) && !s.isEnchanted());
                case "no_ench_armor" -> confiscate(player, s -> isArmor(s) && s.isEnchanted());
                default -> null;
            };
            default -> null;
        };
    }

    /** Телепортация на ближайшее место, где под ногами нет запретной поверхности. */
    private static Component teleportAway(ServerPlayer player, Predicate<BlockState> forbidden) {
        ServerLevel level = player.level(); // на ServerPlayer level() ковариантно возвращает ServerLevel
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            int dist = 3 + rnd.nextInt(8);
            int x = player.blockPosition().getX() + (int) Math.round(Math.cos(ang) * dist);
            int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(ang) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (forbidden.test(level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)))) continue;
            if (!level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).getFluidState().isEmpty()) continue;
            player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
            level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.MASTER, 0.6F, 1.0F);
            return Component.translatable("rosminenadzor.rmn.fix_teleport");
        }
        // рядом всё запретное — поднимем игрока над ситуацией
        player.teleportTo(level, player.getX(), player.getY() + 8, player.getZ(),
                Set.of(), player.getYRot(), player.getXRot(), true);
        return Component.translatable("rosminenadzor.rmn.fix_teleport");
    }

    /** Конфискация: запрещённые предметы выбрасываются из инвентаря; null — нечего изымать. */
    private static Component confiscate(ServerPlayer player, Predicate<ItemStack> filter) {
        var inv = player.getInventory();
        int dropped = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                inv.setItem(i, ItemStack.EMPTY);
                player.drop(stack, true, false);
                dropped += stack.getCount();
            }
        }
        return dropped > 0
                ? Component.translatable("rosminenadzor.rmn.fix_confiscated", dropped)
                : null;
    }

    // ---------------------------------------------------------------- экипировка

    /**
     * Оружие: компонент WEAPON (мечи/булавы и т.п.) либо классы, оставшиеся
     * предметными (топоры, трезубец, лук/арбалет). В 26.1 предметные классы
     * типа SwordItem/ArmorItem удалены — оружие и броня живут в компонентах.
     */
    public static boolean isWeapon(ItemStack stack) {
        return stack.has(DataComponents.WEAPON)
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof ProjectileWeaponItem;
    }

    /** Броня: компонент EQUIPPABLE в гуманоидном слоте; щит и элитры — не броня. */
    public static boolean isArmor(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return false;
        return switch (equippable.slot()) {
            case HEAD, CHEST, LEGS, FEET -> true;
            default -> false;
        };
    }

    /** Дверная древесина: двери, люки, калитки (для запрета взаимодействия). */
    public static boolean isDoorBlock(BlockState state) {
        return state.is(BlockTags.WOODEN_DOORS) || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES);
    }

    // ---------------------------------------------------------------- фабрики

    @SafeVarargs
    private static RmnBan tags(String id, Kind kind, String pool, TagKey<Block>... tags) {
        return new RmnBan(id, kind, pool, Set.of(tags), Set.of(), null, null);
    }

    private static RmnBan direct(String id, Kind kind, String pool, Block... blocks) {
        return new RmnBan(id, kind, pool, Set.of(), Set.of(blocks), null, null);
    }

    @SafeVarargs
    private static RmnBan both(String id, Kind kind, String pool, TagKey<Block> tag, Block... blocks) {
        return new RmnBan(id, kind, pool, Set.of(tag), Set.of(blocks), null, null);
    }

    private static RmnBan letter(String id, char... chars) {
        return new RmnBan(id, Kind.LETTER, "WEIRD", Set.of(), Set.of(), chars, null);
    }

    private static RmnBan silly(String id) {
        return new RmnBan(id, Kind.SILLY, "WEIRD", Set.of(), Set.of(), null, null);
    }

    private static RmnBan simple(String id, Kind kind) {
        return new RmnBan(id, kind, "PVP", Set.of(), Set.of(), null, null);
    }

    /**
     * Запрет из пользовательского {@code custom_bans.json} (конструктор запретов).
     * Блоки/теги уже разрешены валидатором; для LETTER обязателен непустой chars.
     */
    public static RmnBan custom(String id, Kind kind, String pool, Set<TagKey<Block>> blockTags,
                                Set<Block> blocks, char[] maskChars, String titleOverride) {
        return new RmnBan(id, kind, pool, Set.copyOf(blockTags), Set.copyOf(blocks), maskChars, titleOverride);
    }

    /** Свой заголовок запрета; null — у встроенных берётся перевод по ключу. */
    public String titleOverride() {
        return titleOverride;
    }

    /** Список запретов, которых ещё нет среди активных (встроенные + кастомные). */
    public static List<RmnBan> unbanned(List<RmnBan> active) {
        List<RmnBan> out = new ArrayList<>(all());
        out.removeAll(active);
        return out;
    }
}
