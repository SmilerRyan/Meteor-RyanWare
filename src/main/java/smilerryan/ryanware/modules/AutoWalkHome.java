package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

public class AutoWalkHome extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> x = sg.add(new DoubleSetting.Builder()
        .name("x").defaultValue(0).build()
    );

    private final Setting<Double> y = sg.add(new DoubleSetting.Builder()
        .name("y").defaultValue(64).build()
    );

    private final Setting<Double> z = sg.add(new DoubleSetting.Builder()
        .name("z").defaultValue(0).build()
    );

    private final Setting<Double> stopDist = sg.add(new DoubleSetting.Builder()
        .name("stop-distance")
        .defaultValue(1.5)
        .min(0.1)
        .build()
    );

    private boolean walking;

    public AutoWalkHome() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "AutoWalkHome",
            "Walks to a position when no players are in render distance."
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre e) {
        if (mc.player == null || mc.world == null) return;

        if (hasAnyOtherPlayer()) {
            stop();
            return;
        }

        Vec3d target = new Vec3d(x.get(), y.get(), z.get());
        if (mc.player.squaredDistanceTo(target) <= stopDist.get() * stopDist.get()) {
            stop();
            return;
        }

        lookAt(target);
        walk();
    }

    @Override
    public void onDeactivate() {
        stop();
    }

    private boolean hasAnyOtherPlayer() {
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player) return true;
        }
        return false;
    }

    private void walk() {
        mc.options.forwardKey.setPressed(true);
        mc.options.sprintKey.setPressed(true);
        walking = true;

        if (mc.player.horizontalCollision) mc.player.jump();
    }

    private void stop() {
        if (!walking) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        walking = false;
    }

    private void lookAt(Vec3d t) {
        Vec3d e = mc.player.getEyePos();
        double dx = t.x - e.x;
        double dz = t.z - e.z;
        double dy = t.y - e.y;

        double d = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, d)));
    }
}
