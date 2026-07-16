package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import smilerryan.ryanware.RyanWare;
import smilerryan.ryanware.utils.SendChat;

import java.util.HashMap;
import java.util.Map;

public class PlayerCoordsTracker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Boolean> logLogins = sgGeneral.add(new BoolSetting.Builder()
        .name("log-logins")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logLogouts = sgGeneral.add(new BoolSetting.Builder()
        .name("log-logouts")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logTeleports = sgGeneral.add(new BoolSetting.Builder()
        .name("log-teleports")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> msgAppeared = sgGeneral.add(new StringSetting.Builder()
        .name("appeared-format")
        .defaultValue("/ {PLAYER} appeared at {X}, {Y}, {Z}")
        .build()
    );

    private final Setting<String> msgDisappeared = sgGeneral.add(new StringSetting.Builder()
        .name("disappeared-format")
        .defaultValue("/ {PLAYER} disappeared at {X}, {Y}, {Z}")
        .build()
    );

    private final Setting<String> msgTeleportFrom = sgGeneral.add(new StringSetting.Builder()
        .name("teleport-from-format")
        .defaultValue("/ {PLAYER} teleported from {X}, {Y}, {Z}")
        .build()
    );

    private final Setting<String> msgTeleportTo = sgGeneral.add(new StringSetting.Builder()
        .name("teleport-to-format")
        .defaultValue("/ {PLAYER} teleported to {X}, {Y}, {Z}")
        .build()
    );

    private final Map<PlayerEntity, Vec3d> lastPositions = new HashMap<>();

    public PlayerCoordsTracker() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Player-Coords-Tracker", "Tracks coordinates from players logging in, logging out, or teleporting.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // 1. Detect Logins and Teleports
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            
            if (!lastPositions.containsKey(player)) {
                // APPEARED
                if (logLogins.get()) {
                    sendMessage(msgAppeared.get(), player, currentPos);
                }
            } else {
                Vec3d lastPos = lastPositions.get(player);
                // TELEPORT CHECK (Dist > 1 block)
                if (logTeleports.get() && lastPos.distanceTo(currentPos) > 1.0) {
                    sendMessage(msgTeleportFrom.get(), player, lastPos);
                    sendMessage(msgTeleportTo.get(), player, currentPos);
                }
            }
            lastPositions.put(player, currentPos);
        }

        // 2. Detect Logouts
        lastPositions.entrySet().removeIf(entry -> {
            PlayerEntity player = entry.getKey();
            if (!mc.world.getPlayers().contains(player)) {
                if (logLogouts.get()) {
                    sendMessage(msgDisappeared.get(), player, entry.getValue());
                }
                return true; 
            }
            return false;
        });
    }

    private void sendMessage(String format, PlayerEntity player, Vec3d pos) {
        String msg = format.replace("{PLAYER}", player.getName().getString())
                           .replace("{X}", String.format("%.0f", pos.x))
                           .replace("{Y}", String.format("%.0f", pos.y))
                           .replace("{Z}", String.format("%.0f", pos.z));
        SendChat.any(msg);
    }

    @Override
    public void onDeactivate() {
        lastPositions.clear();
    }
}