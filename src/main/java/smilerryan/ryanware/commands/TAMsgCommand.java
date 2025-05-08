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
        super("ta-msg", "Sends a fake TotalAnarchy-style message: PMs or public chat.");
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
    
    private void addMessageToChat(String message) {
        // This directly adds the message to the chat without the [Meteor] prefix
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(Text.of(message));
        }
    }
}