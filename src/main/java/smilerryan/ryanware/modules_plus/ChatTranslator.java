package smilerryan.ryanware.modules_plus;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;

public class ChatTranslator extends Module {
    private static final String API_BASE = "https://translate-pa.googleapis.com/v1/translate?params.client=gtx&dataTypes=TRANSLATION&key=AIzaSyDLEeFI5OtFBwYBIoK_jj5m32rZK5CkCXA&query.sourceLanguage=auto&query.targetLanguage=en&query.text=";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public ChatTranslator() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "Chat-Translator", "Translates all messages into ENGLISH via Google Translate.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String original = event.getMessage().getString().trim();
        if (original.isEmpty() || original.startsWith("[English]")) return;

        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_BASE + encoded)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String json = response.body();
                String translation = extractTranslation(json);

                if (translation != null && !original.equalsIgnoreCase(translation)) {
                    mc.execute(() -> mc.inGameHud.getChatHud().addMessage(Text.literal("§7[§4English§7] " + translation)));
                }
            } catch (Exception e) {
                System.err.println("Translation failed: " + e.getMessage());
            }
        }).start();
    }

    public static String extractTranslation(String json) {
        try {
            int startIndex = json.indexOf("\"translation\": \"") + 16;
            if (startIndex < 16) return null;
            int endIndex = json.indexOf('"', startIndex);
            if (endIndex == -1) return null;
            String input = json.substring(startIndex, endIndex);
            StringBuilder builder = new StringBuilder();
            int i = 0;
            while (i < input.length()) {
                char currentChar = input.charAt(i);
                if (currentChar == '\\' && i + 1 < input.length() && input.charAt(i + 1) == 'u') {
                    // Found a Unicode escape sequence
                    if (i + 5 < input.length()) {
                        try {
                            String hexCode = input.substring(i + 2, i + 6);
                            char unicodeChar = (char) Integer.parseInt(hexCode, 16);
                            builder.append(unicodeChar);
                            i += 6; // Skip past the escape sequence
                            continue;
                        } catch (NumberFormatException e) {
                            // Invalid Unicode sequence, just treat as normal text
                        }
                    }
                }
                builder.append(currentChar);
                i++;
            }
            return builder.toString();
        } catch (Exception e) {
            System.err.println("Failed to translate: " + e.getMessage());
            return null;
        }
    }

}
