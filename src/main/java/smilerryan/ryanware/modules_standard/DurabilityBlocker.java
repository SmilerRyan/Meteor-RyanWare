package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;

import smilerryan.ryanware.RyanWare;

public class DurabilityBlocker extends Module {

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> shiftBypass = sg.add(new BoolSetting.Builder()
        .name("shift-bypass")
        .defaultValue(true)
        .build());

    private final Setting<Integer> durabilityThreshold = sg.add(new IntSetting.Builder()
        .name("durability-threshold")
        .defaultValue(1)
        .min(0)
        .max(100)
        .build());

    private final Setting<Boolean> playSound = sg.add(new BoolSetting.Builder()
        .name("play-sound")
        .defaultValue(true)
        .build());

    public DurabilityBlocker() {
        super(RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Durability-Blocker",
            "Prevents low durability tool usage (no packets, no mixins).");
    }

    private boolean blocked() {
        if (mc.player == null) return false;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty() || !stack.isDamageable()) return false;

        int left = stack.getMaxDamage() - stack.getDamage();

        if (left > durabilityThreshold.get()) return false;

        return !(shiftBypass.get() && mc.options.sneakKey.isPressed());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (blocked()) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
            if (playSound.get()) {
                if (mc.player.age % 15 == 0) {
                    mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.6f, 0.8f);
                }
            }
        }
    }
}