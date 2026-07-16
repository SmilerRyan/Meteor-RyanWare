package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import smilerryan.ryanware.RyanWare;

public class Scaffold extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> placeBelow = sgGeneral.add(new BoolSetting.Builder()
        .name("place-below")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> placeAbove = sgGeneral.add(new BoolSetting.Builder()
        .name("place-above")
        .defaultValue(false)
        .build());

    private final Setting<Integer> placeOffset = sgGeneral.add(new IntSetting.Builder()
        .name("place-offset")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build());

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .defaultValue(0)
        .min(0)
        .sliderMax(6)
        .build());

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .build());


    public Scaffold() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Scaffold",
            "Places blocks below or above the player."
        );
    }


    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        ItemStack stack = mc.player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem)) return;
        FindItemResult item = InvUtils.findInHotbar(stack.getItem());
        if (!item.found()) return;
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(item.slot(), false);
        BlockPos feet = mc.player.getBlockPos();
        int placed = 0;

        if (placeBelow.get()) {
            BlockPos base = feet.down(placeOffset.get());
            for (int x = -radius.get(); x <= radius.get(); x++) {
                for (int z = -radius.get(); z <= radius.get(); z++) {
                    if (placed >= blocksPerTick.get()) break;
                    if (place(base.add(x, 0, z), item)) {
                        placed++;
                    }
                }
                if (placed >= blocksPerTick.get()) break;
            }
        }

        if (placeAbove.get() && placed < blocksPerTick.get()) {
            BlockPos base = feet.up(placeOffset.get());
            for (int x = -radius.get(); x <= radius.get(); x++) {
                for (int z = -radius.get(); z <= radius.get(); z++) {
                    if (placed >= blocksPerTick.get()) break;
                    if (place(base.add(x, 0, z), item)) {
                        placed++;
                    }
                }
                if (placed >= blocksPerTick.get()) break;
            }
        }
        InvUtils.swap(oldSlot, false);
    }

    private boolean place(BlockPos pos, FindItemResult item) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        BlockPos neighbour = pos.down();
        if (mc.world.getBlockState(neighbour).isAir()) return false;
        BlockUtils.place(pos, item, true, 50, true, true, false);
        return true;
    }

}