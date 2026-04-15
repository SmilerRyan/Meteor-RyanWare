package smilerryan.ryanware.modules_3;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
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

import com.google.gson.*;

public class ChatTranslator extends Module {
    public enum Mode {
        Reading, Sending, Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgReading = settings.createGroup("Reading");
    private final SettingGroup sgSending = settings.createGroup("Sending");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which messages to translate.")
        .defaultValue(Mode.Reading)
        .build()
    );

    private final Setting<String> readingLanguage = sgReading.add(new StringSetting.Builder()
        .name("target-language")
        .description("Language chain to translate incoming messages to. (e.g. en, fr,en, ja,ru,en)")
        .defaultValue("en")
        .build()
    );

    private final Setting<String> readingPrefix = sgReading.add(new StringSetting.Builder()
        .name("prefix")
        .description("Prefix to show for translated messages.")
        .defaultValue("&7[&cEnglish&7] &d")
        .build()
    );

    private final Setting<String> sendingLanguage = sgSending.add(new StringSetting.Builder()
        .name("send-language")
        .description("Language chain to translate your messages to. (e.g. en, fr,en, ja,ru,en)")
        .defaultValue("fr,en")
        .build()
    );

    private static final String API_BASE = "https://translate-pa.googleapis.com/v1/translate?params.client=gtx&dataTypes=TRANSLATION&key=AIzaSyDLEeFI5OtFBwYBIoK_jj5m32rZK5CkCXA";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public ChatTranslator() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Chat-Translator", "Translates chat messages via Google Translate.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mode.get() == Mode.Sending) return;
        String original = event.getMessage().getString().replaceAll("^<\\d{1,2}:\\d{2}(?::\\d{2})?>", "").trim();

        if (original.isEmpty() || original.startsWith(readingPrefix.get().replace("&", "§"))) return;

        new Thread(() -> {
            try {
                String translation = translateChain(original, readingLanguage.get());

                if (translation != null && !original.equalsIgnoreCase(translation)) {
                    mc.execute(() -> mc.inGameHud.getChatHud().addMessage(
                        Text.literal(readingPrefix.get().replace("&", "§") + translation)
                    ));
                }
            } catch (Exception e) {
                System.err.println("Translation failed: " + e.getMessage());
            }
        }).start();
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (mode.get() == Mode.Reading) return;

        String original = event.message;

        try {
            String translation = translateChain(original, sendingLanguage.get());
            event.message = translation != null ? translation : original;
        } catch (Exception e) {
            System.err.println("Translation send failed: " + e.getMessage());
        }
    }

    // === Translation chain (multi-hop) ===
    private String translateChain(String text, String chain) throws Exception {
        if (chain == null || chain.isEmpty()) return text;

        String[] langs = chain.split(",");
        String current = text;

        for (String lang : langs) {
            lang = lang.trim();
            if (lang.isEmpty()) continue;

            String encoded = URLEncoder.encode(current, StandardCharsets.UTF_8);
            String url = API_BASE
                + "&query.sourceLanguage=auto"
                + "&query.targetLanguage=" + lang
                + "&query.text=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String translated = extractTranslation(response.body());

            if (translated == null) return current; // fail-safe
            current = translated;
        }

        return current;
    }

    public static String extractTranslation(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("translation")) return null;
            return root.get("translation").getAsString();
        } catch (Exception e) {
            System.err.println("Translation extract failed: " + e.getMessage());
            return null;
        }
    }
}