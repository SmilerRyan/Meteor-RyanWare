package smilerryan.ryanware.modules_essentials;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;

public class ElytraFakeRockets extends Module {
    private final Setting<Boolean> playSound = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("play-sound")
        .description("Play firework sound on boost.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldownTicks = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("anti-spam-ticks")
        .description("Cooldown time in ticks before you can boost again. Set to 0 to disable cooldown.")
        .defaultValue(20)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private int cooldown = 0;

    public ElytraFakeRockets() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix + "E-Elytra-Fake-Rockets", "Simulates Elytra rocket boosting without actual rockets.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isFallFlying()) return;

        if (cooldown > 0) cooldown--;

        if (mc.options.jumpKey.isPressed() && cooldown == 0) {
            Vec3d look = mc.player.getRotationVec(1.0f);

            // Limit vertical component to max 0.5 for smoother climb
            double vertical = Math.min(look.y, 0.5);
            Vec3d boostVec = new Vec3d(look.x, vertical, look.z).normalize().multiply(0.6);

            // Add to current velocity instead of setting it directly
            mc.player.setVelocity(mc.player.getVelocity().add(boostVec));

            if (playSound.get()) {
                mc.player.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);
            }

            // Spawn flame particles behind player to simulate rocket exhaust
            for (int i = 0; i < 5; i++) {
                double offsetX = -boostVec.x * i * 0.5;
                double offsetY = -boostVec.y * i * 0.5;
                double offsetZ = -boostVec.z * i * 0.5;
                mc.world.addParticle(ParticleTypes.FLAME,
                    mc.player.getX() + offsetX,
                    mc.player.getY() + offsetY,
                    mc.player.getZ() + offsetZ,
                    0, 0, 0);
            }

            if (cooldownTicks.get() > 0) {
                cooldown = cooldownTicks.get();
            }
        }

        // Apply slight drag every tick for more natural flight
        Vec3d velocity = mc.player.getVelocity().multiply(0.99);
        mc.player.setVelocity(velocity);
    }
}
