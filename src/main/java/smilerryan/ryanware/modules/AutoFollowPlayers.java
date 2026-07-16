package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class AutoFollowPlayers extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum FollowMode {
        Everyone, Friends, Specific, EveryoneExceptSpecific
    }

    private final Setting<FollowMode> followMode = sgGeneral.add(new EnumSetting.Builder<FollowMode>()
        .name("follow-mode")
        .description("Who to follow.")
        .defaultValue(FollowMode.Everyone)
        .build()
    );

    private final Setting<List<String>> playerList = sgGeneral.add(new StringListSetting.Builder()
        .name("player-list")
        .description("Specific players to follow or ignore (depending on mode).")
        .defaultValue()
        .build()
    );

    private final Setting<Double> minDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-distance")
        .description("Minimum distance to stay away from players.")
        .defaultValue(2.0)
        .min(0.0)
        .build()
    );

    private final Setting<Boolean> legitMode = sgGeneral.add(new BoolSetting.Builder()
        .name("legit-mode")
        .description("If enabled, behaves like walking/jumping. If disabled, flies directly toward players in 3D.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-walls")
        .description("Follow players even if they are behind walls.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .defaultValue(0.3)
        .min(0.01)
        .sliderMax(2.0)
        .build()
    );

    private boolean walking = false;

    public AutoFollowPlayers() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Auto-Follow-Players", "Locks view on and follows players.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        PlayerEntity target = findClosestPlayer();
        if (target == null) {
            stopKeys();
            return;
        }

        lookAt(target.getEyePos());

        double dist = mc.player.squaredDistanceTo(target);

        if (dist <= minDistance.get() * minDistance.get()) {
            stopKeys();
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        if (legitMode.get()) {
            // Normal walk mode
            mc.options.forwardKey.setPressed(true);
            mc.options.sprintKey.setPressed(true);
            walking = true;

            if (mc.player.horizontalCollision) mc.player.jump();
        } else {
            // SAFE POSITION ACCESS (NO getPos())
            double dx = target.getX() - mc.player.getX();
            double dy = target.getY() - mc.player.getY();
            double dz = target.getZ() - mc.player.getZ();

            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len == 0) return;

            Vec3d vel = new Vec3d(dx / len, dy / len, dz / len)
                .multiply(speed.get());

            mc.player.setVelocity(vel);

            // Stop pressing keys so they don’t interfere
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);

            walking = false;
        }
    }

    private PlayerEntity findClosestPlayer() {
        PlayerEntity closest = null;
        double best = Double.MAX_VALUE;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.isSpectator()) continue;

            if (!ignoreWalls.get() && !mc.player.canSee(p)) continue;
            if (!passesFilter(p)) continue;

            double d = mc.player.squaredDistanceTo(p);
            if (d < best) {
                best = d;
                closest = p;
            }
        }
        return closest;
    }

    private boolean passesFilter(PlayerEntity player) {
        String name = player.getName().getString();

        return switch (followMode.get()) {
            case Everyone -> true;

            case Friends -> Friends.get().isFriend(player);

            case Specific -> playerList.get().stream()
                .anyMatch(s -> s.equalsIgnoreCase(name));

            case EveryoneExceptSpecific -> playerList.get().stream()
                .noneMatch(s -> s.equalsIgnoreCase(name));
        };
    }

    private void lookAt(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;

        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private void stopKeys() {
        if (!walking) return;

        mc.options.forwardKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);

        walking = false;
    }

    @Override
    public void onDeactivate() {
        stopKeys();
        if (mc.player != null) mc.player.setVelocity(Vec3d.ZERO);
    }
}