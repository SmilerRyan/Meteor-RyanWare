package smilerryan.ryanware.modules_standard.chat.ollama;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import smilerryan.ryanware.modules_standard.Settings;
import net.minecraft.client.MinecraftClient;

public class OllamaTranslator extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPrompt = settings.createGroup("Prompt");

    private volatile boolean sendingTranslatedChat = false;

    private final Setting<String> model = sgGeneral.add(new StringSetting.Builder()
        .name("model")
        .description("Model to use.")
        .defaultValue("translategemma:latest")
        .build()
    );

    private final Setting<String> promptTemplate = sgPrompt.add(new StringSetting.Builder()
        .name("prompt-template")
        .description("Prompt template, use {input} for your message.")
        .defaultValue("You are a chat translator and rewriter, rewrite or transform the following message exactly as requested by the user and Return ONLY the final message content.\n\nMessage:\n{input}\n\nTranslated:\n\n")
        .build()
    );

    private final Setting<Boolean> queueMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("queue-messages")
        .description("If enabled, messages will be sent as soon as possible instead of blocking chat whilst waiting for a response.")
        .defaultValue(true)
        .build()
    );

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public OllamaTranslator() {
        super(
            RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Ollama-Translator",
            "Sends outgoing chat messages through Ollama and posts the model output directly."
        );
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!isActive() || event.message == null || event.message.isBlank() || sendingTranslatedChat) return;
        String fullPrompt = promptTemplate.get().replace("{input}", event.message);
        if (queueMessages.get()) {
            event.cancel();
            new Thread(() -> {
                String response = Ollama.queryOllama(Modules.get().get(Settings.class).s_Ollama_Url.get(), model.get(), fullPrompt, this);
                sendingTranslatedChat = true;
                mc.getNetworkHandler().sendChatMessage(response);
                sendingTranslatedChat = false;
            }, "Ollama-Translator-Worker").start();
        } else {
            event.message = Ollama.queryOllama(Modules.get().get(Settings.class).s_Ollama_Url.get(), model.get(), fullPrompt, this);
        }
    }

}
