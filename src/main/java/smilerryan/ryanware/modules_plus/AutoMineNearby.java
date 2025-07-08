package smilerryan.ryanware.modules_plus;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;

import java.util.*;

import smilerryan.ryanware.RyanWare;

public class AutoMineNearby extends Module {

    private final Setting<Double> range = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("range")
        .description("How far to search from the crosshair.")
        .defaultValue(6)
        .min(1)
        .sliderMax(12)
        .build()
    );

    private final Setting<List<Block>> blocks = settings.getDefaultGroup().add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks to auto mine.")
        .build()
    );

    private BlockPos targetBlock = null;
    private final Random rand = new Random();

    public AutoMineNearby() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-AutoMineNearby", "Legit auto mines nearby visible selected blocks.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (targetBlock == null || mc.world.isAir(targetBlock)) {
            targetBlock = findClosestVisibleBlock();
        }

        if (targetBlock != null) {
            // Look at block
            lookAt(targetBlock);

            // Move toward block slightly
            approach(targetBlock);

            // Raycast to ensure it's still visible
            if (!canSee(targetBlock)) {
                targetBlock = null;
                return;
            }

            // Legit mining
            if (!mc.interactionManager.isBreakingBlock()) {
                mc.interactionManager.updateBlockBreakingProgress(targetBlock, Direction.UP);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private BlockPos findClosestVisibleBlock() {
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        Vec3d eye = mc.player.getCameraPosVec(1f);
        Vec3d look = mc.player.getRotationVec(1f);
        BlockPos center = BlockPos.ofFloored(eye.add(look.multiply(range.get())));

        int r = range.get().intValue();
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
        float randomness = (float) ((rand.nextDouble() - 0.5) * 1.0); // -0.5 to 0.5
        return current + diff * 0.2f + randomness;
    }

    private void approach(BlockPos pos) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d blockPos = Vec3d.ofCenter(pos);
        double dx = blockPos.x - playerPos.x;
        double dz = blockPos.z - playerPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist > 1.8) {
            double speed = 0.05;
            double angle = Math.atan2(dz, dx);
            mc.player.setVelocity(Math.cos(angle) * speed, mc.player.getVelocity().y, Math.sin(angle) * speed);
        } else {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        }
    }
}
