package smilerryan.ryanware.modules_standard.chat;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextOnly_AI_Chat_2 extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> url = sgGeneral.add(new StringSetting.Builder()
        .name("url")
        .description("URL to TextOnly service.")
        .defaultValue("https://ai.ryanthonton0465.workers.dev/api?hazey=1&q=")
        .build()
    );

    private final Setting<String> prompt = sgGeneral.add(new StringSetting.Builder()
        .name("prompt")
        .description("Prompt. Supports {history_N}, e.g. {history_1}, {history_20}, {history_100}.")
        .defaultValue("{history_20}")
        .build()
    );

    private final Setting<List<String>> triggerWords = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-words")
        .description("Words that trigger the AI call.")
        .defaultValue(Arrays.asList("#"))
        .build()
    );

    private final Setting<String> outputPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("output-prefix")
        .description("Only lines starting with this are sent. Empty sends all lines.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> forcedPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("force-all-output-with-prefix")
        .description("Force all ouput that gets sent to have this prefix.")
        .defaultValue("")
        .build()
    );

    private static final int MAX_HISTORY = 100;

    private final Deque<String> history = new ArrayDeque<>();

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TextOnly_AI_Chat_2() {
        super(
            RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "TextOnly-AI-Chat-2",
            "Uses TextOnly API with history and controlled output."
        );
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.getMessage() == null || mc.player == null) return;

        String msg = event.getMessage().getString();

        history.addLast(msg);
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }

        boolean triggered = false;

        for (String trigger : triggerWords.get()) {
            if (msg.contains(trigger)) {
                triggered = true;
                break;
            }
        }

        if (!triggered) return;

        executor.execute(() -> {
            try {
                String request =
                    url.get() +
                    URLEncoder.encode(buildPrompt(prompt.get()), StandardCharsets.UTF_8);

                String response = new String(
                    new java.net.URL(request)
                        .openStream()
                        .readAllBytes(),
                    StandardCharsets.UTF_8
                ).trim();

                if (response.isEmpty()) return;

                sendResponse(response);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendResponse(String response) {
        String prefix = outputPrefix.get();

        for (String line : response.split("\\R")) {
            line = line.trim();

            if (line.isEmpty()) continue;

            // If prefix exists, only send matching lines
            if (!prefix.isEmpty()) {
                if (!line.startsWith(prefix)) continue;
                line = line.substring(prefix.length()).trim();
            }

            if (line.isEmpty()) continue;

            String message = forcedPrefix.get() + line;

            mc.execute(() -> {
                if (mc.getNetworkHandler() != null) {
                    smilerryan.ryanware.utils.SendChat.any(message);
                }
            });
        }
    }

    private String buildPrompt(String template) {
        Matcher matcher = Pattern.compile("\\{history_(\\d+)}").matcher(template);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            int amount;

            try {
                amount = Integer.parseInt(matcher.group(1));
            } catch (Exception e) {
                amount = 1;
            }

            amount = Math.max(1, Math.min(amount, MAX_HISTORY));

            List<String> messages = new ArrayList<>(history);

            if (messages.size() > amount) {
                messages = messages.subList(
                    messages.size() - amount,
                    messages.size()
                );
            }

            matcher.appendReplacement(
                result,
                Matcher.quoteReplacement(String.join("\n", messages))
            );
        }

        matcher.appendTail(result);

        return result.toString();
    }
}