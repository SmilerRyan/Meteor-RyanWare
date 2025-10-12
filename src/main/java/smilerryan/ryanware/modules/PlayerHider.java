package smilerryan.ryanware.modules;

import com.mojang.authlib.GameProfile;
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
 * Changes client-side GameProfile.name via reflection so tab + nametag + other mods
 * will see the replaced name. Restores originals on disable.
 */
public class PlayerHider extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    private final Setting<List<String>> playersToHide = sg.add(new StringListSetting.Builder()
        .name("players-to-hide")
        .description("Players to replace (real username).")
        .onChanged(v -> needsUpdate = true)
        .build()
    );

    private final Setting<List<String>> replacementNames = sg.add(new StringListSetting.Builder()
        .name("replacement-names")
        .description("Replacement names (same order). Empty = ignore.")
        .onChanged(v -> needsUpdate = true)
        .build()
    );

    // replacement lookup: lowercase original username -> replacement
    private final Map<String, String> replacements = new HashMap<>();
    private boolean needsUpdate = true;

    // store originals per-player to restore on disable: UUID -> originalName
    private final Map<UUID, String> originalProfileNames = new HashMap<>();

    // store original display names used by PlayerListEntry (so we can restore displayName if it was non-null)
    private final Map<UUID, Text> originalEntryDisplayNames = new HashMap<>();

    // Cached reflection field for GameProfile.name
    private static Field gameProfileNameField;

    public PlayerHider() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Player-Hider", "Replace players' display names (tab & nametag) without mixins.");
        initReflection();
    }

    // Initialize reflection for GameProfile.name
    private void initReflection() {
        if (gameProfileNameField != null) return;
        try {
            gameProfileNameField = GameProfile.class.getDeclaredField("name");
            gameProfileNameField.setAccessible(true);

            // try to remove final modifier if present (best-effort)
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(gameProfileNameField, gameProfileNameField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            // Reflection failed — leave field null and we'll attempt fallbacks later
            gameProfileNameField = null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (needsUpdate) {
            updateReplacements();
            needsUpdate = false;
        }

        applyToPlayerList();
        applyToWorldPlayers();
    }

    private void updateReplacements() {
        replacements.clear();
        List<String> hide = playersToHide.get();
        List<String> repl = replacementNames.get();

        for (int i = 0; i < hide.size(); i++) {
            String original = hide.get(i) == null ? "" : hide.get(i).trim();
            String replacement = i < repl.size() && repl.get(i) != null ? repl.get(i).trim() : "";
            if (!original.isEmpty() && !replacement.isEmpty()) {
                replacements.put(original.toLowerCase(Locale.ROOT), replacement);
            }
        }
    }

    // Iterate tab list entries and change the profile name (and clear custom displayName so profile name is used)
    private void applyToPlayerList() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) continue;

            GameProfile profile = entry.getProfile();
            UUID id = profile.getId();
            String currentName = profile.getName();

            // Determine the original name we should match against.
            // If we have stored original, use that; otherwise use the current name (first-seen).
            String originalName = originalProfileNames.containsKey(id) ? originalProfileNames.get(id) : currentName;
            String key = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);

            String replacement = replacements.get(key);

            if (replacement != null) {
                // Save original profile name if not saved already
                if (!originalProfileNames.containsKey(id)) {
                    originalProfileNames.put(id, currentName);
                }

                // Save and clear entry displayName so tab shows profile name (if it had a custom display name)
                try {
                    if (!originalEntryDisplayNames.containsKey(id)) {
                        originalEntryDisplayNames.put(id, entry.getDisplayName());
                    }
                    // Clear explicit display name so client uses profile.name
                    entry.setDisplayName(null);
                } catch (Throwable ignored) {}

                // Apply replacement to the GameProfile's name field (client-side only)
                setProfileName(profile, replacement);
            } else {
                // If we previously modified this profile but now no replacement exists, restore
                if (originalProfileNames.containsKey(id)) {
                    restoreProfileName(profile, id);
                }
            }
        }
    }

    // Iterate world players and ensure their GameProfile reflects replacement too
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

            String replacement = replacements.get(key);

            if (replacement != null) {
                if (!originalProfileNames.containsKey(id)) {
                    originalProfileNames.put(id, currentName);
                }
                setProfileName(profile, replacement);
            } else {
                if (originalProfileNames.containsKey(id)) {
                    restoreProfileName(profile, id);
                }
            }
        }
    }

    // Attempt to set GameProfile.name via reflection. Silent fail if not possible.
    private void setProfileName(GameProfile profile, String newName) {
        if (profile == null) return;
        try {
            if (gameProfileNameField != null) {
                String cur = profile.getName();
                if (!Objects.equals(cur, newName)) {
                    gameProfileNameField.set(profile, newName);
                }
                return;
            }
        } catch (Throwable ignored) {}

        // Fallback (best-effort): attempt to replace the profile object fields in-place using reflection (rare)
        try {
            // build a new GameProfile with same id and new name and try to inject into common holders
            GameProfile newProfile = new GameProfile(profile.getId(), newName);

            // Try replacing the profile object in known holders:
            //  - PlayerListEntry.profile field
            //  - PlayerEntity.gameProfile field
            // We'll search for fields of type GameProfile in these objects and set them.

            // Replace in PlayerListEntry instances in the network handler
            if (mc != null && mc.getNetworkHandler() != null) {
                for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                    if (entry != null && entry.getProfile() == profile) {
                        replaceGameProfileField(entry, newProfile);
                    }
                }
            }

            // Replace in world PlayerEntity instances
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

    // Replace any GameProfile-typed declared field on the target object with newProfile (best-effort)
    private void replaceGameProfileField(Object target, GameProfile newProfile) {
        if (target == null || newProfile == null) return;
        try {
            Field[] fields = target.getClass().getDeclaredFields();
            for (Field f : fields) {
                if (f.getType() == GameProfile.class) {
                    f.setAccessible(true);
                    try {
                        f.set(target, newProfile);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    // Restore a single profile by UUID using saved originalProfileNames and originalEntryDisplayNames
    private void restoreProfileName(GameProfile profile, UUID id) {
        if (profile == null || id == null) return;
        try {
            String original = originalProfileNames.remove(id);
            if (original != null) {
                if (gameProfileNameField != null) {
                    gameProfileNameField.set(profile, original);
                } else {
                    // fallback: try to replace profile object with new one containing original name
                    GameProfile restored = new GameProfile(profile.getId(), original);
                    // try to inject back into PlayerListEntry(s)
                    if (mc != null && mc.getNetworkHandler() != null) {
                        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                            if (entry != null && entry.getProfile() == profile) {
                                replaceGameProfileField(entry, restored);
                            }
                        }
                    }
                    // and into world players
                    if (mc != null && mc.world != null) {
                        for (PlayerEntity p : mc.world.getPlayers()) {
                            GameProfile gp = p.getGameProfile();
                            if (gp == profile) replaceGameProfileField(p, restored);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Restore entry displayName if we saved it
        try {
            if (originalEntryDisplayNames.containsKey(id) && mc != null && mc.getNetworkHandler() != null) {
                for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                    if (entry == null || entry.getProfile() == null) continue;
                    if (id.equals(entry.getProfile().getId())) {
                        Text orig = originalEntryDisplayNames.remove(id);
                        try {
                            entry.setDisplayName(orig);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDeactivate() {
        // Restore all stored original profile names
        if (mc != null) {
            // Restore PlayerListEntry and world PlayerEntity profiles
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
                                // fallback replace profile object
                                replaceGameProfileField(entry, new GameProfile(id, original));
                            }
                        } catch (Throwable ignored) {}
                    }
                    // restore display name if saved
                    if (originalEntryDisplayNames.containsKey(entry.getProfile().getId())) {
                        try {
                            entry.setDisplayName(originalEntryDisplayNames.get(entry.getProfile().getId()));
                        } catch (Throwable ignored) {}
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

        // Clear stored originals
        originalProfileNames.clear();
        originalEntryDisplayNames.clear();
        replacements.clear();
    }
}
