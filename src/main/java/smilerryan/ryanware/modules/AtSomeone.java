package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
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

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        AlwaysOne,         // always respond with just one random player
        RandomDuplicates,  // random player per tag, allow duplicates
        RandomNoDuplicates // random player per tag, no duplicates, fill with "no-one" if not enough
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How @someone responses are generated.")
        .defaultValue(Mode.AlwaysOne)
        .build()
    );


    public AtSomeone() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "@Someone", "Responds with a random online player when '@someone' is seen in chat. 'Help, I've fallen and I can't get up I need @someone'");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().toLowerCase();
        if (!msg.contains("@someone")) return;

        if (mc.player == null || mc.getNetworkHandler() == null) return;
        Collection<PlayerListEntry> playersCollection = mc.getNetworkHandler().getPlayerList();
        if (playersCollection.isEmpty()) return;

        List<PlayerListEntry> players = new ArrayList<>(playersCollection);
        int count = msg.split("@someone", -1).length - 1; // number of occurrences

        StringBuilder reply = new StringBuilder("> ");

        switch (mode.get()) {
            case AlwaysOne -> {
                PlayerListEntry randomPlayer = players.get(random.nextInt(players.size()));
                reply.append(randomPlayer.getProfile().getName());
            }

            case RandomDuplicates -> {
                for (int i = 0; i < count; i++) {
                    PlayerListEntry randomPlayer = players.get(random.nextInt(players.size()));
                    reply.append(randomPlayer.getProfile().getName());
                    if (i < count - 1) reply.append(" ");
                }
            }

            case RandomNoDuplicates -> {
                List<PlayerListEntry> pool = new ArrayList<>(players);
                for (int i = 0; i < count; i++) {
                    if (pool.isEmpty()) {
                        reply.append("no-one");
                    } else {
                        PlayerListEntry chosen = pool.remove(random.nextInt(pool.size()));
                        reply.append(chosen.getProfile().getName());
                    }
                    if (i < count - 1) reply.append(" ");
                }
            }
        }

        mc.player.networkHandler.sendChatMessage(reply.toString());
    }
}
