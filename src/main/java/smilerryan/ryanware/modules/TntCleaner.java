package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import smilerryan.ryanware.RyanWare;

import java.util.*;

public class TntCleaner extends Module {
    private final Queue<BlockPos> targets = new ArrayDeque<>();
    private final Random rand = new Random();
    private Vec3d returnPos = null;
    private Vec3d lastPlayerPos = null;

    public TntCleaner() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-TntCleaner", "Mines nearby TNT blocks and returns to original position.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (targets.isEmpty()) {
            BlockPos found = findClosestTnt(mc.player.getBlockPos(), 6);
            if (found != null) {
                if (returnPos == null) returnPos = mc.player.getPos(); // Save once
                targets.add(found);
                lastPlayerPos = mc.player.getPos();
            }
        }

        if (!targets.isEmpty()) {
            BlockPos pos = targets.peek();
            if (pos != null && !mc.world.isAir(pos)) {
                lookAt(pos);

                // Teleport closer if stuck
                if (mc.player.getPos().squaredDistanceTo(lastPlayerPos) < 0.001) {
                    teleportCloser(pos);
                }
                lastPlayerPos = mc.player.getPos();

                if (isBlockReachable(pos)) {
                    mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    targets.poll(); // remove after mining

                    BlockPos chain = findClosestTnt(pos, 6);
                    if (chain != null) targets.add(chain);
                } else {
                    moveToBlock(pos);
                }
            } else {
                targets.poll(); // remove invalid/air blocks
            }
            return;
        }

        // Return to original position with teleport fallback
        if (returnPos != null) {
            if (mc.player.getPos().squaredDistanceTo(lastPlayerPos) < 0.001) {
                teleportCloser(returnPos);
            }
            lastPlayerPos = mc.player.getPos();

            moveToVec(returnPos);
            if (mc.player.getPos().squaredDistanceTo(returnPos) < 0.25) {
                returnPos = null; // Auto-reset once close enough
            }
        }
    }

// Overloaded teleportCloser for BlockPos
private void teleportCloser(BlockPos pos) {
    Vec3d target = Vec3d.ofCenter(pos).subtract(0, 1, 0);
    mc.player.setPos(target.x, target.y, target.z);
}

// Overloaded teleportCloser for Vec3d
private void teleportCloser(Vec3d pos) {
    Vec3d target = pos.subtract(0, 1, 0);
    mc.player.setPos(target.x, target.y, target.z);
}


    private BlockPos findClosestTnt(BlockPos center, double range) {
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;
        int r = (int) Math.ceil(range);
        BlockPos.Mutable mut = new BlockPos.Mutable();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    mut.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (mc.world.getBlockState(mut).getBlock() == Blocks.TNT) {
                        double dist = mc.player.squaredDistanceTo(mut.getX() + 0.5, mut.getY() + 0.5, mut.getZ() + 0.5);
                        if (dist < closestDist) {
                            closest = mut.toImmutable();
                            closestDist = dist;
                        }
                    }
                }
            }
        }

        return closest;
    }

    private boolean isBlockReachable(BlockPos pos) {
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 5.5 * 5.5;
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
        moveToVec(Vec3d.ofCenter(pos));
    }

    private void moveToVec(Vec3d target) {
        Vec3d playerPos = mc.player.getPos();
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

        // Always allow flight upward
        if (dy > 1.2) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
        }
    }

    @Override
    public void onDeactivate() {
        returnPos = null;
        targets.clear();
        lastPlayerPos = null;
    }
}
