package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.HitResult;
import smilerryan.ryanware.RyanWare;

public class AirPunchSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> swapSlot = sgGeneral.add(new IntSetting.Builder()
        .name("swap-slot")
        .description("Hotbar slot to temporarily swap to.")
        .defaultValue(2)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Boolean> onlyAir = sgGeneral.add(new BoolSetting.Builder()
        .name("only-air")
        .description("Only trigger while looking at air.")
        .defaultValue(true)
        .build()
    );

    private int originalSlot = -1;
    private int ticksRemaining = 0;
    private boolean swapped = false;

    public AirPunchSwap() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Air-Punch-Swap",
            "Temporarily swaps slots for attribute/lunge swapping."
        );
    }

    @Override
    public void onDeactivate() {
        restoreSlot();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // restore after delay
        if (swapped && ticksRemaining > 0) {
            ticksRemaining--;

            if (ticksRemaining <= 0) {
                restoreSlot();
            }
        }

        boolean attacking = mc.options.attackKey.isPressed();

        if (!attacking) return;

        ItemStack mainHand = mc.player.getMainHandStack();

        if (mainHand.isEmpty()) return;

        if (onlyAir.get()) {
            if (mc.crosshairTarget != null
                && mc.crosshairTarget.getType() != HitResult.Type.MISS) {
                return;
            }
        }

        // already swapped
        if (swapped) return;

        originalSlot = mc.player.getInventory().selectedSlot;

        int targetSlot = swapSlot.get() - 1;

        if (targetSlot == originalSlot) return;

        // client slot
        mc.player.getInventory().selectedSlot = targetSlot;

        // sync through interaction manager
        mc.interactionManager.syncSelectedSlot();

        // force packet manually too
        mc.getNetworkHandler().sendPacket(
            new UpdateSelectedSlotC2SPacket(targetSlot)
        );

        swapped = true;

        // 1 tick works best for most lunge swaps
        ticksRemaining = 1;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        // Optional:
        // ensures slot is synced exactly during attack timing
        if (mc.player == null || !swapped) return;

        mc.interactionManager.syncSelectedSlot();
    }

    private void restoreSlot() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (originalSlot >= 0 && originalSlot <= 8) {
            mc.player.getInventory().selectedSlot = originalSlot;

            mc.interactionManager.syncSelectedSlot();

            mc.getNetworkHandler().sendPacket(
                new UpdateSelectedSlotC2SPacket(originalSlot)
            );
        }

        originalSlot = -1;
        ticksRemaining = 0;
        swapped = false;
    }
}
