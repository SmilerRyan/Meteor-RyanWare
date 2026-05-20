package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

public class AntiClimb extends Module {

    public AntiClimb() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Anti-Climb",
            "Prevents jumping or spider climbing against walls."
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        var box = mc.player.getBoundingBox().expand(0.67, 0.0, 0.67);

        boolean nearWall = mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();

        if (!nearWall) return;

        if (mc.player.getVelocity().y > 0) {
            mc.player.setVelocity(
                mc.player.getVelocity().x,
                -999,
                mc.player.getVelocity().z
            );
        }
    }
}