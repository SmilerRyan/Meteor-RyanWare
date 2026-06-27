package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;

import smilerryan.ryanware.RyanWare;

public class AutoHighwayBuilder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
        .name("width")
        .description("Width of the highway.")
        .defaultValue(3)
        .min(1)
        .sliderMax(7)
        .build()
    );

    private final Setting<Block> buildBlock = sgGeneral.add(new BlockSetting.Builder()
        .name("build-block")
        .description("Block to use for building the highway.")
        .defaultValue(Blocks.COBBLESTONE)
        .build()
    );

    private final Setting<Boolean> buildWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("build-walls")
        .description("Add one block high walls on both sides.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoClear = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-clear")
        .description("Automatically clears blocks in front of the highway before moving.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Movement speed.")
        .defaultValue(0.15)
        .min(0.05)
        .max(0.4)
        .sliderMax(0.4)
        .build()
    );

    private float lockedYaw;

    private boolean actionLock = false;

    private Vec3d lastGoodPos = null;
    private int stuckTicks = 0;
    private boolean disabledByStuck = false;

    public AutoHighwayBuilder() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "auto-highway-builder",
            "Auto builds a straight highway with clearing and placement.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        lockedYaw = getClosestCardinalYaw(mc.player.getYaw());
        lastGoodPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        stuckTicks = 0;
        disabledByStuck = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        Vec3d current = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double moved = lastGoodPos == null ? 0 : current.distanceTo(lastGoodPos);

        if (moved < 0.02) stuckTicks++;
        else {
            stuckTicks = 0;
            lastGoodPos = current;
        }

        if (stuckTicks > 10) {
            disabledByStuck = true;

            mc.options.forwardKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.backKey.setPressed(false);

            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return;
        }

        if (disabledByStuck && moved > 0.3) {
            disabledByStuck = false;
            stuckTicks = 0;
        }

        if (disabledByStuck) return;

        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(0);

        actionLock = false;

        if (autoClear.get()) clearFront();

        if (!actionLock) forceWalkForward();

        scaffoldHighway();
    }

    private void forceWalkForward() {
        double rad = Math.toRadians(lockedYaw);
        double vx = -Math.sin(rad) * speed.get();
        double vz = Math.cos(rad) * speed.get();

        mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);

        mc.options.forwardKey.setPressed(true);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.backKey.setPressed(false);
    }

    private void clearFront() {
        BlockPos playerPos = mc.player.getBlockPos();

        Vec3i forward = getCardinalDirection(lockedYaw);
        Vec3i side = getSideDirection();

        int halfWidth = width.get() / 2;

        for (int f = 1; f <= 4; f++) {
            for (int h = 0; h <= 2; h++) {
                BlockPos pos = playerPos.add(forward.getX() * f, h, forward.getZ() * f);

                if (shouldMineBlock(pos)) {
                    mineBlock(pos);
                    actionLock = true;
                    return;
                }
            }
        }

        for (int f = 0; f <= 2; f++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {

                BlockPos base = playerPos
                    .add(forward.getX() * f, 0, forward.getZ() * f)
                    .add(side.getX() * w, 0, side.getZ() * w);

                BlockPos floor = base.down();

                if (shouldMineBlock(floor)) {
                    mineBlock(floor);
                    actionLock = true;
                    return;
                }

                if (shouldMineBlock(base)) {
                    mineBlock(base);
                    actionLock = true;
                    return;
                }

                if (shouldMineBlock(base.up())) {
                    mineBlock(base.up());
                    actionLock = true;
                    return;
                }
            }
        }

        if (buildWalls.get()) {
            for (int f = 0; f <= 3; f++) {
                for (int w = -halfWidth; w <= halfWidth; w++) {

                    if (w != -halfWidth && w != halfWidth) continue;

                    BlockPos base = playerPos
                        .add(forward.getX() * f, 0, forward.getZ() * f)
                        .add(side.getX() * w, 0, side.getZ() * w);

                    for (int h = 0; h <= 2; h++) {
                        BlockPos wall = base.add(0, h, 0);

                        if (shouldMineBlock(wall)) {
                            mineBlock(wall);
                            actionLock = true;
                            return;
                        }
                    }
                }
            }
        }
    }

    private void scaffoldHighway() {
        BlockPos playerPos = mc.player.getBlockPos();
        Vec3i side = getSideDirection();

        int halfWidth = width.get() / 2;

        for (int w = -halfWidth; w <= halfWidth; w++) {

            BlockPos base = playerPos.add(side.getX() * w, 0, side.getZ() * w);

            BlockPos floor = base.down();

            if (shouldPlaceBlock(floor)) {
                placeBlock(floor);
            } else if (shouldMineBlock(floor)) {
                mineBlock(floor);
                actionLock = true;
                return;
            }

            if (shouldMineBlock(base)) {
                mineBlock(base);
                actionLock = true;
                return;
            }

            if (shouldMineBlock(base.up())) {
                mineBlock(base.up());
                actionLock = true;
                return;
            }

            if (buildWalls.get() && (w == -halfWidth || w == halfWidth)) {
                BlockPos wall = base;

                if (shouldPlaceBlock(wall)) {
                    placeBlock(wall);
                } else if (shouldMineBlock(wall)) {
                    mineBlock(wall);
                    actionLock = true;
                    return;
                }
            }
        }
    }

    // FIXED: no longer ignores same-block obstruction incorrectly
    private boolean shouldMineBlock(BlockPos pos) {
        if (mc.world.isAir(pos)) return false;

        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (state.getHardness(mc.world, pos) < 0) return false;

        BlockPos playerPos = mc.player.getBlockPos();
        Vec3i forward = getCardinalDirection(lockedYaw);
        Vec3i side = getSideDirection();

        int halfWidth = width.get() / 2;

        boolean isFloorOrWall = false;

        for (int w = -halfWidth; w <= halfWidth; w++) {

            BlockPos base = playerPos
                .add(side.getX() * w, 0, side.getZ() * w);

            BlockPos floor = base.down();

            if (pos.equals(floor)) isFloorOrWall = true;

            if (buildWalls.get()) {
                if (w == -halfWidth || w == halfWidth) {
                    if (pos.equals(base) || pos.equals(base.up())) {
                        isFloorOrWall = true;
                    }
                }
            }
        }

        if (isFloorOrWall) return false;

        return true;
    }

    private boolean placeBlock(BlockPos pos) {
        int slot = findBuildBlockSlot();
        if (slot == -1) return false;

        int prev = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);

        boolean placed = false;

        for (Direction dir : Direction.values()) {
            BlockPos adj = pos.offset(dir);

            if (!mc.world.isAir(adj)) {
                BlockHitResult hit = new BlockHitResult(
                    Vec3d.ofCenter(pos),
                    dir.getOpposite(),
                    adj,
                    false
                );

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);

                placed = true;
                break;
            }
        }

        mc.player.getInventory().setSelectedSlot(prev);
        return placed;
    }

    private void mineBlock(BlockPos pos) {
        mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findBuildBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof BlockItem bi && bi.getBlock() == buildBlock.get()) {
                return i;
            }
        }
        return -1;
    }

    private Vec3i getSideDirection() {
        Vec3i f = getCardinalDirection(lockedYaw);
        if (f.getX() != 0) return new Vec3i(0, 0, 1);
        return new Vec3i(1, 0, 0);
    }

    private Vec3i getCardinalDirection(float yaw) {
        yaw = MathHelper.wrapDegrees(yaw);
        if (yaw >= -45 && yaw < 45) return new Vec3i(0, 0, 1);
        if (yaw >= 45 && yaw < 135) return new Vec3i(-1, 0, 0);
        if (yaw >= 135 || yaw < -135) return new Vec3i(0, 0, -1);
        return new Vec3i(1, 0, 0);
    }

    private float getClosestCardinalYaw(float yaw) {
        yaw = MathHelper.wrapDegrees(yaw);
        if (yaw >= -45 && yaw < 45) return 0;
        if (yaw >= 45 && yaw < 135) return 90;
        if (yaw >= 135 || yaw < -135) return 180;
        return -90;
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

    private boolean shouldPlaceBlock(BlockPos pos) {
    if (mc.world.isAir(pos)) return true;

    BlockState state = mc.world.getBlockState(pos);
    Block block = state.getBlock();

    // Only replace non-build blocks
    return block != buildBlock.get();
}
}