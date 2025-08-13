package smilerryan.ryanware.modules;

import smilerryan.ryanware.RyanWare;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;

public class CrystalAura3 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Attack range.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Ticks between attacks.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> placeCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("place")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("break")
        .defaultValue(true)
        .build()
    );

    private int tickCounter = 0;

    public CrystalAura3() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "crystal-aura-3", "Automatically attacks with end crystals.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (++tickCounter < delay.get()) return;
        tickCounter = 0;

        if (breakCrystals.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity && mc.player.squaredDistanceTo(entity) <= range.get() * range.get()) {
                    mc.interactionManager.attackEntity(mc.player, entity);
                    mc.player.swingHand(mc.player.getActiveHand());
                    return;
                }
            }
        }

        if (placeCrystals.get()) {
            PlayerEntity target = TargetUtils.getPlayerTarget(range.get(), SortPriority.LowestDistance);
            if (target == null) return;

            BlockPos targetPos = target.getBlockPos().down();
            BlockPos[] offsets = {
                targetPos.north(), targetPos.east(), targetPos.south(), targetPos.west(), targetPos
            };

            for (BlockPos pos : offsets) {
                BlockPos crystalPos = pos.up();
                if ((mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN || mc.world.getBlockState(pos).getBlock() == Blocks.BEDROCK)
                    && mc.world.isAir(crystalPos) && mc.world.isAir(crystalPos.up())) {

                    FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
                    if (!crystal.found()) return;

                    InvUtils.swap(crystal.slot(), true);
                    BlockUtils.place(crystalPos, crystal, false, 0, true);
                    return;
                }
            }
        }
    }
}
