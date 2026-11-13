package com.rosminenadzor.mixin;

import com.rosminenadzor.client.ClientRmn;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * «РосМайнНадзор» (26.1): HUD (утренний баннер, таймер обыска, список изъятых
 * действий) рисуется TAIL-инжекцией в vanilla {@link Gui#extractRenderState} —
 * тот же проход, что рисует хотбар, поэтому слой гарантированно виден. Слой
 * лоадера (Forge AddGuiOverlayLayersEvent / NeoForge RegisterGuiLayersEvent)
 * в 26.1 не отрисовывался и убран.
 */
@Mixin(Gui.class)
public abstract class RmnGuiMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"))
    private void rosmineNadzor$renderHud(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        ClientRmn.render(guiGraphics);
    }
}
