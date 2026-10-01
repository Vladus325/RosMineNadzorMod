package com.rosminenadzor;

import com.rosminenadzor.RosMineNadzor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Пакет сервер → клиент «РосМайнНадзор»: с утра вступил в силу новый запрет —
 * клиент показывает полноэкранный баннер «РосМайнНадзор запретил …» со списком
 * всех запретов дня. Полные описания ({@link RmnBanInfo}: вид, заголовок,
 * маскируемые буквы) нужны клиенту для цензора букв и заголовков кастомных
 * запретов, которых нет в его ланге. Пустой newBanId — тихая синхронизация
 * при входе (без баннера).
 */
public record RmnAnnouncePayload(String newBanId, List<RmnBanInfo> bans) implements CustomPacketPayload {
    public static final Type<RmnAnnouncePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RosMineNadzor.MODID, "rmn_announce"));

    public static final StreamCodec<FriendlyByteBuf, RmnAnnouncePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RmnAnnouncePayload::newBanId,
            RmnBanInfo.LIST_CODEC, RmnAnnouncePayload::bans,
            RmnAnnouncePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
