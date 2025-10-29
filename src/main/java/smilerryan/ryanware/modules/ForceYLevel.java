package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;

public class ForceYLevel extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    public enum Mode { CUSTOM, CURRENT }

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Use a custom Y-level or your current Y-level on enable.")
            .defaultValue(Mode.CUSTOM)
            .build()
    );

    private final Setting<Double> customY = sg.add(new DoubleSetting.Builder()
            .name("custom-y-level")
            .description("The Y-level floor for Air Jesus when mode is CUSTOM.")
            .defaultValue(64.0)
            .min(0.0)
            .max(256.0)
            .sliderMax(256.0)
            .build()
    );

    private final Setting<Boolean> minHeightOnly = sg.add(new BoolSetting.Builder()
            .name("minimum-height-only")
            .description("Prevents the floor from rising automatically; only clamps to minimum height.")
            .defaultValue(false)
            .build()
    );

    private Field fallDistanceField;
    private double floorY = 64.0;
    private boolean crouchDisabled = false;

    public ForceYLevel() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Force-Y-Level", "Run/jump/crouch in the air and clamp falls to a Y-level.");
        initFallDistanceField();
    }

    private void initFallDistanceField() {
        try {
            fallDistanceField = PlayerEntity.class.getSuperclass().getDeclaredField("fallDistance");
            fallDistanceField.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            floorY = mode.get() == Mode.CUSTOM ? customY.get() : mc.player.getY();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        PlayerEntity player = mc.player;

        // Temporarily disable while crouching
        if (player.isSneaking()) {
            crouchDisabled = true;
            return;
        } else if (crouchDisabled) {
            // Reset floorY when re-enabling
            floorY = mode.get() == Mode.CUSTOM ? customY.get() : player.getY();
            crouchDisabled = false;
        }

        // Floor automatically rises if player goes higher (unless disabled)
        if (!minHeightOnly.get() && player.getY() > floorY) {
            floorY = player.getY();
        }

        // Clamp player if below floor
        if (player.getY() < floorY) {
            Vec3d pos = player.getPos();
            Vec3d vel = player.getVelocity();

            // Snap to floor
            player.setPosition(pos.x, floorY, pos.z);

            // Stop downward velocity
            if (vel.y < 0) player.setVelocity(vel.x, 0, vel.z);

            // Reset fall distance via reflection
            if (fallDistanceField != null) {
                try {
                    fallDistanceField.setFloat(player, 0f);
                } catch (Throwable ignored) {}
            }

            // Mark as on ground
            player.setOnGround(true);
        }
    }
}
