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
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

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
                if (debugMode.get()) error("Failed to modify chat message: " + e.getMessage());
            }
        }

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
                if (debugMode.get()) error("Failed to modify game message: " + e.getMessage());
            }
        }
    }

    private void updateReplacements() {
        if (mc == null) return;

        List<String> hidden = playersToHide.get();
        List<String> repls = replacementNames.get();

        if (hidden.equals(cachedPlayersToHide) && repls.equals(cachedReplacementNames)) return;

        if (replaceInTab.get()) restoreAll();

        cachedPlayersToHide = new ArrayList<>(hidden);
        cachedReplacementNames = new ArrayList<>(repls);
        playerReplacements.clear();

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

            if (playerReplacements.containsKey(profileName.toLowerCase())) {
                if (!appliedChanges.contains(uuid)) {
                    if (!originalDisplayNames.containsKey(uuid)) {
                        originalDisplayNames.put(uuid, safeGetDisplayName(entry));
                    }

                    Text newDisplayName = createReplacedDisplayName(entry, profileName);
                    setDisplayOnly(entry, newDisplayName);

                    appliedChanges.add(uuid);

                    if (debugMode.get()) {
                        info("Applied tab change to " + profileName);
                    }
                }
            } else if (appliedChanges.contains(uuid)) {
                restorePlayer(entry, uuid);
            }
        });
    }

    private Text createReplacedDisplayName(PlayerListEntry entry, String originalName) {
        Text originalDisplay = safeGetDisplayName(entry);
        if (originalDisplay == null) {
            String replacement = playerReplacements.getOrDefault(originalName.toLowerCase(), "");
            return replacement.isEmpty() ? Text.literal("§8[Hidden]") : Text.literal(replacement);
        }
        return deepReplace(originalDisplay);
    }

    private Text replaceNamesInText(Text text) {
        if (text == null || playerReplacements.isEmpty()) return text;
        return deepReplace(text);
    }

    private Text deepReplace(Text text) {
        if (text == null) return null;

        String replaced = text.getString();
        for (Map.Entry<String, String> entry : playerReplacements.entrySet()) {
            String originalName = entry.getKey();
            String replacement = entry.getValue();
            replaced = replaced.replaceAll("(?i)\\b" + Pattern.quote(originalName) + "\\b",
                replacement.isEmpty() ? "§8[Hidden]§r" : replacement);
        }

        MutableText rebuilt = Text.literal(replaced).setStyle(text.getStyle());
        for (Text sibling : text.getSiblings()) {
            rebuilt.append(deepReplace(sibling));
        }
        return rebuilt;
    }

    // Nametag replacement now preserves formatting
    public Text getReplacementName(Text originalNameText) {
        if (!replaceInNametag.get()) return originalNameText;
        return deepReplace(originalNameText);
    }

    private Text getChatMessage(ChatMessageS2CPacket packet) {
        try {
            Field messageField = packet.getClass().getDeclaredField("message");
            messageField.setAccessible(true);
            return (Text) messageField.get(packet);
        } catch (Exception e1) {
            try {
                Method getMessage = packet.getClass().getMethod("getMessage");
                return (Text) getMessage.invoke(packet);
            } catch (Exception e2) {
                if (debugMode.get()) error("Failed to get chat message: " + e2.getMessage());
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
            if (debugMode.get()) error("Failed to set chat message: " + e.getMessage());
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
                if (debugMode.get()) error("Failed to get game message: " + e2.getMessage());
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
            if (debugMode.get()) error("Failed to set game message: " + e.getMessage());
        }
    }

    private void restorePlayer(PlayerListEntry entry, UUID uuid) {
        Text originalDisplay = originalDisplayNames.get(uuid);
        if (originalDisplay != null) {
            setDisplayOnly(entry, originalDisplay);
        } else {
            setDisplayOnly(entry, null);
        }
        originalDisplayNames.remove(uuid);
        appliedChanges.remove(uuid);

        if (debugMode.get()) info("Restored " + entry.getProfile().getName());
    }

    private void restoreAll() {
        if (mc == null || mc.getNetworkHandler() == null) return;
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
            try {
                for (Field f : entry.getClass().getDeclaredFields()) {
                    if (f.getType() == Text.class && f.getName().toLowerCase().contains("display")) {
                        f.setAccessible(true);
                        return (Text) f.get(entry);
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void setDisplayOnly(PlayerListEntry entry, Text text) {
        boolean success = false;

        try {
            Method setDisplayName = entry.getClass().getMethod("setDisplayName", Text.class);
            setDisplayName.invoke(entry, text);
            success = true;
        } catch (Exception ignored) {}

        if (!success) {
            try {
                Field displayNameField = entry.getClass().getDeclaredField("displayName");
                displayNameField.setAccessible(true);
                displayNameField.set(entry, text);
                success = true;
            } catch (Exception ignored) {}
        }

        if (!success) {
            String[] possibleFieldNames = {"field_3743", "field_2773", "field_2774", "d", "e"};
            for (String fieldName : possibleFieldNames) {
                try {
                    Field f = entry.getClass().getDeclaredField(fieldName);
                    if (f.getType() == Text.class || f.getType().getSimpleName().equals("class_2561")) {
                        f.setAccessible(true);
                        f.set(entry, text);
                        success = true;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (!success) {
            try {
                Field[] fields = entry.getClass().getDeclaredFields();
                for (Field f : fields) {
                    if (f.getType() == Text.class || f.getType().getSimpleName().equals("class_2561")) {
                        f.setAccessible(true);
                        f.set(entry, text);
                        break;
                    }
                }
            } catch (Exception ignored) {}
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
