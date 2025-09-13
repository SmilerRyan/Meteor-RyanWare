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

    private final Setting<Boolean> hidePlayersEnabled = sg.add(new BoolSetting.Builder()
        .name("hide-players").description("Enable hiding specific players from tab and nametags.").defaultValue(false).build()
    );

    private final Setting<List<String>> playersToHide = sg.add(new StringListSetting.Builder()
        .name("players-to-hide").description("Players to hide.").visible(hidePlayersEnabled::get).build()
    );

    private final Setting<List<String>> replacementNames = sg.add(new StringListSetting.Builder()
        .name("replacement-names").description("Replacement names, same order. Leave empty to just hide.").visible(hidePlayersEnabled::get).build()
    );

    private final Map<UUID, String> playerReplacementsByUUID = new HashMap<>();
    private final Map<UUID, Text> originalDisplayNames = new HashMap<>();
    private final Map<UUID, String> originalProfileNames = new HashMap<>();

    public PlayerHider() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Player-Hider", "Hide or replace players in tab and nametags.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        updateReplacements();
        applyOrRestore();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!hidePlayersEnabled.get()) return;
        if (mc == null || mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            UUID uuid = entry.getProfile().getId();
            if (!playerReplacementsByUUID.containsKey(uuid)) return;

            String replacement = playerReplacementsByUUID.get(uuid);
            String currentProfileName = entry.getProfile().getName();
            Text currentDisplay = safeGetDisplayName(entry);

            // Only apply if different from current
            if ((replacement.isEmpty() && (currentProfileName.isEmpty() && (currentDisplay == null || currentDisplay.getString().isEmpty()))) ||
                (!replacement.isEmpty() && replacement.equals(currentProfileName) && currentDisplay != null && replacement.equals(currentDisplay.getString()))) {
                return;
            }

            originalDisplayNames.putIfAbsent(uuid, currentDisplay);
            originalProfileNames.putIfAbsent(uuid, currentProfileName);

            if (replacement.isEmpty()) {
                setDisplay(entry, Text.literal(""));
                setProfileName(entry, "");
            } else {
                setDisplay(entry, Text.literal(replacement));
                setProfileName(entry, replacement);
            }
        });
    }

    private void updateReplacements() {
        playerReplacementsByUUID.clear();
        if (!hidePlayersEnabled.get() || mc == null || mc.getNetworkHandler() == null) return;

        List<String> hidden = playersToHide.get();
        List<String> repls = replacementNames.get();

        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            String name = entry.getProfile().getName();
            for (int i = 0; i < hidden.size(); i++) {
                if (hidden.get(i).equalsIgnoreCase(name)) {
                    String repl = i < repls.size() ? repls.get(i) : "";
                    playerReplacementsByUUID.put(entry.getProfile().getId(), repl);
                    break;
                }
            }
        });
    }

    private void applyOrRestore() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        mc.getNetworkHandler().getPlayerList().forEach(entry -> {
            UUID uuid = entry.getProfile().getId();
            if (!playerReplacementsByUUID.containsKey(uuid)) {
                Text originalDisplay = originalDisplayNames.get(uuid);
                String originalProfile = originalProfileNames.get(uuid);

                if (originalDisplay != null) setDisplay(entry, originalDisplay);
                if (originalProfile != null) setProfileName(entry, originalProfile);

                originalDisplayNames.remove(uuid);
                originalProfileNames.remove(uuid);
            }
        });
    }

    private Text safeGetDisplayName(PlayerListEntry entry) {
        try {
            Method m = entry.getClass().getMethod("getDisplayName");
            return (Text) m.invoke(entry);
        } catch (Exception ignored) {}
        try {
            for (Field f : entry.getClass().getDeclaredFields()) {
                if (f.getType() == Text.class && f.getName().toLowerCase().contains("display")) {
                    f.setAccessible(true);
                    return (Text) f.get(entry);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void setDisplay(PlayerListEntry entry, Text text) {
        try {
            Method m = entry.getClass().getMethod("setDisplayName", Text.class);
            m.invoke(entry, text);
        } catch (Exception ignored) {}
    }

    private void setProfileName(PlayerListEntry entry, String name) {
        try {
            Field f = entry.getProfile().getClass().getDeclaredField("name");
            f.setAccessible(true);
            f.set(entry.getProfile(), name);
        } catch (Exception ignored) {}
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
