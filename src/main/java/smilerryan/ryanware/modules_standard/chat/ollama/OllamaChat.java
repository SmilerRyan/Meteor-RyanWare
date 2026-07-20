package smilerryan.ryanware.modules_standard.chat.ollama;

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

public class OllamaChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPrompts = settings.createGroup("Prompts");

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

    private final Setting<String> forceOutputPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("forced-output-prefix")
        .description("The prefix that will be added automatically to ALL ai output, leave empty for none.")
        .defaultValue("")
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

    public OllamaChat() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Ollama-Chat", "Uses Ollama to answer in-game questions based on recent chat.");
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
            String reply = Ollama.queryOllama(Modules.get().get(Settings.class).s_Ollama_Url.get(), model.get(), fullPrompt, this);

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
                if ( forceSend || directSendNoPrefix.get() ) {
                    final String output = forceOutputPrefix.get() + message;
                    mc.execute(() -> {
                        smilerryan.ryanware.utils.SendChat.any(output);
                    });
                } else {
                    // Show in chat if enabled
                    if (viewAllResponses.get()) {
                        info(message);
                    }
                }
                continue;

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

}
