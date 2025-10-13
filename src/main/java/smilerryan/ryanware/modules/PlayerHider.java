package smilerryan.ryanware.modules;

import com.mojang.authlib.GameProfile;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
import java.util.*;

/**
 * PlayerHider (single-file, no mixin json edits).
 *
 * Uses a StringListSetting whose each element encodes one player-entry:
 *   enabled;original;display;profile
 *
 * Example:
 *   true;Notch;§cCool Guy;Cool_Guy
 *   false;Dinnerbone;;dinnerbone_real
 *
 * This provides dynamic add/remove in Meteor's standard settings UI while keeping
 * the code single-file and simple. Each entry can be enabled/disabled individually.
 *
 * Added: chat & command replacements:
 * - Outgoing chat/commands: profile(4) -> original(2)
 * - Incoming messages: original(2) -> profile(4)
 */
public class PlayerHider extends Module {
    private final SettingGroup sg = settings.createGroup("Players");

    private final Setting<List<String>> playerEntries = sg.add(new StringListSetting.Builder()
        .name("player-entries")
        .description("One entry per player. Format: enabled;original;display;profile. Example: true;Notch;§cCool Guy;Cool_Guy")
        .build()
    );

    private final Map<String, Entry> replacementMap = new HashMap<>();
    private final Map<UUID, String> originalProfileNames = new HashMap<>();
    private final Map<UUID, Text> originalEntryDisplayNames = new HashMap<>();
    private static Field gameProfileNameField;

    public PlayerHider() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Player-Hider", "Replace players' display names (tab & nametag) without mixins. Use settings list with entries: enabled;original;display;profile");
        initReflection();
    }

    private void initReflection() {
        if (gameProfileNameField != null) return;
        try {
            gameProfileNameField = GameProfile.class.getDeclaredField("name");
            gameProfileNameField.setAccessible(true);
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(gameProfileNameField, gameProfileNameField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            gameProfileNameField = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        updateReplacementMap();
        applyToPlayerList();
        applyToWorldPlayers();
    }

    private void updateReplacementMap() {
        replacementMap.clear();
        List<String> raw = playerEntries.get();

        for (String line : raw) {
            if (line == null) continue;
            Entry e = Entry.fromSettingLine(line);
            if (e == null) continue;
            if (!e.enabled) continue;
            if (e.original == null || e.original.isEmpty()) continue;

            String key = e.original.toLowerCase(Locale.ROOT);
            replacementMap.put(key, e);
        }
    }

    private void applyToPlayerList() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;

            GameProfile profile = entry.getProfile();
            UUID id = profile.getId();
            String currentName = profile.getName();
            String originalName = originalProfileNames.containsKey(id) ? originalProfileNames.get(id) : currentName;
            String key = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);

            Entry replacementEntry = replacementMap.get(key);

            if (replacementEntry != null) {
                if (!originalProfileNames.containsKey(id)) originalProfileNames.put(id, currentName);

                try {
                    if (!originalEntryDisplayNames.containsKey(id)) originalEntryDisplayNames.put(id, entry.getDisplayName());
                    if (!replacementEntry.display.isEmpty()) {
                        entry.setDisplayName(Text.of(replacementEntry.display));
                    } else {
                        entry.setDisplayName(null);
                    }
                } catch (Throwable ignored) {}

                if (!replacementEntry.profile.isEmpty()) setProfileName(profile, replacementEntry.profile);
            } else {
                if (originalProfileNames.containsKey(id)) restoreProfileName(profile, id);
            }
        }
    }

    private void applyToWorldPlayers() {
        if (mc == null || mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null) continue;
            GameProfile profile = player.getGameProfile();
            if (profile == null) continue;

            UUID id = profile.getId();
            String currentName = profile.getName();
            String originalName = originalProfileNames.containsKey(id) ? originalProfileNames.get(id) : currentName;
            String key = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);

            Entry replacementEntry = replacementMap.get(key);

            if (replacementEntry != null) {
                if (!originalProfileNames.containsKey(id)) originalProfileNames.put(id, currentName);
                if (!replacementEntry.profile.isEmpty()) setProfileName(profile, replacementEntry.profile);
            } else {
                if (originalProfileNames.containsKey(id)) restoreProfileName(profile, id);
            }
        }
    }

    private void setProfileName(GameProfile profile, String newName) {
        if (profile == null) return;
        try {
            if (gameProfileNameField != null) {
                String cur = profile.getName();
                if (!Objects.equals(cur, newName)) gameProfileNameField.set(profile, newName);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            GameProfile newProfile = new GameProfile(profile.getId(), newName);
            if (mc != null && mc.getNetworkHandler() != null) {
                for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                    if (entry != null && entry.getProfile() == profile) replaceGameProfileField(entry, newProfile);
                }
            }
            if (mc != null && mc.world != null) {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    try {
                        GameProfile gp = p.getGameProfile();
                        if (gp == profile) replaceGameProfileField(p, newProfile);
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private void replaceGameProfileField(Object target, GameProfile newProfile) {
        if (target == null || newProfile == null) return;
        try {
            Field[] fields = target.getClass().getDeclaredFields();
            for (Field f : fields) {
                if (f.getType() == GameProfile.class) {
                    f.setAccessible(true);
                    try { f.set(target, newProfile); return; } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private void restoreProfileName(GameProfile profile, UUID id) {
        if (profile == null || id == null) return;
        try {
            String original = originalProfileNames.remove(id);
            if (original != null) {
                if (gameProfileNameField != null) {
                    gameProfileNameField.set(profile, original);
                } else {
                    GameProfile restored = new GameProfile(profile.getId(), original);
                    if (mc != null && mc.getNetworkHandler() != null) {
                        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                            if (entry != null && entry.getProfile() == profile) replaceGameProfileField(entry, restored);
                        }
                    }
                    if (mc != null && mc.world != null) {
                        for (PlayerEntity p : mc.world.getPlayers()) {
                            GameProfile gp = p.getGameProfile();
                            if (gp == profile) replaceGameProfileField(p, restored);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            if (originalEntryDisplayNames.containsKey(id) && mc != null && mc.getNetworkHandler() != null) {
                for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                    if (entry == null || entry.getProfile() == null) continue;
                    if (id.equals(entry.getProfile().getId())) {
                        Text orig = originalEntryDisplayNames.remove(id);
                        try { entry.setDisplayName(orig); } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDeactivate() {
        if (mc != null) {
            if (mc.getNetworkHandler() != null) {
                for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                    if (entry == null || entry.getProfile() == null) continue;
                    UUID id = entry.getProfile().getId();
                    if (originalProfileNames.containsKey(id)) {
                        try {
                            String original = originalProfileNames.get(id);
                            if (original != null && gameProfileNameField != null) {
                                gameProfileNameField.set(entry.getProfile(), original);
                            } else if (original != null) {
                                replaceGameProfileField(entry, new GameProfile(id, original));
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (originalEntryDisplayNames.containsKey(entry.getProfile().getId())) {
                        try { entry.setDisplayName(originalEntryDisplayNames.get(entry.getProfile().getId())); } catch (Throwable ignored) {}
                    }
                }
            }

            if (mc.world != null) {
                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player == null) continue;
                    GameProfile profile = player.getGameProfile();
                    if (profile == null) continue;
                    UUID id = profile.getId();
                    if (originalProfileNames.containsKey(id)) {
                        try {
                            String original = originalProfileNames.get(id);
                            if (original != null && gameProfileNameField != null) {
                                gameProfileNameField.set(profile, original);
                            } else if (original != null) {
                                replaceGameProfileField(player, new GameProfile(id, original));
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }

        originalProfileNames.clear();
        originalEntryDisplayNames.clear();
        replacementMap.clear();
    }

    // -----------------------------
    // Chat/Command replacement
    // -----------------------------

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String msg = event.message;
        for (Entry e : replacementMap.values()) {
            if (!e.profile.isEmpty() && !e.original.isEmpty()) {
                msg = msg.replace(e.profile, e.original);
            }
        }
        event.message = msg;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        for (Entry e : replacementMap.values()) {
            if (!e.original.isEmpty() && !e.profile.isEmpty()) {
                msg = msg.replace(e.original, e.profile);
            }
        }
        event.setMessage(Text.of(msg));
    }

    // -----------------------------
    // Helper: Entry parse/format
    // -----------------------------
    private static class Entry {
        boolean enabled;
        String original;
        String display;
        String profile;

        Entry(boolean enabled, String original, String display, String profile) {
            this.enabled = enabled;
            this.original = original == null ? "" : original;
            this.display = display == null ? "" : display;
            this.profile = profile == null ? "" : profile;
        }

        static Entry fromSettingLine(String line) {
            if (line == null) return null;
            String[] parts = line.split(";", -1);
            boolean enabled = false;
            String original = "";
            String display = "";
            String profile = "";

            if (parts.length > 0) {
                String e = parts[0].trim();
                enabled = e.equalsIgnoreCase("true") || e.equals("1") || e.equalsIgnoreCase("enabled");
            }
            if (parts.length > 1) original = parts[1].trim();
            if (parts.length > 2) display = parts[2];
            if (parts.length > 3) profile = parts[3].trim();

            return new Entry(enabled, original, display, profile);
        }

        String toSettingLine() {
            return (enabled ? "true" : "false") + ";" + (original == null ? "" : original) + ";" + (display == null ? "" : display) + ";" + (profile == null ? "" : profile);
        }
    }
}
