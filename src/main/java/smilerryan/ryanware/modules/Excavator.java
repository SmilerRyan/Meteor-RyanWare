package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class Excavator extends Module {

    public Excavator() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Excavator",
            "Mines blocks between two points efficiently.");
    }

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

    private final Setting<Boolean> useFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("use-flight")
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
        .visible(filler::get)
        .build()
    );

    private final Setting<Boolean> waitForBlocks = sgFiller.add(new BoolSetting.Builder()
        .name("wait-for-blocks")
        .defaultValue(true)
        .visible(filler::get)
        .build()
    );

    private enum State {
        IDLE, MOVE, ROTATE, START_BREAK, BREAK, FILL
    }

    private State state = State.IDLE;

    private BlockPos startPos, endPos;
    private List<BlockPos> blocksToMine = new ArrayList<>();
    private int index;

    private BlockPos current;
    private Direction breakSide;
    private int rotationDelay;

    private boolean manualControl = false;
    private int stuckTicks = 0;
    private Vec3d lastPos;

    @Override
    public void onActivate() {
        startPos = endPos = null;
        blocksToMine = new ArrayList<>();
        index = 0;
        state = State.IDLE;
        manualControl = false;
        stuckTicks = 0;
        lastPos = null;
    }

    @Override
    public void onDeactivate() {
        stopMove();
        state = State.IDLE;
        manualControl = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        handleSelection();

        if (blocksToMine == null || index >= blocksToMine.size()) return;

        current = blocksToMine.get(index);
        if (current == null) return;

        BlockState stateAt = mc.world.getBlockState(current);

        handleStuckDetection();

        if (stateAt.isAir()) {
            next();
            return;
        }

        switch (state) {
            case IDLE -> state = State.MOVE;
            case MOVE -> handleMove();
            case ROTATE -> handleRotate();
            case START_BREAK -> startBreak();
            case BREAK -> handleBreak();
            case FILL -> handleFill(stateAt);
        }
    }

    /* ---------------- STUCK / MANUAL CONTROL ---------------- */

    private void handleStuckDetection() {
        Vec3d curPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        if (lastPos == null) lastPos = curPos;

        double moved = curPos.distanceTo(lastPos);
        lastPos = curPos;

        boolean notMoving = moved < 0.01;
        boolean playerInput =
            mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed()
                || mc.options.jumpKey.isPressed()
                || mc.options.sneakKey.isPressed();

        if (state == State.MOVE && notMoving && !playerInput) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }

        if (stuckTicks > 20) {
            manualControl = true;
            stopMove();
        }

        if (manualControl && playerInput) {
            manualControl = false;
            stuckTicks = 0;
            state = State.MOVE;
        }

        if (manualControl && !playerInput) {
            stopMove();
        }
    }

    /* ---------------- SELECTION ---------------- */

    private void handleSelection() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (!mc.options.attackKey.isPressed()) return;

        BlockPos pos = hit.getBlockPos();

        if (startPos == null) {
            startPos = pos;
            info("Start set");
        } else if (endPos == null) {
            endPos = pos;
            calculate();
        }
    }

    private void calculate() {
        blocksToMine.clear();

        int minX = Math.min(startPos.getX(), endPos.getX());
        int maxX = Math.max(startPos.getX(), endPos.getX());
        int minY = Math.min(startPos.getY(), endPos.getY());
        int maxY = Math.max(startPos.getY(), endPos.getY());
        int minZ = Math.min(startPos.getZ(), endPos.getZ());
        int maxZ = Math.max(startPos.getZ(), endPos.getZ());

        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {

                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = mc.world.getBlockState(p);

                    if (s.getHardness(mc.world, p) < 0) continue;

                    if (filler.get()) {
                        if (!s.getBlock().equals(fillerBlock.get()) || s.isAir()) {
                            blocksToMine.add(p);
                        }
                    } else {
                        if (!s.isAir()) blocksToMine.add(p);
                    }
                }
            }
        }

        index = 0;
        state = State.MOVE;

        info("Blocks: " + blocksToMine.size());

        if (blocksToMine.size() == 1) {
            info("Same block selected for both positions. Resetting...");
            startPos = null;
            endPos = null;
            blocksToMine = null;
        }

    }

    /* ---------------- CORE STATES ---------------- */

    private void handleMove() {
        if (manualControl) return;

        if (distance(current) <= range.get()) {
            stopMove();
            state = State.ROTATE;
            rotationDelay = 0;
            return;
        }

        moveToward(current);
    }

    private void handleRotate() {
        if (manualControl) return;

        lookAt(current);

        if (rotationDelay++ > 1) {
            state = State.START_BREAK;
        }
    }

    private void startBreak() {
        if (manualControl) return;

        breakSide = Direction.UP;

        mc.interactionManager.attackBlock(current, breakSide);
        mc.player.swingHand(Hand.MAIN_HAND);

        state = State.BREAK;
    }

    private void handleBreak() {
        if (manualControl) return;

        BlockState s = mc.world.getBlockState(current);

        if (s.isAir()) {
            next();
            return;
        }

        mc.interactionManager.updateBlockBreakingProgress(current, breakSide);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void handleFill(BlockState stateAt) {
        if (!filler.get() || manualControl) {
            next();
            return;
        }

        Item item = fillerBlock.get().asItem();
        FindItemResult res = InvUtils.findInHotbar(i -> i.getItem() == item);

        if (!res.found()) {
            if (!waitForBlocks.get()) next();
            return;
        }

        if (BlockUtils.place(current, res, true, 0)) {
            next();
        }
    }

    /* ---------------- MOVEMENT ---------------- */

    private void moveToward(BlockPos p) {
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d target = Vec3d.ofCenter(p);

        double dx = target.x - pos.x;
        double dz = target.z - pos.z;

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        mc.player.setYaw(yaw);

        mc.options.forwardKey.setPressed(true);

        if (useFlight.get() && mc.player.getAbilities().flying) {
            double dy = target.y - pos.y;

            mc.options.jumpKey.setPressed(dy > 0.2);
            mc.options.sneakKey.setPressed(dy < -0.2);
        }
    }

    private void stopMove() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    /* ---------------- UTIL ---------------- */

    private void next() {
        index++;
        state = State.MOVE;
    }

    private double distance(BlockPos p) {
        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        return pos.distanceTo(Vec3d.ofCenter(p));
    }

    private void lookAt(BlockPos p) {
        Vec3d eye = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
        Vec3d c = Vec3d.ofCenter(p);

        double dx = c.x - eye.x;
        double dy = c.y - eye.y;
        double dz = c.z - eye.z;

        double d = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, d));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }
}