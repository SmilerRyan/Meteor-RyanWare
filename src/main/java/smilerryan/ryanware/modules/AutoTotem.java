package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import smilerryan.ryanware.RyanWare;

public class AutoTotem extends Module {
    public enum Mode {
        Offhand, DoubleTotem
    }

    private final Setting<Mode> mode = settings.getDefaultGroup()
        .add(new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("How to hold totems.")
            .defaultValue(Mode.Offhand)
            .build());

    private final Setting<Boolean> autoLeave = settings.getDefaultGroup()
        .add(new BoolSetting.Builder()
            .name("auto-leave")
            .description("Auto disconnect if no totems can be held anymore.")
            .defaultValue(false)
            .build());

    private boolean hadTotem = false;
    private int disconnectDelay = -1; // ticks until disconnect, -1 = no disconnect scheduled

    public AutoTotem() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "AutoTotem", "Automatically equips totems and optionally disconnects when none are left.");
    }

    @Override
    public void onActivate() {
        hadTotem = hasAnyTotem();
        disconnectDelay = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.player.currentScreenHandler == null) return;

        // Handle delayed disconnect countdown
        if (disconnectDelay >= 0) {
            disconnectDelay--;
            if (disconnectDelay <= 0) {
                mc.disconnect();
                disconnectDelay = -1;
            }
            return;
        }

        boolean offHandEquipped = isTotem(mc.player.getOffHandStack());
        boolean mainHandEquipped = isTotem(mc.player.getMainHandStack());

        if (mode.get() == Mode.Offhand && !offHandEquipped) {
            int slot = findTotemSlot();
            if (slot != -1) moveTotemToOffhand(slot);
        }

        if (mode.get() == Mode.DoubleTotem) {
            if (!mainHandEquipped) {
                int slot = findTotemSlot();
                if (slot != -1) moveTotemToSlot(slot, mc.player.getInventory().selectedSlot + 36);
            }
            if (!offHandEquipped) {
                int slot = findTotemSlot();
                if (slot != -1) moveTotemToOffhand(slot);
            }
        }

        boolean currentlyEquipped = offHandEquipped || mainHandEquipped;
        boolean inventoryHasTotem = findTotemSlot() != -1;

        if (currentlyEquipped) hadTotem = true;

        if (autoLeave.get() && hadTotem && !currentlyEquipped && !inventoryHasTotem) {
            mc.setScreen(new TitleScreen()); // Switch to main menu first
            disconnectDelay = 10; // Wait 10 ticks (~0.5 sec) before disconnecting to avoid crash
        }
    }

    private boolean isTotem(ItemStack stack) {
        return stack != null && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private boolean hasAnyTotem() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (isTotem(mc.player.getInventory().getStack(i))) return true;
        }
        if (isTotem(mc.player.getMainHandStack()) || isTotem(mc.player.getOffHandStack())) return true;
        if (isTotem(mc.player.currentScreenHandler.getCursorStack())) return true;
        return false;
    }

    private int findTotemSlot() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (isTotem(mc.player.getInventory().getStack(i))) {
                int slot = i + 9;
                if (slot < mc.player.currentScreenHandler.slots.size()) return slot;
            }
        }
        return -1;
    }

    private void moveTotemToOffhand(int fromSlot) {
        swapSlot(fromSlot, 45); // Offhand slot is 45
    }

    private void moveTotemToSlot(int fromSlot, int toSlot) {
        swapSlot(fromSlot, toSlot);
    }

    private void swapSlot(int from, int to) {
        int max = mc.player.currentScreenHandler.slots.size();
        if (from < 0 || to < 0 || from >= max || to >= max || from == to) return;

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, to, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
    }
}
