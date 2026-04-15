package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuleMenu extends Module {

    private float textSize = 2.0f;      // text scale
    private int spacingY = 20;           // vertical spacing
    private int columns = 3;             // number of columns
    private int maxTextWidth = 200;      // truncate if longer than this
    private int scrollOffset = 0;        // future scroll support

    private boolean leftMouseDownLast = false;

    public ModuleMenu() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "ModuleMenu", "Displays all modules.");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        List<Module> modules = new ArrayList<>(Modules.get().getAll());
        modules.sort(Comparator.comparing(m -> m.name.toLowerCase()));

        int rows = (int) Math.ceil(modules.size() / (double) columns);

        TextRenderer renderer = TextRenderer.get();
        renderer.begin(textSize, false, true);

        // Calculate column widths
        float[] colWidths = new float[columns];
        for (int i = 0; i < modules.size(); i++) {
            int col = i % columns;
            String name = modules.get(i).name;
            float width = (float) renderer.getWidth(name);
            if (width > colWidths[col]) colWidths[col] = width;
        }
        for (int i = 0; i < columns; i++) colWidths[i] += 10; // padding
        float totalWidth = 0;
        for (float w : colWidths) totalWidth += w;

        // Mouse position
        double mx = mc.mouse.getX() * screenWidth / mc.getWindow().getWidth();
        double my = screenHeight - mc.mouse.getY() * screenHeight / mc.getWindow().getHeight();
        boolean leftMouseDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            int col = i % columns;
            int row = i / columns;

            String name = m.name;
            float width = (float) renderer.getWidth(name);

            // Truncate long names
            // if (width > maxTextWidth) {
            //     while (width > maxTextWidth && name.length() > 0) {
            //         name = name.substring(0, name.length() - 1);
            //         width = (float) renderer.getWidth(name + "...");
            //     }
            //     name += "...";
            // }

            // X position
            float x = (screenWidth / 2f) - (totalWidth / 2f);
            for (int j = 0; j < col; j++) x += colWidths[j];

            // Y position
            float totalHeight = (rows - 1) * spacingY;
            float y = (screenHeight / 2f) - (totalHeight / 2f) + row * spacingY + scrollOffset;

            // Hover check
            boolean hovered = mx >= x && mx <= x + colWidths[col] && my >= y && my <= y + spacingY;

            // Color: hovered yellow, active green, else white
            Color color;
            if (hovered) color = new Color(255, 255, 0); // yellow
            else if (m.isActive()) color = new Color(0, 255, 0); // green
            else color = Color.WHITE;

            renderer.render(name, x, y, color);

            // Toggle on click
            if (hovered && leftMouseDown && !leftMouseDownLast) {
                m.toggle();
            }
        }

        leftMouseDownLast = leftMouseDown;
        renderer.end();
    }
}
