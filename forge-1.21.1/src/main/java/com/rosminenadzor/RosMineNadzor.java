package com.rosminenadzor;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * РМН (РосМайННадзор) — порт на Minecraft Forge 1.21.1: глобальный надзорный
 * орган, каждый игровой день вводящий новый накопительный запрет. Запускается
 * командой {@code /rmn start} или Печатью РосМайнНадзора (ПКМ), нарушения
 * караются по нарастающей вплоть до «суда» с шансом бана, день без нарушений —
 * помилование. Запреты букв работают через цензор текста на клиенте
 * (mixin на StringDecomposer) и фильтр чата на сервере.
 * <p>
 * Отличия от NeoForge-версии: Forge не поддерживает @Mod(dist=CLIENT) — клиентские
 * слушатели (слой баннера) регистрируются из основного класса под guard'ом по
 * {@link Dist}; сеть — SimpleChannel вместо payload-регистратора.
 */
@Mod(RosMineNadzor.MODID)
public class RosMineNadzor {
    public static final String MODID = "rosminenadzor";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Печать РосМайнНадзора: ПКМ — запуск/остановка надзора. */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<RmnSealItem> RMN_SEAL = ITEMS.register("rmn_seal",
            () -> new RmnSealItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** Сеть: оба payload'а едут на клиент (CLIENTBOUND), обработка — на главном потоке. */
    private static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(MODID, "main"))
            .optional()
            .simpleChannel()
            .messageBuilder(RmnControlsPayload.class)
            .direction(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND)
            .codec(RmnControlsPayload.STREAM_CODEC)
            .consumerMainThread((payload, context) ->
                    com.rosminenadzor.client.ClientRmn.setControls(payload.controls()))
            .add()
            .messageBuilder(RmnAnnouncePayload.class)
            .direction(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND)
            .codec(RmnAnnouncePayload.STREAM_CODEC)
            .consumerMainThread((payload, context) ->
                    com.rosminenadzor.client.ClientRmn.announce(payload.newBanId(), payload.bans()))
            .add()
            .build();

    /** Отправка payload'а конкретному игроку. */
    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
    }

    public RosMineNadzor(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ITEMS.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(RosMineNadzor::addSealToCreativeTab);
            modEventBus.addListener(RosMineNadzor::addHudLayer);
        }

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                RmnOverwatch.tick(event.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onBlockBreak);
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onBlockPlace);
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onJump);
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onIncomingDamage);
        MinecraftForge.EVENT_BUS.addListener(RmnHooks::onChat);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RmnOverwatch.onPlayerLogin(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(RmnCommands::register);
        LOGGER.info("РосМайнНадзор (Forge): мод загружен (запуск надзора: /rmn start)");
    }

    private static void addSealToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(new ItemStack(RMN_SEAL.get()),
                    net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    /** Слой HUD-баннера и блокировок: поверх ванильных оверлеев. */
    private static void addHudLayer(net.minecraftforge.client.event.AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(
                ResourceLocation.fromNamespaceAndPath(MODID, "rmn_overlay"),
                (guiGraphics, deltaTracker) -> com.rosminenadzor.client.ClientRmn.render(guiGraphics));
    }
}
