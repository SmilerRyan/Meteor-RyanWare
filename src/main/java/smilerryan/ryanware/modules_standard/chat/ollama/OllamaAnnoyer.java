package smilerryan.ryanware.modules_standard.chat.ollama;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import java.util.concurrent.atomic.AtomicLong;

public class OllamaAnnoyer extends Module {

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
        .defaultValue("Erm,")
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

    // dropdown pick message recieve mode (onReceiveMessage, onChat, both)
    private enum MessageReceiveMode { onReceiveMessage, onChat, Both }
    private final Setting<MessageReceiveMode> messageReceiveMode = sgGeneral.add(new EnumSetting.Builder<MessageReceiveMode>()
        .name("message-receive-mode")
        .description("Which event to listen to for receiving messages.")
        .defaultValue(MessageReceiveMode.onReceiveMessage)
        .build()
    );

    private final AtomicLong lastQueryTime = new AtomicLong(0);

    public OllamaAnnoyer() {
        super(
            RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Ollama-Annoyer",
            "Prompts Ollama AI to respond for every chat message."
        );
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

    private void messageReceived(String raw) {
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
            String response = Ollama.queryOllama(baseUrl.get(), model.get(), promptTemplate.get().replace("{input}", raw), this);
            if (response == null || response.isEmpty() || response.contains(ignoreOutKeyword.get())) return;

            if (response.startsWith("/")) {
                mc.execute(() -> mc.player.networkHandler.sendChatCommand(response.substring(1)));
            } else {
                mc.execute(() -> mc.player.networkHandler.sendChatMessage(response));
            }
        }, "OllamaAnnoyerThread").start();
    }

}
