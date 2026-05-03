package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import smilerryan.ryanware.RyanWare;

public class OneDurabilityToolBlocker extends Module {

    private int soundCooldown = 0;
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> shiftBypass = sgGeneral.add(
        new BoolSetting.Builder()
            .name("shift-bypass")
            .description("Allows using low-durability tools while sneaking (holding Shift).")
            .defaultValue(true)
            .build()
    );

    public OneDurabilityToolBlocker() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "One-Durability-Tool-Blocker", "Prevents you from using tools with 1 durability or less.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (soundCooldown > 0) soundCooldown--;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || !stack.isDamageable()) return;
        int durabilityLeft = stack.getMaxDamage() - stack.getDamage();
        boolean isShiftDown = mc.options.sneakKey.isPressed();
        if (shiftBypass.get() && isShiftDown) return;
        if (durabilityLeft <= 1) {
            mc.options.useKey.setPressed(false);
            mc.options.attackKey.setPressed(false);
            if (mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }
            if (soundCooldown <= 0) {
                mc.player.playSound(
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    0.6f,
                    0.8f
                );
                soundCooldown = 20;
            }
        }
    }
}