package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomTabText extends Module {
    private final SettingGroup sgHeader = settings.createGroup("Header Settings");
    private final SettingGroup sgFooter = settings.createGroup("Footer Settings");
    private final SettingGroup sgPlayerHiding = settings.createGroup("Player Hiding");

    public enum HeaderMode { Remove, Replace, AddToTop, AddToEnd }
    public enum FooterMode { Remove, Replace, AddToTop, AddToEnd }

    private final Setting<Boolean> customHeaderEnabled = sgHeader.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("custom-header").description("Enable custom header modifications.").defaultValue(true).build()
    );

    private final Setting<HeaderMode> headerMode = sgHeader.add(new EnumSetting.Builder<HeaderMode>()
        .name("header-mode").description("How to handle the server's header.").defaultValue(HeaderMode.Replace)
        .visible(() -> customHeaderEnabled.get()).build()
    );

    private final Setting<String> headerText = sgHeader.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("header-text").description("Custom text for the header. Use & for color codes and \\n for new lines.")
        .defaultValue("").visible(() -> customHeaderEnabled.get()).build()
    );

    private final Setting<Boolean> customFooterEnabled = sgFooter.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("custom-footer").description("Enable custom footer modifications.").defaultValue(true).build()
    );

    private final Setting<FooterMode> footerMode = sgFooter.add(new EnumSetting.Builder<FooterMode>()
        .name("footer-mode").description("How to handle the server's footer.").defaultValue(FooterMode.Replace)
        .visible(() -> customFooterEnabled.get()).build()
    );

    private final Setting<String> footerText = sgFooter.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("footer-text").description("Custom text for the footer. Use & for color codes and \\n for new lines.")
        .defaultValue("").visible(() -> customFooterEnabled.get()).build()
    );

    // Player Hiding Settings
    private final Setting<Boolean> hidePlayersEnabled = sgPlayerHiding.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("hide-players")
        .description("Enable hiding specific players from the tab list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> playersToHide = sgPlayerHiding.add(new StringListSetting.Builder()
        .name("players-to-hide")
        .description("List of player names to hide from the tab list.")
        .visible(() -> hidePlayersEnabled.get())
        .build()
    );

    private final Setting<List<String>> replacementNames = sgPlayerHiding.add(new StringListSetting.Builder()
        .name("replacement-names")
        .description("List of replacement names (same order as hidden players). Leave empty to just hide.")
        .visible(() -> hidePlayersEnabled.get())
        .build()
    );

    // Pattern for matching & color codes
    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or])");

    // Store server header/footer for combination modes
    private Text serverHeader = null;
    private Text serverFooter = null;

    /*
     * Player hiding internal state:
     * - playerReplacementsByUUID: what replacement ("" = hide, "X" = replace) to apply for a given UUID this tick
     * - originalDisplayNames: the original display Text for a given UUID (may be null)
     * - originalProfileNames: the original profile name String for a given UUID
     */
    private final Map<UUID, String> playerReplacementsByUUID = new HashMap<>();
    private final Map<UUID, Text> originalDisplayNames = new HashMap<>();
    private final Map<UUID, String> originalProfileNames = new HashMap<>();

    public CustomTabText() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Custom-Tab-Text",
            "Allows customization of the tab overlay text, and player visibility.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Always rebuild mapping from current players -> replacements (so changes apply immediately)
        updatePlayerReplacements();
        // Apply or restore names based on the mapping
        applyOrRestoreNames();
        // Keep header/footer updated
        forceUpdateTabText();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerListHeaderS2CPacket packet) {
            Text packetHeader = packet.header();
            Text packetFooter = packet.footer();

            if (customHeaderEnabled.get() && headerMode.get() != HeaderMode.Replace && headerMode.get() != HeaderMode.Remove) {
                serverHeader = packetHeader;
            }
            if (customFooterEnabled.get() && footerMode.get() != FooterMode.Replace && footerMode.get() != FooterMode.Remove) {
                serverFooter = packetFooter;
            }

            event.cancel();

            if (mc != null && mc.inGameHud != null && mc.inGameHud.getPlayerListHud() != null) {
                PlayerListHud hud = mc.inGameHud.getPlayerListHud();

                if (!customHeaderEnabled.get()) hud.setHeader(packetHeader);
                else hud.setHeader(buildFinalText(headerText.get(), serverHeader, headerMode.get()));

                if (!customFooterEnabled.get()) hud.setFooter(packetFooter);
                else hud.setFooter(buildFinalText(footerText.get(), serverFooter, footerMode.get()));
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        // nothing tab-specific here; modifications are applied in onTick so they're always up to date
    }

    /**
     * Rebuild playerReplacementsByUUID from the current network player list and the user's configured lists.
     * Matching is done against the originalProfileNames (if we saved it) or the current profile name otherwise.
     */
    private void updatePlayerReplacements() {
        playerReplacementsByUUID.clear();
        if (!hidePlayersEnabled.get() || mc == null || mc.getNetworkHandler() == null) return;

        List<String> hiddenPlayers = playersToHide.get();
        List<String> replacements = replacementNames.get();

        Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();

        for (PlayerListEntry entry : playerList) {
            UUID uuid = entry.getProfile().getId();
            // Use saved original name if we have one; otherwise use current profile name
            String nameToMatch = originalProfileNames.getOrDefault(uuid, entry.getProfile().getName());
            if (nameToMatch == null) continue;

            // Find index in hiddenPlayers (case-insensitive match for convenience)
            for (int i = 0; i < hiddenPlayers.size(); i++) {
                String hid = hiddenPlayers.get(i);
                if (hid == null) continue;
                if (hid.equalsIgnoreCase(nameToMatch)) {
                    String repl = (i < replacements.size() && replacements.get(i) != null) ? replacements.get(i) : "";
                    // If replacement string empty -> hide; otherwise replace with string
                    playerReplacementsByUUID.put(uuid, repl);
                    break;
                }
            }
        }
    }

    /**
     * For every current player entry:
     * - if they are in playerReplacementsByUUID -> apply replacement (and store originals if first time)
     * - else if we previously modified them -> restore originals and clear saved state
     */
    private void applyOrRestoreNames() {
        if (mc == null || mc.getNetworkHandler() == null) return;
        Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();

        // First apply replacements for players currently targeted
        for (PlayerListEntry entry : playerList) {
            UUID uuid = entry.getProfile().getId();

            if (playerReplacementsByUUID.containsKey(uuid)) {
                String replacement = playerReplacementsByUUID.get(uuid);
                // store originals only once per UUID
                originalDisplayNames.putIfAbsent(uuid, safeGetDisplayName(entry));
                originalProfileNames.putIfAbsent(uuid, entry.getProfile().getName());

                if (replacement == null || replacement.isEmpty()) {
                    // hide: set display name to empty text (or null) and profile name to empty string
                    setPlayerDisplayName(entry, Text.literal(""));
                    safeSetProfileName(entry, "");
                } else {
                    // replace both display and profile names
                    Text newText = parseFormattedText(replacement);
                    setPlayerDisplayName(entry, newText);
                    safeSetProfileName(entry, replacement);
                }
            } else {
                // not targeted this tick: if we have an original saved, restore it
                if (originalDisplayNames.containsKey(uuid) || originalProfileNames.containsKey(uuid)) {
                    Text origDisplay = originalDisplayNames.get(uuid); // may be null
                    String origProfile = originalProfileNames.get(uuid); // may be null

                    // restore display name
                    setPlayerDisplayName(entry, origDisplay);
                    // restore profile name if available
                    if (origProfile != null) safeSetProfileName(entry, origProfile);

                    // clear saved state
                    originalDisplayNames.remove(uuid);
                    originalProfileNames.remove(uuid);
                }
            }
        }

        // Finally: if saved originals exist for players who are no longer online, we should clear them to avoid memory leak
        // (we don't need to restore anything for offline players)
        originalDisplayNames.keySet().removeIf(uuid -> playerList.stream().noneMatch(e -> e.getProfile().getId().equals(uuid)));
        originalProfileNames.keySet().removeIf(uuid -> playerList.stream().noneMatch(e -> e.getProfile().getId().equals(uuid)));
    }

    /* ----------------------- Reflection helpers ----------------------- */

    // Try to get display name (may be null)
    private Text safeGetDisplayName(PlayerListEntry entry) {
        try {
            // try method getDisplayName()
            Method m = entry.getClass().getMethod("getDisplayName");
            m.setAccessible(true);
            Object res = m.invoke(entry);
            if (res instanceof Text) return (Text) res;
        } catch (Throwable ignored) {}

        // fallback to field that looks like display
        try {
            for (Field f : entry.getClass().getDeclaredFields()) {
                if (f.getType() == Text.class && f.getName().toLowerCase().contains("display")) {
                    f.setAccessible(true);
                    Object o = f.get(entry);
                    if (o instanceof Text) return (Text) o;
                    break;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // Set display name (try method then fallback to field). Accepts null.
    private void setPlayerDisplayName(PlayerListEntry entry, Text displayName) {
        try {
            // try method setDisplayName(Text)
            try {
                Method setMethod = entry.getClass().getMethod("setDisplayName", Text.class);
                setMethod.setAccessible(true);
                setMethod.invoke(entry, displayName);
                return;
            } catch (NoSuchMethodException ignored) {}

            // fallback to field
            for (Field f : entry.getClass().getDeclaredFields()) {
                if (f.getType() == Text.class && f.getName().toLowerCase().contains("display")) {
                    f.setAccessible(true);
                    f.set(entry, displayName);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    // Safely set the profile.name field (GameProfile.name) via reflection
    private void safeSetProfileName(PlayerListEntry entry, String newName) {
        try {
            Object profile = entry.getProfile();
            if (profile == null) return;
            Field nameField = profile.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(profile, newName);
        } catch (Throwable ignored) {}
    }

    /* ----------------------- Header/footer helpers ----------------------- */

    private void forceUpdateTabText() {
        if (mc == null || mc.player == null || mc.inGameHud == null) return;

        PlayerListHud hud = mc.inGameHud.getPlayerListHud();
        if (hud == null) return;

        if (customHeaderEnabled.get()) {
            Text finalHeader = buildFinalText(headerText.get(), serverHeader, headerMode.get());
            hud.setHeader(finalHeader);
        }

        if (customFooterEnabled.get()) {
            Text finalFooter = buildFinalText(footerText.get(), serverFooter, footerMode.get());
            hud.setFooter(finalFooter);
        }
    }

    private Text buildFinalText(String customText, Text serverText, Object mode) {
        String processedCustom = processText(customText);

        if (mode == HeaderMode.Remove || mode == FooterMode.Remove) {
            return null;
        } else if (mode == HeaderMode.Replace || mode == FooterMode.Replace) {
            return processedCustom.isEmpty() ? null : parseFormattedText(processedCustom);
        } else if (mode == HeaderMode.AddToTop || mode == FooterMode.AddToTop) {
            if (processedCustom.isEmpty()) return serverText;
            if (serverText == null) return parseFormattedText(processedCustom);
            return Text.empty().append(parseFormattedText(processedCustom)).append("\n").append(serverText);
        } else if (mode == HeaderMode.AddToEnd || mode == FooterMode.AddToEnd) {
            if (processedCustom.isEmpty()) return serverText;
            if (serverText == null) return parseFormattedText(processedCustom);
            return Text.empty().append(serverText).append("\n").append(parseFormattedText(processedCustom));
        }

        return null;
    }

    private String processText(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.replace("\\n", "\n");
    }

    private Text parseFormattedText(String input) {
        if (input == null || input.isEmpty()) return Text.empty();

        // Convert & codes to § codes for Minecraft formatting
        Matcher matcher = COLOR_PATTERN.matcher(input);
        String formatted = matcher.replaceAll("§$1");

        return Text.literal(formatted);
    }

    /* ----------------------- Activation / Deactivation ----------------------- */

    @Override
    public void onActivate() {
        serverHeader = null;
        serverFooter = null;
        // do not clear originals here; we want to restore on deactivate
        playerReplacementsByUUID.clear();
    }

    @Override
    public void onDeactivate() {
        // Restore header/footer
        if (mc != null && mc.inGameHud != null) {
            PlayerListHud hud = mc.inGameHud.getPlayerListHud();
            if (hud != null) {
                hud.setHeader(serverHeader);
                hud.setFooter(serverFooter);
            }
        }

        // Restore any modified player names
        if (mc != null && mc.getNetworkHandler() != null) {
            Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();
            for (PlayerListEntry entry : playerList) {
                UUID uuid = entry.getProfile().getId();
                // restore display
                if (originalDisplayNames.containsKey(uuid)) {
                    setPlayerDisplayName(entry, originalDisplayNames.get(uuid)); // may be null
                }
                // restore profile name
                if (originalProfileNames.containsKey(uuid)) {
                    safeSetProfileName(entry, originalProfileNames.get(uuid));
                }
            }
        }

        originalDisplayNames.clear();
        originalProfileNames.clear();
        playerReplacementsByUUID.clear();
    }
}
