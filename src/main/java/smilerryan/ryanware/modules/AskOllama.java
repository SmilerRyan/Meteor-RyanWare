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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
        .description("Model to use.")
        .defaultValue("llama3.2:latest")
        .build()
    );

    private final Setting<List<String>> triggerWords = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-words")
        .description("Words that trigger the AI call.")
        .defaultValue(Arrays.asList("#"))
        .build()
    );

    private final Setting<String> directSendPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("direct-send-prefix")
        .description("the prefix that the AI must use to have its response sent directly to chat.")
        .defaultValue("/send ")
        .build()
    );

    private final Setting<Boolean> directSendNoPrefix = sgGeneral.add(new BoolSetting.Builder()
        .name("direct-send-without-prefix")
        .description("If enabled, responses without commands are sent directly to chat as if typed by you without a prefix.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> viewAllResponses = sgGeneral.add(new BoolSetting.Builder()
        .name("view-all-responses")
        .description("Allow you to see invalid Ollama response lines that don't start with the direct send prefix.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> waitDelayMs = sgGeneral.add(new IntSetting.Builder()
        .name("wait-delay-ms")
        .description("How long to wait (in ms) before collecting messages and sending to AI.")
        .defaultValue(0)
        .min(0)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Integer> messageLimit = sgGeneral.add(new IntSetting.Builder()
        .name("message-limit")
        .description("Maximum number of messages to include in the prompt.")
        .defaultValue(20)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<List<String>> ignoreOutKeywords = sgGeneral.add(new StringListSetting.Builder()
        .name("ignore-out-keywords")
        .description("If the response message contains any of these, it will not be sent.")
        .defaultValue()
        .build()
    );

    // New prompt template setting
    private final Setting<String> promptTemplate = sgPrompts.add(new StringSetting.Builder()
        .name("prompt-template")
        .description("Template used for building the prompt. Use {send_prefix} for the send prefix, {messages} for previous chat messages and {prompt} for the current message.")
        .defaultValue("You are an chat responder for Minecraft Chat. You must respond with '{send_prefix}' and your short response or your reply wil be ignored.\n\n{messages}\n{prompt}\n")
        .build()
    );

    // dropdown pick message recieve mode (onReceiveMessage, onChat, both)
    private enum MessageReceiveMode { onReceiveMessage, onChat, Both }
    private final Setting<MessageReceiveMode> messageReceiveMode = sgGeneral.add(new EnumSetting.Builder<MessageReceiveMode>()
        .name("message-receive-mode")
        .description("Which event to listen to for receiving messages.")
        .defaultValue(MessageReceiveMode.onReceiveMessage)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Deque<String> recentMessages = new ArrayDeque<>();
    private static final int MAX_MESSAGES = 100;

    public AskOllama() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "AskOllama", "Uses Ollama to answer in-game questions based on recent chat.");
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        recentMessages.clear();
    }

    private void messageReceived(String msg) {

        // Add recent chat
        recentMessages.addLast(msg);
        while (recentMessages.size() > MAX_MESSAGES) recentMessages.removeFirst();

        // Trigger check
        boolean triggered = false;
        for (String trigger : triggerWords.get()) {
            if (msg.toLowerCase(Locale.ROOT).contains(trigger.toLowerCase(Locale.ROOT))) {
                triggered = true;
                break;
            }
        }
        if (!triggered) return;

        // New thread?
        executor.execute(() -> {
            
            // Wait a moment to allow more messages to come in if needed
            try {Thread.sleep(waitDelayMs.get());} catch (InterruptedException ignored) {}

            // Get recent message
            List<String> messages = new ArrayList<>(recentMessages);
            int limit = messageLimit.get();
            if (messages.size() > limit) {messages = messages.subList(messages.size() - limit, messages.size());}

            // Build prompt
            String fullPrompt = promptTemplate.get()
            .replace("{messages}", String.join("\n", messages))
            .replace("{prompt}", msg)
            .replace("{send_prefix}", directSendPrefix.get());

            // Query Ollama
            String reply = queryOllama(fullPrompt);

            // Skip If response contains any ignore keywords
            if (reply == null) return;
            for (String keyword : ignoreOutKeywords.get()) {
                if (reply.contains(keyword)) return;
            }
            
            // For each line
            for (String line : reply.split("\n")) {

                // Cancel if module was turned off
                if (!isActive()) {return;}

                // Trim and skip empty lines
                String message = line.trim();
                if (message.isEmpty()) continue;
                if (message.length() > 200) {message = message.substring(0, 200) + "... (trimmed)";}

                // Check for the send prefix and strip it if present
                boolean forceSend = message.toLowerCase(Locale.ROOT).startsWith(directSendPrefix.get());
                if (forceSend) {message = message.substring(directSendPrefix.get().length()).trim(); if (message.isEmpty()) continue;}
                
                // If the line was forced or direct
                if (!forceSend || !directSendNoPrefix.get()) {

                    // Show in chat if enabled
                    if (viewAllResponses.get()) {
                        info(message);
                    }

                    continue;

                } else {

                    // Send chat or command
                    if (message.startsWith("/")) {
                        mc.player.networkHandler.sendChatCommand(message.substring(1));
                    } else {
                        mc.player.networkHandler.sendChatMessage(message);
                    }

                }

            }

            
        });

    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.getMessage() == null || mc.player == null) return;
        if (messageReceiveMode.get() == MessageReceiveMode.onReceiveMessage || messageReceiveMode.get() == MessageReceiveMode.Both) messageReceived(event.getMessage().getString());
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        if (event.getMessage() == null || mc.player == null) return;
        if (messageReceiveMode.get() == MessageReceiveMode.onChat || messageReceiveMode.get() == MessageReceiveMode.Both) messageReceived(event.getMessage().getString());
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
                error("Failed: HTTP " + conn.getResponseCode());
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                if (!isActive()) {conn.disconnect(); return "";}
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
