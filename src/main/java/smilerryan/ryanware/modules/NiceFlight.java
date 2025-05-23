package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

public class NiceFlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast to fly.")
        .defaultValue(1.0)
        .min(0.1)
        .max(5.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Boolean> holdToFly = sgGeneral.add(new BoolSetting.Builder()
        .name("hold-to-fly")
        .description("Only fly while holding jump.")
        .defaultValue(false)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean flying = false;
    private boolean wasJumping = false;

    public NiceFlight() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "nice-flight", "Allows you to fly like in creative mode with double-jump.");
    }

    @Override
    public void onActivate() {
        flying = false;
        wasJumping = false;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().allowFlying = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean isJumping = mc.options.jumpKey.isPressed();

        // Handle double jump detection
        if (!wasJumping && isJumping && !mc.player.isOnGround()) {
            // Double jump detected
            flying = true;
            mc.player.getAbilities().allowFlying = true;
            mc.player.getAbilities().flying = true;
        }

        // Handle hold-to-fly mode
        if (holdToFly.get() && flying) {
            if (!isJumping) {
                flying = false;
                mc.player.getAbilities().flying = false;
                mc.player.getAbilities().allowFlying = false;
            }
        }
        // Handle normal mode - land to stop flying
        else if (!holdToFly.get() && flying && mc.player.isOnGround()) {
            flying = false;
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().allowFlying = false;
        }

        // Apply flight speed when flying
        if (flying) {
            mc.player.getAbilities().setFlySpeed((float) (speed.get() * 0.05f));
        }

        wasJumping = isJumping;
    }
} 