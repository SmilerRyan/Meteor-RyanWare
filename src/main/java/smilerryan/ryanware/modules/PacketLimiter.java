package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import smilerryan.ryanware.RyanWare;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;

public class PacketLimiter extends Module {

    private final Setting<Integer> maxPackets = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("max-packets")
        .description("Maximum packets allowed per tick.")
        .defaultValue(100)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Boolean> debugMode = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Show debug info in chat.")
        .defaultValue(false)
        .build()
    );

    private int packetsSent = 0;
    private int highestPacketsSent = 0;

    public PacketLimiter() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Packet-Limiter",
            "Limits the number of packets you can send per tick to prevent the server from kicking you for sending too many packets."
        );
    }

    @Override
    public void onActivate() {
        packetsSent = 0;
        highestPacketsSent = 0;
    }

    @Override
    public void onDeactivate() {
        packetsSent = 0;
        highestPacketsSent = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (packetsSent > highestPacketsSent) {
            highestPacketsSent = packetsSent;
        }

        if (debugMode.get()) {
            info("Highest " + highestPacketsSent + ", Sent " + packetsSent + ", Max " + maxPackets.get() + ".");
        }

        packetsSent = 0;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        Packet<?> packet = event.packet;

        if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket) {
            return;
        }

        if (packetsSent >= maxPackets.get()) {
            event.cancel();
            return;
        }

        packetsSent++;
    }
}