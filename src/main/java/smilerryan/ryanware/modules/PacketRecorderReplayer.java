package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class PacketRecorderReplayer extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Record or Replay packets.")
        .defaultValue(Mode.Record)
        .build()
    );

    private final Setting<Integer> replayDelay = sgGeneral.add(new IntSetting.Builder()
        .name("replay-delay")
        .description("Ticks between packets.")
        .defaultValue(1)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> loop = sgGeneral.add(new BoolSetting.Builder()
        .name("loop")
        .description("Loops replay when finished.")
        .defaultValue(false)
        .build()
    );

    // Only record/replay selected packet types
    private final Setting<List<String>> allowedPackets = sgGeneral.add(
        new StringListSetting.Builder()
            .name("allowed-packets")
            .description("Only packets whose class name contains these strings are recorded/replayed. Empty = allow all.")
            .defaultValue(
                "Craft"
            )
            .build()
    );

    public enum Mode {
        Record,
        Replay
    }

    private final List<Packet<?>> recordedPackets = new ArrayList<>();
    private final List<String> packetNames = new ArrayList<>();

    private int replayIndex;
    private int delayTimer;

    public PacketRecorderReplayer() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Packet-Recorder",
            "Records and replays selected packets."
        );
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Record) {
            recordedPackets.clear();
            packetNames.clear();

            replayIndex = 0;
            delayTimer = 0;

            info("Recording packets...");
        } else {
            if (recordedPackets.isEmpty()) {
                info("No packets recorded.");
                toggle();
                return;
            }

            replayIndex = 0;
            delayTimer = replayDelay.get();

            info("Replaying " + recordedPackets.size() + " packets...");
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mode.get() != Mode.Record) return;

        Packet<?> packet = event.packet;
        String name = packet.getClass().getSimpleName();

        //if (!isAllowed(name)) return;

        info(name);

        recordedPackets.add(packet);
        packetNames.add(name);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mode.get() != Mode.Replay || mc.getNetworkHandler() == null) return;

        while (replayIndex < recordedPackets.size() && !isAllowed(packetNames.get(replayIndex))) {
            replayIndex++;
        }

        if (replayIndex >= recordedPackets.size()) {
            if (loop.get()) {
                replayIndex = 0;
                delayTimer = replayDelay.get();
                info("Replay complete. Looping.");
                return;
            }

            info("Replay complete. Turning off.");
            toggle();
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        mc.getNetworkHandler().sendPacket(recordedPackets.get(replayIndex));

        replayIndex++;
        delayTimer = replayDelay.get();
    }

    private boolean isAllowed(String name) {
        List<String> filters = allowedPackets.get();
        if (filters.isEmpty()) return true;

        for (String f : filters) {
            if (!f.isEmpty() && name.contains(f)) return true;
        }
        return false;
    }
}