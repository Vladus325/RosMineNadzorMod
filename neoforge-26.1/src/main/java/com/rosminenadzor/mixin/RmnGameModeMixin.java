package com.rosminenadzor.mixin;

import com.rosminenadzor.client.ClientRmn;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * «РосМайнНадзор» (26.1): изъятие ЛКМ/ПКМ надёжнее перехватывать на уровне
 * ИСПОЛНИТЕЛЕЙ действий ({@link MultiPlayerGameMode}) — в 26.1 вход стал
 * событийным (KeyMapping изометрично «съедается» тогглами, Input-запись не
 * содержит атаку/использование), а сюда сходится ЛЮБОЙ путь ввода. Ломание
 * блоков (startDestroyBlock/continueDestroyBlock) — та же ЛКМ.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class RmnGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$blockAttack(Player player, Entity entity, CallbackInfo ci) {
        if (ClientRmn.isControlBlocked("attack")) ci.cancel();
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$blockDestroyStart(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRmn.isControlBlocked("attack")) cir.setReturnValue(false);
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$blockDestroyContinue(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRmn.isControlBlocked("attack")) cir.setReturnValue(false);
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$blockUseItem(Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientRmn.isControlBlocked("use")) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$blockUseItemOn(CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientRmn.isControlBlocked("use")) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void rosmineNadzor$blockInteract(CallbackInfoReturnable<InteractionResult> cir) {
        if (ClientRmn.isControlBlocked("use")) cir.setReturnValue(InteractionResult.FAIL);
    }
}
