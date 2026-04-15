package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DeathScreen;
import smilerryan.ryanware.RyanWare;

public class AutoRespawn extends Module {

    public AutoRespawn() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Auto-Respawn", "Automatically requests a respawns if the current screen is a DeathScreen.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (mc.currentScreen instanceof DeathScreen) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }
    }
}
