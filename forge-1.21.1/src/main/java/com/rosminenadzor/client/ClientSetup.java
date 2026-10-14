package com.rosminenadzor.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Клиентская инициализация Forge-порта (у Forge нет @Mod(dist=CLIENT)).
 * ВАЖНО: общий @Mod-класс не должен содержать лямбд, возвращающих клиентские
 * типы — верификатор класса на выделенном сервере принудительно резолвит
 * клиентский Screen («Attempted to load class Screen for invalid dist
 * DEDICATED_SERVER»), даже если ветка под dist-guard'ом никогда не исполняется.
 * Поэтому весь клиентский код собран здесь: класс грузится только на клиенте.
 */
public final class ClientSetup {
    private ClientSetup() {
    }

    public static void init(ModContainer container) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        // Выход из мира: сброс надзорного состояния клиента (цензор букв,
        // блокировки кнопок, таймер обыска) — иначе оно переезжает в меню.
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                ClientRmn.reset());
        // Кнопка «Настройки» на вкладке «Моды»: фабрике передаётся родительский экран.
        container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (Minecraft minecraft, net.minecraft.client.gui.screens.Screen parent) ->
                                new RmnConfigScreen(parent)));
    }
}
