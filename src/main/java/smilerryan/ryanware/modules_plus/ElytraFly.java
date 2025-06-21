package smilerryan.ryanware.modules_plus;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import smilerryan.ryanware.RyanWare;

public class ElytraFly extends Module {
    private final Setting<Double> speed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("speed")
        .description("Flying speed.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMin(0.1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> antiCrash = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("anti-crash")
        .description("Slows down near walls and steep angles.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoPilot = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-pilot")
        .description("Automatically fly forward when above height.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> autoPilotHeight = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("auto-pilot-height")
        .description("Auto-pilot activates above this Y level.")
        .defaultValue(150)
        .min(0)
        .sliderMin(0)
        .sliderMax(320)
        .build()
    );

    private final Vec3d[] directions = new Vec3d[] {
        new Vec3d( 1,  0,  0), new Vec3d(-1,  0,  0),
        new Vec3d( 0,  0,  1), new Vec3d( 0,  0, -1),
        new Vec3d( 1,  0,  1), new Vec3d(-1, 0, -1),
        new Vec3d(-1, 0,  1), new Vec3d( 1, 0, -1),
        new Vec3d( 0, -1, 0) // down
    };

    private static final double MIN_SPEED = 0.1;
    private static final int ANTI_CRASH_RANGE = 3;
    private static final double CRASH_PITCH = 40; // degrees, if you're diving too steep

    public ElytraFly() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-elytra-fly", "Fly with Elytra at a set speed, with anti-crash and manual/auto control.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isFallFlying()) return;

        double baseSpeed = speed.get();
        double currentSpeed = baseSpeed;
        Vec3d moveVec = Vec3d.ZERO;

        boolean isAuto = autoPilot.get() && mc.player.getY() > autoPilotHeight.get();

        if (isAuto) {
            moveVec = mc.player.getRotationVec(1.0f);
        } else {
            boolean anyKey = mc.options.forwardKey.isPressed() || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed();

            if (anyKey) {
                double y = 0;
                if (mc.options.jumpKey.isPressed()) y += 1;
                if (mc.options.sneakKey.isPressed()) y -= 1;

                Vec3d forward = mc.options.forwardKey.isPressed()
                    ? new Vec3d(mc.player.getRotationVec(1.0f).x, 0, mc.player.getRotationVec(1.0f).z).normalize()
                    : Vec3d.ZERO;

                moveVec = new Vec3d(forward.x, y, forward.z).normalize();
            } else {
                moveVec = Vec3d.ZERO;
                currentSpeed = 0;
            }
        }

        if (antiCrash.get()) {
            Box box = mc.player.getBoundingBox().expand(0.5);
            double minFactor = 1.0;

            for (Vec3d dir : directions) {
                Vec3d norm = dir.normalize();
                for (int i = 1; i <= ANTI_CRASH_RANGE; i++) {
                    Box checkBox = box.offset(norm.multiply(i));
                    if (!mc.world.isSpaceEmpty(mc.player, checkBox)) {
                        double factor = (double)(i) / ANTI_CRASH_RANGE;
                        minFactor = Math.min(minFactor, factor);
                        break;
                    }
                }
            }

            currentSpeed = MIN_SPEED + (baseSpeed - MIN_SPEED) * minFactor;

            // extra safety: steep dive detection
            float pitch = mc.player.getPitch();
            if (pitch > CRASH_PITCH) {
                double steepFactor = 1.0 - Math.min((pitch - CRASH_PITCH) / 60.0, 1.0); // scales from 1 to 0
                currentSpeed *= steepFactor;
            }
        }

        Vec3d velocity = moveVec.multiply(currentSpeed);
        mc.player.setVelocity(velocity.x, velocity.y, velocity.z);
    }
}
