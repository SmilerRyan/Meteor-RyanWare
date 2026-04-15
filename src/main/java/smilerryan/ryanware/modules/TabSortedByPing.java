package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.util.math.MatrixStack;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TabSortedByPing extends Module {
    private List<PlayerListEntry> sortedPlayers;
    private static final double SCALE = 2.5; // Text scale factor (2.5x larger)
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
        double spacing = 12 * SCALE; // Adjust spacing based on scale
        
        // Calculate dimensions for the background
        double maxWidth = 0;
        for (PlayerListEntry entry : sortedPlayers) {
            String name = entry.getProfile().getName();
            int ping = entry.getLatency();
            String line = String.format("%dms - %s", ping, name);
            
            // Find the longest line to determine background width
            double width = mc.textRenderer.getWidth(line) * SCALE;
            if (width > maxWidth) maxWidth = width;
        }
        
        // Draw background
        double padding = 5;
        double backgroundWidth = maxWidth + (padding * 2);
        double backgroundHeight = (sortedPlayers.size() * spacing) + (padding * 2);
        
        // Use correct renderer method
        net.minecraft.client.util.math.MatrixStack matrices = event.drawContext.getMatrices();
        event.drawContext.fill(
            (int)(x - padding), 
            (int)(y - padding), 
            (int)(x - padding + backgroundWidth), 
            (int)(y - padding + backgroundHeight),
            new Color(0, 0, 0, 160).getPacked()
        );
        
        // Draw each player entry with scaled text
        for (PlayerListEntry entry : sortedPlayers) {
            String name = entry.getProfile().getName();
            int ping = entry.getLatency();
            String line = String.format("%s - %dms", name, ping);
            
            // Draw text with scale
            matrices.push();
            matrices.scale((float)SCALE, (float)SCALE, 1.0f);
            
            float scaledX = (float)(x / SCALE);
            float scaledY = (float)(y / SCALE);
            
            event.drawContext.drawText(
                mc.textRenderer,
                line,
                (int)scaledX,
                (int)scaledY,
                TEXT_COLOR.getPacked(),
                true
            );
            
            matrices.pop();
            
            y += spacing;
        }
    }
}