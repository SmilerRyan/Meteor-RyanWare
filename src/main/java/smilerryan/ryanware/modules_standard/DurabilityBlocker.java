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

    private final Setting<Integer> plingCooldown = sg.add(new IntSetting.Builder()
        .name("pling-cooldown")
        .description("Ticks between warning sounds. 0 disables sound.")
        .defaultValue(15)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build());

    private int soundTimer = 0;
    private boolean wasBlocked = false;

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
    private void onPacketSend(PacketEvent.Send event) {
        if (!blocked()) return;

        // block attacking entities
        if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
            boolean[] isAttack = {false};

            packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
                @Override
                public void attack() {isAttack[0] = true;}

                @Override
                public void interact(net.minecraft.util.Hand hand) {}

                @Override
                public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {}
            });

            if (isAttack[0]) {
                event.cancel();
                return;
            }
        }

        // block breaking packets + FIX GHOST BLOCKS
        if (event.packet instanceof PlayerActionC2SPacket packet) {
            PlayerActionC2SPacket.Action action = packet.getAction();
            BlockPos pos = packet.getPos();

            if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK ||
                action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK ||
                action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {

                event.cancel();

                return;
            }
        }

        // block item use (right click)
        if (event.packet instanceof PlayerInteractItemC2SPacket) {
            event.cancel();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean blocked = blocked();

        // sound logic (only on state change or cooldown)
        if (plingCooldown.get() > 0) {
            if (blocked && (!wasBlocked || soundTimer <= 0)) {
                mc.player.playSound(
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    0.6f,
                    0.8f
                );
                soundTimer = plingCooldown.get();
            }

            if (soundTimer > 0) soundTimer--;
        }

        wasBlocked = blocked;
    }
}