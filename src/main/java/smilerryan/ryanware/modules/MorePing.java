package smilerryan.ryanware.modules;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import smilerryan.ryanware.RyanWare;

import java.util.HashSet;
import java.util.Random;

public class MorePing extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // General
    private final Setting<Integer> minPing = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-ping")
        .description("The minimum ping in milliseconds to add.")
        .defaultValue(100)
        .min(1)
        .sliderMin(50)
        .sliderMax(500)
        .noSlider()
        .build()
    );

    private final Setting<Integer> randomExtra = sgGeneral.add(new IntSetting.Builder()
        .name("random-extra")
        .description("The maximum random extra ping in milliseconds to add on top of the minimum.")
        .defaultValue(50)
        .min(0)
        .sliderMin(0)
        .sliderMax(200)
        .noSlider()
        .build()
    );

    // Variables
    private final Object2LongMap<KeepAliveC2SPacket> packets = new Object2LongOpenHashMap<>();
    private final Random random = new Random();

    // Constructor
    public MorePing() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "more-ping", "Modify your ping with minimum and random extra delay.");
    }

    // Overrides
    @Override
    public void onActivate() {
        this.packets.clear();
    }

    public void onDeactivate() {
        if (!this.packets.isEmpty()) {
            for (KeepAliveC2SPacket packet : new HashSet<>(this.packets.keySet())) {
                if (this.packets.getLong(packet) + getRandomPing() <= System.currentTimeMillis()) {
                    mc.getNetworkHandler().sendPacket(packet);
                }
            }
        }
    }

    // Helper method to get random ping
    private long getRandomPing() {
        return minPing.get() + (randomExtra.get() > 0 ? random.nextInt(randomExtra.get()) : 0);
    }

    // Packet Send Event
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof KeepAliveC2SPacket packet) {
            if (!this.packets.isEmpty() && new HashSet<>(this.packets.keySet()).contains(packet)) {
                this.packets.removeLong(packet);
                return;
            }

            this.packets.put(packet, System.currentTimeMillis());
            event.cancel();
        }
    }

    // Packet Receive Event
    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        for (KeepAliveC2SPacket packet : new HashSet<>(this.packets.keySet())) {
            if (this.packets.getLong(packet) + getRandomPing() <= System.currentTimeMillis()) {
                mc.getNetworkHandler().sendPacket(packet);
                break;
            }
        }
    }
}