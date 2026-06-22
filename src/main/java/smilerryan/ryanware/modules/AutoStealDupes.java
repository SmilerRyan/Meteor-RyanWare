package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.hit.BlockHitResult;
import smilerryan.ryanware.RyanWare;

public class AutoStealDupes extends Module {

    public AutoStealDupes() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Auto-Steal-Dupes",
            "Automatically steals items from dupe chests."
        );
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoClose = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoReopen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close-then-reopen")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bypassStealClose = sgGeneral.add(new BoolSetting.Builder()
        .name("bypass-steal-and-auto-closing-while-sprinting")
        .defaultValue(true)
        .build()
    );

    private boolean sneakingSnapshot = false;
    private boolean hasSnapshot = false;

    @Override
    public void onActivate() {
        sneakingSnapshot = false;
        hasSnapshot = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!(mc.currentScreen instanceof GenericContainerScreen containerScreen)) {
            hasSnapshot = false;
            return;
        }

        if (mc.interactionManager == null || mc.player == null) return;

        if (!hasSnapshot) {
            sneakingSnapshot = mc.player.isSprinting();
            hasSnapshot = true;
        }

        if (bypassStealClose.get() && hasSnapshot && sneakingSnapshot) return;

        String screenTitle = containerScreen.getTitle().getString();
        ScreenHandler screenHandler = containerScreen.getScreenHandler();

        int startSlot = -1;
        int endSlot = -1;

        if (screenTitle.equalsIgnoreCase("dupe top to bottom")) {
            startSlot = 27;
            endSlot = 54;
        } else if (screenTitle.equalsIgnoreCase("dupe bottom to top")) {
            startSlot = 0;
            endSlot = 26;
        } else {
            return;
        }

        for (int slotIndex = startSlot; slotIndex < endSlot; slotIndex++) {
            Slot slot = screenHandler.getSlot(slotIndex);
            if (slot == null || !slot.hasStack()) continue;

            mc.interactionManager.clickSlot(
                screenHandler.syncId,
                slotIndex,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
            );

            return;
        }

        if (autoClose.get() || autoReopen.get()) {
            mc.player.closeHandledScreen();
        }

        if (autoReopen.get()) {
            if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                mc.interactionManager.interactBlock(
                    mc.player,
                    mc.player.getActiveHand(),
                    bhr
                );
            }
        }
    }
}