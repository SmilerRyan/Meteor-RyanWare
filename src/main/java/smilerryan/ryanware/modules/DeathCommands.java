package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import java.util.Random;
import java.util.List;

public class DeathCommands extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // General settings
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ticks")
        .description("Minimum ticks to wait before sending the message (20 ticks = 1 second).")
        .defaultValue(0)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ticks")
        .description("Maximum ticks to wait before sending the message (20 ticks = 1 second).")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Double> chance = sgGeneral.add(new DoubleSetting.Builder()
        .name("chance")
        .description("Chance to send the message when you die (0.0 = never, 1.0 = always).")
        .defaultValue(1.0)
        .min(0.0)
        .max(1.0)
        .sliderMax(1.0)
        .build()
    );

    // Message settings
    private final Setting<List<String>> deathMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("death-messages")
        .description("Messages to randomly choose from when dying.")
        .defaultValue(List.of(
            "Fuck.",
            ",",
            ",,,"
        ))
        .build()
    );

    private boolean wasAlive = true;
    private int deathDelayTicks = -1;
    private String pendingMessage = null;
    private final Random random = new Random();

    public DeathCommands() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Death-Commands", "Sends random messages when you die.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean isAlive = mc.player.getHealth() > 0;
        if (wasAlive && !isAlive) {
            // Player just died
            if (random.nextDouble() < chance.get()) {
                deathDelayTicks = minDelay.get() + random.nextInt(maxDelay.get() - minDelay.get() + 1);
                List<String> messages = deathMessages.get();
                if (!messages.isEmpty()) {
                    pendingMessage = messages.get(random.nextInt(messages.size()));
                }
            }
        }
        wasAlive = isAlive;

        // Handle delayed message
        if (deathDelayTicks >= 0 && pendingMessage != null) {
            if (deathDelayTicks == 0) {

                if (mc.player != null && mc.player.networkHandler != null) {
                    if (pendingMessage.startsWith("/")) {
                        mc.player.networkHandler.sendChatCommand(pendingMessage.substring(1));
                    } else {
                        mc.player.networkHandler.sendChatMessage(pendingMessage);
                    }
                }
                pendingMessage = null;
                deathDelayTicks = -1;
            } else {
                deathDelayTicks--;
            }
        }
    }

    @Override
    public void onDeactivate() {
        wasAlive = true;
        deathDelayTicks = -1;
        pendingMessage = null;
    }
}
