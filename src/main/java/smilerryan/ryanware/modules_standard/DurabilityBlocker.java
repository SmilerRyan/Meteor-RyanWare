package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import smilerryan.ryanware.RyanWare;

public class DurabilityBlocker extends Module {

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> shiftBypass = sg.add(new BoolSetting.Builder()
        .name("shift-bypass")
        .description("Allows using low durability tools while sneaking.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> durabilityThreshold = sg.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Block actions at or below this durability.")
        .defaultValue(1)
        .min(0)
        .max(100)
        .build());

    private int soundTimer = 0;

    public DurabilityBlocker() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Durability-Blocker", "Prevents using tools below a configurable durability threshold.");
    }

    private boolean blocked() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || !stack.isDamageable()) return false;
        int left = stack.getMaxDamage() - stack.getDamage();
        if (left > durabilityThreshold.get()) return false;
        return !(shiftBypass.get() && mc.options.sneakKey.isPressed());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (blocked() && mc.options.attackKey.isPressed()) {
            mc.options.attackKey.setPressed(false);
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.6f, 0.8f);
        }
    }

}