package smilerryan.ryanware.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class Command_GMC extends Command {

    public Command_GMC() {
        super("gmc", "Toggles between Creative and Survival mode.");
    }

    @Override
    public void build(LiteralArgumentBuilder<net.minecraft.command.CommandSource> builder) {
        builder.executes(context -> {
            toggleGamemode();
            return SINGLE_SUCCESS;
        });
    }

    private void toggleGamemode() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            boolean nowCreative = !mc.player.getAbilities().creativeMode;

            mc.player.getAbilities().allowFlying = nowCreative;
            mc.player.getAbilities().allowModifyWorld = nowCreative;
            mc.player.getAbilities().creativeMode = nowCreative;
            mc.player.getAbilities().flying = nowCreative;
            // flySpeed
            mc.player.getAbilities().invulnerable = nowCreative;
            // walkSpeed

            // Just update the client-side abilities, works for singleplayer/LAN
            mc.player.sendMessage(Text.of("§aYou are now in " + (nowCreative ? "Creative" : "Survival") + " mode!"), false);
        }
    }
}
