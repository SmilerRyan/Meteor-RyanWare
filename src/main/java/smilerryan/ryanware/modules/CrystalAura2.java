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
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;
import net.minecraft.world.explosion.Explosion;

public class CrystalAura2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");

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

    // Safety settings
    private final Setting<Boolean> antiSuicide = sgSafety.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Prevents placing/breaking crystals that would kill you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health to allow crystal actions.")
        .defaultValue(8.0)
        .min(1.0)
        .max(20.0)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgSafety.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximum damage to self allowed.")
        .defaultValue(6.0)
        .min(0.0)
        .max(20.0)
        .build()
    );

    private final Setting<Double> safetyDistance = sgSafety.add(new DoubleSetting.Builder()
        .name("safety-distance")
        .description("Minimum distance from crystals for safety.")
        .defaultValue(3.0)
        .min(1.0)
        .max(10.0)
        .build()
    );

    private int tickCounter = 0;

    public CrystalAura2() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-Crystal-Aura-2", "Automatically attacks with end crystals, but better and safer.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (++tickCounter < delay.get()) return;
        tickCounter = 0;

        // Check if we're healthy enough to engage
        if (antiSuicide.get() && mc.player.getHealth() <= minHealth.get()) {
            return;
        }

        if (breakCrystals.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof EndCrystalEntity) {
                    double distance = mc.player.squaredDistanceTo(entity);
                    if (distance <= range.get() * range.get()) {
                        // Safety check before breaking crystal
                        if (isSafeToBreakCrystal((EndCrystalEntity) entity)) {
                            mc.interactionManager.attackEntity(mc.player, entity);
                            mc.player.swingHand(mc.player.getActiveHand());
                            return;
                        }
                    }
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
            if (bestPos != null && isSafeToPlaceCrystal(bestPos)) {
                InvUtils.swap(crystal.slot(), true);
                BlockUtils.place(bestPos, crystal, false, 0, !strictDirection.get());
            }
        }
    }

    private boolean isSafeToBreakCrystal(EndCrystalEntity crystal) {
        if (!antiSuicide.get()) return true;
        
        Vec3d crystalPos = crystal.getPos();
        double distance = mc.player.getPos().distanceTo(crystalPos);
        
        // Don't break crystals too close to us
        if (distance < safetyDistance.get()) {
            return false;
        }
        
        // Calculate potential damage
        double damage = calculateCrystalDamage(crystalPos);
        
        // Don't break if it would deal too much damage or kill us
        return damage <= maxSelfDamage.get() && 
               mc.player.getHealth() - damage > 1.0;
    }

    private boolean isSafeToPlaceCrystal(BlockPos pos) {
        if (!antiSuicide.get()) return true;
        
        Vec3d crystalPos = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        double distance = mc.player.getPos().distanceTo(crystalPos);
        
        // Don't place crystals too close to us
        if (distance < safetyDistance.get()) {
            return false;
        }
        
        // Calculate potential damage if this crystal were to explode
        double damage = calculateCrystalDamage(crystalPos);
        
        // Don't place if it would deal too much damage or kill us
        return damage <= maxSelfDamage.get() && 
               mc.player.getHealth() - damage > 1.0;
    }

    private double calculateCrystalDamage(Vec3d crystalPos) {
        Vec3d playerPos = mc.player.getPos();
        double distance = playerPos.distanceTo(crystalPos);
        
        if (distance > 12.0) return 0.0;
        
        // Simplified crystal damage calculation
        // Crystal explosions have power of 6.0
        double explosionPower = 6.0;
        double impact = (1.0 - (distance / (explosionPower * 2.0 + 1.0)));
        
        if (impact <= 0.0) return 0.0;
        
        // Base damage calculation
        double damage = (impact * impact + impact) * 7.0 * explosionPower + 1.0;
        
        // Account for armor and enchantments (simplified)
        // This is a rough approximation - actual calculation is more complex
        float armorReduction = mc.player.getArmor() * 0.04f;
        damage *= (1.0f - Math.min(armorReduction, 0.8f));
        
        return damage;
    }

    private BlockPos findBestCrystalPos(PlayerEntity target) {
        BlockPos targetPos = target.getBlockPos();
        double bestScore = -1;
        BlockPos bestPos = null;

        // Check a larger area around the target
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos basePos = targetPos.add(x, y, z);
                    BlockPos crystalPos = basePos.up();
                    
                    if (canPlaceCrystal(basePos, crystalPos)) {
                        double distance = mc.player.squaredDistanceTo(crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
                        if (distance <= placeRange.get() * placeRange.get()) {
                            // Calculate damage to target vs damage to self
                            Vec3d crystalVec = new Vec3d(crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
                            double targetDamage = calculateCrystalDamageToEntity(crystalVec, target.getPos());
                            double selfDamage = calculateCrystalDamage(crystalVec);
                            
                            // Score based on damage ratio and distance
                            double score = targetDamage - (selfDamage * 2.0) - (distance * 0.1);
                            
                            if (score > bestScore) {
                                bestScore = score;
                                bestPos = crystalPos;
                            }
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private double calculateCrystalDamageToEntity(Vec3d crystalPos, Vec3d entityPos) {
        double distance = entityPos.distanceTo(crystalPos);
        
        if (distance > 12.0) return 0.0;
        
        double explosionPower = 6.0;
        double impact = (1.0 - (distance / (explosionPower * 2.0 + 1.0)));
        
        if (impact <= 0.0) return 0.0;
        
        return (impact * impact + impact) * 7.0 * explosionPower + 1.0;
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

        // Check for entities in the way
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