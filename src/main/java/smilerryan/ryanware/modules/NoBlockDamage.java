package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class NoBlockDamage extends Module {

    public NoBlockDamage() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "No-Block-Damage",
            "Prevents elytra collision damage.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isFallFlying()) return;
        if (mc.player.getAbilities().creativeMode) return;

        Vec3d velocity = mc.player.getVelocity();
        if (velocity.lengthSquared() < 0.15) return;

        if (willCollideNextTick(velocity)) {
            // near-zero speed to fully negate damage
            mc.player.setVelocity(
                velocity.x * 0.1,
                velocity.y * 0.1,
                velocity.z * 0.1
            );
        }
    }

    private boolean willCollideNextTick(Vec3d velocity) {
        Box box = mc.player.getBoundingBox();

        // Expand in actual movement direction (critical)
        Box future = box.expand(velocity.x, velocity.y, velocity.z);

        // THIS is the vanilla collision query
        return mc.world.getBlockCollisions(mc.player, future).iterator().hasNext();
    }
}
