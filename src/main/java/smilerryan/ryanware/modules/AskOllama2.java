package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import java.net.URL;
import java.net.HttpURLConnection;

import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

public class AskOllama2 extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> baseUrl = sgGeneral.add(new StringSetting.Builder()
        .name("ollama-url")
        .description("Base URL of the Ollama server.")
        .defaultValue("http://localhost:11434")
        .build()
    );

    private final Setting<String> model = sgGeneral.add(new StringSetting.Builder()
        .name("model")
        .description("Model to use.")
        .defaultValue("llama3.2:latest")
        .build()
    );

    private final Setting<String> promptTemplate = sgGeneral.add(new StringSetting.Builder()
        .name("prompt-template")
        .description("Use {input} for the message, {history_N} for message history (max 1000), and {player} for the current player name.")
        .defaultValue("You are a minecraft chat bot for the player {player}, keep your responses short. Send /noreply to not reply. {history_10} {input}")
        .build()
    );
    
    private final Setting<List<String>> triggerWords = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-keywords")
        .description("Words that trigger the AI call.")
        .defaultValue(Arrays.asList("?"))
        .build()
    );

    private final Setting<String> ignoreOutKeyword = sgGeneral.add(new StringSetting.Builder()
        .name("ignore-out-keyword")
        .description("If the response message contains this keyword, it will not be sent.")
        .defaultValue("/noreply")
        .build()
    );

    private final AtomicLong lastQueryTime = new AtomicLong(0);
    private final List<String> recentMessages = new ArrayList<>();

    public AskOllama2() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "AskOllama2",
            "Answers chat for you with AI."
        );
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        if (!isActive() || mc.player == null) return;

        String raw = e.getMessage().getString();

        // Store the recent message
        if (recentMessages.size() >= 1000) {
            recentMessages.remove(0); // Keep only the last 1000 messages
        }
        recentMessages.add(raw);

        // Check for trigger keywords, if none of the strings are contained in the message, skip processing
        boolean containsTrigger = triggerWords.get().isEmpty() || triggerWords.get().stream().anyMatch(raw::contains);
        if (!containsTrigger) return;

        // Clean and truncate the message
        raw = cleanMessage(raw);
        

        // Prepare the prompt with the user's name and recent messages
        String prompt = preparePrompt(raw);
        new Thread(() -> {
            String response = queryOllama(model.get(), prompt);
            if (response == null || response.isEmpty() || response.contains(ignoreOutKeyword.get())) return;
            String cleaned_response = cleanMessage(response);
            if (cleaned_response.startsWith("/")) {
                mc.execute(() -> mc.player.networkHandler.sendChatCommand(cleaned_response.substring(1)));
            } else {
                mc.execute(() -> mc.player.networkHandler.sendChatMessage(cleaned_response));
            }
        }, "AO2Thread").start();
    }

    private String preparePrompt(String input) {
        String prompt = promptTemplate.get();
        prompt = prompt.replace("{input}", input);
        for (int i = 1; i <= 1000; i++) {
            prompt = prompt.replace("{history_" + i + "}", getRecentMessages(i));
        }
        return prompt;
    }

    private String getRecentMessages(int count) {
        StringBuilder sb = new StringBuilder();
        int size = Math.min(count, recentMessages.size());
        for (int i = 0; i < size; i++) {
            sb.append(recentMessages.get(recentMessages.size() - size + i)).append("\n");
        }
        return sb.toString().trim();
    }

    private String cleanMessage(String message) {
        if (message == null) return null;

        StringBuilder sb = new StringBuilder(message.length());

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            // Skip control / illegal characters
            if (Character.isISOControl(c)) continue;

            // Normalize newlines to spaces
            if (c == '\n' || c == '\r') {
                sb.append(' ');
                continue;
            }

            sb.append(c);
        }

        String cleaned = sb.toString();

        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 197) + "...";
        }

        return cleaned;
    }


    private String queryOllama(String model, String prompt) {
        try {
            URL url = new URL(baseUrl.get() + "/api/chat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String json = String.format("{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false}",
                escapeJson(model), escapeJson(prompt), escapeJson(prompt));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                error("Ollama query failed: HTTP " + conn.getResponseCode());
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    int contentStartIndex = line.indexOf("\"content\":\"");
                    if (contentStartIndex != -1) {
                        contentStartIndex += 11;
                        StringBuilder contentBuilder = new StringBuilder();
                        boolean isEscaped = false;
                        for (int i = contentStartIndex; i < line.length(); i++) {
                            char ch = line.charAt(i);
                            if (isEscaped) {
                                switch (ch) {
                                    case '\"': contentBuilder.append('"'); break;
                                    case '\\': contentBuilder.append('\\'); break;
                                    case 'n': contentBuilder.append('\n'); break;
                                    case 'r': contentBuilder.append('\r'); break;
                                    case 't': contentBuilder.append('\t'); break;
                                    default: contentBuilder.append(ch); break;
                                }
                                isEscaped = false;
                            } else if (ch == '\\') {
                                isEscaped = true;
                            } else if (ch == '"') {
                                break;
                            } else {
                                contentBuilder.append(ch);
                            }
                        }
                        response.append(contentBuilder.toString());
                    }
                }
            }
            conn.disconnect();

            return response.toString()
                .replaceAll("(?i)<think>.*?</think>", "")
                .replaceAll("(?i)\\\\u003c/?think\\\\u003e", "")
                .replaceAll("(?i)<think>|</think>", "")
                .trim();
        } catch (Exception e) {
            error("Ollama query failed: " + e.getMessage());
            return null;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }




}
