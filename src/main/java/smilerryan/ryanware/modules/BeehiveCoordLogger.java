package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class BeehiveCoordLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The range to check for bees.")
        .defaultValue(32.0)
        .min(0.0)
        .max(128.0)
        .build()
    );

    private final Setting<Boolean> showDistance = sgGeneral.add(new BoolSetting.Builder()
        .name("show-distance")
        .description("Shows the distance to the bee.")
        .defaultValue(true)
        .build()
    );

    private final Set<BlockPos> loggedFlowerPositions = new HashSet<>();

    public BeehiveCoordLogger() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Beehive-Coord-Logger", "Logs coordinates of flowers that bees are targeting.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof BeeEntity bee)) continue;
            if (mc.player.distanceTo(bee) > range.get()) continue;

            BlockPos flowerPos = bee.getFlowerPos();
            if (flowerPos != null && !loggedFlowerPositions.contains(flowerPos)) {
                loggedFlowerPositions.add(flowerPos);
                String message = String.format("Bee found flower at [%d, %d, %d]", 
                    flowerPos.getX(), flowerPos.getY(), flowerPos.getZ());
                
                if (showDistance.get()) {
                    double distance = mc.player.distanceTo(bee);
                    message += String.format(" (%.1f blocks away)", distance);
                }
                
                info(message);
            }
        }
    }

    @Override
    public void onDeactivate() {
        loggedFlowerPositions.clear();
    }
}
