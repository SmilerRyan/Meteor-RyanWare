package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.block.Block;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class AntiBlockBreak extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blockAll = sgGeneral.add(new BoolSetting.Builder()
        .name("block-all")
        .description("Prevent breaking any block anywhere.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Block>> blockedBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocked-blocks")
        .description("Blocks you cannot break.")
        .defaultValue(List.of())
        .visible(() -> !blockAll.get())
        .build()
    );

    private final Setting<Boolean> requireCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("creative-only")
        .description("Only prevent block breaking in creative mode.")
        .defaultValue(false)
        .visible(() -> !blockAll.get())
        .build()
    );

    public AntiBlockBreak() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Anti-Block-Break", "Prevents you from breaking certain blocks.");
    }

    public void onTick() {
        if (!isActive() || mc.player == null || mc.world == null) return;

        // Only check if left-click is pressed
        if (!InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)) return;

        // Make sure player is targeting a block
        HitResult target = mc.crosshairTarget;
        if (!(target instanceof BlockHitResult hit)) return;

        BlockPos pos = hit.getBlockPos();
        Block block = mc.world.getBlockState(pos).getBlock();

        // Cancel mining if blocked
        if (blockAll.get() || blockedBlocks.get().contains(block)) {
            if (!requireCreative.get() || mc.player.isCreative()) {
                mc.interactionManager.cancelBlockBreaking();
            }
        }
    }
}
