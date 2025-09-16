package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class AntiBlockPlace extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blockAll = sgGeneral.add(new BoolSetting.Builder()
        .name("block-all")
        .description("Prevent placing any block anywhere.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> blockedBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocked-blocks")
        .description("Blocks you cannot place.")
        .defaultValue(List.of())
        .visible(() -> !blockAll.get())
        .build()
    );

    private final Setting<List<Block>> blockedOn = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocked-on")
        .description("Blocks you cannot place onto.")
        .defaultValue(List.of())
        .visible(() -> !blockAll.get())
        .build()
    );

    public AntiBlockPlace() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Anti-Block-Place", "Prevents you from placing certain blocks.");
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (blockAll.get()) {
            event.cancel();
            return;
        }

        // Block being placed
        if (mc.player.getMainHandStack().getItem() instanceof BlockItem bi) {
            if (blockedBlocks.get().contains(bi.getBlock())) {
                event.cancel();
                return;
            }
        }

        // Block clicked
        BlockPos pos = event.result.getBlockPos();
        Block targetBlock = mc.world.getBlockState(pos).getBlock();
        if (blockedOn.get().contains(targetBlock)) {
            event.cancel();
        }
    }
}
