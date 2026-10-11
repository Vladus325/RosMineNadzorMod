package com.rosminenadzor;

import com.rosminenadzor.RosMineNadzor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Пакет сервер → клиент «РосМайнНадзор»: сколько секунд до планового обыска
 * инвентарей на контрабанду (таймер в углу экрана). 0 — надзор остановлен или
 * обыски выключены: таймер скрыт.
 */
public record RmnInspectionPayload(int seconds) implements CustomPacketPayload {
    public static final Type<RmnInspectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RosMineNadzor.MODID, "rmn_inspection"));

    public static final StreamCodec<FriendlyByteBuf, RmnInspectionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RmnInspectionPayload::seconds,
            RmnInspectionPayload::new);

    /** Обыски выключены или надзор остановлен — скрыть таймер. */
    public static RmnInspectionPayload off() {
        return new RmnInspectionPayload(0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
