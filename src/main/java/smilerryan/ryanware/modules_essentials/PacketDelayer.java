package smilerryan.ryanware.modules_essentials;

import smilerryan.ryanware.RyanWare;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
            .name("packets-to-delay-or-drop (C2S)")
            .description("Client-to-server packets selected here will be delayed or dropped.")
            .filter(PacketUtils.getC2SPackets()::contains)
            .defaultValue(new HashSet<>(PacketUtils.getC2SPackets()))
            .build()
    );

    private final Setting<Set<Class<? extends Packet<?>>>> s2cPackets = settings.getDefaultGroup().add(
        new PacketListSetting.Builder()
            .name("packets-to-delay-or-drop (S2C)")
            .description("Server-to-client packets selected here will be delayed or dropped.")
            .filter(PacketUtils.getS2CPackets()::contains)
            .defaultValue(new HashSet<>(PacketUtils.getS2CPackets()))
            .build()
    );

    private final Setting<Boolean> c2sDrop = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("drop-c2s-instead-of-delaying")
            .description("Drop outgoing packets instead of delaying them.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> s2cDrop = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("drop-s2c-instead-of-delaying")
            .description("Drop incoming packets instead of delaying them.")
            .defaultValue(false)
            .build()
    );

    private final Setting<NotifyMode> notify = settings.getDefaultGroup().add(
        new EnumSetting.Builder<NotifyMode>()
            .name("notify-mode")
            .description("How to notify when packets are delayed or dropped.")
            .defaultValue(NotifyMode.Chat)
            .build()
    );

    private final Setting<Boolean> finishNotify = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("send-details-on-deactivate")
            .description("Send a chat summary when the module is disabled.")
            .defaultValue(true)
            .build()
    );

    private final Queue<Packet<?>> delayingC2S = new LinkedList<>();
    private final Queue<Packet<?>> delayingS2C = new LinkedList<>();

    private int droppedC2S = 0;
    private int droppedS2C = 0;

    public PacketDelayer() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix_essentials + "PacketDelayer", "Delays or drops selected packets.");
    }

    @Override
    public void onDeactivate() {

        int delayedC2S = 0;
        while (!delayingC2S.isEmpty()) {
            Packet<?> p = delayingC2S.poll();
            try {
                mc.getNetworkHandler().sendPacket(p);
                delayedC2S++;
            } catch (Exception ignored) {}
        }

        int delayedS2C = delayingS2C.size();
        delayingS2C.clear();

        int droppedC2S_final = droppedC2S;
        int droppedS2C_final = droppedS2C;

        droppedC2S = 0;
        droppedS2C = 0;

        if (!finishNotify.get()) return;

        java.util.List<String> parts = new java.util.ArrayList<>();

        if (delayedC2S > 0) parts.add(delayedC2S + " delayed C2S");
        if (delayedS2C > 0) parts.add(delayedS2C + " delayed S2C");
        if (droppedC2S_final > 0) parts.add(droppedC2S_final + " dropped C2S");
        if (droppedS2C_final > 0) parts.add(droppedS2C_final + " dropped S2C");

        if (parts.isEmpty()) return;

        info(String.join(", ", parts));

    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (c2sPackets.get().contains(event.packet.getClass())) {
            event.cancel();
            if (c2sDrop.get()) droppedC2S++;
            else delayingC2S.add(event.packet);
            notifyUser(event.packet, true, !c2sDrop.get());
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (s2cPackets.get().contains(event.packet.getClass())) {
            event.cancel();
            if (s2cDrop.get()) droppedS2C++;
            else delayingS2C.add(event.packet);
            notifyUser(event.packet, false, !s2cDrop.get());
        }
    }

    private String readablePacketName(Packet<?> packet) {
        Class<?> cls = packet.getClass();
        String name = cls.getSimpleName();
        if (name.startsWith("class_") || name.startsWith("$")) name = cls.getSuperclass().getSimpleName();
        return name;
    }

    private void notifyUser(Packet<?> packet, boolean c2s, boolean delayed) {
        NotifyMode mode = notify.get();
        if (mode == NotifyMode.None) return;

        String msg = (delayed ? "Delayed " : "Dropped ") + (c2s ? "C2S: " : "S2C: ") + readablePacketName(packet);

        if (mode == NotifyMode.Chat || mode == NotifyMode.Both) info(msg);

        if ((mode == NotifyMode.Sound || mode == NotifyMode.Both) && mc.player != null) {
            try {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
            } catch (Throwable ignored) {}
        }
    }
}
