package com.rosminenadzor.client;

import com.rosminenadzor.RosMineNadzor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Клиентская инициализация — существует только на клиенте (dist = CLIENT),
 * чтобы выделенный сервер никогда не загружал клиентские классы.
 */
@Mod(value = RosMineNadzor.MODID, dist = Dist.CLIENT)
public class ClientInit {
    public ClientInit(IEventBus modEventBus, ModContainer container) {
        // HUD рисует RmnGuiMixin (RegisterGuiLayersEvent в 26.1 не отрисовывался).
        // Выход из мира: сброс надзорного состояния клиента (цензор букв,
        // блокировки кнопок, таймер обыска) — иначе оно переезжает в меню.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                ClientRmn.reset());
        // Кнопка «Настройки» на вкладке «Моды»: фабрике передаётся родительский экран.
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (net.neoforged.neoforge.client.gui.IConfigScreenFactory) (modContainer, parent) ->
                        new RmnConfigScreen(parent));
        RosMineNadzor.LOGGER.info("РосМайнНадзор: клиентская часть загружена");
    }
}
