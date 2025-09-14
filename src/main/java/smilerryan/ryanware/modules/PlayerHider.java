package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.TextContent;
import net.minecraft.text.PlainTextContent;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PlayerHider extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    private final Setting<Boolean> debugMode = sg.add(new BoolSetting.Builder()
        .name("debug-mode").description("Enable debug logging to help troubleshoot name sync issues.").defaultValue(false)
        .build()
    );

    private final Setting<List<String>> playersToHide = sg.add(new StringListSetting.Builder()
        .name("players-to-hide").description("Players to hide/replace.").onChanged(value -> needsUpdate = true).build()
    );

    private final Setting<List<String>> replacementNames = sg.add(new StringListSetting.Builder()
        .name("replacement-names").description("Replacement names, same order. Leave empty to just hide.").onChanged(value -> needsUpdate = true).build()
    );

    private final SettingGroup locations = settings.createGroup("Locations");

    private final Setting<Boolean> replaceInTab = locations.add(new BoolSetting.Builder()
        .name("replace-in-tab").description("Replace names in the tab list.").defaultValue(true).build()
    );

    private final Setting<Boolean> replaceInChat = locations.add(new BoolSetting.Builder()
        .name("replace-in-chat").description("Replace names in chat messages.").defaultValue(true).build()
    );

    private final Setting<Boolean> replaceInNametag = locations.add(new BoolSetting.Builder()
        .name("replace-in-nametag").description("Replace names in nametags above players.").defaultValue(true).build()
    );

    private final Map<String, String> playerReplacements = new HashMap<>();
    private final Map<UUID, Text> originalDisplayNames = new HashMap<>();
    private final Set<UUID> appliedChanges = new HashSet<>();
    
    // Cache to avoid recalculating every tick
    private List<String> cachedPlayersToHide = new ArrayList<>();
    private List<String> cachedReplacementNames = new ArrayList<>();
    private boolean needsUpdate = true;
    private int tickCounter = 0;

    public PlayerHider() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Player-Hider", "Hide or replace players in tab, chat, and nametags.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        
        tickCounter++;
        // Only update when settings change or every 20 ticks (1 second)
        if (needsUpdate || tickCounter % 20 == 0) {
            updateReplacements();
            needsUpdate = false;
        }
        
        if (replaceInTab.get()) {
            applyTabChanges();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!replaceInChat.get()) return;
        
        // Handle chat messages
        if (event.packet instanceof ChatMessageS2CPacket chatPacket) {
            try {
                Text originalMessage = getChatMessage(chatPacket);
                if (originalMessage != null) {
                    Text modifiedMessage = replaceNamesInText(originalMessage);
                    if (!originalMessage.equals(modifiedMessage)) {
                        setChatMessage(chatPacket, modifiedMessage);
                    }
                }
            } catch (Exception e) {
                if (debugMode.get()) {
                    error("Failed to modify chat message: " + e.getMessage());
                }
            }
        }
        
        // Handle system messages
        if (event.packet instanceof GameMessageS2CPacket gamePacket) {
            try {
                Text originalMessage = getGameMessage(gamePacket);
                if (originalMessage != null) {
                    Text modifiedMessage = replaceNamesInText(originalMessage);
                    if (!originalMessage.equals(modifiedMessage)) {
                        setGameMessage(gamePacket, modifiedMessage);
                    }
                }
            } catch (Exception e) {
                if (debugMode.get()) {
                    error("Failed to modify game message: " + e.getMessage());
                }
            }
        }
    }

    private void updateReplacements() {
        if (mc == null) return;

        List<String> hidden = playersToHide.get();
        List<String> repls = replacementNames.get();
        
        // Check if settings changed to avoid unnecessary work
        if (hidden.equals(cachedPlayersToHide) && repls.equals(cachedReplacementNames)) {
            return;
        }
        
        // First restore all before updating
        if (replaceInTab.get()) {
            restoreAll();
        }
        
        cachedPlayersToHide = new ArrayList<>(hidden);
        cachedReplacementNames = new ArrayList<>(repls);
        playerReplacements.clear();

        // Build replacement map
        for (int i = 0; i < hidden.size(); i++) {
            String original = hidden.get(i);
            String replacement = i < repls.size() ? repls.get(i) : "";
            playerReplacements.put(original.toLowerCase(), replacement);
            
            if (debugMode.get()) {
                info("Mapping: " + original + " -> " + (replacement.isEmpty() ? "[HIDDEN]" : replacement));
            }
        }
    }

    private void applyTabChanges() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            if (entry.getProfile() == null) return;
            
            UUID uuid = entry.getProfile().getId();
            String profileName = entry.getProfile().getName();
            String replacement = playerReplacements.get(profileName.toLowerCase());
            
            if (replacement != null) {
                // Need to apply changes
                if (!appliedChanges.contains(uuid)) {
                    // Store original display name only once
                    if (!originalDisplayNames.containsKey(uuid)) {
                        originalDisplayNames.put(uuid, safeGetDisplayName(entry));
                    }
                    
                    // Create new display name by replacing only the username part
                    Text newDisplayName = createReplacedDisplayName(entry, profileName, replacement);
                    setDisplayOnly(entry, newDisplayName);
                    
                    appliedChanges.add(uuid);
                    
                    if (debugMode.get()) {
                        info("Applied tab change to " + profileName + " -> " + 
                             (replacement.isEmpty() ? "[HIDDEN]" : replacement));
                    }
                }
            } else if (appliedChanges.contains(uuid)) {
                // Need to restore changes
                restorePlayer(entry, uuid);
            }
        });
    }

    private Text createReplacedDisplayName(PlayerListEntry entry, String originalName, String replacement) {
        Text originalDisplay = safeGetDisplayName(entry);
        
        if (originalDisplay == null) {
            // No display name, just return the replacement or hidden text
            return replacement.isEmpty() ? Text.literal("§8[Hidden]") : Text.literal(replacement);
        }
        
        String displayString = originalDisplay.getString();
        
        if (replacement.isEmpty()) {
            // Hide by replacing the name with [Hidden]
            String hiddenString = displayString.replaceAll("(?i)\\b" + Pattern.quote(originalName) + "\\b", "§8[Hidden]§r");
            return Text.literal(hiddenString);
        } else {
            // Replace the name while preserving formatting
            return replaceNameInText(originalDisplay, originalName, replacement);
        }
    }

    private Text replaceNamesInText(Text text) {
        if (text == null || playerReplacements.isEmpty()) return text;
        
        String textString = text.getString();
        boolean modified = false;
        
        for (Map.Entry<String, String> entry : playerReplacements.entrySet()) {
            String originalName = entry.getKey();
            String replacement = entry.getValue();
            
            // Create case-insensitive pattern that matches whole words
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(originalName) + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(textString);
            
            if (matcher.find()) {
                if (replacement.isEmpty()) {
                    textString = matcher.replaceAll("§8[Hidden]§r");
                } else {
                    textString = matcher.replaceAll(replacement);
                }
                modified = true;
            }
        }
        
        return modified ? Text.literal(textString) : text;
    }

    private Text replaceNameInText(Text text, String originalName, String replacement) {
        if (text == null) return null;
        
        try {
            // Try to preserve the original text's formatting
            String textString = text.getString();
            String replacedString = textString.replaceAll("(?i)\\b" + Pattern.quote(originalName) + "\\b", replacement);
            
            if (!textString.equals(replacedString)) {
                // Create a new text with similar formatting
                return Text.literal(replacedString);
            }
        } catch (Exception e) {
            if (debugMode.get()) {
                error("Failed to replace name in text: " + e.getMessage());
            }
        }
        
        return text;
    }

    // Nametag replacement (this would need to be hooked into the rendering system)
    public String getReplacementName(String originalName) {
        if (!replaceInNametag.get()) {
            return originalName;
        }
        
        String replacement = playerReplacements.get(originalName.toLowerCase());
        if (replacement != null) {
            return replacement.isEmpty() ? "§8[Hidden]" : replacement;
        }
        
        return originalName;
    }

    private Text getChatMessage(ChatMessageS2CPacket packet) {
        try {
            // Try different field names for the message
            Field messageField = packet.getClass().getDeclaredField("message");
            messageField.setAccessible(true);
            return (Text) messageField.get(packet);
        } catch (Exception e1) {
            try {
                Method getMessage = packet.getClass().getMethod("getMessage");
                return (Text) getMessage.invoke(packet);
            } catch (Exception e2) {
                if (debugMode.get()) {
                    error("Failed to get chat message: " + e2.getMessage());
                }
            }
        }
        return null;
    }

    private void setChatMessage(ChatMessageS2CPacket packet, Text newMessage) {
        try {
            Field messageField = packet.getClass().getDeclaredField("message");
            messageField.setAccessible(true);
            messageField.set(packet, newMessage);
        } catch (Exception e) {
            if (debugMode.get()) {
                error("Failed to set chat message: " + e.getMessage());
            }
        }
    }

    private Text getGameMessage(GameMessageS2CPacket packet) {
        try {
            Field messageField = packet.getClass().getDeclaredField("message");
            messageField.setAccessible(true);
            return (Text) messageField.get(packet);
        } catch (Exception e1) {
            try {
                Method getMessage = packet.getClass().getMethod("getMessage");
                return (Text) getMessage.invoke(packet);
            } catch (Exception e2) {
                if (debugMode.get()) {
                    error("Failed to get game message: " + e2.getMessage());
                }
            }
        }
        return null;
    }

    private void setGameMessage(GameMessageS2CPacket packet, Text newMessage) {
        try {
            Field messageField = packet.getClass().getDeclaredField("message");
            messageField.setAccessible(true);
            messageField.set(packet, newMessage);
        } catch (Exception e) {
            if (debugMode.get()) {
                error("Failed to set game message: " + e.getMessage());
            }
        }
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
        playerReplacements.clear();
        cachedPlayersToHide.clear();
        cachedReplacementNames.clear();
    }
}