package smilerryan.ryanware.modules_essentials;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

public class PlayerTracers extends Module {

    public PlayerTracers() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "PlayerTracers",
                "Draws a line to all players.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Color tracerColor = new Color(1.0f, 0.0f, 0.0f, 1.0f); // Red RGBA

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isInvisible()) continue;

            // Start at center of your player instead of eyes
            Vec3d start = mc.player.getPos().add(0, mc.player.getHeight() / 2, 0);
            Vec3d end = player.getPos().add(0, player.getEyeHeight(player.getPose()), 0);

            // Draw line to other player
            event.renderer.line(
                    start.x, start.y, start.z,
                    end.x, end.y, end.z,
                    tracerColor
            );
        }
    }
}
