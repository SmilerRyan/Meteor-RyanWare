package smilerryan.ryanware.modules_3;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

public class WiderChat extends Module {
    private static WiderChat INSTANCE;

    public WiderChat() {
        super(
            RyanWare.CATEGORY3,
            RyanWare.modulePrefix3 + "Wider-Chat",
            "Unlimited chat width & chat history."
        );
        INSTANCE = this;
    }

    public static boolean enabled() {
        return INSTANCE != null && INSTANCE.isActive();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!enabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;

        // This only matters when module is ON
        mc.options.getChatWidth().setValue(1.0);
    }
}
