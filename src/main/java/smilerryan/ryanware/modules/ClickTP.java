package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public class ClickTP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("Teleport");

    // General
    private final Setting<Boolean> toggleOnKey = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-key")
        .description("Toggles the teleport when the key is pressed.")
        .defaultValue(true)
        .build()
    );

    // Teleport
    private final Setting<Double> maxDistance = sgTeleport.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("The maximum distance you can teleport.")
        .defaultValue(100)
        .min(0)
        .max(1000)
        .build()
    );

    private final Setting<Double> stepSize = sgTeleport.add(new DoubleSetting.Builder()
        .name("step-size")
        .description("Distance to move per step when teleporting.")
        .defaultValue(7)
        .min(1)
        .max(10)
        .build()
    );

    private final Setting<Boolean> safeTeleport = sgTeleport.add(new BoolSetting.Builder()
        .name("safe-teleport")
        .description("Only teleport to safe positions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> teleportDelay = sgTeleport.add(new IntSetting.Builder()
        .name("teleport-delay")
        .description("Delay between teleports in ticks.")
        .defaultValue(20)
        .min(1)
        .max(40)
        .build()
    );

    private Vec3d targetPos = null;
    private int teleportTimer = 0;
    private boolean isProcessing = false;

    public ClickTP() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix+"+-ClickTP", "Teleports you to where you click.");
    }

    public void onActivate() {
        targetPos = null;
        teleportTimer = 0;
        isProcessing = false;
    }

    public void onDeactivate() {
        targetPos = null;
        teleportTimer = 0;
        isProcessing = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (!isActive()) return;
        if (isProcessing) return;

        if (teleportTimer > 0) {
            teleportTimer--;
            return;
        }

        if (mc.options.useKey.isPressed()) {
            handleTeleport();
        }

        if (targetPos != null) {
            stepTeleport();
        }
    }

    private void handleTeleport() {
        if (mc.player == null || mc.world == null) return;
        if (!isActive()) return;
        if (isProcessing) return;
        if (teleportTimer > 0) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos blockPos = blockHit.getBlockPos();
        Vec3d hitPos = hit.getPos();

        // Calculate the position to teleport to (slightly above the block)
        Vec3d target = new Vec3d(
            hitPos.x,
            blockPos.getY() + 1,
            hitPos.z
        );

        // Check if the position is safe
        if (safeTeleport.get() && !isSafePosition(target)) {
            error("Target position is not safe!");
            return;
        }

        // Check if the distance is within the maximum
        double distance = Math.sqrt(
            Math.pow(target.x - mc.player.getX(), 2) +
            Math.pow(target.y - mc.player.getY(), 2) +
            Math.pow(target.z - mc.player.getZ(), 2)
        );

        if (distance > maxDistance.get()) {
            error("Target is too far away!");
            return;
        }

        targetPos = target;
        stepTeleport();
    }

    private void stepTeleport() {
        if (targetPos == null) return;

        Vec3d playerPos = mc.player.getPos();
        double distance = Math.sqrt(
            Math.pow(targetPos.x - playerPos.x, 2) +
            Math.pow(targetPos.y - playerPos.y, 2) +
            Math.pow(targetPos.z - playerPos.z, 2)
        );

        if (distance <= 0.1) {
            // We've reached the target
            targetPos = null;
            teleportTimer = teleportDelay.get();
            return;
        }

        if (distance <= stepSize.get()) {
            // Final teleport to target
            mc.player.setPosition(targetPos);
            targetPos = null;
            teleportTimer = teleportDelay.get();
            return;
        }

        // Calculate step direction
        double dx = targetPos.x - playerPos.x;
        double dy = targetPos.y - playerPos.y;
        double dz = targetPos.z - playerPos.z;

        // Normalize and scale by step size
        double scale = stepSize.get() / distance;
        Vec3d step = new Vec3d(
            playerPos.x + dx * scale,
            playerPos.y + dy * scale,
            playerPos.z + dz * scale
        );

        // Perform step teleport
        mc.player.setPosition(step);
        teleportTimer = 1;
    }

    private boolean isSafePosition(Vec3d pos) {
        BlockPos blockPos = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
        BlockPos blockBelow = blockPos.down();
        BlockPos blockAbove = blockPos.up();

        BlockState blockBelowState = mc.world.getBlockState(blockBelow);
        BlockState blockState = mc.world.getBlockState(blockPos);
        BlockState blockAboveState = mc.world.getBlockState(blockAbove);

        // Check if the block below is solid
        if (!blockBelowState.isSolid()) return false;

        // Check if the target position and block above are air
        return blockState.isAir() && blockAboveState.isAir();
    }
} 