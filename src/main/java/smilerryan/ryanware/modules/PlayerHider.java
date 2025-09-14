package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class PlayerHider extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    private final Setting<Boolean> debugMode = sg.add(new BoolSetting.Builder()
        .name("debug-mode").description("Enable debug logging to help troubleshoot name sync issues.").defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hidePlayersEnabled = sg.add(new BoolSetting.Builder()
        .name("hide-players").description("Enable hiding specific players from tab and nametags.").defaultValue(false)
        .onChanged(value -> needsUpdate = true).build()
    );

    private final Setting<List<String>> playersToHide = sg.add(new StringListSetting.Builder()
        .name("players-to-hide").description("Players to hide.").visible(hidePlayersEnabled::get)
        .onChanged(value -> needsUpdate = true).build()
    );

    private final Setting<List<String>> replacementNames = sg.add(new StringListSetting.Builder()
        .name("replacement-names").description("Replacement names, same order. Leave empty to just hide.").visible(hidePlayersEnabled::get)
        .onChanged(value -> needsUpdate = true).build()
    );

    private final Map<UUID, String> playerReplacementsByUUID = new HashMap<>();
    private final Map<UUID, Text> originalDisplayNames = new HashMap<>();
    private final Map<UUID, String> originalProfileNames = new HashMap<>();
    
    // Cache to avoid recalculating every tick
    private List<String> cachedPlayersToHide = new ArrayList<>();
    private List<String> cachedReplacementNames = new ArrayList<>();
    private boolean needsUpdate = true;
    private int tickCounter = 0;

    public PlayerHider() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Player-Hider", "Hide or replace players in tab and nametags.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tickCounter++;
        // Only update when settings change or every 20 ticks (1 second)
        if (needsUpdate || tickCounter % 20 == 0) {
            updateReplacements();
            applyOrRestore();
            needsUpdate = false;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!hidePlayersEnabled.get()) return;
        if (mc == null || mc.getNetworkHandler() == null) return;

        // Apply changes immediately when packets are received
        applyReplacementsToAllEntries();
        
        // Force a client-side refresh by triggering tab list update
        try {
            mc.execute(() -> {
                if (mc.inGameHud != null && mc.inGameHud.getPlayerListHud() != null) {
                    // This forces the client to re-render the tab list
                    mc.inGameHud.getPlayerListHud().setVisible(true);
                }
            });
        } catch (Exception ignored) {}
    }

    private void applyReplacementsToAllEntries() {
        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            if (entry.getProfile() == null) return;
            
            UUID uuid = entry.getProfile().getId();
            if (!playerReplacementsByUUID.containsKey(uuid)) return;

            String replacement = playerReplacementsByUUID.get(uuid);
            String currentProfileName = entry.getProfile().getName();
            Text currentDisplay = safeGetDisplayName(entry);

            if (debugMode.get()) {
                info("=== DEBUG SESSION START ===");
                info("=== PLAYER DEBUG INFO ===");
                info("UUID: " + uuid.toString());
                info("Current Profile Name: " + currentProfileName);
                info("Current Display Name: " + (currentDisplay != null ? currentDisplay.getString() : "null"));
                info("Replacement: " + replacement);
            }

            // Store original values before any modification
            originalDisplayNames.putIfAbsent(uuid, currentDisplay);
            originalProfileNames.putIfAbsent(uuid, currentProfileName);

            if (replacement.isEmpty()) {
                // Hide the player by setting empty names
                if (debugMode.get()) info("Hiding player: " + currentProfileName);
                setProfileNameOnly(entry, "");
                setDisplayOnly(entry, Text.literal(""));
            } else {
                // Replace with new name - set profile first, then display to match
                if (debugMode.get()) info("Replacing " + currentProfileName + " with " + replacement);
                
                setProfileNameOnly(entry, replacement);
                setDisplayOnly(entry, Text.literal(replacement));
                
                // Verify the changes took effect
                if (debugMode.get()) {
                    String newProfileName = entry.getProfile().getName();
                    Text newDisplayName = safeGetDisplayName(entry);
                    info("After change - Profile: " + newProfileName + ", Display: " + 
                         (newDisplayName != null ? newDisplayName.getString() : "null"));
                    
                    if (!replacement.equals(newProfileName)) {
                        warning("Profile name change failed! Expected: " + replacement + ", Got: " + newProfileName);
                    }
                    if (newDisplayName == null || !replacement.equals(newDisplayName.getString())) {
                        warning("Display name change failed! Expected: " + replacement + ", Got: " + 
                               (newDisplayName != null ? newDisplayName.getString() : "null"));
                    }
                    info("=== DEBUG SESSION END ===");
                }
            }
        });
    }

    private void updateReplacements() {
        if (!hidePlayersEnabled.get() || mc == null || mc.getNetworkHandler() == null) {
            playerReplacementsByUUID.clear();
            return;
        }

        List<String> hidden = playersToHide.get();
        List<String> repls = replacementNames.get();
        
        // Check if settings changed to avoid unnecessary work
        if (hidden.equals(cachedPlayersToHide) && repls.equals(cachedReplacementNames)) {
            return;
        }
        
        cachedPlayersToHide = new ArrayList<>(hidden);
        cachedReplacementNames = new ArrayList<>(repls);
        playerReplacementsByUUID.clear();

        // Clean up disconnected players
        Set<UUID> currentPlayers = new HashSet<>();
        
        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            if (entry.getProfile() == null) return;
            
            UUID uuid = entry.getProfile().getId();
            currentPlayers.add(uuid);
            String name = entry.getProfile().getName();
            
            for (int i = 0; i < hidden.size(); i++) {
                if (hidden.get(i).equalsIgnoreCase(name)) {
                    String repl = i < repls.size() ? repls.get(i) : "";
                    playerReplacementsByUUID.put(uuid, repl);
                    break;
                }
            }
        });
        
        // Clean up data for disconnected players
        originalDisplayNames.keySet().retainAll(currentPlayers);
        originalProfileNames.keySet().retainAll(currentPlayers);
    }

    private void applyOrRestore() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        // First apply all current replacements
        applyReplacementsToAllEntries();

        // Then restore players that are no longer in the replacement map
        Set<UUID> playersToRestore = new HashSet<>();
        
        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            if (entry.getProfile() == null) return;
            
            UUID uuid = entry.getProfile().getId();
            if (!playerReplacementsByUUID.containsKey(uuid) && 
                (originalDisplayNames.containsKey(uuid) || originalProfileNames.containsKey(uuid))) {
                
                Text originalDisplay = originalDisplayNames.get(uuid);
                String originalProfile = originalProfileNames.get(uuid);

                if (originalDisplay != null) setDisplay(entry, originalDisplay);
                if (originalProfile != null) setProfileName(entry, originalProfile);
                
                playersToRestore.add(uuid);
            }
        });
        
        // Clean up restored players from our tracking maps
        playersToRestore.forEach(uuid -> {
            originalDisplayNames.remove(uuid);
            originalProfileNames.remove(uuid);
        });
    }

    private Text safeGetDisplayName(PlayerListEntry entry) {
        try {
            Method m = entry.getClass().getMethod("getDisplayName");
            return (Text) m.invoke(entry);
        } catch (Exception e) {
            // Try fallback field access
            try {
                for (Field f : entry.getClass().getDeclaredFields()) {
                    if (f.getType() == Text.class && f.getName().toLowerCase().contains("display")) {
                        f.setAccessible(true);
                        return (Text) f.get(entry);
                    }
                }
            } catch (Exception fallbackException) {
                // Log only if debugging is needed
                // System.err.println("Failed to get display name: " + fallbackException.getMessage());
            }
        }
        return null;
    }

    private void setDisplayOnly(PlayerListEntry entry, Text text) {
        if (debugMode.get()) {
            info("Setting display name to: " + (text != null ? text.getString() : "null"));
        }
        
        boolean success = false;
        try {
            // Try the standard setDisplayName method
            Method setDisplayName = entry.getClass().getMethod("setDisplayName", Text.class);
            setDisplayName.invoke(entry, text);
            success = true;
            if (debugMode.get()) info("✓ setDisplayName method succeeded");
        } catch (Exception e) {
            if (debugMode.get()) info("✗ setDisplayName method failed: " + e.getMessage());
            
            // Try reflection fallback for different field names
            try {
                Field[] fields = entry.getClass().getDeclaredFields();
                for (Field f : fields) {
                    if (f.getType() == Text.class && 
                        (f.getName().toLowerCase().contains("display") || 
                         f.getName().toLowerCase().contains("name"))) {
                        f.setAccessible(true);
                        f.set(entry, text);
                        success = true;
                        if (debugMode.get()) info("✓ Reflection field '" + f.getName() + "' succeeded");
                        break;
                    }
                }
                
                // If no Text field found by name, try by type - look for field_3743 (class_2561)
                if (!success) {
                    for (Field f : fields) {
                        if (f.getType().getSimpleName().equals("class_2561") || 
                            f.getName().equals("field_3743")) {
                            f.setAccessible(true);
                            f.set(entry, text);
                            success = true;
                            if (debugMode.get()) info("✓ Obfuscated field '" + f.getName() + "' (class_2561) succeeded");
                            break;
                        }
                    }
                }
            } catch (Exception fallbackException) {
                if (debugMode.get()) info("✗ Reflection fallback failed: " + fallbackException.getMessage());
            }
        }
        
        // Additional sync attempt - try common obfuscated field names
        try {
            Field displayNameField = entry.getClass().getDeclaredField("field_3743");
            displayNameField.setAccessible(true);
            displayNameField.set(entry, text);
            if (!success) success = true;
            if (debugMode.get()) info("✓ Direct field_3743 update succeeded");
        } catch (Exception ignored) {
            if (debugMode.get()) info("✗ Direct field_3743 not found or failed");
        }
        
        if (debugMode.get() && !success) {
            warning("All display name setting methods failed!");
            // List all available fields for debugging
            info("Available fields in PlayerListEntry:");
            for (Field f : entry.getClass().getDeclaredFields()) {
                info("  - " + f.getName() + " (" + f.getType().getSimpleName() + ")");
            }
        }
    }

    private void setProfileNameOnly(PlayerListEntry entry, String name) {
        if (debugMode.get()) {
            info("Setting profile name to: " + name);
        }
        
        boolean success = false;
        try {
            // Try to modify the GameProfile's name field
            Field nameField = entry.getProfile().getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entry.getProfile(), name);
            success = true;
            if (debugMode.get()) info("✓ GameProfile name field succeeded");
            
            // Also try to update any cached profile data
            try {
                Field profileField = entry.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                Object profile = profileField.get(entry);
                if (profile != null) {
                    Field cachedNameField = profile.getClass().getDeclaredField("name");
                    cachedNameField.setAccessible(true);
                    cachedNameField.set(profile, name);
                    if (debugMode.get()) info("✓ Cached profile name update succeeded");
                }
            } catch (Exception ignored) {
                if (debugMode.get()) info("✗ Cached profile update failed or not needed");
            }
            
        } catch (Exception e) {
            if (debugMode.get()) info("✗ GameProfile name field failed: " + e.getMessage());
            
            // Try alternative approaches for different Minecraft versions
            try {
                // Some versions might have a setter method
                Method setName = entry.getProfile().getClass().getMethod("setName", String.class);
                setName.invoke(entry.getProfile(), name);
                success = true;
                if (debugMode.get()) info("✓ GameProfile setName method succeeded");
            } catch (Exception ignored) {
                if (debugMode.get()) info("✗ GameProfile setName method failed");
            }
        }
        
        if (debugMode.get() && !success) {
            warning("All profile name setting methods failed!");
            // List available methods and fields for debugging
            info("Available methods in GameProfile:");
            for (Method m : entry.getProfile().getClass().getDeclaredMethods()) {
                if (m.getName().toLowerCase().contains("name")) {
                    info("  - " + m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")");
                }
            }
            info("Available fields in GameProfile:");
            for (Field f : entry.getProfile().getClass().getDeclaredFields()) {
                info("  - " + f.getName() + " (" + f.getType().getSimpleName() + ")");
            }
        }
    }

    // Legacy methods for compatibility
    private void setDisplay(PlayerListEntry entry, Text text) {
        setDisplayOnly(entry, text);
    }

    private void setProfileName(PlayerListEntry entry, String name) {
        setProfileNameOnly(entry, name);
    }

    @Override
    public void onDeactivate() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            UUID uuid = entry.getProfile().getId();
            Text originalDisplay = originalDisplayNames.get(uuid);
            String originalProfile = originalProfileNames.get(uuid);

            if (originalDisplay != null) setDisplay(entry, originalDisplay);
            if (originalProfile != null) setProfileName(entry, originalProfile);
        });

        originalDisplayNames.clear();
        originalProfileNames.clear();
        playerReplacementsByUUID.clear();
    }
}
