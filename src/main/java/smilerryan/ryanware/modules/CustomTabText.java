package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smilerryan.ryanware.RyanWare;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CustomTabText extends Module {
    private final SettingGroup sgHeader = settings.createGroup("Header Settings");
    private final SettingGroup sgFooter = settings.createGroup("Footer Settings");
    private final SettingGroup sgColumns = settings.createGroup("Column Settings");
    private final SettingGroup sgPlayerHiding = settings.createGroup("Player Hiding");

    // Header Mode Enum
    public enum HeaderMode {
        Ignore,
        Replace,
        AddToTop,
        AddToEnd
    }

    // Footer Mode Enum  
    public enum FooterMode {
        Ignore,
        Replace,
        AddToTop,
        AddToEnd
    }

    // Header Settings
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

    // Footer Settings
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

    // Column Settings
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
        .description("List of replacement names (same order as hidden players). Leave empty for no replacement.")
        .visible(() -> hidePlayersEnabled.get())
        .build()
    );

    // Pattern for matching & color codes
    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or])");
    
    // Store server header/footer for combination modes
    private Text serverHeader = null;
    private Text serverFooter = null;
    
    // Reflection fields for column width
    private Field maxPlayersPerColumnField = null;
    private boolean reflectionInitialized = false;

    public CustomTabText() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Custom-Tab-Text",
            "Allows customization of the tab overlay text, columns, and player visibility.");
    }

    private void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        
        try {
            // Try to find the field that controls columns in PlayerListHud
            Class<?> hudClass = PlayerListHud.class;
            Field[] fields = hudClass.getDeclaredFields();
            
            for (Field field : fields) {
                // Look for integer fields that might control column count
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    // This is a guess - you might need to find the exact field name
                    // Common names: maxPlayersPerColumn, columns, etc.
                    if (field.getName().toLowerCase().contains("column") || 
                        field.getName().toLowerCase().contains("player")) {
                        maxPlayersPerColumnField = field;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Reflection failed, column width won't work
            info("Could not initialize reflection for column width");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        forceUpdateTabText();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerListHeaderS2CPacket packet) {
            // Store server's header/footer for combination modes
            if (customHeaderEnabled.get() && headerMode.get() != HeaderMode.Replace) {
                serverHeader = packet.header();
            }
            if (customFooterEnabled.get() && footerMode.get() != FooterMode.Replace) {
                serverFooter = packet.footer();
            }
            
            // Cancel if we're replacing or ignoring
            if ((customHeaderEnabled.get() && headerMode.get() == HeaderMode.Replace) ||
                (customFooterEnabled.get() && footerMode.get() == FooterMode.Replace) ||
                (customHeaderEnabled.get() && headerMode.get() == HeaderMode.Ignore) ||
                (customFooterEnabled.get() && footerMode.get() == FooterMode.Ignore)) {
                event.cancel();
            }
            
            // Immediately re-apply ours
            forceUpdateTabText();
        } else if (event.packet instanceof PlayerListS2CPacket packet && hidePlayersEnabled.get()) {
            // Handle player list modifications for hiding players
            modifyPlayerList(packet);
        }
    }

    private void forceUpdateTabText() {
        if (mc == null || mc.player == null || mc.inGameHud == null) return;
        
        PlayerListHud hud = mc.inGameHud.getPlayerListHud();
        if (hud == null) return;

        // Handle header
        if (customHeaderEnabled.get()) {
            Text finalHeader = buildFinalText(headerText.get(), serverHeader, headerMode.get());
            hud.setHeader(finalHeader);
        }

        // Handle footer
        if (customFooterEnabled.get()) {
            Text finalFooter = buildFinalText(footerText.get(), serverFooter, footerMode.get());
            hud.setFooter(finalFooter);
        }

        // Apply custom column width if enabled
        if (customColumns.get()) {
            applyCustomColumnWidth();
        }
    }

    private Text buildFinalText(String customText, Text serverText, Object mode) {
        String processedCustom = processText(customText);
        
        if (mode == HeaderMode.Ignore || mode == FooterMode.Ignore) {
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
        
        // Replace \\n with actual newlines
        return input.replace("\\n", "\n");
    }

    private Text parseFormattedText(String input) {
        if (input == null || input.isEmpty()) return Text.empty();

        // Convert & codes to § codes for Minecraft formatting
        Matcher matcher = COLOR_PATTERN.matcher(input);
        String formatted = matcher.replaceAll("§$1");
        
        return Text.literal(formatted);
    }

    private void applyCustomColumnWidth() {
        initReflection();
        
        if (maxPlayersPerColumnField == null) return;
        
        try {
            PlayerListHud hud = mc.inGameHud.getPlayerListHud();
            int targetColumns = columnWidth.get();
            
            // Calculate max players per column based on total players and desired columns
            int totalPlayers = mc.getNetworkHandler() != null ? 
                mc.getNetworkHandler().getPlayerList().size() : 80;
            int maxPerColumn = Math.max(1, (totalPlayers + targetColumns - 1) / targetColumns);
            
            maxPlayersPerColumnField.setInt(hud, maxPerColumn);
        } catch (Exception e) {
            // Reflection failed
        }
    }

    private void modifyPlayerList(PlayerListS2CPacket packet) {
        try {
            List<String> hiddenPlayers = playersToHide.get();
            List<String> replacements = replacementNames.get();
            
            if (hiddenPlayers.isEmpty()) return;

            // Try multiple possible field names for entries
            Class<?> packetClass = packet.getClass();
            Field entriesField = null;
            
            String[] possibleFieldNames = {"entries", "playerAdditionEntries", "actions", "field_179769_b"};
            for (String fieldName : possibleFieldNames) {
                try {
                    entriesField = packetClass.getDeclaredField(fieldName);
                    entriesField.setAccessible(true);
                    break;
                } catch (NoSuchFieldException ignored) {}
            }
            
            if (entriesField == null) {
                // Try to find any List field
                Field[] allFields = packetClass.getDeclaredFields();
                for (Field field : allFields) {
                    if (List.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        entriesField = field;
                        break;
                    }
                }
            }
            
            if (entriesField != null) {
                @SuppressWarnings("unchecked")
                List<PlayerListS2CPacket.Entry> entries = (List<PlayerListS2CPacket.Entry>) entriesField.get(packet);
                
                // Modify entries to hide players
                entries.removeIf(entry -> {
                    if (entry.profile() != null) {
                        String playerName = entry.profile().getName();
                        return hiddenPlayers.contains(playerName);
                    }
                    return false;
                });
            }
            
        } catch (Exception e) {
            // Only log if it's a real error, not field access issues
            if (!(e instanceof NoSuchFieldException) && !(e instanceof IllegalAccessException)) {
                warning("Failed to modify player list: " + e.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void onActivate() {
        serverHeader = null;
        serverFooter = null;
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
    }
}