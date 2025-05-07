package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;

public class FullBright extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Store the previous gamma value to restore later
    private double originalGamma = -1;

    public FullBright() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "fullbright", "Forces brightness to full and restores your setting when turned off.");
    }

    @Override
    public void onActivate() {
        if (mc.options != null) {
            // Save current gamma before overriding
            originalGamma = mc.options.getGamma().getValue();
            mc.options.getGamma().setValue(1000.0);  // Force max brightness
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.options != null && originalGamma != -1) {
            mc.options.getGamma().setValue(originalGamma);  // Restore saved brightness
        }
    }
}
