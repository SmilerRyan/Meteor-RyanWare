package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import smilerryan.ryanware.RyanWare;

import java.util.List;
import java.util.Random;

public class AntiTouch extends Module {

    public enum Mode {
        Everyone, Friends, NonFriends,
        FriendsExceptList, NonFriendsExceptList, OnlyList
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> safeDistance = sg.add(new DoubleSetting.Builder()
        .name("safe-distance").defaultValue(6).min(1).sliderMax(20).build());

    private final Setting<Boolean> sprint = sg.add(new BoolSetting.Builder()
        .name("sprint").defaultValue(true).build());

    private final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder()
        .name("rotate").defaultValue(true).build());

    private final Setting<Boolean> includeMobs = sg.add(new BoolSetting.Builder()
        .name("include-mobs")
        .description("Avoid mobs that are looking at you.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> stuckTicks = sg.add(new IntSetting.Builder()
        .name("stuck-ticks").defaultValue(6).min(1).sliderMax(40).build());

    private final Setting<Boolean> antiTrap = sg.add(new BoolSetting.Builder()
        .name("anti-trap").defaultValue(true).build());

    private final Setting<Double> trapRange = sg.add(new DoubleSetting.Builder()
        .name("trap-range").defaultValue(3.5).min(1).sliderMax(6).build());

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode").defaultValue(Mode.Everyone).build());

    private final Setting<List<String>> list = sg.add(new StringListSetting.Builder()
        .name("list").build());

    // State
    private Vec3d lastPos;
    private int stuck;
    private int escapeTicks;
    private int panicTicks;
    private float escapeYaw;
    private boolean controlling;

    private final Random random = new Random();

    public AntiTouch() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Anti-Touch",
            "Automatically moves away from players or mobs looking at you.");
    }

    @Override
    public void onActivate() {
        lastPos = null;
        stuck = 0;
        escapeTicks = 0;
        panicTicks = 0;
        controlling = false;
    }

    @Override
    public void onDeactivate() {
        release();
    }

    // --- BLOCK DETECTION ---
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!antiTrap.get()) return;
        if (mc.player == null) return;

        BlockPos pos = event.pos;

        double dist = mc.player.squaredDistanceTo(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5
        );

        if (dist > trapRange.get() * trapRange.get()) return;

        if (event.oldState.isAir() && !event.newState.isAir()) {
            panicTicks = 10;
        }
    }

    // --- MAIN LOOP ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        if (mc.player.isSpectator()) {
            if (controlling) release();
            return;
        }

        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        // PANIC
        if (panicTicks > 0) {
            panicTicks--;

            controlling = true;

            if (rotate.get()) {
                float yaw = mc.player.getYaw() + (float)(random.nextInt(360) - 180);
                mc.player.setYaw(yaw);
                pressForward();
                strongStrafe();
            } else {
                moveBackwards();
                strongStrafe();
            }

            mc.options.jumpKey.setPressed(true);
            return;
        }

        boolean danger = hasNearbyThreat();

        // stuck detection
        if (danger && lastPos != null) {
            if (pos.distanceTo(lastPos) < 0.03) stuck++;
            else stuck = 0;

            if (stuck >= stuckTicks.get()) {
                escapeYaw = mc.player.getYaw() + (float)(90 + random.nextInt(180));
                escapeTicks = 12;
                stuck = 0;
            }
        }

        lastPos = pos;

        if (!danger && escapeTicks <= 0) {
            if (controlling) release();
            return;
        }

        controlling = true;

        // ESCAPE
        if (escapeTicks > 0) {
            escapeTicks--;

            if (rotate.get()) {
                mc.player.setYaw(escapeYaw);
                pressForward();
            } else {
                moveBackwards();
            }

            strongStrafe();
            jumpIfColliding();
            return;
        }

        // OPEN SPACE SCORING
        Vec3d bestDir = null;
        double bestScore = -999;

        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));

            double score = 0;

            // terrain scan
            for (int d = 1; d <= 3; d++) {
                Vec3d check = pos.add(dir.multiply(d));
                BlockPos bp = new BlockPos((int)check.x, (int)check.y, (int)check.z);

                if (!mc.world.getBlockState(bp).isAir()) score -= 6 * d;
                else score += 2;
            }

            // player penalty
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (!shouldAvoid(p)) continue;

                double dist = mc.player.distanceTo(p);
                if (dist > safeDistance.get()) continue;

                Vec3d predicted = new Vec3d(p.getX(), p.getY(), p.getZ())
                    .add(p.getVelocity().multiply(5));

                Vec3d toPlayer = predicted.subtract(pos).normalize();
                double dot = dir.dotProduct(toPlayer);

                if (dot > 0.3) score -= 4;
            }

            // mob penalty (LOOKING AT YOU)
            if (includeMobs.get()) {
                for (MobEntity m : mc.world.getEntitiesByClass(
                        MobEntity.class,
                        mc.player.getBoundingBox().expand(safeDistance.get()),
                        e -> true)) {

                    if (!isMobLookingAtMe(m)) continue;

                    double dist = mc.player.distanceTo(m);
                    if (dist > safeDistance.get()) continue;

                    Vec3d predicted = new Vec3d(m.getX(), m.getY(), m.getZ())
                        .add(m.getVelocity().multiply(5));

                    Vec3d toMob = predicted.subtract(pos).normalize();
                    double dot = dir.dotProduct(toMob);

                    if (dot > 0.3) score -= 4;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir == null) return;

        if (rotate.get()) {
            float targetYaw = (float)(Math.toDegrees(Math.atan2(bestDir.z, bestDir.x)) - 90f);
            mc.player.setYaw(smooth(mc.player.getYaw(), targetYaw, 20f));
            pressForward();
        } else {
            moveBackwards();
        }

        randomStrafe();
        jumpIfColliding();
    }

    private boolean hasNearbyThreat() {
        // players
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (!shouldAvoid(p)) continue;
            if (mc.player.distanceTo(p) <= safeDistance.get()) return true;
        }

        // mobs (LOOKING AT YOU)
        if (includeMobs.get()) {
            for (MobEntity m : mc.world.getEntitiesByClass(
                    MobEntity.class,
                    mc.player.getBoundingBox().expand(safeDistance.get()),
                    e -> true)) {

                if (!isMobLookingAtMe(m)) continue;

                if (mc.player.distanceTo(m) <= safeDistance.get()) return true;
            }
        }

        return false;
    }

    // --- NEW: LOOK DETECTION ---
    private boolean isMobLookingAtMe(MobEntity m) {
        Vec3d mobEyes = m.getEyePos();
        Vec3d playerEyes = mc.player.getEyePos();

        Vec3d look = m.getRotationVec(1.0f).normalize();
        Vec3d toPlayer = playerEyes.subtract(mobEyes).normalize();

        double dot = look.dotProduct(toPlayer);

        return dot > 0.65;
    }

    private void pressForward() {
        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        if (sprint.get()) mc.player.setSprinting(true);
    }

    private void moveBackwards() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(true);
        if (sprint.get()) mc.player.setSprinting(true);
    }

    private void strongStrafe() {
        boolean left = random.nextBoolean();
        mc.options.leftKey.setPressed(left);
        mc.options.rightKey.setPressed(!left);
    }

    private void randomStrafe() {
        if (random.nextDouble() < 0.3) strongStrafe();
        else {
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
    }

    private void jumpIfColliding() {
        mc.options.jumpKey.setPressed(mc.player.horizontalCollision);
    }

    private void release() {
        if (mc.options == null) return;

        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);

        controlling = false;
    }

    private float smooth(float cur, float target, float max) {
        float delta = wrap(target - cur);
        if (delta > max) delta = max;
        if (delta < -max) delta = -max;
        return cur + delta;
    }

    private float wrap(float d) {
        d %= 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private boolean shouldAvoid(PlayerEntity p) {
        String name = p.getName().getString();
        boolean friend = Friends.get().isFriend(p);
        boolean inList = list.get().contains(name);

        return switch (mode.get()) {
            case Everyone -> true;
            case Friends -> friend;
            case NonFriends -> !friend;
            case FriendsExceptList -> friend && !inList;
            case NonFriendsExceptList -> !friend && !inList;
            case OnlyList -> inList;
        };
    }
}