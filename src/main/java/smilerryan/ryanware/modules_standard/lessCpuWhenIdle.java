package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import smilerryan.ryanware.RyanWare;

public class lessCpuWhenIdle extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> runWhenFocused = sgGeneral.add(
        new BoolSetting.Builder()
            .name("run-when-focused")
            .description("Call GLFW.glfwWaitEvents() when the window is focused.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> runWhenUnfocused = sgGeneral.add(
        new BoolSetting.Builder()
            .name("run-when-unfocused")
            .description("Call GLFW.glfwWaitEvents() when the window is unfocused.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> delayTicks = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay-milliseconds")
            .description("Minimum milliseconds between calls to GLFW.glfwWaitEvents().")
            .defaultValue(0)
            .min(0)
            .build()
    );

    private long lastCallTime = 0;

    public lessCpuWhenIdle() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Less-CPU-When-Idle", "Reduces CPU load by calling GLFW.glfwWaitEvents() to reduce cpu for input events automatically.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        // Delay between calls
        if (delayTicks.get() > 0) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCallTime < delayTicks.get()) return;
            lastCallTime = currentTime;
        }

        // Call if focused and enabled
        if (mc.isWindowFocused() && runWhenFocused.get()) GLFW.glfwWaitEvents();

        // Call if unfocused and enabled
        if (!mc.isWindowFocused() && runWhenUnfocused.get()) GLFW.glfwWaitEvents();

    }

}