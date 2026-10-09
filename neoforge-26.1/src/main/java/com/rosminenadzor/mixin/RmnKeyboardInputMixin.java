package com.rosminenadzor.mixin;

import com.rosminenadzor.client.ClientRmn;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * «РосМайнНадзор» (26.1): надёжное глушение ПРИСЕДА и ПРЫЖКА на уровне
 * ввода. В 26.1 keyPresses — immutable record, поэтому после vanilla-тика
 * (KeyboardInput.tick TAIL) пересобираем record с погашенными флагами
 * заблокированных действий (присед, прыжок).
 */
@Mixin(KeyboardInput.class)
public abstract class RmnKeyboardInputMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void rosmineNadzor$suppressShiftAndJump(CallbackInfo ci) {
        ClientRmn.sanitizeKeyPresses((KeyboardInput) (Object) this);
    }
}
