package smilerryan.ryanware.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class Command_TAMsg1 extends Command {
    
    public Command_TAMsg1() {
        super("ta-msg-1", "Sends a fake TotalAnarchy-style message: PMs, public chat, join, leave, death coords, or teleport messages.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder
            // msg-to
            .then(literal("msg-to")
                .then(argument("username", StringArgumentType.word())
                    .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String user = StringArgumentType.getString(context, "username");
                            String message = StringArgumentType.getString(context, "message");
                            sendPrivate(user, message, true);
                            return SINGLE_SUCCESS;
                        })
                    )
                )
            )
            // msg-from
            .then(literal("msg-from")
                .then(argument("username", StringArgumentType.word())
                    .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String user = StringArgumentType.getString(context, "username");
                            String message = StringArgumentType.getString(context, "message");
                            sendPrivate(user, message, false);
                            return SINGLE_SUCCESS;
                        })
                    )
                )
            )
            // public
            .then(literal("public")
                .then(argument("username", StringArgumentType.word())
                    .then(argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String user = StringArgumentType.getString(context, "username");
                            String message = StringArgumentType.getString(context, "message");
                            sendPublic(user, message);
                            return SINGLE_SUCCESS;
                        })
                    )
                )
            )
            // join
            .then(literal("join")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String user = StringArgumentType.getString(context, "username");
                        sendJoin(user);
                        return SINGLE_SUCCESS;
                    })
                )
            )
            // leave
            .then(literal("leave")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String user = StringArgumentType.getString(context, "username");
                        sendLeave(user);
                        return SINGLE_SUCCESS;
                    })
                )
            )
            // death
            .then(literal("death-location")
                .then(argument("x", StringArgumentType.word())
                    .then(argument("y", StringArgumentType.word())
                        .then(argument("z", StringArgumentType.word())
                            .executes(context -> {
                                String x = StringArgumentType.getString(context, "x");
                                String y = StringArgumentType.getString(context, "y");
                                String z = StringArgumentType.getString(context, "z");
                                sendDeathLocation(x, y, z);
                                return SINGLE_SUCCESS;
                            })
                        )
                    )
                )
            )
            // tpa-request-from
            .then(literal("tpa-request-from")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        sendTpaFromRequest(username);
                        return SINGLE_SUCCESS;
                    })
                )
            )
            // tpa-accept-from
            .then(literal("tpa-accept-from")
                .executes(context -> {
                    sendTpaFromAccept();
                    return SINGLE_SUCCESS;
                })
            )
            // tpa-from-deny
            .then(literal("tpa-deny-from")
                .executes(context -> {
                    sendTpaFromDeny();
                    return SINGLE_SUCCESS;
                })
            )
            // tpa-request-to
            .then(literal("tpa-request-to")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        sendTpaToRequest(username);
                        return SINGLE_SUCCESS;
                    })
                )
            )
            // tpa-deny-to
            .then(literal("tpa-deny-to")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        sendTpaToDeny();
                        return SINGLE_SUCCESS;
                    })
                )
            )
            // tpa-accept-to
            .then(literal("tpa-accept-to")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        sendTpaToAccept(username);
                        return SINGLE_SUCCESS;
                    })
                )
            );

    }

    private void sendPrivate(String user, String message, boolean isTo) {
        String formatted;
        if (isTo) {
            formatted = "§c§l(!) §f§l[§e§lYou §d-> §e" + user + "§f§l] §b" + message;
        } else {
            formatted = "§c§l(!) §f§l[§e" + user + " §d-> §e§lYou§f§l] §b" + message;
        }
        addMessageToChat(formatted);
    }

    private void sendPublic(String user, String message) {
        String tag = "§7";
        String name = user;
        String chatColor = "§7";
        if (user.length() > 2 && user.charAt(1) == '_') {
            char rankLetter = user.charAt(0);
            if (rankLetter == 'V') {
                tag = "§6" + rankLetter + "§f ";
                name = user.substring(2);
                chatColor = "§f";
            } else if (rankLetter == 'M') {
                tag = "§b" + rankLetter + "§f ";
                name = user.substring(2);
                chatColor = "§f";
            }
        }
        String formatted = String.format("§f<%s%s§f> %s%s", tag, name, chatColor, message);
        addMessageToChat(formatted);
    }
    
    private void sendJoin(String user) {
        addMessageToChat("§8" + user + " joined the game.");
    }
    
    private void sendLeave(String user) {
        addMessageToChat("§8" + user + " left the game.");
    }
    
    private void sendDeathLocation(String x, String y, String z) {
        addMessageToChat("§fYour death location is: §cX:" + x + " Y:" + y + " Z:" + z + "§6!");
    }
    
    // Tpa From Request
    private void sendTpaFromRequest(String username) {
        addMessageToChat("§c" + username + " §6has sent you a teleport request!");
        addMessageToChat("§6You have §c60§6 seconds to accept or deny the request");
        addMessageToChat("§6To accept the teleport request, type §c/tpaccept");
        addMessageToChat("§6To deny the teleport request, type §c/tpdeny");
        addMessageToChat("§a§lACCEPT §7§l| §c§lDENY");
    }
    
    // Tpa From Accept
    private void sendTpaFromAccept() {
        addMessageToChat("§6You §caccepted§6 the teleport request!");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            Thread delayThread = new Thread(() -> {
                try {
                    Thread.sleep(6000);
                    mc.execute(() -> { addMessageToChat("§6Teleported!"); });
                } catch (InterruptedException e) { }
            });
            delayThread.setDaemon(true);
            delayThread.start();
        }
    }

    // Tpa From Deny
    private void sendTpaFromDeny() {
        addMessageToChat("§6You have rejected the §crequest§6!");
    }

    // Tpa To Request
    private void sendTpaToRequest(String username) {
        addMessageToChat("§6Sending a teleport request to §c" + username + "§6. If you want to cancel it, type §c/tpcancel " + username);
    }

    // Tpa To Deny
    private void sendTpaToDeny() {
        addMessageToChat("§6The player has rejected your request!");
    }

    // Tpa To Accept
    private void sendTpaToAccept(String username) {
        addMessageToChat("§6You will be teleported in §c8§6 seconds!");
        addMessageToChat("§c" + username + " §6has accepted your teleportation request.");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            Thread delayThread = new Thread(() -> {
                try {
                    Thread.sleep(6000);
                    mc.execute(() -> { addMessageToChat("§6Teleported!"); });
                } catch (InterruptedException e) { }
            });
            delayThread.setDaemon(true);
            delayThread.start();
        }
    }

    private void addMessageToChat(String message) {
        MinecraftClient.getInstance().player.sendMessage(Text.of(message.replace('&', '§')), false);
    }
}