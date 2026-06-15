package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
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

    private final Setting<List<String>> allowedPackets = sgGeneral.add(
        new StringListSetting.Builder()
            .name("allowed-packets")
            .description("Filter by packet class name substring.")
            .defaultValue("Craft")
            .build()
    );

    private final Setting<Boolean> dumpFields = sgGeneral.add(new BoolSetting.Builder()
        .name("dump-fields")
        .description("Logs key packet fields using reflection (debug heavy).")
        .defaultValue(false)
        .build()
    );

    public enum Mode {
        Record,
        Replay
    }

    private final List<Packet<?>> recordedPackets = new ArrayList<>();
    private final List<String> packetIds = new ArrayList<>();

    private int replayIndex;
    private int delayTimer;

    public PacketRecorderReplayer() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Packet-Recorder",
            "Records and replays packets with readable debug info."
        );
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Record) {
            recordedPackets.clear();
            packetIds.clear();

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

        String id = packet.getClass().getName();
        recordedPackets.add(packet);
        packetIds.add(id);

        info(formatPacket(packet, true));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mode.get() != Mode.Replay || mc.getNetworkHandler() == null) return;

        while (replayIndex < recordedPackets.size() && !isAllowed(packetIds.get(replayIndex))) {
            replayIndex++;
        }

        if (replayIndex >= recordedPackets.size()) {
            if (loop.get()) {
                replayIndex = 0;
                delayTimer = replayDelay.get();
                info("Replay looped.");
                return;
            }

            info("Replay finished.");
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

    private boolean isAllowed(String id) {
        List<String> filters = allowedPackets.get();
        if (filters.isEmpty()) return true;

        for (String f : filters) {
            if (!f.isEmpty() && id.contains(f)) return true;
        }
        return false;
    }

    private String formatPacket(Packet<?> packet, boolean c2s) {
        StringBuilder sb = new StringBuilder();

        String className = packet.getClass().getSimpleName();
        if (className.startsWith("class_") || className.isEmpty()) {
            className = packet.getClass().getName();
            className = className.substring(className.lastIndexOf('.') + 1);
        }

        sb.append(c2s ? "[C2S] " : "[S2C] ");
        sb.append(className);

        // safe toString preview (often most useful)
        try {
            String str = packet.toString();
            if (str.length() > 150) str = str.substring(0, 150) + "...";
            sb.append(" | ").append(str);
        } catch (Exception ignored) {}

        // optional field dump
        if (dumpFields.get()) {
            sb.append("\n  fields: ");
            sb.append(dumpPacketFields(packet));
        }

        return sb.toString();
    }

    private String dumpPacketFields(Packet<?> packet) {
        StringBuilder sb = new StringBuilder();

        Field[] fields = packet.getClass().getDeclaredFields();

        int count = 0;
        for (Field f : fields) {
            if (count >= 6) break; // prevent spam

            try {
                f.setAccessible(true);
                Object val = f.get(packet);

                sb.append(f.getName())
                  .append("=")
                  .append(String.valueOf(val))
                  .append(" ");
                count++;
            } catch (Exception ignored) {}
        }

        if (sb.length() == 0) return "none";
        return sb.toString().trim();
    }
}