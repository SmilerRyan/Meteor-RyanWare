package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.MinecraftClient;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

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
            .description("Overall Size of the Crosshair (Scale)")
            .defaultValue(10)
            .min(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> horizontalPos = sg.add(new IntSetting.Builder()
            .name("horizontal-position")
            .description("Horizontal position adjustment")
            .defaultValue(0)
            .min(-10000)
            .max(10000)
            .build()
    );

    private final Setting<Integer> verticalPos = sg.add(new IntSetting.Builder()
            .name("vertical-position")
            .description("Vertical position adjustment")
            .defaultValue(0)
            .min(-10000)
            .max(10000)
            .build()
    );


    public CustomCrosshair() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "custom-crosshair", "Draws a custom crosshair using a '+' symbol.");
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        drawCrosshair(event);
    }

    private void drawCrosshair(Render2DEvent event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double cx = mc.getWindow().getScaledWidth() / 2.0;
        double cy = mc.getWindow().getScaledHeight() / 2.0;
        Color c = color.get();
        int sizeVal = size.get();
        int horizontalPosVal = horizontalPos.get();
        int verticalPosVal = verticalPos.get();

        float scale = sizeVal/5f;
        TextRenderer.get().begin(scale, false, true);

        String plusSymbol = "+";
        TextRenderer.get().render(plusSymbol, (int) (cx + horizontalPosVal), (int) (cy + verticalPosVal), c);

        TextRenderer.get().end();
    }
}