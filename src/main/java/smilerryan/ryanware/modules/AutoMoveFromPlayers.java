package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class AutoMoveFromPlayers extends Module {

    public enum Mode {
        Everyone,
        Friends,
        NonFriends,
        FriendsExceptList,
        NonFriendsExceptList,
        OnlyList
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> safeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("safe-distance")
        .description("Minimum distance to keep from players.")
        .defaultValue(6.0)
        .min(1.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Double> deadzone = sgGeneral.add(new DoubleSetting.Builder()
        .name("deadzone")
        .description("Minimum vector strength before reacting.")
        .defaultValue(0.05)
        .min(0.0)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Who to avoid.")
        .defaultValue(Mode.Everyone)
        .build()
    );

    private final Setting<List<String>> list = sgGeneral.add(new StringListSetting.Builder()
        .name("list")
        .description("Custom player list.")
        .build()
    );

    public AutoMoveFromPlayers() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Auto-Move-From-Players",
            "Backpedals away from nearby players without interfering when safe.");
    }

    @Override
    public void onDeactivate() {
        if (mc.options == null) return;
        mc.options.backKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        Vec3d myPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d totalAway = Vec3d.ZERO;
        int count = 0;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!shouldAvoid(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > safeDistance.get()) continue;

            Vec3d theirPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            Vec3d away = myPos.subtract(theirPos);

            if (away.lengthSquared() == 0) continue;

            double weight = 1.0 / Math.max(dist, 0.1);
            totalAway = totalAway.add(away.normalize().multiply(weight));
            count++;
        }

        // If no threats, DO NOTHING (don't override controls)
        if (count == 0 || totalAway.length() < deadzone.get()) {
            mc.options.backKey.setPressed(false);
            return;
        }

        // Threat detected → move backwards
        mc.options.backKey.setPressed(true);
    }

    private boolean shouldAvoid(PlayerEntity player) {
        String name = player.getName().getString();
        boolean isFriend = Friends.get().isFriend(player);
        boolean inList = list.get().contains(name);

        return switch (mode.get()) {
            case Everyone -> true;
            case Friends -> isFriend;
            case NonFriends -> !isFriend;
            case FriendsExceptList -> isFriend && !inList;
            case NonFriendsExceptList -> !isFriend && !inList;
            case OnlyList -> inList;
        };
    }
}