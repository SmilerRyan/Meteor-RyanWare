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
import net.minecraft.entity.player.PlayerInventory;

import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private int disconnectDelay = -1; // ticks until disconnect, -1 = none

    public AutoTotem() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "AutoTotem", "Always equips the largest totem stacks, consolidates them, and can hold one in both hands.");
    }

    @Override
    public void onActivate() {
        hadTotem = hasAnyTotem();
        disconnectDelay = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.player.currentScreenHandler == null) return;

        // Handle disconnect countdown
        if (disconnectDelay >= 0) {
            disconnectDelay--;
            if (disconnectDelay <= 0) {
                mc.disconnect();
                disconnectDelay = -1;
            }
            return;
        }

        // Gather all totem slots sorted by stack size (largest first)
        List<Integer> totemSlots = getTotemSlotsSorted();
        if (totemSlots.isEmpty()) {
            handleAutoLeave();
            return;
        }

        // Consolidate stacks into as few as possible
        consolidateTotems(totemSlots);

        // Refresh after consolidation
        totemSlots = getTotemSlotsSorted();
        if (totemSlots.isEmpty()) {
            handleAutoLeave();
            return;
        }

        ItemStack offhand = mc.player.getOffHandStack();
        ItemStack mainhand = mc.player.getMainHandStack();

        // Mode: Offhand only
        if (mode.get() == Mode.Offhand) {
            int bestSlot = totemSlots.get(0);
            if (!isTotem(offhand) || offhand.getCount() < mc.player.getInventory().getStack(bestSlot).getCount()) {
                moveTotemToOffhand(bestSlot);
            }
        }

        // Mode: DoubleTotem (offhand = biggest, mainhand = 2nd biggest if available)
        if (mode.get() == Mode.DoubleTotem) {
            if (totemSlots.size() >= 1) {
                int bestSlot = totemSlots.get(0);
                if (!isTotem(offhand) || offhand.getCount() < mc.player.getInventory().getStack(bestSlot).getCount()) {
                    moveTotemToOffhand(bestSlot);
                }
            }
            if (totemSlots.size() >= 2) {
                int secondSlot = totemSlots.get(1);
                if (!isTotem(mainhand) || mainhand.getCount() < mc.player.getInventory().getStack(secondSlot).getCount()) {
                    moveTotemToSlot(secondSlot, mc.player.getInventory().selectedSlot + 36);
                }
            }
        }

        boolean equipped = isTotem(offhand) || isTotem(mainhand);
        if (equipped) hadTotem = true;

        if (autoLeave.get() && hadTotem && !equipped && totemSlots.isEmpty()) {
            mc.setScreen(new TitleScreen());
            disconnectDelay = 10;
        }
    }

    private boolean isTotem(ItemStack stack) {
        return stack != null && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private boolean hasAnyTotem() {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (isTotem(inv.getStack(i))) return true;
        }
        return isTotem(mc.player.getMainHandStack()) || isTotem(mc.player.getOffHandStack());
    }

    private List<Integer> getTotemSlotsSorted() {
        List<Integer> slots = new ArrayList<>();
        PlayerInventory inv = mc.player.getInventory();

        for (int i = 0; i < inv.size(); i++) {
            if (isTotem(inv.getStack(i))) slots.add(i);
        }

        slots.sort(Comparator.comparingInt((Integer i) -> inv.getStack(i).getCount()).reversed());
        return slots;
    }

    private void consolidateTotems(List<Integer> slots) {
        if (slots.size() < 2) return;

        PlayerInventory inv = mc.player.getInventory();
        int targetSlot = slots.get(0); // biggest stack target
        ItemStack targetStack = inv.getStack(targetSlot);

        for (int i = 1; i < slots.size(); i++) {
            int fromSlot = slots.get(i);
            ItemStack fromStack = inv.getStack(fromSlot);

            if (isTotem(fromStack) && targetStack.getCount() < targetStack.getMaxCount()) {
                int moveAmount = Math.min(fromStack.getCount(), targetStack.getMaxCount() - targetStack.getCount());
                if (moveAmount > 0) {
                    // Pick up from source
                    swapSlot(fromSlot < 9 ? 36 + fromSlot : fromSlot, fromSlot < 9 ? 36 + fromSlot : fromSlot);

                    // Place onto target
                    swapSlot(fromSlot < 9 ? 36 + fromSlot : fromSlot, targetSlot < 9 ? 36 + targetSlot : targetSlot);

                    // Update targetStack reference after merging
                    targetStack = inv.getStack(targetSlot);
                }
            }
        }
    }

    private void moveTotemToOffhand(int fromSlot) {
        swapSlot(fromSlot < 9 ? 36 + fromSlot : fromSlot, 45); // Offhand slot = 45
    }

    private void moveTotemToSlot(int fromSlot, int toSlot) {
        swapSlot(fromSlot < 9 ? 36 + fromSlot : fromSlot, toSlot);
    }

    private void swapSlot(int from, int to) {
        int max = mc.player.currentScreenHandler.slots.size();
        if (from < 0 || to < 0 || from >= max || to >= max || from == to) return;

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, to, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, from, 0, SlotActionType.PICKUP, mc.player);
    }

    private void handleAutoLeave() {
        ItemStack offhand = mc.player.getOffHandStack();
        ItemStack mainhand = mc.player.getMainHandStack();
        boolean equipped = isTotem(offhand) || isTotem(mainhand);

        if (autoLeave.get() && hadTotem && !equipped) {
            mc.setScreen(new TitleScreen());
            disconnectDelay = 10;
        }
    }
}
