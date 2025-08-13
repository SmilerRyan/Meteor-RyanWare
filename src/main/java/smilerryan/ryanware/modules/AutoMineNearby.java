package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.*;

import smilerryan.ryanware.RyanWare;

public class AutoMineNearby extends Module {

    private final Setting<Double> searchDistance = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("search-distance")
        .description("Distance to search from you and from each mined block.")
        .defaultValue(6)
        .min(1)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> breaksPerTick = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("breaks-per-tick")
        .description("Max blocks to break per tick.")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<List<Block>> blocks = settings.getDefaultGroup().add(new BlockListSetting.Builder()
        .name("target-blocks")
        .description("Blocks to auto mine.")
        .build()
    );

    private final Setting<Boolean> autoToggle = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Disables when no more blocks are nearby.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> flyToReach = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("fly-to-reach")
        .description("Simulated flight (air jumps upward) to reach blocks.")
        .defaultValue(false)
        .build()
    );

    private final Queue<BlockPos> targets = new ArrayDeque<>();
    private final Random rand = new Random();

    public AutoMineNearby() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "AutoMineNearby", "Fast legit auto mines nearby visible selected blocks.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (targets.isEmpty()) {
            BlockPos found = findClosestVisibleBlockNear(mc.player.getBlockPos(), searchDistance.get());
            if (found != null) {
                targets.add(found);
            } else if (autoToggle.get()) {
                toggle();
                return;
            }
        }

        int breaks = 0;
        while (!targets.isEmpty() && breaks < breaksPerTick.get()) {
            BlockPos pos = targets.poll();
            if (pos == null || mc.world.isAir(pos)) continue;

            if (!canSee(pos)) continue;

            lookAt(pos);
            if (isBlockReachable(pos)) {
                mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
                mc.player.swingHand(Hand.MAIN_HAND);
                breaks++;

                BlockPos chain = findClosestVisibleBlockNear(pos, searchDistance.get());
                if (chain != null) targets.add(chain);
            } else {
                moveToBlock(pos);
                targets.add(pos);
                break;
            }
        }

        if (targets.isEmpty() && autoToggle.get()) toggle();
    }

    private BlockPos findClosestVisibleBlockNear(BlockPos center, double searchRange) {
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        int r = (int) Math.ceil(searchRange);
        BlockPos.Mutable mut = new BlockPos.Mutable();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    mut.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!mc.world.isAir(mut) && blocks.get().contains(mc.world.getBlockState(mut).getBlock())) {
                        double dist = mc.player.squaredDistanceTo(mut.getX() + 0.5, mut.getY() + 0.5, mut.getZ() + 0.5);
                        if (dist < closestDist && canSee(mut)) {
                            closest = mut.toImmutable();
                            closestDist = dist;
                        }
                    }
                }
            }
        }

        return closest;
    }

    private boolean canSee(BlockPos pos) {
        Vec3d eye = mc.player.getCameraPosVec(1f);
        Vec3d blockCenter = Vec3d.ofCenter(pos);

        BlockHitResult result = mc.world.raycast(new RaycastContext(
            eye, blockCenter,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(pos);
    }

    private boolean isBlockReachable(BlockPos pos) {
        double reachDistance = 4.5;
        Vec3d eyePos = mc.player.getCameraPosVec(1f);
        Vec3d blockCenter = Vec3d.ofCenter(pos);

        if (eyePos.distanceTo(blockCenter) > reachDistance) return false;

        BlockHitResult result = mc.world.raycast(new RaycastContext(
            eyePos, blockCenter,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(pos);
    }

    private void lookAt(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d eye = mc.player.getCameraPosVec(1f);
        Vec3d delta = center.subtract(eye);
        double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));

        mc.player.setYaw(smooth(mc.player.getYaw(), yaw));
        mc.player.setPitch(smooth(mc.player.getPitch(), pitch));
    }

    private float smooth(float current, float target) {
        float diff = MathHelper.wrapDegrees(target - current);
        float randomness = (float) ((rand.nextDouble() - 0.5) * 0.5);
        return current + diff * 0.2f + randomness;
    }

    private void moveToBlock(BlockPos pos) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d target = Vec3d.ofCenter(pos);
        double dx = target.x - playerPos.x;
        double dz = target.z - playerPos.z;
        double dy = target.y - playerPos.y;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double speed = 0.06 + rand.nextDouble() * 0.02;

        if (distXZ > 0.3) {
            double angle = Math.atan2(dz, dx);
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);
        }

        if (dy > 0.5 && mc.player.getVelocity().y <= 0.01) {
            mc.player.jump();
        }

        if (flyToReach.get() && dy > 1.2) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
        }
    }
}
