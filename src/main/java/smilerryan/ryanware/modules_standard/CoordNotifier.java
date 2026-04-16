package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public class CoordNotifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> wholeCommand = sgGeneral.add(new StringSetting.Builder()
        .name("whole-command")
        .description("The whole command, auto replaces [ox] [oy] [oz] [nx] [ny] [nz] [tag] with coordinates and tag.")
        .defaultValue("/teammsg [tag] from [ox] [oy] [oz] TO [nx] [ny] [nz]")
        .build()
    );

    private final Setting<Boolean> hideLeakedMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-leaked-messages")
        .description("Whether to hide leaked messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> coordLeakTag = sgGeneral.add(new StringSetting.Builder()
        .name("coord-leak-tag")
        .description("The tag to identify coord leak messages, default is [Coord-TP-Leak].")
        .defaultValue("[Coord-TP-Leak]")
        .build()
    );

    public CoordNotifier() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Coord-Notifier", "Notifies a player when you get teleported and hides coord leak messages.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {

            if (MeteorClient.mc.player == null) return;

            String rawCommand = wholeCommand.get();

            int old_x = (int) MeteorClient.mc.player.getX();
            int old_y = (int) MeteorClient.mc.player.getY();
            int old_z = (int) MeteorClient.mc.player.getZ();

            // Delay one tick so position updates
            MeteorClient.mc.execute(() -> {
                int new_x = (int) MeteorClient.mc.player.getX();
                int new_y = (int) MeteorClient.mc.player.getY();
                int new_z = (int) MeteorClient.mc.player.getZ();

                String cmd = rawCommand
                    .replace("[ox]", String.valueOf(old_x))
                    .replace("[oy]", String.valueOf(old_y))
                    .replace("[oz]", String.valueOf(old_z))
                    .replace("[nx]", String.valueOf(new_x))
                    .replace("[ny]", String.valueOf(new_y))
                    .replace("[nz]", String.valueOf(new_z))
                    .replace("[tag]", coordLeakTag.get());

                if (cmd.startsWith("/")) {
                    MeteorClient.mc.player.networkHandler.sendChatCommand(cmd.substring(1));
                } else {
                    MeteorClient.mc.player.networkHandler.sendChatMessage(cmd);
                }
            });
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (hideLeakedMessages.get() && event.getMessage().getString().contains(coordLeakTag.get())) {event.cancel();}
    }

}