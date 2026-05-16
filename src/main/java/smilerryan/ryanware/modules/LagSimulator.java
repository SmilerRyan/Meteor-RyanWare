package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

public class LagSimulator extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> sleepMs = sgGeneral.add(new IntSetting.Builder()
        .name("Milliseconds to sleep per Tick")
        .defaultValue(10)
        .min(0)
        .sliderMax(200)
        .build()
    );

    public final Setting<Boolean> enableInScreens = sgGeneral.add(new BoolSetting.Builder()
        .name("Enable in Screens")
        .description("Whether to enable the lag even when you have a screen open (e.g. inventory, chat, etc.)")
        .defaultValue(true)
        .build()
    );

    public LagSimulator() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Lag-Simulator", "Makes your game lag render to help reduce CPU usage.");
    }

    @EventHandler
    private void onRender(TickEvent.Post event) {
        if (!enableInScreens.get() && mc.currentScreen != null) return;

        try {
            Thread.sleep(sleepMs.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}