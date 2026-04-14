package smilerryan.ryanware.modules_3;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

public class PlayerTracers extends Module {

    private final Color tracerColor = new Color(255, 0, 0, 255);

    public PlayerTracers() {
        super(
            RyanWare.CATEGORY3,
            RyanWare.modulePrefix3 + "Player-Tracers",
            "Draws stable tracers to real players."
        );
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        float t = event.tickDelta;

        // Interpolated eye position
        double px = lerp(mc.player.prevX, mc.player.getX(), t);
        double py = lerp(mc.player.prevY, mc.player.getY(), t)
                + mc.player.getEyeHeight(mc.player.getPose());
        double pz = lerp(mc.player.prevZ, mc.player.getZ(), t);

        // Small forward offset keeps start point inside frustum
        Vec3d forward = mc.player.getRotationVec(t).multiply(0.25);
        double sx = px + forward.x;
        double sy = py + forward.y;
        double sz = pz + forward.z;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            String name = stripFormatting(player.getName().getString());
            if (name.isEmpty()
                    || name.startsWith("CIT-")
                    || name.startsWith("[NPC]")
                    || name.startsWith("[BOT]")) continue;

            double tx = lerp(player.prevX, player.getX(), t);
            double ty = lerp(player.prevY, player.getY(), t)
                    + player.getEyeHeight(player.getPose());
            double tz = lerp(player.prevZ, player.getZ(), t);

            event.renderer.line(sx, sy, sz, tx, ty, tz, tracerColor);
        }
    }

    private double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private String stripFormatting(String s) {
        if (s == null || s.isEmpty()) return "";

        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') { i++; continue; }
            out.append(c);
        }
        return out.toString().trim();
    }
}
