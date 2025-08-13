package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class AtSomeone extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Random random = new Random();

    public AtSomeone() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "@Someone", "Responds with a random online player when '@someone' is seen in chat.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().toLowerCase();
        if (msg.contains("@someone")) {
            if (mc.player != null && mc.getNetworkHandler() != null) {
                Collection<PlayerListEntry> playersCollection = mc.getNetworkHandler().getPlayerList();
                if (playersCollection.isEmpty()) return;

                List<PlayerListEntry> players = new ArrayList<>(playersCollection);
                PlayerListEntry randomPlayer = players.get(random.nextInt(players.size()));
                String username = randomPlayer.getProfile().getName();

                String command = "> " + username;
                mc.player.networkHandler.sendChatMessage(command);
            }
        }
    }
}
