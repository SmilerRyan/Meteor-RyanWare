package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;

import meteordevelopment.meteorclient.utils.Utils;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class AIChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> prompt = sgGeneral.add(new StringSetting.Builder()
        .name("prompt")
        .description("Prompt sent to the AI worker.")
        .defaultValue("Hello!")
        .build()
    );

    private final HttpClient client = HttpClient.newHttpClient();

    public AIChat() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "AI Chat",
            "Sends a prompt to an AI worker and prints the response in chat."
        );
    }

    @Override
    public void onActivate() {
        sendRequest();
    }

    private void sendRequest() {
        String encodedPrompt = URLEncoder.encode(prompt.get(), StandardCharsets.UTF_8);
        String url = "https://ai.ryanthonton0465.workers.dev?q=" + encodedPrompt;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        CompletableFuture.supplyAsync(() -> {
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }).thenAccept(response -> {
            if (mc.player != null) {
                mc.execute(() -> {
                    mc.player.sendMessage(Utils.prefix("AI: " + response), false);
                });
            }
        });
    }
}