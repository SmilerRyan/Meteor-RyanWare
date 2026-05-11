package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import smilerryan.ryanware.RyanWare;

public class AutoItemFrameDupe extends Module {

    private boolean tickToggle = false;

    public AutoItemFrameDupe() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Auto-Item-Frame-Dupe",
            "Automatically interacts with item frames you look at."
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        tickToggle = !tickToggle;
        if (!tickToggle) return;

        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;

        if (!(((EntityHitResult) mc.crosshairTarget).getEntity() instanceof ItemFrameEntity frame)) return;

        if (frame.getHeldItemStack().isEmpty()) {
            mc.interactionManager.interactEntityAtLocation(
                mc.player,
                frame,
                (EntityHitResult) mc.crosshairTarget,
                Hand.MAIN_HAND
            );
        } else {
            mc.interactionManager.attackEntity(mc.player, frame);
        }
    }

}