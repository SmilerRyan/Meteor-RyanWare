package smilerryan.ryanware.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class TAMsgCommand extends Command {
    public TAMsgCommand() {
        super("ta-msg", "Sends a fake TotalAnarchy-style message: PMs, public chat, join, leave, death coords, or teleport messages.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder
            .then(literal("to")
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
            .then(literal("from")
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
            .then(literal("join")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String user = StringArgumentType.getString(context, "username");
                        sendJoin(user);
                        return SINGLE_SUCCESS;
                    })
                )
            )
            .then(literal("leave")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String user = StringArgumentType.getString(context, "username");
                        sendLeave(user);
                        return SINGLE_SUCCESS;
                    })
                )
            )
            .then(literal("death")
                .then(argument("x", StringArgumentType.word())
                    .then(argument("y", StringArgumentType.word())
                        .then(argument("z", StringArgumentType.word())
                            .executes(context -> {
                                String x = StringArgumentType.getString(context, "x");
                                String y = StringArgumentType.getString(context, "y");
                                String z = StringArgumentType.getString(context, "z");
                                sendDeath(x, y, z);
                                return SINGLE_SUCCESS;
                            })
                        )
                    )
                )
            )
            .then(literal("tpa")
                .then(argument("username", StringArgumentType.word())
                    .executes(context -> {
                        String username = StringArgumentType.getString(context, "username");
                        sendTpaRequest(username);
                        return SINGLE_SUCCESS;
                    })
                )
            )
            .then(literal("tpaccept")
                .executes(context -> {
                    sendTpaccept();
                    return SINGLE_SUCCESS;
                })
            )
            .then(literal("tpdeny")
                .executes(context -> {
                    sendTpdeny();
                    return SINGLE_SUCCESS;
                })
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
        String tag = "";
        String name = user;
        if (user.length() > 2 && user.charAt(1) == '_') {
            char rankLetter = user.charAt(0);
            if (rankLetter == 'V') {
                tag = "§6" + rankLetter; // Gold color for V prefix
                name = user.substring(2);
            } else if (rankLetter == 'M') {
                tag = "§b" + rankLetter; // Blue color for M prefix
                name = user.substring(2);
            }
            // Any other letter prefix is treated as part of the username
        }
        String formatted = String.format("§f<%s§f%s> §7%s", tag.isEmpty() ? "" : tag + " ", name, message);
        addMessageToChat(formatted);
    }
    
    private void sendJoin(String user) {
        String formatted = "§8" + user + " joined the game.";
        addMessageToChat(formatted);
    }
    
    private void sendLeave(String user) {
        String formatted = "§8" + user + " left the game.";
        addMessageToChat(formatted);
    }
    
    private void sendDeath(String x, String y, String z) {
        String formatted = "§fYour death location is: §cX:" + x + " Y:" + y + " Z:" + z + "§6!";
        addMessageToChat(formatted);
    }
    
    private void sendTpaRequest(String username) {
        addMessageToChat("§c" + username + " §6has sent you a teleport request!");
        addMessageToChat("§6You have §c60§6 seconds to accept or deny the request");
        addMessageToChat("§6To accept the teleport request, type §c/tpaccept");
        addMessageToChat("§6To deny the teleport request, type §c/tpdeny");
        addMessageToChat("§a§lACCEPT §7§l| §c§lDENY");
    }
    
    private void sendTpaccept() {
        addMessageToChat("§6You §caccepted§6 the teleport request!");
        
        // Schedule the "Teleported!" message after 6 seconds
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            Thread delayThread = new Thread(() -> {
                try {
                    Thread.sleep(6000); // 6 seconds delay
                    mc.execute(() -> {
                        addMessageToChat("§6Teleported!");
                    });
                } catch (InterruptedException e) {
                    // Ignore interruption
                }
            });
            delayThread.setDaemon(true);
            delayThread.start();
        }
    }
    
    private void sendTpdeny() {
        addMessageToChat("§6You have rejected the §crequest§6!");
    }
   
    private void addMessageToChat(String message) {
        // This directly adds the message to the chat without the [Meteor] prefix
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(Text.of(message));
        }
    }
}