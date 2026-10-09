package com.rosminenadzor.mixin;

import com.rosminenadzor.client.ClientRmn;
import net.minecraft.util.StringDecomposer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * «РосМайнНадзор», запрет буквы: изгнанная буква заменяется на «_» во всём
 * видимом тексте игры. Точка — {@link StringDecomposer#iterateFormatted}:
 * через его строковые перегрузки проходит разбор ЛЮБОГО рендеримого текста —
 * чат, строка ввода, названия предметов и заголовки, таблички и книги.
 * Стили сохраняются — маскируется только сам кодпоинт.
 * <p>
 * ГОТЧИ: (1) целиться в сам Font («renderText») НЕЛЬЗЯ — инжекции в Font
 * молча не срабатывают; (2) строковых перегрузов три, у каждого ровно один
 * String — дескрипторы + ordinal 0; (3) StringDecomposer общий класс — на
 * выделенном сервере миксин применится, но ветка по Dist не даёт загрузиться
 * клиентскому ClientRmn; (4) без запрещённых букв maskForRender возвращает ту
 * же ссылку.
 */
@Mixin(StringDecomposer.class)
public abstract class TextCensorMixin {

    @ModifyVariable(
            method = "iterateFormatted(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private static String rosmineNadzor$censor(String text) {
        return FMLEnvironment.dist == Dist.CLIENT ? ClientRmn.maskForRender(text) : text;
    }

    @ModifyVariable(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private static String rosmineNadzor$censorOffset(String text) {
        return FMLEnvironment.dist == Dist.CLIENT ? ClientRmn.maskForRender(text) : text;
    }

    @ModifyVariable(
            method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private static String rosmineNadzor$censorStyles(String text) {
        return FMLEnvironment.dist == Dist.CLIENT ? ClientRmn.maskForRender(text) : text;
    }
}
