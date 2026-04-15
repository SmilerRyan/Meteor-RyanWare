package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

public class AutoWalkForwards extends Module {

    public AutoWalkForwards() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Auto-Walk-Forwards", "Automatically presses forwardKey for you.");
    }

    @Override
    public void onDeactivate() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;
        mc.options.forwardKey.setPressed(true);
    }

}
