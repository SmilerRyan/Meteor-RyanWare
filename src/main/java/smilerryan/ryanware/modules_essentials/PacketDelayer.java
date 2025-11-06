package smilerryan.ryanware.modules_essentials;

import smilerryan.ryanware.RyanWare;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.PacketListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.sound.SoundEvents;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class PacketDelayer extends Module {
    public enum NotifyMode {
        None,
        Chat,
        Sound,
        Both
    }

    private final Setting<Set<Class<? extends Packet<?>>>> c2sPackets = settings.getDefaultGroup().add(
        new PacketListSetting.Builder()
            .name("c2s-packets")
            .description("Client-to-server packets to delay.")
            .filter(PacketUtils.getC2SPackets()::contains)
            .defaultValue(new HashSet<>())
            .build()
    );

    private final Setting<Set<Class<? extends Packet<?>>>> s2cPackets = settings.getDefaultGroup().add(
        new PacketListSetting.Builder()
            .name("s2c-packets")
            .description("Server-to-client packets to drop.")
            .filter(PacketUtils.getS2CPackets()::contains)
            .defaultValue(new HashSet<>())
            .build()
    );

    private final Setting<NotifyMode> notify = settings.getDefaultGroup().add(
        new EnumSetting.Builder<NotifyMode>()
            .name("notify-mode")
            .description("How to notify when delaying packets.")
            .defaultValue(NotifyMode.Chat)
            .build()
    );

    private final Queue<Packet<?>> delayingC2S = new LinkedList<>();
    private final Queue<Packet<?>> delayingS2C = new LinkedList<>();

    public PacketDelayer() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix_essentials + "PacketDelayer",
            "Delays specified packets going to and from the server.");
    }

    @Override
    public void onDeactivate() {
        // Flush delayed C2S packets
        int countC2S = 0;
        while (!delayingC2S.isEmpty()) {
            Packet<?> p = delayingC2S.poll();
            try {
                mc.getNetworkHandler().sendPacket(p);
                countC2S++;
            } catch (Exception ignored) { }
        }
        if (countC2S > 0) info("Flushed " + countC2S + " delayed C2S packet(s).");

        // Drop S2C
        int droppedS2C = delayingS2C.size();
        delayingS2C.clear();
        if (droppedS2C > 0) info("Dropped " + droppedS2C + " S2C packet(s).");
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (c2sPackets.get().contains(event.packet.getClass())) {
            delayingC2S.add(event.packet);
            event.cancel();
            notifyUser(event.packet, true);
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (s2cPackets.get().contains(event.packet.getClass())) {
            delayingS2C.add(event.packet);
            event.cancel();
            notifyUser(event.packet, false);
        }
    }

    private void notifyUser(Packet<?> packet, boolean c2s) {
        NotifyMode mode = notify.get();
        if (mode == NotifyMode.None) return;

        String msg = (c2s ? "Delayed C2S: " : "Delayed S2C: ") + packet.getClass().getSimpleName();

        if (mode == NotifyMode.Chat || mode == NotifyMode.Both)
            info(msg);

        if ((mode == NotifyMode.Sound || mode == NotifyMode.Both) && mc.player != null) {
            try {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            } catch (Throwable ignored) {}
        }
    }
}
