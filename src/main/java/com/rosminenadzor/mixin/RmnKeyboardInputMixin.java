package com.rosminenadzor.mixin;

import com.rosminenadzor.client.ClientRmn;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * «РосМайнНадзор»: надёжное глушение ПРИСЕДА на уровне полей ввода.
 * KeyMappingMixin на isDown не покрывает keyShift — это ToggleKeyMapping,
 * чьё состояние обновляется событиями клавиатуры, а не чтением физической
 * клавиши. После vanilla-тика ввода (KeyboardInput.tick TAIL) принудительно
 * гасим флаги заблокированных действий: присед и прыжок.
 */
@Mixin(KeyboardInput.class)
public abstract class RmnKeyboardInputMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void rosmineNadzor$suppressShiftAndJump(CallbackInfo ci) {
        ClientRmn.sanitizeVanillaInput((KeyboardInput) (Object) this);
    }
}
