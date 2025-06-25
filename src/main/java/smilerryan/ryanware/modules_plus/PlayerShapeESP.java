package smilerryan.ryanware.modules_plus;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class PlayerShapeESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Boolean> onlyOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("only-own")
        .description("Only render shapes for your own player.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> ballSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("sphere-size")
        .description("Size of the spheres.")
        .defaultValue(0.1)
        .min(0.1)
        .max(0.5)
        .sliderMax(0.5)
        .build()
    );

    private final Setting<Double> shapeSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("shape-size")
        .description("Size of the main shape.")
        .defaultValue(1.5)
        .min(0.1)
        .max(3.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Double> friendSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("friend-size")
        .description("Size for friends.")
        .defaultValue(1.5)
        .min(0.1)
        .max(3.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Double> enemySize = sgGeneral.add(new DoubleSetting.Builder()
        .name("enemy-size")
        .description("Size for enemies.")
        .defaultValue(0.5)
        .min(0.1)
        .max(3.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Integer> gradation = sgGeneral.add(new IntSetting.Builder()
        .name("gradation")
        .description("Detail level of the shapes.")
        .defaultValue(30)
        .min(20)
        .max(100)
        .sliderMax(100)
        .build()
    );

    private final Setting<SettingColor> shapeColor = sgColors.add(new ColorSetting.Builder()
        .name("shape-color")
        .description("Color of the main shape.")
        .defaultValue(new SettingColor(231, 180, 122, 255))
        .build()
    );

    private final Setting<SettingColor> headColor = sgColors.add(new ColorSetting.Builder()
        .name("head-color")
        .description("Color of the head sphere.")
        .defaultValue(new SettingColor(240, 50, 180, 255))
        .build()
    );

    public PlayerShapeESP() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "player-shape-esp", "Renders 3D shapes around players.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (onlyOwn.get() && player != mc.player) continue;

            double size = getPlayerSize(player);
            Vec3d base = getEntityPosition(player, event.tickDelta);
            Vec3d forward = base.add(0.0, player.getHeight() / 2.4, 0.0)
                .add(Vec3d.fromPolar(0.0f, player.getYaw()).multiply(0.1));
            Vec3d left = forward.add(Vec3d.fromPolar(0.0f, player.getYaw() - 90.0f).multiply(ballSize.get()));
            Vec3d right = forward.add(Vec3d.fromPolar(0.0f, player.getYaw() + 90.0f).multiply(ballSize.get()));

            // Draw spheres
            drawSphere(player, ballSize.get(), gradation.get(), left, shapeColor.get(), 0, event.tickDelta, event);
            drawSphere(player, ballSize.get(), gradation.get(), right, shapeColor.get(), 0, event.tickDelta, event);
            
            // Draw main shape
            drawMainShape(player, size, forward, event.tickDelta, event);
        }
    }

    private double getPlayerSize(PlayerEntity player) {
        if (Friends.get().isFriend(player)) {
            return friendSize.get();
        } else if (player != mc.player) {
            return enemySize.get();
        } else {
            return shapeSize.get();
        }
    }

    private Vec3d getEntityPosition(Entity entity, float tickDelta) {
        double x = entity.prevX + (entity.getX() - entity.prevX) * tickDelta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * tickDelta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * tickDelta;
        return new Vec3d(x, y, z);
    }

    private void drawSphere(PlayerEntity player, double radius, int gradation, Vec3d pos, SettingColor color, int stage, float tickDelta, Render3DEvent event) {
        for (float alpha = 0.0f; alpha < Math.PI; alpha += Math.PI / gradation) {
            for (float beta = 0.0f; beta < 2 * Math.PI; beta += Math.PI / gradation) {
                double x1 = pos.x + radius * Math.cos(beta) * Math.sin(alpha);
                double y1 = pos.y + radius * Math.sin(beta) * Math.sin(alpha);
                double z1 = pos.z + radius * Math.cos(alpha);

                double sin = Math.sin(alpha + Math.PI / gradation);
                double x2 = pos.x + radius * Math.cos(beta) * sin;
                double y2 = pos.y + radius * Math.sin(beta) * sin;
                double z2 = pos.z + radius * Math.cos(alpha + Math.PI / gradation);

                Vec3d base = getEntityPosition(player, tickDelta);
                Vec3d forward = base.add(0.0, player.getHeight() / 2.4, 0.0)
                    .add(Vec3d.fromPolar(0.0f, player.getYaw()).multiply(0.1));
                Vec3d vec3d = new Vec3d(x1, y1, z1);

                switch (stage) {
                    case 1:
                        if (!vec3d.isInRange(forward, 0.145)) continue;
                        break;
                    case 2:
                        double size = getPlayerSize(player);
                        if (vec3d.isInRange(forward, size + 0.095)) continue;
                        break;
                }

                event.renderer.line(x1, y1, z1, x2, y2, z2, color);
            }
        }
    }

    private void drawMainShape(PlayerEntity player, double size, Vec3d start, float tickDelta, Render3DEvent event) {
        Vec3d copy = start;
        start = start.add(Vec3d.fromPolar(0.0f, player.getYaw()).multiply(0.1));
        Vec3d end = start.add(Vec3d.fromPolar(0.0f, player.getYaw()).multiply(size));
        
        List<Vec3d> vecs = getVec3ds(start, 0.1);
        for (Vec3d vec3d : vecs) {
            if (vec3d.isInRange(copy, 0.145)) {
                if (!vec3d.isInRange(copy, 0.135)) {
                    Vec3d pos = vec3d.add(Vec3d.fromPolar(0.0f, player.getYaw()).multiply(size));
                    event.renderer.line(vec3d.x, vec3d.y, vec3d.z, pos.x, pos.y, pos.z, shapeColor.get());
                }
            }
        }

        drawSphere(player, 0.1, gradation.get(), start, shapeColor.get(), 1, tickDelta, event);
        drawSphere(player, 0.1, gradation.get(), end, headColor.get(), 2, tickDelta, event);
    }

    private List<Vec3d> getVec3ds(Vec3d vec3d, double radius) {
        List<Vec3d> vec3ds = new ArrayList<>();
        
        for (float alpha = 0.0f; alpha < Math.PI; alpha += Math.PI / gradation.get()) {
            for (float beta = 0.0f; beta < 2 * Math.PI; beta += Math.PI / gradation.get()) {
                double x1 = vec3d.x + radius * Math.cos(beta) * Math.sin(alpha);
                double y1 = vec3d.y + radius * Math.sin(beta) * Math.sin(alpha);
                double z1 = vec3d.z + radius * Math.cos(alpha);
                Vec3d vec = new Vec3d(x1, y1, z1);
                vec3ds.add(vec);
            }
        }
        
        return vec3ds;
    }
}