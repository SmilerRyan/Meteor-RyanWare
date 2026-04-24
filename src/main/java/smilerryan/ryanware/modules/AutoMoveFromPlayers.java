package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.util.List;
import java.util.Random;

public class AutoMoveFromPlayers extends Module {

    public enum Mode {
        Everyone,
        Friends,
        NonFriends,
        FriendsExceptList,
        NonFriendsExceptList,
        OnlyList
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> safeDistance = sg.add(new DoubleSetting.Builder()
        .name("safe-distance").defaultValue(6).min(1).sliderMax(20).build());

    private final Setting<Double> deadzone = sg.add(new DoubleSetting.Builder()
        .name("deadzone").defaultValue(0.05).min(0).sliderMax(1).build());

    private final Setting<Integer> stuckTicks = sg.add(new IntSetting.Builder()
        .name("stuck-ticks").defaultValue(6).min(1).sliderMax(40).build());

    private final Setting<Boolean> sprint = sg.add(new BoolSetting.Builder()
        .name("sprint")
        .description("Sprint while escaping.")
        .defaultValue(true)
        .build());

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode").defaultValue(Mode.Everyone).build());

    private final Setting<List<String>> list = sg.add(new StringListSetting.Builder()
        .name("list").build());

    // --- State ---
    private Vec3d lastPos = null;
    private int stuckCounter = 0;
    private int escapeTicks = 0;
    private float escapeYaw = 0;
    private final Random random = new Random();

    // Track if WE are controlling movement
    private boolean controlling = false;

    public AutoMoveFromPlayers() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Auto-Move-From-Players",
            "Hard-to-trap evasive movement without hijacking controls.");
    }

    @Override
    public void onActivate() {
        lastPos = null;
        stuckCounter = 0;
        escapeTicks = 0;
        controlling = false;
    }

    @Override
    public void onDeactivate() {
        releaseKeys();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        // --- Ignore spectator completely ---
        if (mc.player.isSpectator()) {
            if (controlling) releaseKeys();
            return;
        }

        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        // --- Build avoidance vector ---
        Vec3d totalAway = Vec3d.ZERO;
        int count = 0;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (!shouldAvoid(p)) continue;

            double dist = mc.player.distanceTo(p);
            if (dist > safeDistance.get()) continue;

            Vec3d away = pos.subtract(new Vec3d(p.getX(), p.getY(), p.getZ()));
            if (away.lengthSquared() == 0) continue;

            double weight = 1.0 / Math.max(dist, 0.1);
            totalAway = totalAway.add(away.normalize().multiply(weight));
            count++;
        }

        boolean danger = count > 0 && totalAway.length() >= deadzone.get();

        // --- Stuck detection ---
        if (lastPos != null && danger) {
            double moved = pos.distanceTo(lastPos);

            if (moved < 0.03) stuckCounter++;
            else stuckCounter = 0;

            if (stuckCounter >= stuckTicks.get()) {
                escapeYaw = mc.player.getYaw() + (float)(90 + random.nextInt(180));
                escapeTicks = 12;
                stuckCounter = 0;
            }
        }

        lastPos = pos;

        // --- If no danger: RELEASE control ---
        if (!danger && escapeTicks <= 0) {
            if (controlling) releaseKeys();
            return;
        }

        controlling = true;

        // --- Escape mode ---
        if (escapeTicks > 0) {
            escapeTicks--;

            mc.player.setYaw(escapeYaw);

            mc.options.forwardKey.setPressed(true);
            mc.options.backKey.setPressed(false);

            mc.options.leftKey.setPressed(random.nextBoolean());
            mc.options.rightKey.setPressed(!mc.options.leftKey.isPressed());

            mc.options.jumpKey.setPressed(mc.player.horizontalCollision);

            if (sprint.get()) mc.player.setSprinting(true);

            return;
        }

        // --- Normal evasion ---
        Vec3d dir = totalAway.normalize();

        float targetYaw = (float)(Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90f);
        mc.player.setYaw(smoothYaw(mc.player.getYaw(), targetYaw, 15f));

        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);

        // Strafe oscillation
        boolean left = Math.sin(System.currentTimeMillis() / 200.0) > 0;
        mc.options.leftKey.setPressed(left);
        mc.options.rightKey.setPressed(!left);

        mc.options.jumpKey.setPressed(mc.player.horizontalCollision);

        if (sprint.get()) mc.player.setSprinting(true);
    }

    private void releaseKeys() {
        if (mc.options == null) return;

        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);

        controlling = false;
    }

    private float smoothYaw(float current, float target, float max) {
        float delta = wrap(target - current);
        if (delta > max) delta = max;
        if (delta < -max) delta = -max;
        return current + delta;
    }

    private float wrap(float d) {
        d %= 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private boolean shouldAvoid(PlayerEntity player) {
        String name = player.getName().getString();
        boolean isFriend = Friends.get().isFriend(player);
        boolean inList = list.get().contains(name);

        return switch (mode.get()) {
            case Everyone -> true;
            case Friends -> isFriend;
            case NonFriends -> !isFriend;
            case FriendsExceptList -> isFriend && !inList;
            case NonFriendsExceptList -> !isFriend && !inList;
            case OnlyList -> inList;
        };
    }
}