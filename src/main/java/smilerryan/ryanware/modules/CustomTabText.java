package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import smilerryan.ryanware.RyanWare;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomTabText extends Module {
    private final SettingGroup sgHeader = settings.createGroup("Header Settings");
    private final SettingGroup sgFooter = settings.createGroup("Footer Settings");
    private final SettingGroup sgColumns = settings.createGroup("Column Settings");
    private final SettingGroup sgPlayerHiding = settings.createGroup("Player Hiding");

    public enum HeaderMode { Remove, Replace, AddToTop, AddToEnd }
    public enum FooterMode { Remove, Replace, AddToTop, AddToEnd }

    private final Setting<Boolean> customHeaderEnabled = sgHeader.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("custom-header")
        .description("Enable custom header modifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<HeaderMode> headerMode = sgHeader.add(new EnumSetting.Builder<HeaderMode>()
        .name("header-mode")
        .description("How to handle the server's header.")
        .defaultValue(HeaderMode.Replace)
        .visible(() -> customHeaderEnabled.get())
        .build()
    );

    private final Setting<String> headerText = sgHeader.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("header-text")
        .description("Custom text for the header. Use & for color codes and \\n for new lines.")
        .defaultValue("")
        .visible(() -> customHeaderEnabled.get())
        .build()
    );

    private final Setting<Boolean> customFooterEnabled = sgFooter.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("custom-footer")
        .description("Enable custom footer modifications.")
        .defaultValue(true)
        .build()
    );

    private final Setting<FooterMode> footerMode = sgFooter.add(new EnumSetting.Builder<FooterMode>()
        .name("footer-mode")
        .description("How to handle the server's footer.")
        .defaultValue(FooterMode.Replace)
        .visible(() -> customFooterEnabled.get())
        .build()
    );

    private final Setting<String> footerText = sgFooter.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("footer-text")
        .description("Custom text for the footer. Use & for color codes and \\n for new lines.")
        .defaultValue("")
        .visible(() -> customFooterEnabled.get())
        .build()
    );

    private final Setting<Boolean> customColumns = sgColumns.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
        .name("custom-columns")
        .description("Enable custom column width for the tab list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> columnWidth = sgColumns.add(new IntSetting.Builder()
        .name("column-width")
        .description("Number of columns to display in the tab list.")
        .defaultValue(4)
        .min(1)
        .max(20)
        .visible(() -> customColumns.get())
        .build()
    );

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

    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or])");

    private Text serverHeader = null;
    private Text serverFooter = null;

    private Map<String, String> playerNameReplacements = new HashMap<>();
    private List<PlayerListEntry> filteredPlayers = null;

    // FIX: missing field
    private final Map<PlayerListEntry, Text> originalDisplayNames = new HashMap<>();

    public CustomTabText() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Custom-Tab-Text",
            "Allows customization of the tab overlay text, columns, and player visibility.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        updatePlayerList();
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

                if (!customHeaderEnabled.get()) {
                    hud.setHeader(packetHeader);
                } else {
                    Text finalHeader = buildFinalText(headerText.get(), serverHeader, headerMode.get());
                    hud.setHeader(finalHeader);
                }

                if (!customFooterEnabled.get()) {
                    hud.setFooter(packetFooter);
                } else {
                    Text finalFooter = buildFinalText(footerText.get(), serverFooter, footerMode.get());
                    hud.setFooter(finalFooter);
                }
            }
        }
    }

    @EventHandler 
    private void onRender2D(Render2DEvent event) {
        if (mc.options.playerListKey.isPressed()) {
            applyCustomTabModifications();
        }
    }

    private void updatePlayerList() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        playerNameReplacements.clear();

        if (hidePlayersEnabled.get()) {
            List<String> hiddenPlayers = playersToHide.get();
            List<String> replacements = replacementNames.get();

            for (int i = 0; i < hiddenPlayers.size(); i++) {
                String hiddenName = hiddenPlayers.get(i);
                String replacement = (i < replacements.size() && !replacements.get(i).isEmpty()) 
                    ? replacements.get(i) : null;

                if (replacement != null) {
                    playerNameReplacements.put(hiddenName, replacement);
                } else {
                    playerNameReplacements.put(hiddenName, "");
                }
            }
        }

        Collection<PlayerListEntry> allPlayers = mc.getNetworkHandler().getPlayerList();
        filteredPlayers = allPlayers.stream()
            .filter(entry -> {
                String playerName = entry.getProfile().getName();
                String replacement = playerNameReplacements.get(playerName);
                return replacement == null || !replacement.isEmpty();
            })
            .collect(Collectors.toList());
    }

    private void applyCustomTabModifications() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        try {
            PlayerListHud hud = mc.inGameHud.getPlayerListHud();

            if (hidePlayersEnabled.get() && !playerNameReplacements.isEmpty()) {
                modifyPlayerDisplayNames();
            }

            if (customColumns.get()) {
                applyCustomColumnLayout();
            }
        } catch (Exception ignored) {}
    }

    private void modifyPlayerDisplayNames() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        try {
            Collection<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList();

            for (PlayerListEntry entry : playerList) {
                String originalName = entry.getProfile().getName();
                String replacement = playerNameReplacements.get(originalName);

                if (replacement != null) {
                    if (replacement.isEmpty()) {
                        continue;
                    } else {
                        originalDisplayNames.putIfAbsent(entry, entry.getDisplayName());
                        modifyEntryDisplayName(entry, replacement);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void modifyEntryDisplayName(PlayerListEntry entry, String newName) {
        try {
            var entryClass = entry.getClass();
            var fields = entryClass.getDeclaredFields();

            for (var field : fields) {
                if (field.getType() == Text.class && field.getName().toLowerCase().contains("display")) {
                    field.setAccessible(true);
                    field.set(entry, Text.literal(newName));
                    return;
                }
            }

            var profile = entry.getProfile();
            var profileClass = profile.getClass();
            var nameField = profileClass.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(profile, newName);

        } catch (Exception ignored) {}
    }

    // FIX: missing method
    private void setPlayerDisplayName(PlayerListEntry entry, Text displayName) {
        try {
            var entryClass = entry.getClass();
            var fields = entryClass.getDeclaredFields();

            for (var field : fields) {
                if (field.getType() == Text.class && field.getName().toLowerCase().contains("display")) {
                    field.setAccessible(true);
                    field.set(entry, displayName);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private void applyCustomColumnLayout() {
        try {
            if (filteredPlayers == null) return;

            int playerCount = filteredPlayers.size();
            int desiredColumns = columnWidth.get();
            int maxPlayersPerColumn = MathHelper.ceil((float) playerCount / desiredColumns);

            PlayerListHud hud = mc.inGameHud.getPlayerListHud();
            var hudClass = hud.getClass();
            var fields = hudClass.getDeclaredFields();

            for (var field : fields) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    String fieldName = field.getName().toLowerCase();
                    if (fieldName.contains("column") || fieldName.contains("max")) {
                        field.setInt(hud, maxPlayersPerColumn);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

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

        Matcher matcher = COLOR_PATTERN.matcher(input);
        String formatted = matcher.replaceAll("§$1");

        return Text.literal(formatted);
    }

    @Override
    public void onActivate() {
        serverHeader = null;
        serverFooter = null;
        originalDisplayNames.clear();
        forceUpdateTabText();
    }

    @Override
    public void onDeactivate() {
        if (mc != null && mc.inGameHud != null) {
            PlayerListHud hud = mc.inGameHud.getPlayerListHud();
            if (hud != null) {
                hud.setHeader(serverHeader);
                hud.setFooter(serverFooter);
            }
        }

        restoreOriginalPlayerNames();
        originalDisplayNames.clear();
    }

    private void restoreOriginalPlayerNames() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        try {
            for (Map.Entry<PlayerListEntry, Text> entry : originalDisplayNames.entrySet()) {
                PlayerListEntry playerEntry = entry.getKey();
                Text originalDisplayName = entry.getValue();
                setPlayerDisplayName(playerEntry, originalDisplayName);
            }
        } catch (Exception ignored) {}
    }
}
