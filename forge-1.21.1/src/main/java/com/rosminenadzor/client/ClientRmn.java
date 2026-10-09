package com.rosminenadzor.client;

import com.rosminenadzor.RmnBanInfo;
import com.rosminenadzor.RosMineNadzor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * «РосМайнНадзор» (клиент): полноэкранный баннер «РосМайнНадзор запретил …»
 * по утрам, глушение заблокированных «кнопок» (действий, не клавиш — работает
 * через mixin RmnKeyMappingMixin на уровне KeyMapping, поэтому переназначенные
 * бинды не спасают) и цензор изгнанных букв: буква заменяется на «_» во всём
 * тексте игры (mixin.TextCensorMixin на StringDecomposer). Латинские двойники
 * (А/A, О/O/0, Е/E, Н/H, Т/T, Р/P, С/C, К/K, М/M) маскируются тоже.
 * <p>
 * Состояние приходит пакетами: список запретов (цензор + баннер) и заблокированные
 * «кнопки». Утро — баннер с буквой запрета (обход цензора флагом REVEAL); чат и
 * нарушения называют букву номером в алфавите («пятнадцатую букву алфавита»).
 */
public final class ClientRmn {
    /** Сколько висит утренний баннер, мс. */
    private static final long BANNER_MS = 6000;

    private static final List<String> BLOCKED_CONTROLS = new CopyOnWriteArrayList<>();
    /** Описания активных запретов (id/вид/заголовок/буквы) — из payload сервера. */
    private static volatile List<RmnBanInfo> activeInfos = List.of();
    /** Плоская таблица запрещённых символов активных запретов букв — горячий путь цензора. */
    private static volatile char[] maskedChars = new char[0];
    private static volatile long bannerUntil;
    private static volatile RmnBanInfo bannerNew;
    private static volatile List<RmnBanInfo> bannerInfos = List.of();

    /**
     * Флаг «показать как есть» для утреннего баннера: цензор глушит запрещённую
     * букву во всём тексте, включая объявление о запрете — но на баннере букву
     * нужно видеть, иначе игрок не узнает, что именно запретили. Ставится только
     * вокруг отрисовки названия запрета буквы.
     */
    private static final ThreadLocal<Boolean> REVEAL = ThreadLocal.withInitial(() -> false);
    /** Разовый лог первой маскировки — диагностика цензора на Forge. */
    private static boolean maskLogged;

    private ClientRmn() {
    }

    // ---------------------------------------------------------------- пакеты

    /** S2C rosminenadzor:rmn_controls — список заблокированных «кнопок» игрока. */
    public static void setControls(List<String> controls) {
        RosMineNadzor.LOGGER.info("РМН-клиент: получены заблокированные кнопки {}", controls);
        BLOCKED_CONTROLS.clear();
        BLOCKED_CONTROLS.addAll(controls);
    }

    /**
     * S2C rosminenadzor:rmn_announce — новый запрет дня (баннер) или тихая
     * синхронизация при входе (newBanId пустой — без баннера и звука).
     * Описания запретов содержат заголовки (в т.ч. кастомных из custom_bans.json
     * сервера) и маскируемые буквы для цензора.
     */
    public static void announce(String newBanId, List<RmnBanInfo> bans) {
        activeInfos = List.copyOf(bans);
        rebuildMaskedChars();
        RosMineNadzor.LOGGER.info("РМН-цензор: получены запреты {}, маскируемых букв {}",
                bans.stream().map(RmnBanInfo::id).toList(), maskedChars.length);
        if (newBanId == null || newBanId.isEmpty()) return;
        bannerNew = infoById(newBanId);
        bannerInfos = List.copyOf(bans);
        bannerUntil = System.currentTimeMillis() + BANNER_MS;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 0.7F));
        }
    }

    private static RmnBanInfo infoById(String id) {
        for (RmnBanInfo info : activeInfos) {
            if (info.id().equals(id)) return info;
        }
        return null;
    }

    private static void rebuildMaskedChars() {
        char[] out = new char[0];
        for (RmnBanInfo info : activeInfos) {
            if (!"LETTER".equals(info.kind()) || info.chars().isEmpty()) continue;
            char[] mask = info.chars().toCharArray();
            char[] next = new char[out.length + mask.length];
            System.arraycopy(out, 0, next, 0, out.length);
            System.arraycopy(mask, 0, next, out.length, mask.length);
            out = next;
        }
        maskedChars = out;
    }

    // ---------------------------------------------------------------- глушение действий

    /** Действие этого маппинга изъято надзором? (уровень KeyMapping — бинды не спасают). */
    public static boolean suppressInput(KeyMapping mapping) {
        if (BLOCKED_CONTROLS.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        Options options = mc.options;
        if (options == null) return false;
        if (mapping == options.keyUp) return BLOCKED_CONTROLS.contains("forward");
        if (mapping == options.keyDown) return BLOCKED_CONTROLS.contains("back");
        if (mapping == options.keyLeft) return BLOCKED_CONTROLS.contains("left");
        if (mapping == options.keyRight) return BLOCKED_CONTROLS.contains("right");
        if (mapping == options.keyJump) return BLOCKED_CONTROLS.contains("jump");
        if (mapping == options.keyShift) return BLOCKED_CONTROLS.contains("sneak");
        if (mapping == options.keyAttack) return BLOCKED_CONTROLS.contains("attack");
        if (mapping == options.keyUse) return BLOCKED_CONTROLS.contains("use");
        return false;
    }

    /** Заблокировано ли действие по имени (forward/back/left/right/jump/sneak/attack/use). */
    public static boolean isControlBlocked(String control) {
        return BLOCKED_CONTROLS.contains(control);
    }

    /**
     * Надёжное глушение приседа и прыжка на уровне полей ввода (mixin
     * RmnKeyboardInputMixin, TAIL KeyboardInput.tick): keyShift — ToggleKeyMapping,
     * его isDown не всегда проходит через KeyMapping-миксин, поэтому после
     * vanilla-расчёта ввода принудительно гасим флаги.
     */
    public static void sanitizeVanillaInput(net.minecraft.client.player.KeyboardInput input) {
        if (BLOCKED_CONTROLS.isEmpty()) return;
        if (BLOCKED_CONTROLS.contains("sneak")) input.shiftKeyDown = false;
        if (BLOCKED_CONTROLS.contains("jump")) input.jumping = false;
    }

    // ---------------------------------------------------------------- цензор букв

    /** Действует ли цензор: есть изгнанная буква и мы в игре. */
    public static boolean censorActive() {
        if (maskedChars.length == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null;
    }

    /**
     * Маскирует запрещённые буквы в строке разбора текста (mixin TextCensorMixin).
     * Горячий путь: без запрещённых букв возвращает ту же ссылку, без аллокаций.
     */
    public static String maskForRender(String text) {
        if (REVEAL.get()) return text;
        char[] mask = maskedChars;
        if (text == null || text.isEmpty() || mask.length == 0) return text;
        boolean hit = false;
        for (int i = 0; i < text.length() && !hit; i++) {
            char c = text.charAt(i);
            for (char m : mask) {
                if (c == m) {
                    hit = true;
                    break;
                }
            }
        }
        if (!hit) return text;
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            for (char m : mask) {
                if (chars[i] == m) {
                    chars[i] = '_';
                    if (!maskLogged) {
                        maskLogged = true;
                        RosMineNadzor.LOGGER.info("РМН-цензор: маскирую текст в игре, пример «{}»",
                                text.length() > 40 ? text.substring(0, 40) + "…" : text);
                    }
                    break;
                }
            }
        }
        return new String(chars);
    }

    // ---------------------------------------------------------------- рендер

    public static void render(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options == null) return;
        long now = System.currentTimeMillis();
        if (now < bannerUntil) {
            renderBanner(guiGraphics, mc, now);
        }
        renderBlockedHud(guiGraphics, mc);
    }

    /** Утренний баннер: «РОСМАЙНАДЗОР / запретил / <запрет>» + список запретов дня.
     *  Рисуется простыми строками (I18n, мимо Component); для запретов букв — с
     *  обходом цензора, чтобы буква была видна в объявлении. */
    private static void renderBanner(GuiGraphics guiGraphics, Minecraft mc, long now) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int y = Math.max(20, height / 4 - 30);

        guiGraphics.fill(0, y, width, y + 62, 0xB0140505);
        guiGraphics.fill(0, y, width, y + 2, 0xFF6B0000);
        guiGraphics.fill(0, y + 60, width, y + 62, 0xFF6B0000);

        guiGraphics.drawCenteredString(mc.font, Component.translatable("rosminenadzor.rmn.banner_title")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                width / 2, y + 8, 0xFF5555);
        guiGraphics.drawCenteredString(mc.font, Component.translatable("rosminenadzor.rmn.banner_banned")
                        .withStyle(ChatFormatting.WHITE),
                width / 2, y + 20, 0xFFFFFF);
        boolean reveal = bannerNew != null && "LETTER".equals(bannerNew.kind());
        String title = bannerTitle(bannerNew);
        if (reveal) REVEAL.set(true);
        guiGraphics.drawCenteredString(mc.font, title, width / 2, y + 32, 0xFFAA00);
        if (reveal) REVEAL.set(false);
        if (bannerInfos.size() > 1) {
            guiGraphics.drawCenteredString(mc.font,
                    I18n.get("rosminenadzor.rmn.banner_day", joinTitles(bannerInfos)),
                    width / 2, y + 48, 0xAAAAAA);
        }
    }

    /** Нижний левый угол: какие действия изъяты надзором у этого игрока. */
    private static void renderBlockedHud(GuiGraphics guiGraphics, Minecraft mc) {
        if (BLOCKED_CONTROLS.isEmpty()) return;
        int height = mc.getWindow().getGuiScaledHeight();
        Component text = Component.translatable("rosminenadzor.rmn.blocked_hud",
                String.join(", ", BLOCKED_CONTROLS.stream()
                        .map(c -> Component.translatable("rosminenadzor.rmn.ctrl." + c).getString())
                        .toList()));
        int x = 4;
        int y = height - 22;
        guiGraphics.fill(x - 3, y - 3, x + mc.font.width(text) + 3, y + 11, 0x90000000);
        guiGraphics.drawString(mc.font, text, x, y, 0xFFFF5555, true);
    }

    /**
     * Текст запрета для показа: ключ перевода встроенного — переводим (для
     * буквенного запрета берётся баннерный ключ с реальной буквой), свой текст
     * кастомного из custom_bans.json — как есть.
     */
    private static String bannerTitle(RmnBanInfo info) {
        if (info == null) return "";
        if ("LETTER".equals(info.kind()) && I18n.exists("rosminenadzor.rmn.banner." + info.id())) {
            return I18n.get("rosminenadzor.rmn.banner." + info.id());
        }
        if (info.title().startsWith("rosminenadzor.rmn.ban.") && I18n.exists(info.title())) {
            return I18n.get(info.title());
        }
        return info.title();
    }

    private static String joinTitles(List<RmnBanInfo> infos) {
        List<String> titles = new ArrayList<>();
        for (RmnBanInfo info : infos) {
            String title = info.title();
            if (title.startsWith("rosminenadzor.rmn.ban.") && I18n.exists(title)) {
                titles.add(I18n.get(title));
            } else {
                titles.add(title);
            }
        }
        return String.join(", ", titles);
    }
}
