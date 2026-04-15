package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class AutoFollowItems extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum FilterMode {
        Off, Whitelist, Blacklist
    }

    private final Setting<FilterMode> filterMode = sgGeneral.add(new EnumSetting.Builder<FilterMode>()
        .name("filter-mode")
        .description("Whitelist, blacklist, or off.")
        .defaultValue(FilterMode.Off)
        .build()
    );

    private final Setting<List<String>> itemList = sgGeneral.add(new StringListSetting.Builder()
        .name("item-list")
        .description("Items to whitelist or blacklist.")
        .defaultValue()
        .build()
    );

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("Maximum distance to follow items. Set to 0 for unlimited.")
        .defaultValue(0.0)
        .min(0.0)
        .build()
    );

    private final Setting<Boolean> autoWalkJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-walk-jump")
        .description("Automatically walk towards and jump up blocks to get to items.")
        .defaultValue(true)
        .build()
    );

    private boolean wasAutoWalking = false;
    private long lastManualInputTime = 0;

    public AutoFollowItems() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "AutoFollowItems", "Automatically walks/jumps towards items with a whitelist/blacklist/max-range.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        // Detect manual movement input
        if (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()
            || mc.options.jumpKey.isPressed() || mc.options.sprintKey.isPressed()) {
            lastManualInputTime = System.currentTimeMillis();
            if (wasAutoWalking) {
                mc.options.forwardKey.setPressed(false);
                wasAutoWalking = false;
            }
            return;
        }

        // Check if 0.5s has passed since last manual input
        if (System.currentTimeMillis() - lastManualInputTime < 500) {
            if (wasAutoWalking) {
                mc.options.forwardKey.setPressed(false);
                wasAutoWalking = false;
            }
            return;
        }

        ItemEntity closest = findClosestItem();

        if (closest == null) {
            // Stop auto-walk if we were auto-walking
            if (wasAutoWalking) {
                mc.options.forwardKey.setPressed(false);
                wasAutoWalking = false;
            }
            return;
        }

        // Look at item
        lookAt(closest.getPos());

        // Stop auto-walk if item is within pickup range
        if (mc.player.squaredDistanceTo(closest) <= 1.0) {
            if (wasAutoWalking) {
                mc.options.forwardKey.setPressed(false);
                wasAutoWalking = false;
            }
            return;
        }

        if (autoWalkJump.get()) {
            mc.options.forwardKey.setPressed(true);
            wasAutoWalking = true;

            if (mc.player.horizontalCollision) {
                mc.player.jump();
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (wasAutoWalking) {
            mc.options.forwardKey.setPressed(false);
            wasAutoWalking = false;
        }
    }

    private ItemEntity findClosestItem() {
        ItemEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity item)) continue;
            if (!passesFilter(item)) continue;

            double dist = mc.player.squaredDistanceTo(item);
            if (maxDistance.get() > 0 && dist > maxDistance.get() * maxDistance.get()) continue;

            if (dist < closestDist) {
                closestDist = dist;
                closest = item;
            }
        }
        return closest;
    }

    private boolean passesFilter(ItemEntity item) {
        if (filterMode.get() == FilterMode.Off) return true;

        String name = item.getStack().getName().getString().toLowerCase();
        boolean inList = itemList.get().stream().anyMatch(s -> name.contains(s.toLowerCase()));

        if (filterMode.get() == FilterMode.Whitelist) return inList;
        if (filterMode.get() == FilterMode.Blacklist) return !inList;
        return true;
    }

    private void lookAt(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;

        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }
}
