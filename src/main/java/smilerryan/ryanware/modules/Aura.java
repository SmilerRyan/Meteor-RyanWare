package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import smilerryan.ryanware.RyanWare;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;

public class Aura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgCombat = settings.createGroup("Combat");

    // General
    private final Setting<Boolean> toggleOnKey = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-key")
        .description("Toggles the aura when the key is pressed.")
        .defaultValue(true)
        .build()
    );

    // Targeting
    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the aura can reach.")
        .defaultValue(4.5)
        .min(0)
        .max(100)
        .build()
    );

    private final Setting<Double> stepSize = sgTargeting.add(new DoubleSetting.Builder()
        .name("step-size")
        .description("Distance to move per step when targeting.")
        .defaultValue(7)
        .min(1)
        .max(10)
        .build()
    );

    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder()
        .name("players")
        .description("Target players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hostiles = sgTargeting.add(new BoolSetting.Builder()
        .name("hostiles")
        .description("Target hostile mobs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> passives = sgTargeting.add(new BoolSetting.Builder()
        .name("passives")
        .description("Target passive mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgTargeting.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Attack through walls.")
        .defaultValue(false)
        .build()
    );

    // Combat
    private final Setting<Integer> attacksPerTick = sgCombat.add(new IntSetting.Builder()
        .name("attacks-per-tick")
        .description("Number of attacks per tick.")
        .defaultValue(1)
        .min(1)
        .max(10)
        .build()
    );

    private Entity targetOverride = null;
    private Vec3d targetPos = null;
    private int teleportTimer = 0;
    private boolean isProcessing = false;

    public Aura() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix+"aura", "Automatically attacks entities around you.");
    }

    public void onActivate() {
        targetOverride = null;
        targetPos = null;
        teleportTimer = 0;
        isProcessing = false;
    }

    public void onDeactivate() {
        targetOverride = null;
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

        if (targetOverride != null && !targetOverride.isAlive()) {
            targetOverride = null;
            targetPos = null;
        }

        if (targetOverride == null) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                EntityHitResult hit = (EntityHitResult) mc.crosshairTarget;
                targetOverride = hit.getEntity();
                targetPos = targetOverride.getPos();
            }
        }

        if (targetOverride != null) {
            if (targetPos == null) {
                targetPos = targetOverride.getPos();
            }
            stepTeleport();
        } else {
            List<Entity> targets = getTargets();
            if (!targets.isEmpty()) {
                targetOverride = targets.get(0);
                targetPos = targetOverride.getPos();
                stepTeleport();
            }
        }
    }

    private void stepTeleport() {
        if (targetPos == null || targetOverride == null) return;

        Vec3d playerPos = mc.player.getPos();
        double distance = Math.sqrt(
            Math.pow(targetPos.x - playerPos.x, 2) +
            Math.pow(targetPos.y - playerPos.y, 2) +
            Math.pow(targetPos.z - playerPos.z, 2)
        );

        if (distance <= range.get()) {
            // We're in range, attack the target
            attack(targetOverride);
            return;
        }

        if (distance <= stepSize.get()) {
            // Final teleport to get in range
            Vec3d finalPos = new Vec3d(
                targetPos.x - (targetPos.x - playerPos.x) * (range.get() / distance),
                targetPos.y - (targetPos.y - playerPos.y) * (range.get() / distance),
                targetPos.z - (targetPos.z - playerPos.z) * (range.get() / distance)
            );
            mc.player.setPosition(finalPos);
            teleportTimer = 1;
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

    private List<Entity> getTargets() {
        List<Entity> targets = new ArrayList<>();
        Vec3d pos = mc.player.getPos();
        Box box = new Box(
            pos.x - range.get(), pos.y - range.get(), pos.z - range.get(),
            pos.x + range.get(), pos.y + range.get(), pos.z + range.get()
        );

        for (Entity entity : mc.world.getOtherEntities(mc.player, box)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (!entity.isAlive()) continue;
            if (!throughWalls.get() && !mc.player.canSee(entity)) continue;

            if (entity instanceof PlayerEntity && players.get()) {
                targets.add(entity);
            } else if (isHostile(entity) && hostiles.get()) {
                targets.add(entity);
            } else if (isPassive(entity) && passives.get()) {
                targets.add(entity);
            }
        }

        targets.sort((a, b) -> {
            double distA = mc.player.distanceTo(a);
            double distB = mc.player.distanceTo(b);
            return Double.compare(distA, distB);
        });

        return targets;
    }

    private boolean isHostile(Entity entity) {
        return entity.getType() == EntityType.ZOMBIE ||
               entity.getType() == EntityType.SKELETON ||
               entity.getType() == EntityType.SPIDER ||
               entity.getType() == EntityType.CREEPER ||
               entity.getType() == EntityType.ENDERMAN ||
               entity.getType() == EntityType.WITCH;
    }

    private boolean isPassive(Entity entity) {
        return entity.getType() == EntityType.COW ||
               entity.getType() == EntityType.PIG ||
               entity.getType() == EntityType.SHEEP ||
               entity.getType() == EntityType.CHICKEN;
    }

    private void attack(Entity target) {
        if (mc.player == null || mc.world == null) return;
        if (target == null) return;
        if (!target.isAlive()) return;
        if (mc.player.distanceTo(target) > range.get()) return;
        if (!throughWalls.get() && !mc.player.canSee(target)) return;

        for (int i = 0; i < attacksPerTick.get(); i++) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(mc.player.getActiveHand());
        }
    }
} 