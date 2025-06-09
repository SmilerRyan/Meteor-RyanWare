package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public class CoordNotifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> wholeCommand = sgGeneral.add(new StringSetting.Builder()
        .name("whole-command")
        .description("The whole command, auto replaces [x] [y] [z] [tag] with coordinates and tag.")
        .defaultValue("ServerOwner")
        .build()
    );

    private final Setting<Boolean> hideLeakedMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-leaked-messages")
        .description("Whether to hide leaked messages.")
        .defaultValue(false)
        .build()
    );


    private static final String COORD_LEAK_TAG = "[Coord-TP-Leak]";

    public CoordNotifier() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "coord-notifier", "Notifies a player when you get teleported and hides coord leak messages.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {

            // get the whole command
            String rawCommand = wholeCommand.get();

            // replace [x], [y], [z] [tag] with coordinates and tag seperately
            String x = String.valueOf((int) MeteorClient.mc.player.getX());
            String y = String.valueOf((int) MeteorClient.mc.player.getY());
            String z = String.valueOf((int) MeteorClient.mc.player.getZ());
            rawCommand = rawCommand.replace("[x]", x).replace("[y]", y).replace("[z]", z).replace("[tag]", COORD_LEAK_TAG);

            // if it's a command send it as a command, if not send it as a chat message
            if (rawCommand.startsWith("/")) {
                MeteorClient.mc.player.networkHandler.sendChatCommand(rawCommand.substring(1));
            } else {
                MeteorClient.mc.player.networkHandler.sendChatMessage(rawCommand);
            }

        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (hideLeakedMessages.get() && event.getMessage().getString().contains(COORD_LEAK_TAG)) {event.cancel();}
    }

}