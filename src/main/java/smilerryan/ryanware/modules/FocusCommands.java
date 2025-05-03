package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;
import smilerryan.ryanware.RyanWare;

public class FocusCommands extends Module {
    private final SettingGroup sgFocus = settings.getDefaultGroup();

    private final Setting<String> onFocusCommands = sgFocus.add(new StringSetting.Builder()
        .name("on-focus-commands")
        .description("Commands to run when window gains focus, separated by ';'")
        .defaultValue("i'm back;m.toggle anti-afk off")
        .build()
    );

    private final Setting<String> onUnfocusCommands = sgFocus.add(new StringSetting.Builder()
        .name("on-unfocus-commands")
        .description("Commands to run when window loses focus, separated by ';'")
        .defaultValue("brb;m.toggle anti-afk on")
        .build()
    );

    private boolean lastFocused = true;

    public FocusCommands() {
        super(RyanWare.CATEGORY, "RyanWare-focus-commands", "Sends commands when window focus changes.");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        boolean focused = GLFW.glfwGetWindowAttrib(mc.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == 1;
        if (focused != lastFocused && mc.player != null) {
            String block = focused ? onFocusCommands.get() : onUnfocusCommands.get();
            for (String cmd : block.split(";")) {
                String trimmed = cmd.trim();
                if (!trimmed.isEmpty()) mc.getNetworkHandler().sendChatMessage(trimmed);
            }
            lastFocused = focused;
        }
    }

}
