package com.rosminenadzor;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Печать РосМайнНадзора: ПКМ — запустить или остановить надзор (глобально,
 * для всех игроков). Остановка снимает запреты, блокировки «кнопок» и счётчики
 * нарушений; повторный запуск начинает накопление заново. Предмет — прямой
 * аналог команды {@code /rmn start}, только в руках.
 */
public class RmnSealItem extends Item {
    public RmnSealItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            MinecraftServer server = level.getServer();
            if (server != null) {
                RmnOverwatch.toggle(server, player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.accept(Component.translatable("rosminenadzor.item.rmn_seal.hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
