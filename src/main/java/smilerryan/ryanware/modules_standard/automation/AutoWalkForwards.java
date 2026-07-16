package smilerryan.ryanware.modules_standard.automation;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

public class AutoWalkForwards extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Automatically holds the jump key.")
        .defaultValue(false)
        .build()
    );

    public AutoWalkForwards() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Auto-Walk-Forwards", "Automatically presses forwardKey for you.");
    }

    @Override
    public void onDeactivate() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        if (autoJump.get()) mc.options.jumpKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;
        mc.options.forwardKey.setPressed(true);
        if (autoJump.get()) {
            mc.options.jumpKey.setPressed(!mc.options.sneakKey.isPressed());
        }
    }

}
