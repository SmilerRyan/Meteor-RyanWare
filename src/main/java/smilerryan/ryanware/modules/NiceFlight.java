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

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean wasJumping = false;
    private boolean isFlying = false;
    private boolean canDoubleJump = true;

    public NiceFlight() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "nice-flight", "Toggle flying with double jump, like creative mode.");
    }

    @Override
    public void onActivate() {
        wasJumping = false;
        isFlying = false;
        canDoubleJump = true;
        if (mc.player != null) {
            mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
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

        // Only detect double jump when not flying and player is in air
        if (!isFlying && !wasJumping && isJumping && !mc.player.isOnGround() && canDoubleJump) {
            // Enable flying
            isFlying = true;
            mc.player.getAbilities().allowFlying = true;
            mc.player.getAbilities().flying = true;
            canDoubleJump = false;
        }
        // Detect double jump while flying to disable it
        else if (isFlying && !wasJumping && isJumping && !mc.player.isOnGround() && canDoubleJump) {
            // Disable flying
            isFlying = false;
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().allowFlying = false;
            canDoubleJump = false;
        }

        // Reset double jump ability when player touches ground
        if (mc.player.isOnGround()) {
            canDoubleJump = true;
        }

        // Apply flight speed when flying
        if (isFlying) {
            mc.player.getAbilities().setFlySpeed((float) (speed.get() * 0.05f));
        }

        wasJumping = isJumping;
    }
} 