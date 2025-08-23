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
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-No-Block-Damage", "Prevents elytra collision damage.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getAbilities().creativeMode) return;
        if (!mc.player.isFallFlying()) return;
        Vec3d velocity = mc.player.getVelocity();
        Vec3d position = mc.player.getPos();
        if (shouldPreventElytraCollision(velocity, position)) {
            Vec3d newVelocity = new Vec3d(velocity.x * 0.8, velocity.y * 0.8, velocity.z * 0.8);
            mc.player.setVelocity(newVelocity);
        }
    }

    private boolean shouldPreventElytraCollision(Vec3d velocity, Vec3d position) {
        if (velocity.length() < 0.5) return false;
        
        Vec3d normalizedVel = velocity.normalize();
        for (double d = 1.0; d <= 3.0; d += 0.5) {
            Vec3d checkPos = position.add(normalizedVel.multiply(d));
            BlockPos blockPos = new BlockPos((int) checkPos.x, (int) checkPos.y, (int) checkPos.z);

            BlockState state = mc.world.getBlockState(blockPos);
            boolean isCollidableBlock = !state.isAir() && state.getBlock().getDefaultState().isSolidBlock(mc.world, blockPos);

            if (isCollidableBlock) {
                return true;
            }
        }
        return false;
    }

}