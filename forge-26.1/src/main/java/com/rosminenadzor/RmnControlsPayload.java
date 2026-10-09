package com.rosminenadzor;

import com.rosminenadzor.RosMineNadzor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Пакет сервер → клиент «РосМайнНадзор»: заблокированные «кнопки» игрока
 * (forward/back/left/right/jump/sneak/attack/use) — клиент глушит СООТВЕТСТВУЮЩИЕ
 * действия, а не клавиши, поэтому переназначенные бинды не спасают.
 */
public record RmnControlsPayload(List<String> controls) implements CustomPacketPayload {
    public static final Type<RmnControlsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RosMineNadzor.MODID, "rmn_controls"));

    public static final StreamCodec<FriendlyByteBuf, RmnControlsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RmnControlsPayload::controls,
            RmnControlsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
