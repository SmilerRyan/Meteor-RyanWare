package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
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

    private boolean wasAutoWalking = false;

    public AutoFollowPlayers() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "AutoFollowPlayers", "Locks view on and follows players.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        PlayerEntity closest = findClosestPlayer();
        if (closest == null) {
            stopWalking();
            return;
        }

        // Always snap look instantly
        lookAt(closest.getPos());

        double dist = mc.player.squaredDistanceTo(closest);
        if (dist <= minDistance.get() * minDistance.get()) {
            stopWalking();
            if (!legitMode.get()) mc.player.setVelocity(Vec3d.ZERO);
            return;
        }

        if (legitMode.get()) {
            // Normal walk mode
            mc.options.forwardKey.setPressed(true);
            mc.options.sprintKey.setPressed(true);
            wasAutoWalking = true;

            if (mc.player.horizontalCollision) mc.player.jump();
        }
        else {
            // Flying mode: move directly toward target
            Vec3d targetPos = closest.getPos();
            Vec3d playerPos = mc.player.getPos();

            Vec3d diff = targetPos.subtract(playerPos);

            // Speed control
            double speed = 0.3; // tweak as needed
            Vec3d velocity = diff.normalize().multiply(speed);

            mc.player.setVelocity(velocity);
            mc.player.velocityDirty = true;

            // Stop pressing keys so they don’t interfere
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            wasAutoWalking = false;
        }
    }

    @Override
    public void onDeactivate() {
        stopWalking();
        if (!legitMode.get() && mc.player != null) {
            mc.player.setVelocity(Vec3d.ZERO);
            mc.player.velocityDirty = true;
        }
    }

    private void stopWalking() {
        if (wasAutoWalking) {
            mc.options.forwardKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            wasAutoWalking = false;
        }
    }

    private PlayerEntity findClosestPlayer() {
        PlayerEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            if (!ignoreWalls.get() && !mc.player.canSee(player)) continue;
            if (!passesFilter(player)) continue;

            double dist = mc.player.squaredDistanceTo(player);
            if (dist < closestDist) {
                closestDist = dist;
                closest = player;
            }
        }
        return closest;
    }

    private boolean passesFilter(PlayerEntity player) {
        String name = player.getGameProfile().getName();

        switch (followMode.get()) {
            case Everyone:
                return true;
            case Friends:
                return Friends.get().isFriend(player);
            case Specific:
                return playerList.get().stream().anyMatch(s -> name.equalsIgnoreCase(s));
            case EveryoneExceptSpecific:
                return playerList.get().stream().noneMatch(s -> name.equalsIgnoreCase(s));
        }
        return true;
    }

    private void lookAt(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;

        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }
}
