package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TabSortedByPing extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSimilarPing = settings.createGroup("Similar Ping");

    public enum SortMode {
        Name,
        PingLowToHigh,
        PingHighToLow
    }

    // --- General Settings ---
    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("How to sort the players in the list.")
        .defaultValue(SortMode.PingLowToHigh)
        .build()
    );

    private final Setting<String> format = sgGeneral.add(new StringSetting.Builder()
        .name("format")
        .description("The layout of the line. Use {name} and {ping} as placeholders.")
        .defaultValue("{ping}ms - {name}")
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale factor of the text.")
        .defaultValue(0.67)
        .min(0.1)
        .max(10.0)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .description("The color for normal players.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    // --- Similar Ping Settings ---
    private final Setting<Boolean> similarPingEnable = sgSimilarPing.add(new BoolSetting.Builder()
        .name("enable")
        .description("Color players with similar pings.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> similarPingAmount = sgSimilarPing.add(new IntSetting.Builder()
        .name("amount")
        .description("Maximum ping difference to be considered similar.")
        .defaultValue(0)
        .min(0)
        .sliderMax(500)
        .build()
    );

    private final Setting<SettingColor> similarPingColor = sgSimilarPing.add(new ColorSetting.Builder()
        .name("color")
        .description("The color for players with similar pings.")
        .defaultValue(new SettingColor(128, 128, 128, 255))
        .build()
    );

    private List<PlayerListEntry> sortedPlayers;
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 160); // Semi-transparent black background

    public TabSortedByPing() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Tab-Sorted-By-Ping", "Custom tab list sorted by ping with numbers.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.getNetworkHandler() == null) return;
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        
        Comparator<PlayerListEntry> comparator;
        switch (sortMode.get()) {
            case Name:
                comparator = Comparator.comparing(entry -> entry.getProfile().name(), String.CASE_INSENSITIVE_ORDER);
                break;
            case PingHighToLow:
                comparator = Comparator.comparingInt(PlayerListEntry::getLatency).reversed();
                break;
            case PingLowToHigh:
            default:
                comparator = Comparator.comparingInt(PlayerListEntry::getLatency);
                break;
        }

        sortedPlayers = networkHandler.getPlayerList().stream()
            .sorted(comparator)
            .collect(Collectors.toList());
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (sortedPlayers == null || sortedPlayers.isEmpty()) return;
        
        double x = 10;
        double y = 20;
        
        double scaleValue = scale.get();
        
        // Calculate dynamic line height based on font metrics and scale
        int lineHeight = (int) (mc.textRenderer.fontHeight * scaleValue);
        
        // Find highest ping to determine layout padding dynamically
        int maxPing = 0;
        for (PlayerListEntry entry : sortedPlayers) {
            if (entry.getLatency() > maxPing) {
                maxPing = entry.getLatency();
            }
        }
        int maxPingLength = String.valueOf(maxPing).length();

        // Track players with similar pings for rendering custom colors
        Set<PlayerListEntry> similarPlayers = new HashSet<>();
        if (similarPingEnable.get()) {
            int amount = similarPingAmount.get();
            int size = sortedPlayers.size();
            for (int i = 0; i < size; i++) {
                PlayerListEntry p1 = sortedPlayers.get(i);
                for (int j = i + 1; j < size; j++) {
                    PlayerListEntry p2 = sortedPlayers.get(j);
                    if (Math.abs(p1.getLatency() - p2.getLatency()) <= amount) {
                        similarPlayers.add(p1);
                        similarPlayers.add(p2);
                    }
                }
            }
        }
        
        // Calculate dimensions for the background dynamically using formatted text
        double maxWidth = 0;
        for (PlayerListEntry entry : sortedPlayers) {
            String line = formatEntry(entry, maxPingLength);
            
            // Find the longest line to determine background width
            double width = mc.textRenderer.getWidth(line) * scaleValue;
            if (width > maxWidth) maxWidth = width;
        }
        
        // Draw background
        double padding = 5;
        double backgroundWidth = maxWidth + (padding * 2);
        double backgroundHeight = (sortedPlayers.size() * lineHeight) + (padding * 2);
        
        event.drawContext.fill(
            (int)(x - padding), 
            (int)(y - padding), 
            (int)(x - padding + backgroundWidth), 
            (int)(y - padding + backgroundHeight),
            BACKGROUND_COLOR.getPacked()
        );
        
        // Draw each player entry
        for (PlayerListEntry entry : sortedPlayers) {
            String line = formatEntry(entry, maxPingLength);
            int colorToUse = similarPlayers.contains(entry) ? similarPingColor.get().getPacked() : textColor.get().getPacked();
            
            // Push matrices using JOML's updated 2D Matrix API
            event.drawContext.getMatrices().pushMatrix();
            
            // Use 2D scaling
            event.drawContext.getMatrices().scale((float) scaleValue, (float) scaleValue);
            
            // Draw text
            event.drawContext.drawText(
                mc.textRenderer,
                line,
                (int) (x / scaleValue),
                (int) (y / scaleValue),
                colorToUse,
                true
            );
            
            // Pop the matrix
            event.drawContext.getMatrices().popMatrix();
            
            y += lineHeight;
        }
    }

    private String formatEntry(PlayerListEntry entry, int maxPingLength) {
        String name = entry.getProfile().name();
        int ping = entry.getLatency();
        
        // Always left pads with spaces up to maxPingLength (e.g. "  5" or "120")
        String paddedPing = String.format("%" + maxPingLength + "d", ping);
        
        // Replace custom formatting tags dynamically
        return format.get()
            .replace("{name}", name)
            .replace("{ping}", paddedPing);
    }
}