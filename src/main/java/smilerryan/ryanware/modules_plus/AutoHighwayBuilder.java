package smilerryan.ryanware.modules_plus;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;

import smilerryan.ryanware.RyanWare;

public class AutoHighwayBuilder extends Module {

    private final Setting<Integer> width = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("width")
        .description("Width of the highway.")
        .defaultValue(3)
        .min(1)
        .sliderMax(7)
        .build()
    );

    private final Setting<Block> buildBlock = settings.getDefaultGroup().add(new BlockSetting.Builder()
        .name("build-block")
        .description("Block to use for building the highway.")
        .defaultValue(Blocks.COBBLESTONE)
        .build()
    );

    private final Setting<Boolean> buildWalls = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("build-walls")
        .description("Add one block high walls on both sides.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> speed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("speed")
        .description("Movement speed.")
        .defaultValue(0.15)
        .min(0.05)
        .max(0.4)
        .sliderMax(0.4)
        .build()
    );

    // Locked cardinal yaw (N/E/S/W)
    private float lockedYaw;

    public AutoHighwayBuilder() {
        super(RyanWare.CATEGORY, "auto-highway-builder", "Auto walks forward locked to cardinal direction with building.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        lockedYaw = getClosestCardinalYaw(mc.player.getYaw());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Lock yaw to cardinal direction
        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(0); // Optional: lock pitch straight ahead

        // Force forward movement only (no strafe/back)
        forceWalkForward();

        scaffoldHighway();
    }

    private void forceWalkForward() {
        // Calculate forward velocity vector based on locked yaw
        double rad = Math.toRadians(lockedYaw);
        double vx = -Math.sin(rad) * speed.get();
        double vz = Math.cos(rad) * speed.get();

        // Set velocity, preserve vertical velocity (falling/jumping)
        mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);

        // Force forward key pressed, disable left/right/back keys
        mc.options.forwardKey.setPressed(true);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.backKey.setPressed(false);
    }

    private void scaffoldHighway() {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3i sideDir = getSideDirection();
        int halfWidth = width.get() / 2;

        for (int w = -halfWidth; w <= halfWidth; w++) {
            BlockPos basePos = playerPos.add(sideDir.getX() * w, 0, sideDir.getZ() * w);

            // Floor block below player
            BlockPos floorPos = basePos.add(0, -1, 0);

            if (shouldPlaceBlock(floorPos)) placeBlock(floorPos);
            else if (shouldMineBlock(floorPos)) mineBlock(floorPos);

            // Clear head level blocks
            BlockPos clearPos1 = basePos;
            BlockPos clearPos2 = basePos.add(0, 1, 0);

            if (shouldMineBlock(clearPos1)) mineBlock(clearPos1);
            if (shouldMineBlock(clearPos2)) mineBlock(clearPos2);

            // Build walls if enabled (one block high)
            if (buildWalls.get() && (w == -halfWidth || w == halfWidth)) {
                BlockPos wallPos = basePos;
                if (shouldPlaceBlock(wallPos)) placeBlock(wallPos);
                else if (shouldMineBlock(wallPos)) mineBlock(wallPos);
            }
        }
    }

    private Vec3i getSideDirection() {
        Vec3i forwardDir = getCardinalDirection(lockedYaw);

        // Perpendicular for width axis
        if (forwardDir.getX() != 0) return new Vec3i(0, 0, 1); // East/West -> width N/S
        else return new Vec3i(1, 0, 0); // North/South -> width E/W
    }

    private Vec3i getCardinalDirection(float yaw) {
        yaw = MathHelper.wrapDegrees(yaw);
        if (yaw >= -45 && yaw < 45) return new Vec3i(0, 0, 1);    // South
        else if (yaw >= 45 && yaw < 135) return new Vec3i(-1, 0, 0);  // West
        else if (yaw >= 135 || yaw < -135) return new Vec3i(0, 0, -1); // North
        else return new Vec3i(1, 0, 0); // East
    }

    private float getClosestCardinalYaw(float yaw) {
        yaw = MathHelper.wrapDegrees(yaw);
        if (yaw >= -45 && yaw < 45) return 0;    // South (Minecraft yaw=0)
        else if (yaw >= 45 && yaw < 135) return 90;  // West
        else if (yaw >= 135 || yaw < -135) return 180; // North
        else return -90; // East
    }

    private boolean shouldPlaceBlock(BlockPos pos) {
        if (mc.world.isAir(pos)) return true;
        Block currentBlock = mc.world.getBlockState(pos).getBlock();
        return currentBlock != buildBlock.get();
    }

    private boolean shouldMineBlock(BlockPos pos) {
        if (mc.world.isAir(pos)) return false;
        Block currentBlock = mc.world.getBlockState(pos).getBlock();
        return currentBlock != buildBlock.get() && canReachBlock(pos);
    }

    private boolean placeBlock(BlockPos pos) {
        if (!canReachBlock(pos)) return false;

        int slot = findBuildBlockSlot();
        if (slot == -1) return false;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty()) return false;

        int previousSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        boolean placed = false;
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = pos.offset(dir);
            if (!mc.world.isAir(adjacentPos)) {
                BlockHitResult hitResult = new BlockHitResult(
                    Vec3d.ofCenter(pos).add(Vec3d.of(dir.getVector()).multiply(0.5)),
                    dir.getOpposite(),
                    adjacentPos,
                    false
                );

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
                mc.player.swingHand(Hand.MAIN_HAND);
                placed = true;
                break;
            }
        }

        mc.player.getInventory().selectedSlot = previousSlot;
        return placed;
    }

    private void mineBlock(BlockPos pos) {
        if (!canReachBlock(pos)) return;

        mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findBuildBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem) {
                BlockItem blockItem = (BlockItem) stack.getItem();
                if (blockItem.getBlock() == buildBlock.get()) return i;
            }
        }
        return -1;
    }

    private boolean canReachBlock(BlockPos pos) {
        double reachDistance = 4.5;
        Vec3d eyePos = mc.player.getCameraPosVec(1f);
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        return eyePos.distanceTo(blockCenter) <= reachDistance;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            mc.options.forwardKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.backKey.setPressed(false);
        }
    }
}
