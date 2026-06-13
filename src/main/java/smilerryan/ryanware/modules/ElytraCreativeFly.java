package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

public class ElytraCreativeFly extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private boolean flying = false;
    private boolean lastFallFlying = false;

    public ElytraCreativeFly() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Elytra-Creative-Fly",
            "Allows creative-style flight while using an elytra. Works well on (1.21.1)."
        );
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().allowFlying = false;
        }

        flying = false;
        lastFallFlying = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        boolean fallFlying = mc.player.isGliding();
        boolean onGround = mc.player.isOnGround();

        if (fallFlying && !lastFallFlying && !flying) {
            flying = true;

            mc.player.getAbilities().allowFlying = true;
            mc.player.getAbilities().flying = true;
        }

        if (onGround && flying) {
            flying = false;

            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().allowFlying = false;
        }

        lastFallFlying = fallFlying;
    }

}
