package smilerryan.ryanware.modules_plus;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import smilerryan.ryanware.RyanWare;

public class MaxMaceKill extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder()
        .name("require-mace")
        .description("Only works when holding a mace.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showReady = sgGeneral.add(new BoolSetting.Builder()
        .name("show-ready")
        .description("Displays when max mace damage is ready.")
        .defaultValue(true)
        .build()
    );

    private boolean launching = false;
    private int launchTicks = 0;
    private static final int MAX_LAUNCH_TICKS = 20; // 1 second (20 ticks)

    public MaxMaceKill() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-max-mace-kill", "Launches you up and slams down with mace for max damage.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (launching) {
            if (launchTicks < MAX_LAUNCH_TICKS) {
                mc.player.setVelocity(mc.player.getVelocity().x, 1.5, mc.player.getVelocity().z);
                mc.player.velocityDirty = true;
                launchTicks++;
            } else if (mc.player.getVelocity().y < -0.3) {
                if (showReady.get()) info("Mace ready! Falling for max damage.");
                launching = false;
                launchTicks = 0;
            }
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null) return;
        if (requireMace.get() && !isHoldingMace()) return;

        if (!launching && mc.player.isOnGround()) {
            launching = true;
            launchTicks = 0;
            event.cancel(); // Cancel original attack; we'll hit on descent
        } else if (!mc.player.isOnGround() && mc.player.fallDistance >= 1.0f) {
            info(String.format("Mace attack with %.1f blocks of momentum!", mc.player.fallDistance));
        }
    }

    private boolean isHoldingMace() {
        return mc.player.getMainHandStack().getItem() == Items.MACE;
    }
}
