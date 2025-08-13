package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
    private final SettingGroup sgFiller = settings.createGroup("Filler");

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

    private final Setting<Boolean> filler = sgFiller.add(new BoolSetting.Builder()
        .name("filler")
        .description("Place blocks after mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Block> fillerBlock = sgFiller.add(new BlockSetting.Builder()
        .name("filler-block")
        .description("The block to place after mining.")
        .defaultValue(Blocks.STONE)
        .visible(() -> filler.get())
        .build()
    );

    private final Setting<Boolean> waitForBlocks = sgFiller.add(new BoolSetting.Builder()
        .name("wait-for-blocks")
        .description("Wait if no filler blocks are available in inventory.")
        .defaultValue(true)
        .visible(() -> filler.get())
        .build()
    );

    private final Setting<Boolean> useFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("use-flight")
        .description("Use flight to reach blocks if available.")
        .defaultValue(true)
        .build()
    );

    private BlockPos startPos;
    private BlockPos endPos;
    private List<BlockPos> blocksToMine;
    private int currentIndex;
    private boolean waitingForBlocks = false;
    private int blockCheckTimer = 0;

    public Excavator() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-Excavator", "Mines blocks between two points efficiently.");
    }

    @Override
    public void onActivate() {
        startPos = null;
        endPos = null;
        blocksToMine = new ArrayList<>();
        currentIndex = 0;
        waitingForBlocks = false;
        blockCheckTimer = 0;
    }

    @Override
    public void onDeactivate() {
        startPos = null;
        endPos = null;
        blocksToMine = null;
        currentIndex = 0;
        waitingForBlocks = false;
        blockCheckTimer = 0;
        // Release all keys when deactivated
        if (mc != null && mc.options != null) {
            mc.options.attackKey.setPressed(false);
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
        }
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
                // Check if the same block was clicked
                if (pos.equals(startPos)) {
                    info("Same block selected for both positions. Resetting...");
                    startPos = null;
                    endPos = null;
                    blocksToMine = null;
                    currentIndex = 0;
                    return;
                }
                endPos = pos;
                info("End position set to " + pos);
                calculateBlocksToMine();
            }
        }

        // Mine blocks if we have a list
        if (blocksToMine != null && !blocksToMine.isEmpty() && currentIndex < blocksToMine.size()) {
            BlockPos pos = blocksToMine.get(currentIndex);
            BlockState state = mc.world.getBlockState(pos);

            // Calculate target position (1 block away from the target block)
            Vec3d playerPos = mc.player.getPos();
            Vec3d blockCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            
            // Calculate direction vector from block to player
            double dx = playerPos.x - blockCenter.x;
            double dy = playerPos.y - blockCenter.y;
            double dz = playerPos.z - blockCenter.z;
            
            // Normalize the direction vector
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0) {
                dx /= length;
                dy /= length;
                dz /= length;
            }
            
            // Calculate ideal position (1 block away from the target)
            Vec3d idealPos = new Vec3d(
                blockCenter.x + dx,
                blockCenter.y + dy,
                blockCenter.z + dz
            );
            
            // Calculate distance to ideal position
            double distToIdeal = Math.sqrt(
                Math.pow(playerPos.x - idealPos.x, 2) +
                Math.pow(playerPos.y - idealPos.y, 2) +
                Math.pow(playerPos.z - idealPos.z, 2)
            );

            // Check if we're too close to the block
            double distToBlock = PlayerUtils.distanceTo(pos);
            if (distToBlock < 1.0) {
                // Move away from the block
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(true);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                
                // Handle vertical movement for flight
                boolean canFly = useFlight.get() && (mc.player.getAbilities().flying || mc.player.getAbilities().allowFlying);
                if (canFly) {
                    if (dy > 0.1) {
                        mc.options.jumpKey.setPressed(true);
                        mc.options.sneakKey.setPressed(false);
                    } else if (dy < -0.1) {
                        mc.options.jumpKey.setPressed(false);
                        mc.options.sneakKey.setPressed(true);
                    } else {
                        mc.options.jumpKey.setPressed(false);
                        mc.options.sneakKey.setPressed(false);
                    }
                }
                
                // Release attack key while moving
                mc.options.attackKey.setPressed(false);
                return;
            }
            
            // Check if we need to move to ideal position
            if (distToIdeal > 0.1) {
                // Calculate movement direction to ideal position
                double moveDx = idealPos.x - playerPos.x;
                double moveDy = idealPos.y - playerPos.y;
                double moveDz = idealPos.z - playerPos.z;
                double moveAngle = Math.atan2(moveDz, moveDx);
                float yaw = (float) (Math.toDegrees(moveAngle) - 90);
                mc.player.setYaw(yaw);

                // Set movement keys based on flight capability
                boolean canFly = useFlight.get() && (mc.player.getAbilities().flying || mc.player.getAbilities().allowFlying);
                
                if (canFly) {
                    // Use flight movement
                    mc.options.forwardKey.setPressed(true);
                    mc.options.backKey.setPressed(false);
                    mc.options.leftKey.setPressed(false);
                    mc.options.rightKey.setPressed(false);
                    
                    // Handle vertical movement for flight
                    if (moveDy > 0.1) {
                        mc.options.jumpKey.setPressed(true);
                        mc.options.sneakKey.setPressed(false);
                    } else if (moveDy < -0.1) {
                        mc.options.jumpKey.setPressed(false);
                        mc.options.sneakKey.setPressed(true);
                    } else {
                        mc.options.jumpKey.setPressed(false);
                        mc.options.sneakKey.setPressed(false);
                    }
                } else {
                    // Use normal movement
                    mc.options.forwardKey.setPressed(true);
                    mc.options.backKey.setPressed(false);
                    mc.options.leftKey.setPressed(false);
                    mc.options.rightKey.setPressed(false);
                    mc.options.jumpKey.setPressed(false);
                    mc.options.sneakKey.setPressed(false);
                }

                // Release attack key while moving
                mc.options.attackKey.setPressed(false);
                return;
            } else {
                // Stop all movement when in position
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                mc.options.jumpKey.setPressed(false);
                mc.options.sneakKey.setPressed(false);
            }

            // Rotate to face the block
            double dxr = blockCenter.x - playerPos.x;
            double dyr = blockCenter.y - playerPos.y;
            double dzr = blockCenter.z - playerPos.z;
            double distXZ = Math.sqrt(dxr * dxr + dzr * dzr);
            float yaw = (float) (Math.toDegrees(Math.atan2(dzr, dxr)) - 90.0F);
            float pitch = (float) -(Math.toDegrees(Math.atan2(dyr, distXZ)));
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);

            // If it's air and filler is enabled, try to place block
            if (state.isAir()) {
                if (filler.get()) {
                    // Check for blocks every 5 ticks
                    if (blockCheckTimer++ >= 5) {
                        blockCheckTimer = 0;
                        Item fillerItem = fillerBlock.get().asItem();
                        FindItemResult result = InvUtils.findInHotbar(item -> item.getItem() == fillerItem);
                        
                        if (result.found()) {
                            waitingForBlocks = false;
                        } else if (waitForBlocks.get()) {
                            waitingForBlocks = true;
                            info("Waiting for filler blocks...");
                            return;
                        } else {
                            currentIndex++;
                            if (currentIndex >= blocksToMine.size()) {
                                info("Finished mining and filling!");
                                toggle();
                            }
                            return;
                        }
                    }

                    if (!waitingForBlocks) {
                        Item fillerItem = fillerBlock.get().asItem();
                        FindItemResult result = InvUtils.findInHotbar(item -> item.getItem() == fillerItem);
                        
                        // Only place block if we're in range
                        if (distToBlock <= range.get()) {
                            // Place the block
                            if (BlockUtils.place(pos, result, rotate.get(), 0)) {
                                waitingForBlocks = false;
                                currentIndex++;
                                if (currentIndex >= blocksToMine.size()) {
                                    info("Finished mining and filling!");
                                    toggle();
                                }
                                return;
                            }
                        }
                    }
                } else {
                    currentIndex++;
                    if (currentIndex >= blocksToMine.size()) {
                        info("Finished processing all blocks!");
                        toggle();
                    }
                    return;
                }
            }
            // If it's not air and not the filler block, mine it
            else if (state.getBlock() != fillerBlock.get()) {
                // Hold attack key to mine the block
                mc.options.attackKey.setPressed(true);
                if (swing.get()) {
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
                // Add completion check after mining
                if (mc.world.getBlockState(pos).isAir()) {
                    currentIndex++;
                    if (currentIndex >= blocksToMine.size()) {
                        info("Finished processing all blocks!");
                        toggle();
                    }
                }
            } else {
                // Skip this block as it's already the filler block
                currentIndex++;
                if (currentIndex >= blocksToMine.size()) {
                    info("Finished processing all blocks!");
                    toggle();
                }
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
                    
                    // Skip unbreakable blocks
                    if (state.getHardness(mc.world, pos) < 0) continue;
                    
                    // If filler is enabled, include air blocks and skip blocks that are already the filler block
                    if (filler.get()) {
                        if (state.isAir() || state.getBlock() != fillerBlock.get()) {
                            blocksToMine.add(pos);
                        }
                    } else {
                        // If filler is disabled, only add non-air blocks
                        if (!state.isAir()) {
                            blocksToMine.add(pos);
                        }
                    }
                }
            }
        }

        if (blocksToMine.isEmpty()) {
            info("No blocks found to process!");
            toggle();
            return;
        }

        info("Found " + blocksToMine.size() + " blocks to process.");
        currentIndex = 0;
    }
} 