package com.rosminenadzor;

import com.rosminenadzor.RosMineNadzor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Пакет сервер → клиент «РосМайнНадзор»: заблокированные «кнопки» игрока
 * (forward/back/left/right/jump/sneak/attack/use) — клиент глушит СООТВЕТСТВУЮЩИЕ
 * действия, а не клавиши, поэтому переназначенные бинды не спасают.
 */
public record RmnControlsPayload(List<String> controls) implements CustomPacketPayload {
    public static final Type<RmnControlsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RosMineNadzor.MODID, "rmn"));

    public static final StreamCodec<FriendlyByteBuf, RmnControlsPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RmnControlsPayload::write, RmnControlsPayload::new);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(controls.size());
        for (String control : controls) {
            buf.writeUtf(control, 32);
        }
    }

    private RmnControlsPayload(FriendlyByteBuf buf) {
        this(readControls(buf));
    }

    private static List<String> readControls(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> out = new ArrayList<>(Math.min(size, 16));
        for (int i = 0; i < size; i++) {
            out.add(buf.readUtf(32));
        }
        return out;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
