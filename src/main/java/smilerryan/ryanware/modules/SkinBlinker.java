package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.entity.PlayerModelPart;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;
import java.util.Set;

public class SkinBlinker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("Delay between skin part toggles in seconds.")
        .defaultValue(0.5)
        .min(0.1)
        .max(10.0)
        .sliderMin(0.1)
        .sliderMax(5.0)
        .build()
    );

    private int tickCounter = 0;
    private boolean skinVisible = true;

    public SkinBlinker() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "skin-blinker", "Makes your skin parts blink on and off at specified intervals");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        skinVisible = true;
        setSkinPartsVisible(true);
    }

    @Override
    public void onDeactivate() {
        // Restore all skin parts when module is disabled
        setSkinPartsVisible(true);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tickCounter++;
        
        // Convert delay from seconds to ticks (20 ticks = 1 second)
        int delayTicks = (int) (delay.get() * 20);
        
        if (tickCounter >= delayTicks) {
            tickCounter = 0;
            skinVisible = !skinVisible;
            setSkinPartsVisible(skinVisible);
        }
    }

    private void setSkinPartsVisible(boolean visible) {
        if (mc.player == null) return;
        
        try {
            // Get the enabledPlayerModelParts field using reflection
            java.lang.reflect.Field field = mc.options.getClass().getDeclaredField("enabledPlayerModelParts");
            field.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            java.util.Set<PlayerModelPart> enabledParts = (java.util.Set<PlayerModelPart>) field.get(mc.options);
            
            // Toggle all skin parts
            for (PlayerModelPart part : PlayerModelPart.values()) {
                if (visible) {
                    enabledParts.add(part);
                } else {
                    enabledParts.remove(part);
                }
            }
            
            // Save the options to apply changes
            mc.options.write();
            
        } catch (Exception e) {
            // Fallback: try alternative method names or mappings
            try {
                // Try obfuscated field name (common in 1.21.x)
                java.lang.reflect.Field field = mc.options.getClass().getDeclaredField("field_1826"); // enabledPlayerModelParts obfuscated
                field.setAccessible(true);
                
                @SuppressWarnings("unchecked")
                java.util.Set<PlayerModelPart> enabledParts = (java.util.Set<PlayerModelPart>) field.get(mc.options);
                
                for (PlayerModelPart part : PlayerModelPart.values()) {
                    if (visible) {
                        enabledParts.add(part);
                    } else {
                        enabledParts.remove(part);
                    }
                }
                
                mc.options.write();
                
            } catch (Exception e2) {
                // If both fail, log error
                System.err.println("SkinBlinker: Failed to access enabledPlayerModelParts field: " + e2.getMessage());
            }
        }
    }
}