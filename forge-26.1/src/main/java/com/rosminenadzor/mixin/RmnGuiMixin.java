package com.rosminenadzor.mixin;

import com.rosminenadzor.client.ClientRmn;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * «РосМайННадзор» (Forge 26.1): HUD (утренний баннер, таймер обыска, список
 * изъятых действий) рисуется инжекцией в vanilla {@link Gui#extractRenderState} —
 * тот же проход, что рисует хотбар. ГОТЧА (Forge 64.1.5): extractRenderState
 * ПЕРЕЗАПИСАН Forge — при screen != LevelLoadingScreen весь HUD уходит в
 * {@code ForgeLayeredDraw.extractRenderState(...)} с немедленным return, поэтому
 * точка должна быть RETURN (каждый return), а не TAIL (только последний, мёртвая
 * ванильная ветка). Слой лоадера (AddGuiOverlayLayersEvent) в 64.x мёртв целиком —
 * событие не постится нигде. Дескриптор: (GuiGraphicsExtractor, DeltaTracker) —
 * НЕ как у Screen; неверный дескриптор глотается молча.
 */
@Mixin(Gui.class)
public abstract class RmnGuiMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "RETURN"))
    private void rosmineNadzor$renderHud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker,
                                         CallbackInfo ci) {
        ClientRmn.render(guiGraphics);
    }
}
