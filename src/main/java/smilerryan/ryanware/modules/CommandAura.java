package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import smilerryan.ryanware.RyanWare;

import java.util.HashSet;
import java.util.Set;

public class CommandAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgEnter = settings.createGroup("Enter");
    private final SettingGroup sgLeave = settings.createGroup("Leave");
    
    // General settings
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The range to check for players.")
        .defaultValue(32.0)
        .min(0.0)
        .max(128.0)
        .build()
    );

    // Enter settings
    private final Setting<Boolean> enterEnabled = sgEnter.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Whether to send commands when players enter range.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> enterCommand = sgEnter.add(new StringSetting.Builder()
        .name("command")
        .description("The command to execute when a player enters range. Use {PLAYER} for player name.")
        .defaultValue("/msg {PLAYER} you have ENTERED my render distance.")
        .visible(enterEnabled::get)
        .build()
    );

    // Leave settings
    private final Setting<Boolean> leaveEnabled = sgLeave.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Whether to send commands when players leave range.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> leaveCommand = sgLeave.add(new StringSetting.Builder()
        .name("command")
        .description("The command to execute when a player leaves range. Use {PLAYER} for player name.")
        .defaultValue("/msg {PLAYER} you have LEFT my render distance.")
        .visible(leaveEnabled::get)
        .build()
    );

    private final Set<PlayerEntity> trackedPlayers = new HashSet<>();

    public CommandAura() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix+"+-Command-Aura", "Sends commands when players enter or leave chunks.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        
        // Check all players in range
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue; // Skip self
            
            boolean inRange = mc.player.distanceTo(player) <= range.get();
            boolean wasTracked = trackedPlayers.contains(player);
            
            if (inRange && !wasTracked) {
                // Player just entered range
                trackedPlayers.add(player);
                if (enterEnabled.get()) {
                    sendCommand(player, enterCommand.get());
                }
            } else if (!inRange && wasTracked) {
                // Player just left range
                trackedPlayers.remove(player);
                if (leaveEnabled.get()) {
                    sendCommand(player, leaveCommand.get());
                }
            }
        }
        
        // Clean up players that left the world
        trackedPlayers.removeIf(player -> !mc.world.getPlayers().contains(player));
    }

    private void sendCommand(PlayerEntity target, String command) {
        String commandText = command.replace("{PLAYER}", target.getName().getString());
        mc.player.networkHandler.sendCommand(commandText.substring(1)); // Remove the leading slash
    }

    @Override
    public void onDeactivate() {
        trackedPlayers.clear();
    }
} 