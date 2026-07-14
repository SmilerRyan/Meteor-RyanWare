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

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale factor of the text.")
        .defaultValue(2.5)
        .min(0.5)
        .max(5.0)
        .sliderMax(5.0)
        .build()
    );

    // --- Similar Ping Settings ---
    private final Setting<Boolean> similarPingEnable = sgSimilarPing.add(new BoolSetting.Builder()
        .name("enable")
        .description("Color players with similar pings.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> similarPingAmount = sgSimilarPing.add(new IntSetting.Builder()
        .name("amount")
        .description("Maximum ping difference to be considered similar.")
        .defaultValue(0)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<SettingColor> similarPingColor = sgSimilarPing.add(new ColorSetting.Builder()
        .name("color")
        .description("The color for players with similar pings.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private List<PlayerListEntry> sortedPlayers;
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 160); // Semi-transparent black background
    private static final Color TEXT_COLOR = new Color(255, 255, 255); // White text

    public TabSortedByPing() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Tab-Sorted-By-Ping", "Custom tab list sorted by ping with numbers.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.getNetworkHandler() == null) return;
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        sortedPlayers = networkHandler.getPlayerList().stream()
            .sorted(Comparator.comparingInt(PlayerListEntry::getLatency))
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

        // Track players with similar pings for rendering custom colors (O(N^2) optimized pre-pass)
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
            int colorToUse = similarPlayers.contains(entry) ? similarPingColor.get().getPacked() : TEXT_COLOR.getPacked();
            
            // Push matrices using JOML's updated 2D Matrix API
            event.drawContext.getMatrices().pushMatrix();
            
            // Use 2D scaling (no Z-axis required in Matrix3x2f)
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
            
            // Pop the matrix using JOML's updated 2D Matrix API
            event.drawContext.getMatrices().popMatrix();
            
            y += lineHeight;
        }
    }

    private String formatEntry(PlayerListEntry entry, int maxPingLength) {
        String name = entry.getProfile().name();
        int ping = entry.getLatency();
        
        // Always left pads with spaces up to maxPingLength (e.g. "  5" or "120")
        String paddedPing = String.format("%" + maxPingLength + "d", ping);
        
        return String.format("%sms - %s", paddedPing, name);
    }
}