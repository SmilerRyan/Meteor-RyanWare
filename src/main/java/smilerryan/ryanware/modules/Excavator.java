package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class Excavator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum range to mine blocks.")
        .defaultValue(4.5)
        .min(1)
        .max(6)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Automatically rotate towards blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing hand when mining.")
        .defaultValue(true)
        .build()
    );

    private BlockPos startPos;
    private BlockPos endPos;
    private List<BlockPos> blocksToMine;
    private int currentIndex;

    public Excavator() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "excavator", "Mines blocks between two points efficiently.");
    }

    @Override
    public void onActivate() {
        startPos = null;
        endPos = null;
        blocksToMine = new ArrayList<>();
        currentIndex = 0;
    }

    @Override
    public void onDeactivate() {
        startPos = null;
        endPos = null;
        blocksToMine = null;
        currentIndex = 0;
        // Release attack key when deactivated
        if (mc != null && mc.options != null) mc.options.attackKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Handle block selection
        if (mc.options.attackKey.isPressed() && mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            
            if (startPos == null) {
                startPos = pos;
                info("Start position set to " + pos);
            } else if (endPos == null) {
                endPos = pos;
                info("End position set to " + pos);
                calculateBlocksToMine();
            }
        }

        // Mine blocks if we have a list
        if (blocksToMine != null && !blocksToMine.isEmpty() && currentIndex < blocksToMine.size()) {
            BlockPos pos = blocksToMine.get(currentIndex);

            // Skip air blocks (already mined)
            if (mc.world.getBlockState(pos).isAir()) {
                currentIndex++;
                // Release attack key if block is broken
                mc.options.attackKey.setPressed(false);
                // Check if we're done
                if (currentIndex >= blocksToMine.size()) {
                    info("Finished mining all blocks!");
                    toggle();
                }
                return;
            }

            // Check if we can reach the block
            double dist = PlayerUtils.distanceTo(pos);
            if (dist > range.get()) {
                // Walk toward the block
                Vec3d playerPos = mc.player.getPos();
                double dx = pos.getX() + 0.5 - playerPos.x;
                double dz = pos.getZ() + 0.5 - playerPos.z;
                double angle = Math.atan2(dz, dx);
                float yaw = (float) (Math.toDegrees(angle) - 90);
                mc.player.setYaw(yaw);

                // Set movement keys
                mc.options.forwardKey.setPressed(true);
                mc.options.backKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                // Release attack key while moving
                mc.options.attackKey.setPressed(false);
                return;
            } else {
                // Stop moving
                mc.options.forwardKey.setPressed(false);
            }

            // Rotate to face the block
            Vec3d playerPos = mc.player.getCameraPosVec(1.0F);
            Vec3d blockCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double dxr = blockCenter.x - playerPos.x;
            double dyr = blockCenter.y - playerPos.y;
            double dzr = blockCenter.z - playerPos.z;
            double distXZ = Math.sqrt(dxr * dxr + dzr * dzr);
            float yaw = (float) (Math.toDegrees(Math.atan2(dzr, dxr)) - 90.0F);
            float pitch = (float) -(Math.toDegrees(Math.atan2(dyr, distXZ)));
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);

            // Hold attack key to mine the block
            mc.options.attackKey.setPressed(true);
            if (swing.get()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void calculateBlocksToMine() {
        if (startPos == null || endPos == null) return;

        blocksToMine = new ArrayList<>();
        
        // Get the min and max coordinates
        int minX = Math.min(startPos.getX(), endPos.getX());
        int maxX = Math.max(startPos.getX(), endPos.getX());
        int minY = Math.min(startPos.getY(), endPos.getY());
        int maxY = Math.max(startPos.getY(), endPos.getY());
        int minZ = Math.min(startPos.getZ(), endPos.getZ());
        int maxZ = Math.max(startPos.getZ(), endPos.getZ());

        // Add blocks from top to bottom
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    
                    // Skip air and unbreakable blocks
                    if (!state.isAir() && state.getHardness(mc.world, pos) >= 0) {
                        blocksToMine.add(pos);
                    }
                }
            }
        }

        info("Found " + blocksToMine.size() + " blocks to mine.");
        currentIndex = 0;
    }
} 