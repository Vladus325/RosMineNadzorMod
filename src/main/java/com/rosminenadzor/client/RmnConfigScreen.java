package com.rosminenadzor.client;

import com.rosminenadzor.RmnConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Экран настроек «РосМайнНадзора»: открывается кнопкой «Настройки» на вкладке
 * «Моды» (расширение {@code IConfigScreenFactory}; у Forge-порта —
 * {@code ConfigScreenHandler.ConfigScreenFactory}, фабрике передаётся
 * родительский экран). Конфиг у мода свой JSON, поэтому экран ручной:
 * переключатели вкл/выкл и числовые поля; «Сохранить» применяет значения в
 * память и сразу пишет config.json — перечитывать /rmn reload не нужно.
 */
public final class RmnConfigScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int FIELD_W = 60;

    private final Screen parent;
    private final List<Object> rows = new ArrayList<>();

    /** Числовая настройка: поле ввода + клампы + сеттер в RmnConfig. */
    private static final class IntRow {
        final String key;
        final EditBox box;
        final int min;
        final int max;
        final IntConsumer set;
        int x;
        int y;

        IntRow(String key, EditBox box, int min, int max, IntConsumer set) {
            this.key = key;
            this.box = box;
            this.min = min;
            this.max = max;
            this.set = set;
        }
    }

    /** Логическая настройка: кнопка ВКЛ/ВЫКЛ + сеттер в RmnConfig. */
    private static final class BoolRow {
        final String key;
        Button button;
        final Consumer<Boolean> set;
        boolean value;
        int x;
        int y;

        BoolRow(String key, Button button, Consumer<Boolean> set) {
            this.key = key;
            this.button = button;
            this.set = set;
        }
    }

    public RmnConfigScreen(Screen parent) {
        super(Component.translatable("rosminenadzor.rmn.cfg.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        addBool("builtin", () -> RmnConfig.builtInEnabled, v -> RmnConfig.builtInEnabled = v);
        addBool("letters", () -> RmnConfig.lettersEnabled, v -> RmnConfig.lettersEnabled = v);
        addBool("silly", () -> RmnConfig.sillyEnabled, v -> RmnConfig.sillyEnabled = v);
        addBool("use", () -> RmnConfig.useEnabled, v -> RmnConfig.useEnabled = v);
        addBool("equip", () -> RmnConfig.equipEnabled, v -> RmnConfig.equipEnabled = v);
        addBool("useblock", () -> RmnConfig.useBlockEnabled, v -> RmnConfig.useBlockEnabled = v);
        addBool("interact", () -> RmnConfig.interactEnabled, v -> RmnConfig.interactEnabled = v);
        addBool("clemency", () -> RmnConfig.clemencyEnabled, v -> RmnConfig.clemencyEnabled = v);
        addBool("inspect_enabled", () -> RmnConfig.inventoryCheckEnabled, v -> RmnConfig.inventoryCheckEnabled = v);
        addInt("ban_interval", () -> RmnConfig.banIntervalSeconds, v -> RmnConfig.banIntervalSeconds = v, 0, 86_400);
        addInt("max_bans", () -> RmnConfig.maxActiveBans, v -> RmnConfig.maxActiveBans = v, 0, 1000);
        addInt("warn", () -> RmnConfig.warnViolations, v -> RmnConfig.warnViolations = v, 0, 100);
        addInt("controls", () -> RmnConfig.controlViolations, v -> RmnConfig.controlViolations = v, 0, 200);
        addInt("court_base", () -> RmnConfig.courtBaseChance, v -> RmnConfig.courtBaseChance = v, 0, 100);
        addInt("court_step", () -> RmnConfig.courtChanceStep, v -> RmnConfig.courtChanceStep = v, 0, 100);
        addInt("clemency_controls", () -> RmnConfig.clemencyControls, v -> RmnConfig.clemencyControls = v, 1, 8);
        addInt("clemency_mercy", () -> RmnConfig.clemencyMercyViolations, v -> RmnConfig.clemencyMercyViolations = v, 0, 100);
        addInt("clemency_interval", () -> RmnConfig.clemencyIntervalSeconds, v -> RmnConfig.clemencyIntervalSeconds = v, 0, 86_400);
        addInt("spacing", () -> RmnConfig.violationSpacingSeconds, v -> RmnConfig.violationSpacingSeconds = v, 0, 300);
        addInt("jail", () -> RmnConfig.jailSeconds, v -> RmnConfig.jailSeconds = v, 5, 3600);
        addInt("inspect_seconds", () -> RmnConfig.inventoryCheckSeconds, v -> RmnConfig.inventoryCheckSeconds = v, 0, 86_400);

        int half = (rows.size() + 1) / 2;
        int colW = Math.min(this.width / 2 - 12, 250);
        for (int i = 0; i < rows.size(); i++) {
            int colX = (i < half ? 8 : this.width / 2 + 4);
            int y = 32 + (i % half) * ROW_H;
            placeRow(rows.get(i), colX, y, colW);
        }
        addRenderableWidget(Button.builder(Component.translatable("rosminenadzor.rmn.cfg.save"),
                        b -> saveAndClose())
                .bounds(this.width / 2 - 155, this.height - 26, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width / 2 + 5, this.height - 26, 150, 20).build());
    }

    private void placeRow(Object row, int x, int y, int colW) {
        if (row instanceof IntRow r) {
            r.x = x;
            r.y = y;
            r.box.setX(x + colW - FIELD_W);
            r.box.setY(y + 1);
            addRenderableWidget(r.box);
        } else if (row instanceof BoolRow b) {
            b.x = x;
            b.y = y;
            b.button.setX(x + colW - 50);
            b.button.setY(y + 1);
            addRenderableWidget(b.button);
        }
    }

    private void addInt(String key, IntSupplier get, IntConsumer set, int min, int max) {
        EditBox box = new EditBox(this.font, 0, 0, FIELD_W, 18,
                Component.translatable("rosminenadzor.rmn.cfg." + key));
        box.setValue(String.valueOf(get.getAsInt()));
        box.setFilter(s -> s.isEmpty() || (s.matches("\\d{1,7}") && s.chars().allMatch(Character::isDigit)));
        box.setMaxLength(7);
        rows.add(new IntRow(key, box, min, max, set));
    }

    private void addBool(String key, java.util.function.BooleanSupplier get, Consumer<Boolean> set) {
        BoolRow row = new BoolRow(key, null, set);
        row.value = get.getAsBoolean();
        row.button = Button.builder(boolLabel(row.value), b -> {
            row.value = !row.value;
            b.setMessage(boolLabel(row.value));
        }).bounds(0, 0, 50, 18).build();
        rows.add(row);
    }

    private static Component boolLabel(boolean value) {
        return Component.translatable(value
                ? "rosminenadzor.rmn.cfg.on"
                : "rosminenadzor.rmn.cfg.off").withStyle(value
                ? net.minecraft.ChatFormatting.GREEN
                : net.minecraft.ChatFormatting.RED);
    }

    private void saveAndClose() {
        for (Object row : rows) {
            if (row instanceof IntRow r) {
                try {
                    int v = Integer.parseInt(r.box.getValue().trim());
                    r.set.accept(Math.max(r.min, Math.min(r.max, v)));
                } catch (NumberFormatException ignored) {
                    // пустое поле — оставляем прежнее значение
                }
            } else if (row instanceof BoolRow b) {
                b.set.accept(b.value);
            }
        }
        RmnConfig.save();
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        for (Object row : rows) {
            String label = I18n.get("rosminenadzor.rmn.cfg." + keyOf(row));
            int x = xOf(row);
            int y = yOf(row);
            guiGraphics.fill(x - 3, y, x + this.width / 2 - 18, y + 18, 0x50000000);
            guiGraphics.drawString(this.font, label, x, y + 5, 0xFFCCCCCC, true);
        }
    }

    private static String keyOf(Object row) {
        return row instanceof IntRow r ? r.key : ((BoolRow) row).key;
    }

    private static int xOf(Object row) {
        return row instanceof IntRow r ? r.x : ((BoolRow) row).x;
    }

    private static int yOf(Object row) {
        return row instanceof IntRow r ? r.y : ((BoolRow) row).y;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
