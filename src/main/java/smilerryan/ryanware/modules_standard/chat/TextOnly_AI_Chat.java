package smilerryan.ryanware.modules_standard.chat;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import smilerryan.ryanware.modules_standard.Settings;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextOnly_AI_Chat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> url = sgGeneral.add(new StringSetting.Builder()
        .name("url")
        .description("URL To TextOnly Service")
        .defaultValue("http://localhost/?s=1&q=")
        .build()
    );

    private final Setting<List<String>> triggerWords = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-words")
        .description("Words that trigger the AI call.")
        .defaultValue(Arrays.asList("#"))
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Prefix to apply to AI generated messages.")
        .defaultValue("AI: ")
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TextOnly_AI_Chat() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "TextOnly-AI-Chat", "Uses a Text Only API to answer chat.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.getMessage() == null || mc.player == null) return;

        String msg = event.getMessage().getString();

        // Trigger check
        boolean triggered = false;
        for (String trigger : triggerWords.get()) {
            if (msg.toLowerCase(Locale.ROOT).contains(trigger.toLowerCase(Locale.ROOT))) {
                triggered = true;
                break;
            }
        }
        if (!triggered) return;

        executor.execute(() -> {
            try {
                String requestUrl = url.get() + java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
                String response = prefix.get() + new String(new java.net.URL(requestUrl).openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                mc.execute(() -> {
                    if (mc.getNetworkHandler() != null && !response.isEmpty()) {
                        smilerryan.ryanware.utils.SendChat.any(response);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

}
