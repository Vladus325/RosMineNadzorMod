package com.rosminenadzor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки надзора: {@code config/rosminenadzor/config.json} (создаётся со
 * всеми ключами при первом запуске, перечитывается командой /rmn reload).
 * Применяются к НОВЫМ запретам и нарушениям — уже активные запреты и
 * заблокированные «кнопки» не пересматриваются.
 */
public final class RmnConfig {
    /** Реальных секунд между запретами; 0 — по игровым суткам (рассвет, как в концепте). */
    public static volatile int banIntervalSeconds = 0;
    /** Потолок активных запретов; 0 — без потолка (весь каталог). */
    public static volatile int maxActiveBans = 0;
    /** Встроенный каталог (46 запретов) участвует в розыгрыше; false — только кастомные. */
    public static volatile boolean builtInEnabled = true;
    /** Виды запретов, участвующие в розыгрыше. */
    public static volatile boolean lettersEnabled = true;
    public static volatile boolean sillyEnabled = true;
    public static volatile boolean pvpEnabled = true;
    public static volatile boolean interactEnabled = true;
    /** Помилование: включённость, сколько «кнопок» возвращается и на сколько
     *  нарушений (≈20% шанса при шаге 20) снижается счётчик за чистый цикл. */
    public static volatile boolean clemencyEnabled = true;
    public static volatile int clemencyControls = 1;
    public static volatile int clemencyMercyViolations = 1;
    /** Лестница наказаний: нарушения 1..warnViolations — предупреждения,
     *  далее до controlViolations — блокировки «кнопок», дальше — «суд». */
    public static volatile int warnViolations = 2;
    public static volatile int controlViolations = 5;
    /** «Суд»: базовый шанс бана, %, и шаг за каждое следующее нарушение. */
    public static volatile int courtBaseChance = 20;
    public static volatile int courtChanceStep = 20;
    /** Минимальная пауза между зачитанными нарушениями одного и того же запрета, сек. */
    public static volatile int violationSpacingSeconds = 2;

    private RmnConfig() {
    }

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("rosminenadzor").resolve("config.json");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                save();
                RosMineNadzor.LOGGER.info("РМН: создан config.json с настройками по умолчанию");
                return;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                RosMineNadzor.LOGGER.warn("РМН: config.json не объект — использую значения по умолчанию");
                return;
            }
            JsonObject json = root.getAsJsonObject();
            banIntervalSeconds = clampInt(json, "banIntervalSeconds", banIntervalSeconds, 0, 86_400);
            maxActiveBans = clampInt(json, "maxActiveBans", maxActiveBans, 0, 1000);
            builtInEnabled = clampBool(json, "builtInEnabled", builtInEnabled);
            lettersEnabled = clampBool(json, "lettersEnabled", lettersEnabled);
            sillyEnabled = clampBool(json, "sillyEnabled", sillyEnabled);
            pvpEnabled = clampBool(json, "pvpEnabled", pvpEnabled);
            interactEnabled = clampBool(json, "interactEnabled", interactEnabled);
            clemencyEnabled = clampBool(json, "clemencyEnabled", clemencyEnabled);
            clemencyControls = clampInt(json, "clemencyControls", clemencyControls, 1, 8);
            clemencyMercyViolations = clampInt(json, "clemencyMercyViolations", clemencyMercyViolations, 0, 100);
            warnViolations = clampInt(json, "warnViolations", warnViolations, 0, 100);
            controlViolations = clampInt(json, "controlViolations", controlViolations, 0, 200);
            if (controlViolations < warnViolations) {
                RosMineNadzor.LOGGER.warn("РМН: controlViolations ({}) меньше warnViolations ({}) — выровнял",
                        controlViolations, warnViolations);
                controlViolations = warnViolations;
            }
            courtBaseChance = clampInt(json, "courtBaseChance", courtBaseChance, 0, 100);
            courtChanceStep = clampInt(json, "courtChanceStep", courtChanceStep, 0, 100);
            violationSpacingSeconds = clampInt(json, "violationSpacingSeconds", violationSpacingSeconds, 0, 300);
            save(); // нормализуем файл: все ключи в актуальном виде
            RosMineNadzor.LOGGER.info("РМН: настройки загружены (интервал {}с, буквы {}, помилование {})",
                    banIntervalSeconds, lettersEnabled ? "вкл" : "выкл", clemencyEnabled ? "вкл" : "выкл");
        } catch (Exception e) {
            RosMineNadzor.LOGGER.error("РМН: не удалось прочитать config.json — использую значения по умолчанию", e);
        }
    }

    public static void save() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("rosminenadzor").resolve("config.json");
        try {
            JsonObject json = new JsonObject();
            json.addProperty("banIntervalSeconds", banIntervalSeconds);
            json.addProperty("maxActiveBans", maxActiveBans);
            json.addProperty("builtInEnabled", builtInEnabled);
            json.addProperty("lettersEnabled", lettersEnabled);
            json.addProperty("sillyEnabled", sillyEnabled);
            json.addProperty("pvpEnabled", pvpEnabled);
            json.addProperty("interactEnabled", interactEnabled);
            json.addProperty("clemencyEnabled", clemencyEnabled);
            json.addProperty("clemencyControls", clemencyControls);
            json.addProperty("clemencyMercyViolations", clemencyMercyViolations);
            json.addProperty("warnViolations", warnViolations);
            json.addProperty("controlViolations", controlViolations);
            json.addProperty("courtBaseChance", courtBaseChance);
            json.addProperty("courtChanceStep", courtChanceStep);
            json.addProperty("violationSpacingSeconds", violationSpacingSeconds);
            Files.createDirectories(file.getParent());
            Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            RosMineNadzor.LOGGER.error("РМН: не удалось сохранить config.json", e);
        }
    }

    private static int clampInt(JsonObject json, String key, int def, int min, int max) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return def;
        return Math.max(min, Math.min(max, e.getAsInt()));
    }

    private static boolean clampBool(JsonObject json, String key, boolean def) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()
                ? e.getAsBoolean() : def;
    }
}
