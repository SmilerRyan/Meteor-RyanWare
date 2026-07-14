package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TabSortedByPing extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private List<PlayerListEntry> sortedPlayers;
    private static final double SCALE = 2.5; // Text scale factor
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
        
        // Calculate dynamic line height based on font metrics and scale
        int lineHeight = (int) (mc.textRenderer.fontHeight * SCALE);
        
        // Calculate dimensions for the background
        double maxWidth = 0;
        for (PlayerListEntry entry : sortedPlayers) {
            String name = entry.getProfile().name();
            int ping = entry.getLatency();
            
            // Format string dynamically for background calculation
            String line =  String.format("%s - %dms", name, ping);
            
            // Find the longest line to determine background width
            double width = mc.textRenderer.getWidth(line) * SCALE;
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
            String name = entry.getProfile().name();
            int ping = entry.getLatency();
            
            // Format based on setting
            String line = pingOnRight.get() ? 
                String.format("%s - %dms", name, ping) : 
                String.format("%dms - %s", ping, name);
            
            // Push matrices using JOML's updated 2D Matrix API
            event.drawContext.getMatrices().pushMatrix();
            
            // Use 2D scaling (no Z-axis required in Matrix3x2f)
            event.drawContext.getMatrices().scale((float)SCALE, (float)SCALE);
            
            // Draw text
            event.drawContext.drawText(
                mc.textRenderer,
                line,
                (int) (x / SCALE),
                (int) (y / SCALE),
                TEXT_COLOR.getPacked(),
                true
            );
            
            // Pop the matrix using JOML's updated 2D Matrix API
            event.drawContext.getMatrices().popMatrix();
            
            y += lineHeight;
        }
    }
}