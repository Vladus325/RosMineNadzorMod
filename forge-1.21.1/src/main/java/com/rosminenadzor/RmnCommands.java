package com.rosminenadzor;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;

/**
 * Команды надзора: {@code /rmn start} — запуск (удобно вешать на донат-ивент
 * Extra с произвольной ценой), {@code /rmn stop} — остановка, {@code /rmn now} —
 * внеочередной запрет, {@code /rmn interval <day|секунды>} — смена ритма,
 * {@code /rmn ban <id>} — принудительный запрет из каталога (отладка/демо:
 * no_jump_sand, no_letter_n, no_weapon, ...), {@code /rmn reload} — перечитать конфиги.
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
                .then(Commands.literal("now").executes(RmnCommands::now))
                .then(Commands.literal("interval")
                        .then(Commands.argument("seconds", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(List.of("day", "0", "300", "600", "1200"), builder))
                                .executes(RmnCommands::interval)))
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

    /** Внеочередной запрет: новый запрет сразу, не дожидаясь смены дня/интервала. */
    private static int now(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!RmnOverwatch.isActive()) {
            source.sendFailure(Component.translatable("rosminenadzor.cmd.not_active"));
            return 0;
        }
        if (RmnOverwatch.rollNewBanNow(source.getServer())) {
            source.sendSuccess(() -> Component.translatable("rosminenadzor.cmd.now_started")
                    .withStyle(ChatFormatting.RED), false);
            return 1;
        }
        source.sendFailure(Component.translatable("rosminenadzor.cmd.now_empty"));
        return 0;
    }

    /**
     * Смена ритма запретов прямо в игре: {@code /rmn interval day} (или 0) —
     * по игровым суткам, {@code /rmn interval 300} — каждые 300 реальных секунд.
     * Значение сохраняется в config.json.
     */
    private static int interval(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String raw = StringArgumentType.getString(ctx, "seconds").trim().toLowerCase(Locale.ROOT);
        int seconds;
        if (raw.equals("day")) {
            seconds = 0;
        } else {
            try {
                seconds = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                source.sendFailure(Component.translatable("rosminenadzor.cmd.interval_bad", raw));
                return 0;
            }
        }
        if (seconds < 0 || seconds > 86_400) {
            source.sendFailure(Component.translatable("rosminenadzor.cmd.interval_bad", raw));
            return 0;
        }
        RmnConfig.banIntervalSeconds = seconds;
        RmnConfig.save();
        RmnOverwatch.resetCycleTimer(source.getServer());
        if (seconds == 0) {
            source.sendSuccess(() -> Component.translatable("rosminenadzor.cmd.interval_days")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.translatable("rosminenadzor.cmd.interval_seconds", seconds)
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static int forceBan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String banId = StringArgumentType.getString(ctx, "banId").trim().toLowerCase(Locale.ROOT);
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
