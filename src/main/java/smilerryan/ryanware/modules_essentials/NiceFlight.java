package smilerryan.ryanware.modules_essentials;

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

    private final Setting<Boolean> instantTakeoff = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-takeoff")
        .description("Take off immediately when jumping, without requiring double jump.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> enableEveryTick = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-flight-every-tick")
        .description("Bypasses some anti-fly by re-enabling flight every tick.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> bypassAntiKick = sgGeneral.add(new BoolSetting.Builder()
        .name("bypass-anti-kick")
        .description("Bypasses vanilla anti-kick while flying.")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean wasJumping = false;
    private boolean isFlying = false;

    // Vanilla-like double tap timer (~7 ticks window)
    private int jumpTimer = 0;
    private static final int DOUBLE_TAP_WINDOW = 7;

    public NiceFlight() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix_essentials + "Nice-Flight", "Toggle flying with double jump, like creative mode.");
    }

    @Override
    public void onActivate() {
        wasJumping = false;
        isFlying = false;
        jumpTimer = 0;

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

        // Instant takeoff mode
        if (instantTakeoff.get()) {
            if (!isFlying && isJumping && !mc.player.isOnGround()) {
                isFlying = true;
                mc.player.getAbilities().allowFlying = true;
                mc.player.getAbilities().flying = true;
            }
        }

        // Double-tap detection (vanilla-like)
        if (!wasJumping && isJumping) {
            if (jumpTimer > 0) {
                isFlying = !isFlying;
                mc.player.getAbilities().allowFlying = !isFlying;
                mc.player.getAbilities().flying = !isFlying;
                jumpTimer = 0;
            } else {
                jumpTimer = DOUBLE_TAP_WINDOW;
            }
        }

        // Countdown timer
        if (jumpTimer > 0) jumpTimer--;

        // Reset on ground (vanilla behavior feel)
        if (mc.player.isOnGround() && isFlying && instantTakeoff.get()) {
            isFlying = false;
            mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }

        // Apply allowFlying every tick
        if (enableEveryTick.get() && isFlying) {
            mc.player.getAbilities().allowFlying = true;
            mc.player.getAbilities().flying = true;
        }

        // Apply flight speed when flying
        if (isFlying) {
            mc.player.getAbilities().setFlySpeed((float) (speed.get() * 0.05f));
        }

        // Bypass Vanilla Anti-kick
        if (isFlying && !mc.player.isOnGround() && bypassAntiKick.get() && mc.player.age % 10 < 2) {
            mc.player.setVelocity(
                mc.player.getVelocity().x,
                mc.player.getVelocity().y + (mc.player.age % 10 == 0 ? -0.04 : 0.04),
                mc.player.getVelocity().z
            );
        }
        
        wasJumping = isJumping;
    }
}