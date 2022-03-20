package ca.bkaw.svcraftaudio;

import com.destroystokyo.paper.brigadier.BukkitBrigadierCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.List;
import java.util.UUID;

public class AudioCommand {
    private static final SimpleCommandExceptionType PLAYER_ONLY = new SimpleCommandExceptionType(new LiteralMessage("This is a player only command."));
    private static final DynamicCommandExceptionType USER_NOT_FOUND = new DynamicCommandExceptionType(userId -> new LiteralMessage("The user \"" + userId + "\" does not exist / is not connected."));

    private final SVCraftAudio svcraftAudio;
    private final UserManager userManager;

    public AudioCommand(SVCraftAudio svcraftAudio, UserManager userManager) {
        this.svcraftAudio = svcraftAudio;
        this.userManager = userManager;
    }

    public void register(CommandDispatcher<BukkitBrigadierCommandSource> dispatcher) {
        dispatcher.register(
            literal("audio")
                .requires(obj -> obj.getBukkitSender().hasPermission("svcraftaudio.command.audio"))
                .executes(ctx -> {
                    Player player = getPlayer(ctx);

                    String connectId = UUID.randomUUID().toString().substring(0, 8);
                    String userId = IdUtil.randomUserId();
                    Connection connectionRaw = this.svcraftAudio.getConnectionRaw();

                    if (connectionRaw.isOpen()) {
                        connectionRaw.addConnectId(connectId, userId, player.getName());
                        sendLink(player, connectId);
                    } else {
                        player.sendMessage("Connecting to svcraft-audio...");
                        this.svcraftAudio.getConnectionAsync().thenAccept(connection -> {
                           connection.addConnectId(connectId, userId, player.getName());
                           sendLink(player, connectId);
                        });
                    }

                    return 1;
                })
                .then(
                    literal("admin")
                        .requires(obj -> obj.getBukkitSender().hasPermission("svcraftaudio.command.admin"))
                        .then(
                            literal("reload")
                                .executes(ctx -> {
                                    this.svcraftAudio.reloadConfiguration();
                                    ctx.getSource().getBukkitSender().sendMessage(
                                        Component.text("The svcraft-audio configuration was reloaded.", NamedTextColor.GREEN)
                                    );
                                    return 1;
                                })
                        )
                        .then(
                            literal("who")
                                .then(
                                    argument("userId", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String userId = StringArgumentType.getString(ctx, "userId");
                                            ctx.getSource().getBukkitSender().sendMessage(Component.text()
                                                .append(Component.text(userId + " is "))
                                                .append(this.svcraftAudio.getUserComponent(userId))
                                            );
                                            return 1;
                                        })
                                        .suggests((ctx, builder) -> {
                                            for (User user : this.userManager.getUsers()) {
                                                if (StringUtil.startsWithIgnoreCase(user.getId(), builder.getRemaining())) {
                                                    builder.suggest(user.getId(), new LiteralMessage(user.getPlayer().getName()));
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                )
                        )
                        .then(
                            literal("list")
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getBukkitSender();
                                    for (User user : this.userManager.getUsers()) {
                                        sender.sendMessage(Component.text()
                                            .append(this.svcraftAudio.getUserComponent(user))
                                            .append(Component.text(" - " + user.getHearingUsers().size() + " hearing users"))
                                        );
                                    }
                                    return 1;
                                })
                        )
                        .then(
                            literal("resync")
                                .executes(ctx -> {
                                    resync2(ctx, false);
                                    return 1;
                                })
                                .then(
                                    literal("silent")
                                        .executes(ctx -> {
                                            resync2(ctx, true);
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("peersinfo")
                                .executes(ctx -> {
                                    Connection connection = this.svcraftAudio.getConnection();
                                    connection.setPeerInfoSender(ctx.getSource().getBukkitSender());
                                    connection.send("peersinfo");
                                    return 1;
                                })
                        )
                        .then(
                            literal("resendvolumes")
                                .executes(ctx -> {
                                    for (User user : this.userManager.getUsers()) {
                                        for (User hearingUser : user.getHearingUsers()) {
                                            double volume = user.getVolumeFor(hearingUser);
                                            user.setVolumeFor(hearingUser, volume);
                                        }
                                    }
                                    ctx.getSource().getBukkitSender().sendMessage("Resent all volumes.");
                                    return 1;
                                })
                        )
                        .then(
                            literal("sendraw")
                                .then(
                                    argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String message = StringArgumentType.getString(ctx, "message");
                                            this.svcraftAudio.getConnection().send(message);
                                            ctx.getSource().getBukkitSender().sendMessage("The message was sent.");
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("sendrawall")
                                .then(
                                    argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String message = StringArgumentType.getString(ctx, "message");
                                            List<User> users = this.userManager.getUsers();
                                            for (User user : users) {
                                                user.sendRaw(message);
                                            }
                                            ctx.getSource().getBukkitSender().sendMessage(
                                                "The messages was sent to "
                                                    + users.size() + " user"
                                                    + (users.size() == 1 ? "" : "s")
                                            );
                                            return 1;
                                        })
                                )
                        )
                        .then(
                            literal("state")
                                .executes(ctx -> {
                                    Connection connection = this.svcraftAudio.getConnectionRaw();
                                    ctx.getSource().getBukkitSender().sendMessage(
                                        "The connection is in ready state: " + connection.getReadyState().name()
                                    );
                                    return 1;
                                })
                        )
                        .then(
                            literal("reconnect")
                                .executes(ctx -> {
                                    this.svcraftAudio.getConnectionRaw().reconnect();
                                    ctx.getSource().getBukkitSender().sendMessage("The connection will reconnect asynchronously");
                                    return 1;
                                })
                        )
                        .then(
                            literal("reloadpage")
                                .then(
                                    literal("all")
                                        .executes(ctx -> {
                                            List<User> users = this.userManager.getUsers();
                                            for (User user : users) {
                                                user.sendReload();
                                            }
                                            ctx.getSource().getBukkitSender().sendMessage(
                                                "Reloaded the svcraft-audio website for "
                                                    + users.size() + " user"
                                                    + (users.size() == 1 ? "" : "s")
                                            );
                                            return 1;
                                        })
                                )
                                .then(
                                    argument("userId", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String userId = StringArgumentType.getString(ctx, "userId");
                                            User user = this.userManager.getUser(userId);
                                            if (user == null) {
                                                throw USER_NOT_FOUND.create(userId);
                                            }
                                            user.sendReload();

                                            ctx.getSource().getBukkitSender().sendMessage(Component.text()
                                                .append(Component.text("Reloaded the svcraft-audio website for "))
                                                .append(this.svcraftAudio.getUserComponent(user))
                                            );
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<BukkitBrigadierCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<BukkitBrigadierCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static Player getPlayer(CommandContext<BukkitBrigadierCommandSource> ctx) throws CommandSyntaxException {
        Entity entity = ctx.getSource().getBukkitEntity();
        if (entity instanceof Player) {
            return (Player) entity;
        }
        throw PLAYER_ONLY.create();
    }

    private void sendLink(Player player, String connectId) {
        player.sendMessage(Component.text()
            .content("Click here to open your link to svcraft-audio")
            .clickEvent(
                ClickEvent.openUrl(this.svcraftAudio.getConfiguration().url + "?connectId=" + connectId)
            )
            .color(NamedTextColor.BLUE)
            .decorate(TextDecoration.UNDERLINED));
    }

    private void resync2(CommandContext<BukkitBrigadierCommandSource> ctx, boolean silent) {
        this.svcraftAudio.getConnection().send("resync2");

        if (!silent) {
            for (User user : this.userManager.getUsers()) {
                user.getPlayer().sendMessage("svcraft-audio is reconnecting.");
            }
        }

        for (User user : this.userManager.getUsers()) {
            user.getHearingUsers().clear();
        }

        ctx.getSource().getBukkitSender().sendMessage("Everybody is being resynchronise2'd");
    }
}
