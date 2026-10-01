package com.rosminenadzor.client;

import com.rosminenadzor.RosMineNadzor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Клиентская инициализация — существует только на клиенте (dist = CLIENT),
 * чтобы выделенный сервер никогда не загружал клиентские классы.
 */
@Mod(value = RosMineNadzor.MODID, dist = Dist.CLIENT)
public class ClientInit {
    public ClientInit(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(ClientInit::registerGuiLayers);
        RosMineNadzor.LOGGER.info("РосМайнНадзор: клиентская часть загружена");
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RosMineNadzor.MODID, "rmn_overlay"),
                (guiGraphics, deltaTracker) -> ClientRmn.render(guiGraphics));
    }
}
