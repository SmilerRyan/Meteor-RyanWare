package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import smilerryan.ryanware.RyanWare;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoStealDupes extends Module {

    public AutoStealDupes() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Auto-Steal-Dupes",
            "Automatically steals items from dupe chests."
        );
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("steal-mode")
        .description("How to handle items.")
        .defaultValue(Mode.PICKUP)
        .build()
    );
    
    private enum Mode {
        PICKUP,
        DROP
    }
 
    private final Setting<Boolean> oneStackPerTick = sgGeneral.add(new BoolSetting.Builder()
        .name("one-stack-per-tick")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close")
        .defaultValue(false)
        .build()
    );

    @EventHandler
    private void onTick(TickEvent.Pre event) {
    
        if (!(mc.currentScreen instanceof GenericContainerScreen containerScreen)) {
            return;
        }

        if (mc.interactionManager == null || mc.player == null) return;

        String screenTitle = containerScreen.getTitle().getString().toLowerCase().trim();
        ScreenHandler screenHandler = containerScreen.getScreenHandler();

        Pattern DUPE_PATTERN = Pattern.compile("(?i)dupe (.*) to (.*)");
        Matcher matcher = DUPE_PATTERN.matcher(screenTitle);
        if (!matcher.matches()) return;

        String targetStr = matcher.group(2).trim();
        List<Integer> targetSlots = new ArrayList<>();

        switch (targetStr) {
            case "top" -> {
                for (int i = 0; i <= 26; i++) targetSlots.add(i);
            }
            case "bottom" -> {
                for (int i = 27; i <= 53; i++) targetSlots.add(i);
            }
            default -> {
                String[] parts = targetStr.split(",");
                for (String part : parts) {
                    try {
                        int slot = Integer.parseInt(part.trim()) - 1;
                        if (slot >= 0 && slot < screenHandler.slots.size()) {
                            targetSlots.add(slot);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        for (int slotIndex : targetSlots) {
            Slot slot = screenHandler.getSlot(slotIndex);
            if (slot == null || !slot.hasStack()) continue;
            mc.interactionManager.clickSlot(
                screenHandler.syncId,slotIndex,
                (mode.get() == Mode.PICKUP ? 0 : 1),
                (mode.get() == Mode.PICKUP ? SlotActionType.QUICK_MOVE : SlotActionType.THROW),
                mc.player
            );
            if (oneStackPerTick.get()) return;            
        }

        if (autoClose.get()) {
            mc.player.closeHandledScreen();
        }
    }
}