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

public class LessCPUWhenUnfocused extends Module {

    public LessCPUWhenUnfocused() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Less-CPU-When-Unfocused",
            "Reduces CPU usage when unfocused using GLFW event blocking.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!mc.isWindowFocused()) GLFW.glfwWaitEvents();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.isWindowFocused()) GLFW.glfwWaitEvents();
    }
}