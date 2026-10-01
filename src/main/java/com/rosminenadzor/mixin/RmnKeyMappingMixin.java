package com.rosminenadzor.mixin;

import com.rosminenadzor.client.ClientRmn;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * «РосМайнНадзор»: изъятые надзором действия всегда «не нажаты» — на уровне
 * {@link KeyMapping} (isDown/consumeClick), поэтому блокируется ДЕЙСТВИЕ
 * (движение, прыжок, присед, атака, использование), а не физическая клавиша:
 * переназначенные бинды и мышь покрываются сами собой; GUI не затронут.
 * Другие моды могут ставить свои HEAD-инжекции на те же методы — инжекторы
 * выстраиваются в цепочку.
 */
@Mixin(KeyMapping.class)
public abstract class RmnKeyMappingMixin {

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$suppressedIsDown(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRmn.suppressInput((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$suppressedConsumeClick(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRmn.suppressInput((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
