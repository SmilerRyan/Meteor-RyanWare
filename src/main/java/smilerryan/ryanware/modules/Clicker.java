package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

public class Clicker extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public enum Mode {
        NOTHING,
        HOLD,
        CLICK
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Mode> leftMode = sg.add(
        new EnumSetting.Builder<Mode>()
            .name("left")
            .defaultValue(Mode.NOTHING)
            .build()
    );

    private final Setting<Integer> leftDelay = sg.add(
        new IntSetting.Builder()
            .name("left-delay-ticks")
            .defaultValue(5)
            .min(1)
            .visible(() -> leftMode.get() == Mode.CLICK)
            .build()
    );

    private final Setting<Mode> rightMode = sg.add(
        new EnumSetting.Builder<Mode>()
            .name("right")
            .defaultValue(Mode.NOTHING)
            .build()
    );

    private final Setting<Integer> rightDelay = sg.add(
        new IntSetting.Builder()
            .name("right-delay-ticks")
            .defaultValue(5)
            .min(1)
            .visible(() -> rightMode.get() == Mode.CLICK)
            .build()
    );

    private int leftTicks = 0;
    private int rightTicks = 0;

    public Clicker() {
        super(
            RyanWare.CATEGORY,
            RyanWare.modulePrefix_extras + "Clicker",
            "Simple left/right clicker using tick delays."
        );
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // LEFT CLICK
        switch (leftMode.get()) {
            case NOTHING -> mc.options.attackKey.setPressed(false);

            case HOLD -> mc.options.attackKey.setPressed(true);

            case CLICK -> {
                mc.options.attackKey.setPressed(false);
                if (leftTicks-- <= 0) {
                    mc.options.attackKey.setPressed(true);
                    leftTicks = leftDelay.get();
                }
            }
        }

        // RIGHT CLICK
        switch (rightMode.get()) {
            case NOTHING -> mc.options.useKey.setPressed(false);

            case HOLD -> mc.options.useKey.setPressed(true);

            case CLICK -> {
                mc.options.useKey.setPressed(false);
                if (rightTicks-- <= 0) {
                    mc.options.useKey.setPressed(true);
                    rightTicks = rightDelay.get();
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        leftTicks = 0;
        rightTicks = 0;
    }
}
