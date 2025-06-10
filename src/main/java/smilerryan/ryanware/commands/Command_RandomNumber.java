package smilerryan.ryanware.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Random;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class Command_RandomNumber extends Command {

    public Command_RandomNumber() {
        super("RandomNumber", "Generates random numbers in a specified range.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("min", IntegerArgumentType.integer())
            .then(argument("max", IntegerArgumentType.integer())
                .then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int min = IntegerArgumentType.getInteger(context, "min");
                        int max = IntegerArgumentType.getInteger(context, "max");
                        int amount = IntegerArgumentType.getInteger(context, "amount");
                        generateRandomNumbers(min, max, amount);
                        return SINGLE_SUCCESS;
                    })
                )
            )
        );
    }

    private void generateRandomNumbers(int min, int max, int amount) {
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }

        Random random = new Random();
        StringBuilder result = new StringBuilder("§6Generated Numbers: ");

        for (int i = 0; i < amount; i++) {
            int num = random.nextInt((max - min) + 1) + min;
            result.append(num);
            if (i < amount - 1) result.append(", ");
        }

        addMessageToChat(result.toString());
    }

    private void addMessageToChat(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(Text.of(message));
        }
    }
}
