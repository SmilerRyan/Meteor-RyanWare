package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Constructor;

public class TotemBypass extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public TotemBypass() {
        super(RyanWare.CATEGORY, "TotemBypass", "Tricks the server into thinking you aren't going to die.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        // Only send spoofed movement if health is low, indicating totem is likely to trigger
        if (mc.player.getHealth() <= 1.0f) {
            try {
                // Fake death state to avoid triggering the totem for other players
                fakeDeathState();
                // Continue spoofing movement to simulate "death-like" behavior
                sendFakeMovement();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void fakeDeathState() {
        // Send a movement packet to fake being dead without triggering the totem
        double x = mc.player.getX();
        double y = mc.player.getY() - 0.05; // small downward nudge
        double z = mc.player.getZ();
        boolean onGround = mc.player.isOnGround();

        try {
            Class<?> packetClass = PlayerMoveC2SPacket.PositionAndOnGround.class;
            Constructor<?> constructor = packetClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);

            Packet<?> packet = (Packet<?>) constructor.newInstance(x, y, z, onGround);
            mc.getNetworkHandler().sendPacket(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendFakeMovement() throws Exception {
        double x = mc.player.getX();
        double y = mc.player.getY() - 0.05; // small downward nudge
        double z = mc.player.getZ();
        boolean onGround = mc.player.isOnGround();

        // Try to find the constructor for PositionAndOnGround dynamically
        Class<?> packetClass = PlayerMoveC2SPacket.PositionAndOnGround.class;
        Constructor<?> constructor = packetClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);

        Packet<?> packet = (Packet<?>) constructor.newInstance(x, y, z, onGround);
        mc.getNetworkHandler().sendPacket(packet);
    }
}
