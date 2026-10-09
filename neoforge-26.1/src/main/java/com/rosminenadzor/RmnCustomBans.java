package com.rosminenadzor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rosminenadzor.RmnBan.Kind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Конструктор запретов: пользовательские запреты из
 * {@code config/rosminenadzor/custom_bans.json} (формат — пример в соседнем
 * {@code custom_bans.example.json}). Поддерживаемые ключевые слова вида:
 * <pre>
 *   { "id": "no_break_marble", "kind": "BREAK",
 *     "blocks": ["minecraft:stone"], "tags": ["minecraft:base_stone_overworld"],
 *     "title": "мраморные раскопки", "pool": "MAJOR" }
 * </pre>
 * kind: JUMP_ON (нельзя прыгать по), BREAK (ломать), PLACE (ставить) — блоки/теги;
 * LETTER — «chars»: маскируемые буквы. pool (необязательно): MAJOR/WEIRD/PVP —
 * с какого дня запрет попадает в пул (по умолчанию: блоки — MAJOR, буквы — WEIRD).
 * Перезагрузка — {@code /rmn reload}; изменения подхватываются до активации
 * надзора или добавляются к будущим роллам, если тот уже работает.
 */
public final class RmnCustomBans {
    private RmnCustomBans() {
    }

    /** Загрузить (или создать пустой) пользовательский каталог запретов. */
    public static List<RmnBan> load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("rosminenadzor").resolve("custom_bans.json");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, "[]\n", StandardCharsets.UTF_8);
                writeExample(file.getParent().resolve("custom_bans.example.json"));
                RosMineNadzor.LOGGER.info("РМН: создан пустой custom_bans.json и пример custom_bans.example.json");
                return List.of();
            }
            List<RmnBan> out = new ArrayList<>();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) {
                    RosMineNadzor.LOGGER.warn("РМН: custom_bans.json должен быть массивом объектов — файл пропущен");
                    return List.of();
                }
                for (JsonElement element : root.getAsJsonArray()) {
                    RmnBan ban = parse(element);
                    if (ban != null) out.add(ban);
                }
            }
            RosMineNadzor.LOGGER.info("РМН: загружено кастомных запретов: {}", out.size());
            return out;
        } catch (Exception e) {
            RosMineNadzor.LOGGER.error("РМН: не удалось прочитать custom_bans.json — кастомные запреты пропущены", e);
            return List.of();
        }
    }

    private static RmnBan parse(JsonElement element) {
        if (!element.isJsonObject()) {
            RosMineNadzor.LOGGER.warn("РМН: элемент custom_bans.json не объект — пропущен");
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        JsonElement enabled = json.get("enabled");
        if (enabled != null && enabled.isJsonPrimitive() && !enabled.getAsBoolean()) {
            RosMineNadzor.LOGGER.info("РМН: запрет «{}» выключен (enabled: false) — пропущен",
                    string(json, "id"));
            return null;
        }
        String id = string(json, "id");
        if (id == null || !id.matches("[a-z0-9_]{1,64}")) {
            RosMineNadzor.LOGGER.warn("РМН: запрет без корректного id (a-z 0-9 _, до 64) — пропущен: {}", id);
            return null;
        }
        if (RmnBan.byId(id) != null) {
            RosMineNadzor.LOGGER.warn("РМН: id «{}» уже занят встроенным или другим кастомным запретом — пропущен", id);
            return null;
        }
        Kind kind = parseKind(string(json, "kind"));
        if (kind == null) {
            RosMineNadzor.LOGGER.warn("РМН: запрет «{}» — kind должен быть JUMP_ON/BREAK/PLACE/USE/EQUIP/USEBLOCK/LETTER — пропущен", id);
            return null;
        }

        Set<TagKey<Block>> tags = new LinkedHashSet<>();
        for (String tagId : stringList(json, "tags")) {
            Identifier rl = Identifier.tryParse(tagId);
            if (rl == null) {
                RosMineNadzor.LOGGER.warn("РМН: «{}» — некорректный тег «{}» — пропущен", id, tagId);
                continue;
            }
            tags.add(TagKey.create(Registries.BLOCK, rl));
        }
        Set<Block> blocks = new LinkedHashSet<>();
        for (String blockId : stringList(json, "blocks")) {
            Identifier rl = Identifier.tryParse(blockId);
            Block block = rl == null ? null : BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (block == null) {
                RosMineNadzor.LOGGER.warn("РМН: «{}» — блок «{}» не найден в реестре — пропущен", id, blockId);
                continue;
            }
            blocks.add(block);
        }

        // Наборы предметов для USE (использование) и EQUIP (ношение брони)
        Set<TagKey<Item>> itemTags = new LinkedHashSet<>();
        for (String tagId : stringList(json, "itemTags")) {
            Identifier rl = Identifier.tryParse(tagId);
            if (rl == null) {
                RosMineNadzor.LOGGER.warn("РМН: «{}» — некорректный тег предметов «{}» — пропущен", id, tagId);
                continue;
            }
            itemTags.add(TagKey.create(Registries.ITEM, rl));
        }
        Set<Item> items = new LinkedHashSet<>();
        for (String itemId : stringList(json, "items")) {
            Identifier rl = Identifier.tryParse(itemId);
            Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item == null) {
                RosMineNadzor.LOGGER.warn("РМН: «{}» — предмет «{}» не найден в реестре — пропущен", id, itemId);
                continue;
            }
            items.add(item);
        }

        char[] chars = null;
        if (kind == Kind.LETTER) {
            String raw = string(json, "chars");
            if (raw == null || raw.isEmpty()) {
                RosMineNadzor.LOGGER.warn("РМН: запрет буквы «{}» без «chars» — пропущен", id);
                return null;
            }
            Set<Character> unique = new LinkedHashSet<>();
            for (char c : raw.toCharArray()) {
                unique.add(c);
            }
            chars = toCharArray(unique);
        } else if (blocks.isEmpty() && tags.isEmpty()
                && (kind != Kind.USE && kind != Kind.EQUIP || items.isEmpty() && itemTags.isEmpty())) {
            RosMineNadzor.LOGGER.warn("РМН: запрет «{}» без запрещённых блоков/предметов — пропущен", id);
            return null;
        }

        String pool = parsePool(string(json, "pool"), kind);
        return RmnBan.custom(id, kind, pool, tags, blocks, itemTags, items, chars, string(json, "title"));
    }

    /** Ключевое слово вида запрета (JUMP — синоним JUMP_ON, PVP — старое имя ADVANCED). */
    private static Kind parseKind(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "JUMP_ON", "JUMP" -> Kind.JUMP_ON;
            case "BREAK" -> Kind.BREAK;
            case "PLACE" -> Kind.PLACE;
            case "USE" -> Kind.USE;
            case "EQUIP" -> Kind.EQUIP;
            case "USEBLOCK" -> Kind.USEBLOCK;
            case "LETTER" -> Kind.LETTER;
            default -> null;
        };
    }

    private static String parsePool(String raw, Kind kind) {
        if (raw != null) {
            String upper = raw.trim().toUpperCase(Locale.ROOT);
            if (upper.equals("MAJOR") || upper.equals("WEIRD") || upper.equals("ADVANCED")) return upper;
            if (upper.equals("PVP")) return "ADVANCED"; // старое имя пула
            RosMineNadzor.LOGGER.warn("РМН: неизвестный pool «{}» — беру по умолчанию для вида", raw);
        }
        return kind == Kind.LETTER ? "WEIRD" : "MAJOR";
    }

    private static String string(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString().trim() : null;
    }

    private static List<String> stringList(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        JsonElement e = json.get(key);
        if (e != null && e.isJsonArray()) {
            for (JsonElement item : e.getAsJsonArray()) {
                if (item.isJsonPrimitive() && !item.getAsString().isBlank()) {
                    out.add(item.getAsString().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private static char[] toCharArray(Set<Character> chars) {
        char[] out = new char[chars.size()];
        int i = 0;
        for (char c : chars) out[i++] = c;
        return out;
    }

    /** Пример с закомментированным смыслом (JSON без комментариев — файл-образец). */
    private static void writeExample(Path path) {
        String example = """
                [
                  {
                    "id": "no_break_marble",
                    "kind": "BREAK",
                    "blocks": ["minecraft:stone", "minecraft:andesite"],
                    "tags": ["minecraft:base_stone_overworld"],
                    "title": "мраморные раскопки",
                    "pool": "MAJOR"
                  },
                  {
                    "id": "no_jump_on_slabs",
                    "kind": "JUMP_ON",
                    "tags": ["minecraft:wool"],
                    "title": "прыгать по шерсти"
                  },
                  {
                    "id": "no_letter_z",
                    "kind": "LETTER",
                    "chars": "ЗзZz",
                    "title": "букву «З»",
                    "pool": "WEIRD"
                  }
                ]
                """;
        try {
            Files.writeString(path, example, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
