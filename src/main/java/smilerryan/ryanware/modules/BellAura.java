package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class BellAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Boolean> lookAtBell = sgGeneral.add(new BoolSetting.Builder()
        .name("look-at-bell")
        .description("Whether to look at the bell before ringing it.")
        .defaultValue(true)
        .build()
    );
    
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Whether to ring closest bell or all bells in range.")
        .defaultValue(Mode.Closest)
        .build()
    );

    public enum Mode {
        Closest,
        All
    }

    public BellAura() {
        super(smilerryan.ryanware.RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "bell-aura", "Automatically looks at and clicks the nearest bell.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Find all bells in range
        List<BlockPos> bellsInRange = new ArrayList<>();
        int radius = 6;
        BlockPos playerPos = mc.player.getBlockPos();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    if (mc.world.getBlockState(checkPos).getBlock() == Blocks.BELL) {
                        bellsInRange.add(checkPos);
                    }
                }
            }
        }

        if (bellsInRange.isEmpty()) return;

        // Handle different modes
        if (mode.get() == Mode.Closest) {
            // Find closest bell
            BlockPos closestBell = null;
            double closestDist = Double.MAX_VALUE;
            for (BlockPos bell : bellsInRange) {
                double dist = mc.player.squaredDistanceTo(bell.getX() + 0.5, bell.getY() + 0.5, bell.getZ() + 0.5);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestBell = bell;
                }
            }
            ringBell(closestBell);
        } else {
            // Ring all bells
            for (BlockPos bell : bellsInRange) {
                ringBell(bell);
            }
        }
    }

    private void ringBell(BlockPos bellPos) {
        if (lookAtBell.get()) {
            Vec3d lookPos = new Vec3d(bellPos.getX() + 0.5, bellPos.getY() + 0.5, bellPos.getZ() + 0.5);
            Vec3d eyes = mc.player.getEyePos();
            double dx = lookPos.x - eyes.x;
            double dy = lookPos.y - eyes.y;
            double dz = lookPos.z - eyes.z;
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }

        Direction direction = getDirectionFromPlayer(bellPos);
        BlockHitResult hitResult = new BlockHitResult(
            new Vec3d(bellPos.getX() + 0.5, bellPos.getY() + 0.5, bellPos.getZ() + 0.5),
            direction,
            bellPos,
            false
        );
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private Direction getDirectionFromPlayer(BlockPos pos) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d blockPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        
        double dx = playerPos.x - blockPos.x;
        double dz = playerPos.z - blockPos.z;
        
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        } else {
            return dz > 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }
}