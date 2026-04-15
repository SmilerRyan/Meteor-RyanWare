package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AskOllama extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPrompts = settings.createGroup("Prompts");

    private final Setting<String> baseUrl = sgGeneral.add(new StringSetting.Builder()
        .name("ollama-url")
        .description("Base URL of the Ollama server.")
        .defaultValue("http://localhost:11434")
        .build()
    );

    private final Setting<String> model = sgGeneral.add(new StringSetting.Builder()
        .name("model")
        .description("Model to use (e.g., llama3).")
        .defaultValue("llama3")
        .build()
    );

    private final Setting<List<String>> triggerWords = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-words")
        .description("Words that trigger the AI call.")
        .defaultValue(Arrays.asList("unscramble"))
        .build()
    );

    private final Setting<String> extraContext = sgGeneral.add(new StringSetting.Builder()
        .name("extra-context")
        .description("Extra context to include in the prompt.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> allowRespondAsMe = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-responding-as-me")
        .description("Allow Ollama to respond as you by sending chat messages or commands if response starts with [SEND].")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> simulateRespondAsMe = sgGeneral.add(new BoolSetting.Builder()
        .name("simulate-responding-as-me")
        .description("If true, logs the respond-as-me messages instead of actually sending them (for testing).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowOtherResponses = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-other-responses")
        .description("Allow you to see invalid Ollama response lines that don't start with /send or /guide prefixes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowGuideResponses = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-guide-responses")
        .description("Allow Ollama to provide '/guide' messages, and include '/guide' instructions in the AI prompt.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> directSend = sgGeneral.add(new BoolSetting.Builder()
        .name("direct-send-without-prefix")
        .description("If enabled, responses without commands are sent directly to chat as if typed by you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> waitDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("wait-delay-ms")
        .description("How long to wait (in ms) before collecting messages and sending to AI.")
        .defaultValue(800)
        .min(0)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Integer> contextLimit = sgGeneral.add(new IntSetting.Builder()
        .name("context-limit")
        .description("Maximum number of messages to include in prompt context.")
        .defaultValue(20)
        .min(1)
        .sliderMax(50)
        .build()
    );

    // New prompt template setting
    private final Setting<String> promptTemplate = sgPrompts.add(new StringSetting.Builder()
        .name("prompt-template")
        .description("Template used for building the prompt.\n" +
            "Placeholders:\n" +
            "{context} = recent chat messages\n" +
            "{userPrompt} = extracted user input\n" +
            "{extraContext} = extra context setting\n" +
            "{player} = your player name\n")
        .defaultValue(
            "{context}\n" +
            "{userPrompt}\n\n" +
            "User context:\n{extraContext}\n\n" +
            "You can respond as {player} with '/say ...'.\n" +
            "You can guide {player} on how to respond with '/guide ...'.\n" +
            "You may also respond with '/stfu', '/nothing', '/stop' or '/ignore' to provide no response.\n" +
            "REMEMBER, ALL RESPONSES WITHOUT A COMMAND/PREFIX WILL BE IGNORED.\n" +
            "Your response:\n"
        )
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Deque<String> recentMessages = new ArrayDeque<>();
    private static final int MAX_MESSAGES = 50;

    private static final Set<String> STOP_COMMANDS = new HashSet<>(Arrays.asList(
        "/stfu", "/nothing", "/stop", "/ignore"
    ));

    public AskOllama() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "AskOllama", "Uses Ollama to answer in-game questions based on recent chat.");
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        recentMessages.clear();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
    //private void onChat(ReceiveMessageEvent event) {
        if (!isActive() || event.getMessage() == null) return;

        String msg = event.getMessage().getString();

        if (
            msg.startsWith("[Ollama Other]") || 
            msg.startsWith("[Ollama Guide]") || 
            msg.startsWith("[Ollama Send Simulated]") || 
            msg.startsWith("[Ollama Send Blocked]")
        ) return;

        recentMessages.addLast(msg);
        if (recentMessages.size() > MAX_MESSAGES) recentMessages.removeFirst();

        for (String trigger : triggerWords.get()) {
            int index = msg.toLowerCase().indexOf(trigger.toLowerCase());
            if (index != -1) {
                executor.submit(() -> {
                    try { Thread.sleep(waitDelayMs.get()); } catch (InterruptedException ignored) {}

                    List<String> context = new ArrayList<>(recentMessages);
                    int limit = contextLimit.get();
                    if (context.size() > limit) context = context.subList(context.size() - limit, context.size());

                    String contextStr = String.join("\n", context);
                    String userPrompt = msg.substring(index + trigger.length()).trim();
                    String playerName = mc.player != null ? mc.player.getGameProfile().getName() : "Player";

                    String fullPrompt = promptTemplate.get()
                        .replace("{context}", contextStr)
                        .replace("{userPrompt}", userPrompt)
                        .replace("{extraContext}", extraContext.get())
                        .replace("{player}", playerName);

                    String reply = queryOllama(fullPrompt);
                    if (reply != null) {
                        mc.execute(() -> {
                            String[] lines = reply.split("\n");
                            for (String line : lines) {
                                String trimmedLower = line.trim().toLowerCase(Locale.ROOT);
                                if (line.length() > 200) line = line.substring(0, 200) + "... (trimmed)";

                                if (STOP_COMMANDS.contains(trimmedLower)) return;

                                if (trimmedLower.startsWith("/say ")) {
                                    String message = line.substring(5).trim();
                                    if (message.isEmpty()) continue;
                                    if (!allowRespondAsMe.get()) {
                                        mc.inGameHud.getChatHud().addMessage(Text.of("[Ollama Send Blocked] " + message));
                                        continue;
                                    }
                                    if (simulateRespondAsMe.get()) {
                                        mc.inGameHud.getChatHud().addMessage(Text.of("[Ollama Send] " + message));
                                    } else if (mc.player != null) {
                                        if (message.startsWith("/")) mc.player.networkHandler.sendChatCommand(message.substring(1));
                                        else mc.player.networkHandler.sendChatMessage(message);
                                    }
                                } else if (trimmedLower.startsWith("/guide ")) {
                                    if (!allowGuideResponses.get()) continue;
                                    String guide = line.substring(7).trim();
                                    if (!guide.isEmpty()) mc.inGameHud.getChatHud().addMessage(Text.of("[Ollama Guide] " + guide));
                                } else {
                                    if (directSend.get()) {
                                        String message = line.trim();
                                        if (!allowRespondAsMe.get()) {
                                            mc.inGameHud.getChatHud().addMessage(Text.of("[Ollama Send Blocked] " + message));
                                            continue;
                                        }
                                        if (simulateRespondAsMe.get()) {
                                            mc.inGameHud.getChatHud().addMessage(Text.of("[Ollama Send Simulated] " + message));
                                        } else if (mc.player != null) {
                                            if (message.startsWith("/")) mc.player.networkHandler.sendChatCommand(message.substring(1));
                                            else mc.player.networkHandler.sendChatMessage(message);
                                        }
                                    } else if (allowOtherResponses.get()) {
                                        mc.inGameHud.getChatHud().addMessage(Text.of("[Ollama Other] " + line));
                                    }
                                }
                            }
                        });
                    }
                });
                break;
            }
        }
    }

    private String queryOllama(String prompt) {
        try {
            URL url = new URL(baseUrl.get() + "/api/chat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String json = String.format("{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false}",
                escapeJson(model.get()), escapeJson(prompt), escapeJson(prompt));

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
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
