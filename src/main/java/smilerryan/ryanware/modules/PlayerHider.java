package smilerryan.ryanware.modules;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;

public class PlayerHider extends Module {
    public static PlayerHider INSTANCE;

    private final SettingGroup sg = settings.createGroup("Players");

    private final Setting<List<String>> playerEntries = sg.add(new StringListSetting.Builder()
            .name("player-entries")
            .description("One entry per player. Format: enabled;original;display;profile;skin (0=keep, 1=default, URL=custom)")
            .build()
    );

    private final Setting<Boolean> replacePlayers = sg.add(new BoolSetting.Builder()
            .name("replace-players")
            .description("Replace tab list, chat tab autocomplete, and outgoing messages.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> incomingChatReplacer = sg.add(new BoolSetting.Builder()
            .name("incoming-chat-replacer")
            .description("Replace player names in incoming chat messages.")
            .defaultValue(true)
            .build()
    );

    private final Map<String, Entry> replacementMap = new HashMap<>();
    private final Map<String, Entry> fakeToRealMap = new HashMap<>();
    private final Map<UUID, String> originalProfileNames = new HashMap<>();
    private final Map<UUID, Text> originalEntryDisplayNames = new HashMap<>();
    private final Map<UUID, com.mojang.authlib.properties.PropertyMap> originalSkins = new HashMap<>();
    private static Field gameProfileNameField;

    public PlayerHider() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Player-Hider",
                "Replace players' display names, chat, and command suggestions with fake names.");
        INSTANCE = this;
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
        updateReplacementMaps();
        if (replacePlayers.get()) {
            applyToPlayerList();
            applyToWorldPlayers();
        }
    }

    private void updateReplacementMaps() {
        replacementMap.clear();
        fakeToRealMap.clear();
        List<String> raw = playerEntries.get();
        for (String line : raw) {
            if (line == null) continue;
            Entry e = Entry.fromSettingLine(line);
            if (e == null || !e.enabled || e.original.isEmpty()) continue;
            replacementMap.put(e.original.toLowerCase(Locale.ROOT), e);
            if (!e.profile.isEmpty()) fakeToRealMap.put(e.profile.toLowerCase(Locale.ROOT), e);
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
                    if (!replacementEntry.display.isEmpty()) entry.setDisplayName(Text.of(replacementEntry.display));
                    else entry.setDisplayName(null);
                } catch (Throwable ignored) {}
                if (!replacementEntry.profile.isEmpty()) setProfileName(profile, replacementEntry.profile);
                if (!replacementEntry.skin.equals("0")) hidePlayerSkin(profile, replacementEntry.skin);
            } else if (originalProfileNames.containsKey(id)) restoreProfileName(profile, id);
        }
    }

    private void applyToWorldPlayers() {
        if (mc == null || mc.world == null) return;
        for (var player : mc.world.getPlayers()) {
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
                if (!replacementEntry.skin.equals("0")) hidePlayerSkin(profile, replacementEntry.skin);
            } else if (originalProfileNames.containsKey(id)) restoreProfileName(profile, id);
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
                for (var p : mc.world.getPlayers()) {
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
                if (gameProfileNameField != null) gameProfileNameField.set(profile, original);
                else replaceGameProfileField(profile, new GameProfile(profile.getId(), original));
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
        if (originalSkins.containsKey(id)) {
            try {
                profile.getProperties().clear();
                profile.getProperties().putAll(originalSkins.get(id));
            } catch (Throwable ignored) {}
            originalSkins.remove(id);
        }
    }

    private void hidePlayerSkin(GameProfile profile, String skin) {
        if (profile == null) return;
        try {
            UUID id = profile.getId();
            if (!originalSkins.containsKey(id)) {
                com.mojang.authlib.properties.PropertyMap copy = new com.mojang.authlib.properties.PropertyMap();
                copy.putAll(profile.getProperties());
                originalSkins.put(id, copy);
            }
            if (skin.equals("0")) return; // keep original
            else if (skin.equals("1")) profile.getProperties().removeAll("textures"); // default skin
            else {
                // treat as URL
                profile.getProperties().removeAll("textures");
                profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", skin));
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
                            if (original != null && gameProfileNameField != null) gameProfileNameField.set(entry.getProfile(), original);
                            else if (original != null) replaceGameProfileField(entry, new GameProfile(id, original));
                        } catch (Throwable ignored) {}
                    }
                    if (originalEntryDisplayNames.containsKey(entry.getProfile().getId())) {
                        try { entry.setDisplayName(originalEntryDisplayNames.get(entry.getProfile().getId())); } catch (Throwable ignored) {}
                    }
                    if (originalSkins.containsKey(id)) {
                        try {
                            entry.getProfile().getProperties().clear();
                            entry.getProfile().getProperties().putAll(originalSkins.get(id));
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (mc.world != null) {
                for (var player : mc.world.getPlayers()) {
                    if (player == null) continue;
                    GameProfile profile = player.getGameProfile();
                    if (profile == null) continue;
                    UUID id = profile.getId();
                    if (originalProfileNames.containsKey(id)) {
                        try {
                            String original = originalProfileNames.get(id);
                            if (original != null && gameProfileNameField != null) gameProfileNameField.set(profile, original);
                            else if (original != null) replaceGameProfileField(player, new GameProfile(id, original));
                        } catch (Throwable ignored) {}
                    }
                    if (originalSkins.containsKey(id)) {
                        try {
                            profile.getProperties().clear();
                            profile.getProperties().putAll(originalSkins.get(id));
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
        originalProfileNames.clear();
        originalEntryDisplayNames.clear();
        replacementMap.clear();
        fakeToRealMap.clear();
        originalSkins.clear();
    }

    // -----------------------------
    // Chat & Command Replacement
    // -----------------------------
    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!replacePlayers.get()) return;

        String msg = event.message;
        String replaced = msg;
        boolean changed = false;

        for (Entry e : fakeToRealMap.values()) {
            if (!e.profile.isEmpty() && !e.original.isEmpty()) {
                String temp = replaced.replaceAll("(?i)\\b" + Pattern.quote(e.profile) + "\\b", e.original);
                if (!temp.equals(replaced)) {
                    replaced = temp;
                    changed = true;
                }
            }
        }

        if (changed) event.message = replaced;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!incomingChatReplacer.get()) return;

        String msg = event.getMessage().getString();
        boolean hasPlayer = false;

        for (Entry e : replacementMap.values()) {
            if (!e.original.isEmpty() && !e.profile.isEmpty() && msg.contains(e.original)) {
                msg = msg.replace(e.original, e.profile);
                hasPlayer = true;
            }
        }

        if (hasPlayer) event.setMessage(Text.of(msg));
    }

    private static class Entry {
        boolean enabled;
        String original;
        String display;
        String profile;
        String skin; // 0=keep, 1=default, URL=custom

        Entry(boolean enabled, String original, String display, String profile, String skin) {
            this.enabled = enabled;
            this.original = original == null ? "" : original;
            this.display = display == null ? "" : display;
            this.profile = profile == null ? "" : profile;
            this.skin = skin == null ? "0" : skin;
        }

        static Entry fromSettingLine(String line) {
            if (line == null) return null;
            String[] parts = line.split(";", -1);
            boolean enabled = false;
            String original = "";
            String display = "";
            String profile = "";
            String skin = "0"; // default: keep original

            if (parts.length > 0) {
                String e = parts[0].trim();
                enabled = e.equalsIgnoreCase("true") || e.equals("1") || e.equalsIgnoreCase("enabled");
            }
            if (parts.length > 1) original = parts[1].trim();
            if (parts.length > 2) display = parts[2];
            if (parts.length > 3) profile = parts[3].trim();
            if (parts.length > 4) skin = parts[4].trim();

            return new Entry(enabled, original, display, profile, skin);
        }

        String toSettingLine() {
            return (enabled ? "true" : "false") + ";" + (original == null ? "" : original) + ";" +
                   (display == null ? "" : display) + ";" + (profile == null ? "" : profile) + ";" +
                   (skin == null ? "0" : skin);
        }
    }

    // -----------------------------
    // Mixin for command tab completion
    // -----------------------------
    @Mixin(ClientPlayNetworkHandler.class)
    public static class PlayerHiderMixin {
        @Inject(method = "onCommandSuggestions", at = @At("HEAD"), cancellable = true)
        private void injectCommandSuggestions(CommandSuggestionsS2CPacket packet, CallbackInfo ci) {
            PlayerHider module = PlayerHider.INSTANCE;
            if (module == null || !module.replacePlayers.get()) return;

            try {
                Field fInput = CommandSuggestionsS2CPacket.class.getDeclaredField("input");
                Field fStart = CommandSuggestionsS2CPacket.class.getDeclaredField("start");
                Field fSuggestions = CommandSuggestionsS2CPacket.class.getDeclaredField("suggestions");
                fInput.setAccessible(true);
                fStart.setAccessible(true);
                fSuggestions.setAccessible(true);

                String input = (String) fInput.get(packet);
                int start = (int) fStart.get(packet);
                Suggestions old = (Suggestions) fSuggestions.get(packet);

                SuggestionsBuilder builder = new SuggestionsBuilder(input, start);

                for (Suggestion s : old.getList()) {
                    String text = s.getText();
                    Entry replacement = null;
                    String lowerText = text.toLowerCase(Locale.ROOT);
                    for (Entry e : module.replacementMap.values()) {
                        if (!e.original.isEmpty() && lowerText.startsWith(e.original.toLowerCase(Locale.ROOT))) {
                            replacement = e;
                            break;
                        }
                    }
                    String finalText = (replacement != null && !replacement.profile.isEmpty()) ? replacement.profile : text;
                    builder.suggest(finalText);
                }

                fSuggestions.set(packet, builder.build());
            } catch (Throwable ignored) {}
        }
    }
}
