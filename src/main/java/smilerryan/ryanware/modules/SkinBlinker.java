package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.entity.PlayerModelPart;
import smilerryan.ryanware.RyanWare;
import smilerryan.ryanware.mixins.GameOptionsAccessor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class SkinBlinker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgParts = settings.createGroup("Parts");
    private final SettingGroup sgIntervals = settings.createGroup("Intervals");

    // General settings
    private final Setting<Boolean> toggleAll = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-all")
        .description("Toggle all skin parts together with a global interval.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> globalInterval = sgGeneral.add(new IntSetting.Builder()
        .name("global-interval")
        .description("Global blink interval for all parts in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(toggleAll::get)
        .build()
    );

    // Part toggles
    private final Setting<Boolean> blinkCape = sgParts.add(new BoolSetting.Builder()
        .name("blink-cape")
        .description("Toggle cape blinking.")
        .defaultValue(true)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Boolean> blinkHat = sgParts.add(new BoolSetting.Builder()
        .name("blink-hat")
        .description("Toggle hat/helmet blinking.")
        .defaultValue(true)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Boolean> blinkJacket = sgParts.add(new BoolSetting.Builder()
        .name("blink-jacket")
        .description("Toggle jacket blinking.")
        .defaultValue(true)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Boolean> blinkLeftSleeve = sgParts.add(new BoolSetting.Builder()
        .name("blink-left-sleeve")
        .description("Toggle left sleeve blinking.")
        .defaultValue(true)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Boolean> blinkRightSleeve = sgParts.add(new BoolSetting.Builder()
        .name("blink-right-sleeve")
        .description("Toggle right sleeve blinking.")
        .defaultValue(true)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Boolean> blinkLeftPants = sgParts.add(new BoolSetting.Builder()
        .name("blink-left-pants")
        .description("Toggle left pants leg blinking.")
        .defaultValue(true)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Boolean> blinkRightPants = sgParts.add(new BoolSetting.Builder()
        .name("blink-right-pants")
        .description("Toggle right pants leg blinking.")
        .defaultValue(true)
        .visible(() -> !toggleAll.get())
        .build()
    );

    // Interval settings
    private final Setting<Integer> capeInterval = sgIntervals.add(new IntSetting.Builder()
        .name("cape-interval")
        .description("Blink interval for cape in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Integer> hatInterval = sgIntervals.add(new IntSetting.Builder()
        .name("hat-interval")
        .description("Blink interval for hat in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Integer> jacketInterval = sgIntervals.add(new IntSetting.Builder()
        .name("jacket-interval")
        .description("Blink interval for jacket in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Integer> leftSleeveInterval = sgIntervals.add(new IntSetting.Builder()
        .name("left-sleeve-interval")
        .description("Blink interval for left sleeve in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Integer> rightSleeveInterval = sgIntervals.add(new IntSetting.Builder()
        .name("right-sleeve-interval")
        .description("Blink interval for right sleeve in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Integer> leftPantsInterval = sgIntervals.add(new IntSetting.Builder()
        .name("left-pants-interval")
        .description("Blink interval for left pants in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(() -> !toggleAll.get())
        .build()
    );

    private final Setting<Integer> rightPantsInterval = sgIntervals.add(new IntSetting.Builder()
        .name("right-pants-interval")
        .description("Blink interval for right pants in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(1)
        .max(250)
        .sliderMin(1)
        .sliderMax(250)
        .visible(() -> !toggleAll.get())
        .build()
    );

    // Tick counters for each part
    private final Map<PlayerModelPart, Integer> tickCounters = new EnumMap<>(PlayerModelPart.class);
    private int globalCounter = 0;

    public SkinBlinker() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "skin-blinker", "Automatically toggles skin layers (cape, hat, jacket, etc.) at configurable intervals.");
        
        // Initialize all counters
        for (PlayerModelPart part : PlayerModelPart.values()) {
            tickCounters.put(part, 0);
        }
    }

    @Override
    public void onActivate() {
        // Reset all counters when module is enabled
        for (PlayerModelPart part : PlayerModelPart.values()) {
            tickCounters.put(part, 0);
        }
        globalCounter = 0;
        
        info("Skin Blinker activated! Your skin layers will now blink.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (toggleAll.get()) {
            // Use global counter for all parts
            globalCounter++;
            
            if (globalCounter >= globalInterval.get()) {
                // Toggle all parts at once
                toggleAllParts();
                globalCounter = 0;
            }
        } else {
            // Process each skin part individually
            processModelPart(PlayerModelPart.CAPE, blinkCape.get(), capeInterval.get());
            processModelPart(PlayerModelPart.HAT, blinkHat.get(), hatInterval.get());
            processModelPart(PlayerModelPart.JACKET, blinkJacket.get(), jacketInterval.get());
            processModelPart(PlayerModelPart.LEFT_SLEEVE, blinkLeftSleeve.get(), leftSleeveInterval.get());
            processModelPart(PlayerModelPart.RIGHT_SLEEVE, blinkRightSleeve.get(), rightSleeveInterval.get());
            processModelPart(PlayerModelPart.LEFT_PANTS_LEG, blinkLeftPants.get(), leftPantsInterval.get());
            processModelPart(PlayerModelPart.RIGHT_PANTS_LEG, blinkRightPants.get(), rightPantsInterval.get());
        }
    }

    private void toggleAllParts() {
        Set<PlayerModelPart> enabledParts = ((GameOptionsAccessor) mc.options).getEnabledPlayerModelParts();
        
        // Toggle all parts
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (enabledParts.contains(part)) {
                enabledParts.remove(part);
            } else {
                enabledParts.add(part);
            }
        }
        
        // Save the options to persist the change
        mc.options.write();
    }

    private void processModelPart(PlayerModelPart part, boolean enabled, int interval) {
        if (!enabled) return;

        int counter = tickCounters.get(part);
        counter++;

        if (counter >= interval) {
            // Get the set of enabled parts and toggle this part
            Set<PlayerModelPart> enabledParts = ((GameOptionsAccessor) mc.options).getEnabledPlayerModelParts();
            
            if (enabledParts.contains(part)) {
                enabledParts.remove(part);
            } else {
                enabledParts.add(part);
            }
            
            // Save the options to persist the change
            mc.options.write();
            
            // Reset counter
            tickCounters.put(part, 0);
        } else {
            // Increment counter
            tickCounters.put(part, counter);
        }
    }
}