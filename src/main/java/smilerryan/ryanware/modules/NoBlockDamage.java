package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

public class NoBlockDamage extends Module {

    public NoBlockDamage() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "No-Block-Damage", "Prevents all types of block-related damage.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getAbilities().creativeMode) return;
        
        Vec3d velocity = mc.player.getVelocity();
        Vec3d position = mc.player.getPos();
        
        if (shouldPreventFallDamage() || 
            shouldPreventElytraCollision(velocity, position) || 
            shouldPreventSuffocation(position)) {
            reduceVelocity(velocity);
        }
    }

    private boolean shouldPreventFallDamage() {
        if (mc.player.isOnGround() || mc.player.getAbilities().flying) return false;
        if (mc.player.isTouchingWater() || mc.player.isInLava()) return false;
        if (mc.player.getVelocity().y > -0.5) return false;
        return isApproachingGround();
    }

    private boolean shouldPreventElytraCollision(Vec3d velocity, Vec3d position) {
        if (!mc.player.isFallFlying()) return false;
        if (velocity.length() < 0.5) return false;
        
        Vec3d normalizedVel = velocity.normalize();
        for (double d = 0.5; d <= 2.0; d += 0.5) {
            Vec3d checkPos = position.add(normalizedVel.multiply(d));
            BlockPos blockPos = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z);
            if (isCollidableBlock(blockPos)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldPreventSuffocation(Vec3d position) {
        BlockPos playerPos = new BlockPos((int) position.x, (int) position.y, (int) position.z);
        BlockPos headPos = new BlockPos((int) position.x, (int) (position.y + 1.8), (int) position.z);
        return isCollidableBlock(playerPos) || isCollidableBlock(headPos);
    }

    private boolean isApproachingGround() {
        Vec3d position = mc.player.getPos();
        int playerX = (int) position.x;
        int playerZ = (int) position.z;
        int minY = Math.max(mc.world.getBottomY(), (int) position.y - 3);
        
        for (int y = (int) position.y; y >= minY; y--) {
            BlockPos pos = new BlockPos(playerX, y, playerZ);
            if (isCollidableBlock(pos)) {
                double distance = position.y - (y + 1);
                return distance <= 2.5;
            }
        }
        return false;
    }

    private boolean isCollidableBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && state.getBlock().getDefaultState().isSolidBlock(mc.world, pos);
    }

    private void reduceVelocity(Vec3d currentVelocity) {
        Vec3d newVelocity = new Vec3d(
            currentVelocity.x * 0.7,
            Math.max(currentVelocity.y * 0.7, -0.2),
            currentVelocity.z * 0.7
        );
        mc.player.setVelocity(newVelocity);
    }
}