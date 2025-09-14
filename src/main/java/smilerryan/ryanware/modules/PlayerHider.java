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
    private final Set<UUID> appliedChanges = new HashSet<>();
    
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
        if (!hidePlayersEnabled.get()) {
            restoreAll();
            return;
        }
        
        tickCounter++;
        // Only update when settings change or every 20 ticks (1 second)
        if (needsUpdate || tickCounter % 20 == 0) {
            updateReplacements();
            needsUpdate = false;
        }
        
        applyChanges();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        // Let the tick handler manage changes to avoid conflicts
    }

    private void updateReplacements() {
        if (mc == null || mc.getNetworkHandler() == null) {
            playerReplacementsByUUID.clear();
            return;
        }

        List<String> hidden = playersToHide.get();
        List<String> repls = replacementNames.get();
        
        // Check if settings changed to avoid unnecessary work
        if (hidden.equals(cachedPlayersToHide) && repls.equals(cachedReplacementNames)) {
            return;
        }
        
        // First restore all before updating
        restoreAll();
        
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
    }

    private void applyChanges() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            if (entry.getProfile() == null) return;
            
            UUID uuid = entry.getProfile().getId();
            
            if (playerReplacementsByUUID.containsKey(uuid)) {
                // Need to apply changes
                if (!appliedChanges.contains(uuid)) {
                    String replacement = playerReplacementsByUUID.get(uuid);
                    
                    // Store original display name only once
                    if (!originalDisplayNames.containsKey(uuid)) {
                        originalDisplayNames.put(uuid, safeGetDisplayName(entry));
                    }
                    
                    // NEVER modify the profile name - only the display name
                    // This preserves all tab list functionality (ping, position, scoreboard)
                    if (replacement.isEmpty()) {
                        // For hiding: use invisible text but keep it non-null
                        setDisplayOnly(entry, Text.literal("§r§0"));
                    } else {
                        setDisplayOnly(entry, Text.literal(replacement));
                    }
                    
                    appliedChanges.add(uuid);
                    
                    if (debugMode.get()) {
                        info("Applied change to " + entry.getProfile().getName() + " -> " + 
                             (replacement.isEmpty() ? "[HIDDEN]" : replacement));
                    }
                }
            } else if (appliedChanges.contains(uuid)) {
                // Need to restore changes
                restorePlayer(entry, uuid);
            }
        });
    }

    private void restorePlayer(PlayerListEntry entry, UUID uuid) {
        Text originalDisplay = originalDisplayNames.get(uuid);

        if (originalDisplay != null) {
            setDisplayOnly(entry, originalDisplay);
        } else {
            // If no original display name, clear it to let the game use the profile name
            setDisplayOnly(entry, null);
        }

        originalDisplayNames.remove(uuid);
        appliedChanges.remove(uuid);
        
        if (debugMode.get()) {
            info("Restored " + entry.getProfile().getName());
        }
    }

    private void restoreAll() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        // Create a copy of the set to avoid concurrent modification
        Set<UUID> toRestore = new HashSet<>(appliedChanges);
        
        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            if (entry.getProfile() == null) return;
            
            UUID uuid = entry.getProfile().getId();
            if (toRestore.contains(uuid)) {
                restorePlayer(entry, uuid);
            }
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
                if (debugMode.get()) {
                    info("Failed to get display name: " + fallbackException.getMessage());
                }
            }
        }
        return null;
    }

    private void setDisplayOnly(PlayerListEntry entry, Text text) {
        if (debugMode.get()) {
            info("Setting display name to: " + (text != null ? text.getString() : "null"));
        }
        
        boolean success = false;
        
        // Method 1: Try the standard setDisplayName method
        try {
            Method setDisplayName = entry.getClass().getMethod("setDisplayName", Text.class);
            setDisplayName.invoke(entry, text);
            success = true;
            if (debugMode.get()) info("✓ setDisplayName method succeeded");
        } catch (Exception e) {
            if (debugMode.get()) info("✗ setDisplayName method failed: " + e.getMessage());
        }
        
        // Method 2: Try direct field access
        if (!success) {
            try {
                Field displayNameField = entry.getClass().getDeclaredField("displayName");
                displayNameField.setAccessible(true);
                displayNameField.set(entry, text);
                success = true;
                if (debugMode.get()) info("✓ displayName field succeeded");
            } catch (Exception e) {
                if (debugMode.get()) info("✗ displayName field failed: " + e.getMessage());
            }
        }
        
        // Method 3: Try common obfuscated field names
        if (!success) {
            String[] possibleFieldNames = {"field_3743", "field_2773", "field_2774", "d", "e"};
            for (String fieldName : possibleFieldNames) {
                try {
                    Field f = entry.getClass().getDeclaredField(fieldName);
                    if (f.getType() == Text.class || f.getType().getSimpleName().equals("class_2561")) {
                        f.setAccessible(true);
                        f.set(entry, text);
                        success = true;
                        if (debugMode.get()) info("✓ Obfuscated field '" + fieldName + "' succeeded");
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        
        // Method 4: Search through all Text fields as last resort
        if (!success) {
            try {
                Field[] fields = entry.getClass().getDeclaredFields();
                for (Field f : fields) {
                    if (f.getType() == Text.class || f.getType().getSimpleName().equals("class_2561")) {
                        f.setAccessible(true);
                        f.set(entry, text);
                        success = true;
                        if (debugMode.get()) info("✓ Generic Text field '" + f.getName() + "' succeeded");
                        break;
                    }
                }
            } catch (Exception e) {
                if (debugMode.get()) info("✗ Generic field search failed: " + e.getMessage());
            }
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

    @Override
    public void onDeactivate() {
        restoreAll();
        playerReplacementsByUUID.clear();
        cachedPlayersToHide.clear();
        cachedReplacementNames.clear();
    }
}