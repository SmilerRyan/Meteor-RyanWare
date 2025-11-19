package smilerryan.ryanware.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class Command_showText extends Command {

    public Command_showText() {
        super("showText", "Prints any text you give it.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder
            .then(argument("text", StringArgumentType.greedyString())
                .executes(context -> {
                    MinecraftClient.getInstance().player.sendMessage(Text.of(StringArgumentType.getString(context, "text").replace('&', '§')), false);
                    return SINGLE_SUCCESS;
                })
            );
    }

}