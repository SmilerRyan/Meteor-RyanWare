package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class ChatRepeater extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> triggerWords = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-words")
        .description("Repeat messages containing any of these.")
        .defaultValue(Arrays.asList("tab:"))
        .build()
    );

    private final Setting<List<String>> blacklistWords = sgGeneral.add(new StringListSetting.Builder()
        .name("blacklist-words")
        .description("Ignore messages containing any of these.")
        .defaultValue(Arrays.asList(">"))
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Pattern TIME_PREFIX = Pattern.compile("^\\s*<\\d{2}:\\d{2}:\\d{2}>\\s*");
    private static final String RYANWARE_PREFIX = "[Meteor] [RyanWare ";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> repeatTask;

    private String pendingMessage;
    private int pendingCount = 0;

    public ChatRepeater() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Chat-Repeater",
            "Repeats matching chat messages."
        );
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.getMessage() == null || mc.player == null) return;

        String msg = event.getMessage().getString();

        // Remove timestamp
        msg = TIME_PREFIX.matcher(msg).replaceFirst("");

        // Remove RyanWare/Meteor prefix
        if (msg.startsWith(RYANWARE_PREFIX)) {
            msg = msg.substring(RYANWARE_PREFIX.length());
        }

        msg = msg.trim();

        if (msg.isEmpty()) return;

        for (String word : blacklistWords.get()) {
            if (!word.isEmpty() && msg.contains(word)) {
                return;
            }
        }

        boolean triggered = false;

        for (String word : triggerWords.get()) {
            if (!word.isEmpty() && msg.contains(word)) {
                triggered = true;
                break;
            }
        }

        if (!triggered) return;


        // Add this message to the current 500ms window
        pendingMessage = msg;
        pendingCount++;

        // Reset timer from the latest received message
        if (repeatTask != null && !repeatTask.isDone()) {
            repeatTask.cancel(false);
        }

        repeatTask = scheduler.schedule(() -> {
            String finalMessage = pendingMessage;
            int count = pendingCount;

            pendingMessage = null;
            pendingCount = 0;
            repeatTask = null;

            mc.execute(() -> {
                if (count > 1) {
                    info("Ignoring " + count + ".");
                    return;
                }

                if (mc.getNetworkHandler() != null && finalMessage != null) {
                    smilerryan.ryanware.utils.SendChat.any(finalMessage);
                }
            });

        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDeactivate() {
        if (repeatTask != null) {
            repeatTask.cancel(false);
            repeatTask = null;
        }

        pendingMessage = null;
        pendingCount = 0;
    }
}