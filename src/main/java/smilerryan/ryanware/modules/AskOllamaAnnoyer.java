package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class AskOllamaAnnoyer extends Module {

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
        .description("Use {input} for the message.")
        .defaultValue("Rewrite the following Minecraft chat message in a friendly 'Erm, actually...' style. Remove all usernames, prefixes, tags, or special codes. Format it like: 'Erm, actually USERNAME, meant to say: MESSAGE'. Correct only spelling or grammar mistakes. If the message is already correct, or if it is a system message like 'X left the game', respond with exactly [NONE] and nothing else. {input}")
        .build()
    );

    private final Setting<String> ignoreInKeyword = sgGeneral.add(new StringSetting.Builder()
        .name("ignore-in-keyword")
        .description("If the incoming message contains this keyword, it will be ignored and not sent to Ollama.")
        .defaultValue("")
        .build()
    );
	
	private final Setting<String> ignoreOutKeyword = sgGeneral.add(new StringSetting.Builder()
        .name("ignore-out-keyword")
        .description("If the response message contains this keyword, it will not be sent.")
        .defaultValue("[NONE]")
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ms")
        .description("Minimum time in ms between queries. 0 disables cooldown.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10000)
        .build()
    );

    private final AtomicLong lastQueryTime = new AtomicLong(0);

    public AskOllamaAnnoyer() {
        super(
            RyanWare.CATEGORY,
            RyanWare.modulePrefix_extras + "AskOllamaAnnoyer",
            "Corrects bad English with AI."
        );
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        if (!isActive() || mc.player == null) return;

        String raw = e.getMessage().getString();

        // Ignore messages containing the keyword
        if (ignoreInKeyword.get() != null && !ignoreInKeyword.get().isEmpty() && raw.contains(ignoreInKeyword.get())) {
            return;
        }

        // Ignore cooldown
        long now = System.currentTimeMillis();
        if (cooldown.get() > 0 && now - lastQueryTime.get() < cooldown.get()) return;
        lastQueryTime.set(now);

        // Run Ollama query on a separate thread to avoid lag
        new Thread(() -> {
            String response = queryOllama(model.get(), promptTemplate.get().replace("{input}", raw));
            if (response == null || response.isEmpty() || response.contains(ignoreOutKeyword.get())) return;

            if (response.startsWith("/")) {
                mc.execute(() -> mc.player.networkHandler.sendChatCommand(response.substring(1)));
            } else {
                mc.execute(() -> mc.player.networkHandler.sendChatMessage(response));
            }
        }, "OllamaAnnoyerThread").start();
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
