package com.rosminenadzor;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * РМН (РосМайННадзор) — порт на NeoForge для Minecraft 26.1.x: глобальный
 * надзорный орган, каждый игровой день вводящий новый накопительный запрет.
 * Запускается командой {@code /rmn start} или Печатью РосМайнНадзора (ПКМ),
 * нарушения караются по нарастающей вплоть до «суда» с шансом бана, день без
 * нарушений — помилование. Запреты букв работают через цензор текста на клиенте
 * (mixin на StringDecomposer) и фильтр чата на сервере.
 */
@Mod(RosMineNadzor.MODID)
public class RosMineNadzor {
    public static final String MODID = "rosminenadzor";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Печать РосМайнНадзора: ПКМ — запуск/остановка надзора. */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<RmnSealItem> RMN_SEAL = ITEMS.register("rmn_seal",
            () -> new RmnSealItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public RosMineNadzor(IEventBus modEventBus, ModContainer container) {
        ITEMS.register(modEventBus);
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
                event.accept(new ItemStack(RMN_SEAL.get()));
            }
        });
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                RmnOverwatch.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(RmnHooks::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(RmnHooks::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(RmnHooks::onJump);
        NeoForge.EVENT_BUS.addListener(RmnHooks::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(RmnHooks::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(RmnHooks::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(RmnHooks::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(RmnHooks::onChat);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RmnOverwatch.onPlayerLogin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(RmnCommands::register);
        LOGGER.info("РосМайнНадзор 26.1: мод загружен (запуск надзора: /rmn start)");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // optional — клиент без мода не отваливается, надзор просто не действует
        registrar.playToClient(RmnControlsPayload.TYPE, RmnControlsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.rosminenadzor.client.ClientRmn.setControls(payload.controls())))
                .optional();
        registrar.playToClient(RmnAnnouncePayload.TYPE, RmnAnnouncePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.rosminenadzor.client.ClientRmn.announce(payload.newBanId(), payload.bans())))
                .optional();
    }
}
