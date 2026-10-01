package com.rosminenadzor;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды надзора: {@code /rmn start} — запуск (удобно вешать на донат-ивент
 * Extra с произвольной ценой), {@code /rmn ban <id>} — принудительный запрет
 * из каталога (отладка/демо: no_jump_sand, no_letter_n, no_weapon, ...).
 */
public final class RmnCommands {
    private RmnCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rmn")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("start").executes(RmnCommands::start))
                .then(Commands.literal("stop").executes(ctx -> {
                    RmnOverwatch.stop(ctx.getSource().getServer());
                    return 1;
                }))
                .then(Commands.literal("ban")
                        .then(Commands.argument("banId", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (RmnBan ban : RmnBan.all()) {
                                        builder.suggest(ban.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(RmnCommands::forceBan)))
                .then(Commands.literal("reload").executes(ctx -> {
                    RmnOverwatch.reloadCustomBans();
                    ctx.getSource().sendSuccess(() -> Component
                            .translatable("rosminenadzor.cmd.reloaded"), false);
                    return 1;
                })));
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        RmnOverwatch.start(source.getServer(), source.getPlayer(), true);
        return 1;
    }

    private static int forceBan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String banId = StringArgumentType.getString(ctx, "banId").trim().toLowerCase(java.util.Locale.ROOT);
        if (!RmnOverwatch.isActive()) {
            source.sendFailure(Component.translatable("rosminenadzor.cmd.not_active"));
            return 0;
        }
        if (RmnBan.byId(banId) == null) {
            source.sendFailure(Component.translatable("rosminenadzor.cmd.unknown_ban", banId));
            return 0;
        }
        RmnOverwatch.debugAddBan(source.getServer(), banId);
        source.sendSuccess(() -> Component.translatable("rosminenadzor.cmd.ban_started", banId)
                .withStyle(ChatFormatting.RED), false);
        return 1;
    }
}
