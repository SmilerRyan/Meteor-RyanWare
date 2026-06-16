package smilerryan.ryanware.commands.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class command_reply extends Command {
    private static String lastWhisperer;

    public command_reply() {
        super(smilerryan.ryanware.RyanWare.commandPrefix + "reply", "Sends a /tell to the last person who whispered to you.");
    }
    
    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        lastWhisperer = null;
    }
        
    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();

        msg = msg.replaceAll("§.", "");

        String marker = " whispers to you:";
        int idx = msg.indexOf(marker);
        if (idx <= 0) return;

        String before = msg.substring(0, idx).trim();

        String[] parts = before.split("\\s+");
        String username = parts[parts.length - 1];

        lastWhisperer = username;
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("text", StringArgumentType.greedyString())
            .executes(context -> {
                if (lastWhisperer == null) {
                    error("Nobody has whispered to you yet.");
                    return SINGLE_SUCCESS;
                }

                String text = StringArgumentType.getString(context, "text");

                MinecraftClient.getInstance().player.networkHandler.sendChatCommand(
                    "tell " + lastWhisperer + " " + text
                );

                return SINGLE_SUCCESS;
            }));
    }
}