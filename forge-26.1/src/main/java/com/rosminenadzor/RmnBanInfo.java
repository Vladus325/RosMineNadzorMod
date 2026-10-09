package com.rosminenadzor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Описание одного запрета для клиента: id, ключевое слово вида, заголовок
 * (ключ перевода встроенного или свой текст кастомного) и маскируемые буквы
 * (только для LETTER — из них клиент строит цензор). Поля — строки: заголовок
 * кастомного запрета определён в custom_bans.json сервера, у клиента его нет.
 */
public record RmnBanInfo(String id, String kind, String title, String chars) {

    public static final StreamCodec<FriendlyByteBuf, RmnBanInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RmnBanInfo::id,
            ByteBufCodecs.STRING_UTF8, RmnBanInfo::kind,
            ByteBufCodecs.STRING_UTF8, RmnBanInfo::title,
            ByteBufCodecs.STRING_UTF8, RmnBanInfo::chars,
            RmnBanInfo::new);

    public static final StreamCodec<FriendlyByteBuf, List<RmnBanInfo>> LIST_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());
}
