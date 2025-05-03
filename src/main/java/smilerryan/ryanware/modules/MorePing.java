package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import smilerryan.ryanware.RyanWare;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Random;

public class MorePing extends Module {
    private final SettingGroup sgPing = settings.getDefaultGroup();

    private final Setting<Integer> baseDelay = sgPing.add(new IntSetting.Builder()
        .name("base-delay-ms")
        .description("Minimum additional ping in milliseconds.")
        .defaultValue(100)
        .min(0)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Integer> randomExtra = sgPing.add(new IntSetting.Builder()
        .name("random-extra-ms")
        .description("Random extra delay added to base.")
        .defaultValue(50)
        .min(0)
        .sliderMax(500)
        .build()
    );

    private final Queue<DelayedPacket> delayedPackets = new ConcurrentLinkedQueue<>();
    private final Random random = new Random();

    public MorePing() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix+"more-ping", "Spoofs a higher ping by delaying manually sent packets.");
    }

    private static class DelayedPacket {
        public final Packet<?> packet;
        public final long sendTime;

        public DelayedPacket(Packet<?> packet, long delayMs) {
            this.packet = packet;
            this.sendTime = System.currentTimeMillis() + delayMs;
        }
    }

    public void sendWithPing(Packet<?> packet) {
        int delay = baseDelay.get() + random.nextInt(randomExtra.get() + 1);
        delayedPackets.add(new DelayedPacket(packet, delay));
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        long now = System.currentTimeMillis();
        while (!delayedPackets.isEmpty()) {
            DelayedPacket dp = delayedPackets.peek();
            if (dp.sendTime <= now) {
                mc.getNetworkHandler().sendPacket(dp.packet);
                delayedPackets.poll();
            } else break;
        }
    }

    @Override
    public void onDeactivate() {
        delayedPackets.clear();
    }
}
