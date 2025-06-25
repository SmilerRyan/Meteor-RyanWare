package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import smilerryan.ryanware.RyanWare;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.block.Blocks;

public class CrystalAura extends Module {
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

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range for placing crystals.")
        .defaultValue(4.0)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> strictDirection = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-direction")
        .description("Only place when looking at the block.")
        .defaultValue(false)
        .build()
    );

    private int tickCounter = 0;

    public CrystalAura() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "Crystal-Aura", "Automatically attacks with end crystals.");
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

            // Check if we have crystals before doing expensive calculations
            FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (!crystal.found()) return;

            BlockPos bestPos = findBestCrystalPos(target);
            if (bestPos != null) {
                InvUtils.swap(crystal.slot(), true);
                BlockUtils.place(bestPos, crystal, false, 0, !strictDirection.get());
            }
        }
    }

    private BlockPos findBestCrystalPos(PlayerEntity target) {
        BlockPos targetPos = target.getBlockPos();
        double bestDistance = Double.MAX_VALUE;
        BlockPos bestPos = null;

        // Check a larger area around the target
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos basePos = targetPos.add(x, y, z);
                    BlockPos crystalPos = basePos.up();
                    
                    if (canPlaceCrystal(basePos, crystalPos)) {
                        double distance = mc.player.squaredDistanceTo(crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
                        if (distance <= placeRange.get() * placeRange.get() && distance < bestDistance) {
                            bestDistance = distance;
                            bestPos = crystalPos;
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean canPlaceCrystal(BlockPos basePos, BlockPos crystalPos) {
        // Check if base block is obsidian or bedrock
        if (!(mc.world.getBlockState(basePos).getBlock() == Blocks.OBSIDIAN || 
              mc.world.getBlockState(basePos).getBlock() == Blocks.BEDROCK)) {
            return false;
        }

        // Check if crystal position and above are air
        if (!mc.world.isAir(crystalPos) || !mc.world.isAir(crystalPos.up())) {
            return false;
        }

        // Check for entities in the way (most important fix)
        Box crystalBox = new Box(crystalPos);
        if (!mc.world.getOtherEntities(null, crystalBox).isEmpty()) {
            return false;
        }

        // Check the space above as well for entities
        Box aboveBox = new Box(crystalPos.up());
        if (!mc.world.getOtherEntities(null, aboveBox).isEmpty()) {
            return false;
        }

        return true;
    }
}