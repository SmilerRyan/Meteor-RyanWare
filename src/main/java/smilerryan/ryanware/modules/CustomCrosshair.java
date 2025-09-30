package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.Random;

public class CustomCrosshair extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
            .name("color")
            .description("Color of the crosshair")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .build()
    );

    private final Setting<Integer> size = sg.add(new IntSetting.Builder()
            .name("size")
            .description("Size of l")
            .defaultValue(10)
            .min(1)
            .sliderMax(30)
            .build()
    );

    private final Setting<Integer> gap = sg.add(new IntSetting.Builder()
            .name("gap")
            .description("Gap from center")
            .defaultValue(3)
            .min(0)
            .sliderMax(20)
            .build()
    );

    public CustomCrosshair() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "custom-crosshair", "Draws an l crosshair.");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        drawLs(event);
    }

    private void drawLs(Render2DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double cx = mc.getWindow().getScaledWidth() / 2.0;
        double cy = mc.getWindow().getScaledHeight() / 2.0;
        Color c = color.get();
        int sizeVal = size.get();
        int gapVal = gap.get();
        String l = "l";

        //  TextRenderer.get().begin(1.0, false, true); //ORIGINAL
        float scale = sizeVal / 10f;
        TextRenderer.get().begin(scale, false, true);

        TextRenderer.get().render(l, (int) (cx - gapVal - sizeVal/2), (int) (cy + sizeVal/4), c); // left
        TextRenderer.get().render(l, (int) (cx + gapVal - sizeVal/2), (int) (cy + sizeVal/4), c); // right
        TextRenderer.get().render(l, (int) (cx - sizeVal/2), (int) (cy - gapVal - sizeVal/4), c); // top
        TextRenderer.get().render(l, (int) (cx - sizeVal/2), (int) (cy + gapVal + sizeVal/4), c); // bottom

        TextRenderer.get().end();
    }
}