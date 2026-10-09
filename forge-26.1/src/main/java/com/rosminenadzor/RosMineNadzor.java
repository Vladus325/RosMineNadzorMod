package com.rosminenadzor;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
 * РМН (РосМайННадзор) — порт на Minecraft Forge 26.1.x: глобальный надзорный
 * орган, каждый игровой день вводящий новый накопительный запрет. Запускается
 * командой {@code /rmn start} или Печатью РосМайнНадзора (ПКМ), нарушения
 * караются по нарастающей вплоть до «суда» с шансом бана, день без нарушений —
 * помилование. Запреты букв работают через цензор текста на клиенте
 * (mixin на StringDecomposer) и фильтр чата на сервере.
 * <p>
 * EventBus 7: события само-постящиеся — у каждого класса есть статическое поле
 * BUS, слушатели вешаются через BUS.addListener(consumer). @Mod без dist —
 * клиентские слушатели регистрируются из основного класса под guard'ом по
 * {@link Dist}.
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
            .named(Identifier.fromNamespaceAndPath(MODID, "main"))
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
        var modBusGroup = context.getModBusGroup();
        ITEMS.register(modBusGroup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            BuildCreativeModeTabContentsEvent.BUS.addListener(RosMineNadzor::addSealToCreativeTab);
            net.minecraftforge.client.event.AddGuiOverlayLayersEvent.BUS.addListener(RosMineNadzor::addHudLayer);
        }

        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> RmnOverwatch.tick(event.server()));
        net.minecraftforge.event.level.BlockEvent.BreakEvent.BUS.addListener(RmnHooks::onBlockBreak);
        net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.BUS.addListener(RmnHooks::onBlockPlace);
        net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent.BUS.addListener(RmnHooks::onJump);
        net.minecraftforge.event.entity.player.PlayerInteractEvent.BUS.addListener(RmnHooks::onPlayerInteract);
        net.minecraftforge.event.entity.living.LivingAttackEvent.BUS.addListener(RmnHooks::onIncomingDamage);
        net.minecraftforge.event.ServerChatEvent.BUS.addListener(RmnHooks::onChat);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RmnOverwatch.onPlayerLogin(player);
            }
        });
        RegisterCommandsEvent.BUS.addListener(RmnCommands::register);
        LOGGER.info("РосМайнНадзор (Forge 26.1): мод загружен (запуск надзора: /rmn start)");
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
                Identifier.fromNamespaceAndPath(MODID, "rmn_overlay"),
                (guiGraphics, deltaTracker) -> com.rosminenadzor.client.ClientRmn.render(guiGraphics));
    }
}
