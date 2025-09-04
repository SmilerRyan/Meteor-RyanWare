package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.network.ClientPlayerEntity;

public class LookDownDropper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("How fast the head moves up/down.")
        .defaultValue(0.8)
        .min(0.1)
        .sliderMax(3)
        .build());
    
    private final Setting<Boolean> shouldDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-item")
        .description("Whether to drop the currently held item.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> shouldLoop = sgGeneral.add(new BoolSetting.Builder()
        .name("loop")
        .description("Keeps the module active, repeating the look down and up cycle.")
        .defaultValue(false)
        .build());
    
    public enum DingMode {
        OFF,
        ONCE_PER_STATE,
        REPEATING
    }

    private final Setting<DingMode> dingMode = sgGeneral.add(new EnumSetting.Builder<DingMode>()
        .name("ding-mode")
        .description("Controls how ding sounds play.")
        .defaultValue(DingMode.REPEATING)
        .build());

    private enum State {
        MOVING_DOWN,
        DROPPING,
        MOVING_UP,
        IDLE
    }

    private State state = State.IDLE;
    private float pitch; // current pitch angle in degrees (0 = horizontal, 90 = down)
    private float initialPitch; // saved pitch before activation
    private boolean droppedItem = false;

    // Track if ding played this tick for ONCE_PER_STATE mode
    private boolean dingPlayedThisState = false;

    // Flag for safe disabling after moving up
    private boolean isDisabling = false;

    public LookDownDropper() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "LookDownDropper", "Slowly looks down, drops held item (optional), looks back up, and then optionally repeats.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle(); // disable immediately if no player
            return;
        }
        initialPitch = mc.player.getPitch();
        pitch = initialPitch;
        droppedItem = false;
        dingPlayedThisState = false;
        isDisabling = false;
        state = State.MOVING_DOWN;
    }

    @Override
    public void onDeactivate() {
        // We only want to handle the "move up" phase if the module is manually deactivated
        // and is not already moving up.
        if (mc.player != null && state != State.MOVING_UP && state != State.IDLE) {
            isDisabling = true;
            state = State.MOVING_UP;
            dingPlayedThisState = false;
        } else if (mc.player != null && state == State.IDLE) {
            // If we are in IDLE, it means the cycle is complete, so we can stop.
            // Reset player pitch just in case.
            setPlayerPitch(initialPitch);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        double moveSpeed = speed.get();

        switch (state) {
            case MOVING_DOWN -> {
                pitch += moveSpeed;
                if (pitch >= 90) {
                    pitch = 90;
                    state = shouldDrop.get() ? State.DROPPING : State.MOVING_UP;
                    dingPlayedThisState = false;
                }
                setPlayerPitch(pitch);
                playDingIfAllowed(0.5f);
            }
            case DROPPING -> {
                if (!droppedItem && shouldDrop.get()) {
                    player.dropSelectedItem(false);
                    droppedItem = true;
                    dingPlayedThisState = false;
                    playDingIfAllowed(1.0f);
                }
                state = State.MOVING_UP;
                dingPlayedThisState = false;
            }
            case MOVING_UP -> {
                // Keep moving up until the pitch is equal to or less than the initial pitch.
                if (pitch > initialPitch) {
                    pitch -= moveSpeed;
                    if (pitch < initialPitch) pitch = initialPitch;
                    setPlayerPitch(pitch);
                    playDingIfAllowed(2.0f);
                } else {
                    // Once the pitch is restored, the cycle is complete.
                    state = State.IDLE;
                    if (shouldLoop.get() && !isDisabling) {
                        // If looping is enabled and we are not in the process of disabling,
                        // reset for the next cycle.
                        state = State.MOVING_DOWN;
                        droppedItem = false;
                        dingPlayedThisState = false;
                    } else {
                        // Otherwise, if not looping or if we are in the process of disabling,
                        // the module is done.
                        toggle();
                    }
                }
            }
            case IDLE -> {
                // No action needed in the IDLE state.
            }
        }
    }

    private void playDingIfAllowed(float soundPitch) {
        if (dingMode.get() == DingMode.OFF) return;

        if (dingMode.get() == DingMode.ONCE_PER_STATE) {
            if (!dingPlayedThisState) {
                playDing(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), soundPitch);
                dingPlayedThisState = true;
            }
        } else if (dingMode.get() == DingMode.REPEATING) {
            playDing(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), soundPitch);
        }
    }

    private void setPlayerPitch(float pitchDegrees) {
        if (mc.player != null) {
            mc.player.setPitch(pitchDegrees);
        }
    }

    private void playDing(SoundEvent sound, float dingPitch) {
        if (mc.player != null && mc.world != null) {
            mc.world.playSound(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                sound, SoundCategory.PLAYERS,
                0.5f, dingPitch,
                false);
        }
    }
}