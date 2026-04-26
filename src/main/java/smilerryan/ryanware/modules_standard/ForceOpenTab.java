package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

public class ForceOpenTab extends Module {

    public ForceOpenTab() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Force-Open-Tab", "Forces the tab list to stay open.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null || mc.options == null) return;
        mc.options.playerListKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.options == null) return;
        mc.options.playerListKey.setPressed(true);
    }

}
