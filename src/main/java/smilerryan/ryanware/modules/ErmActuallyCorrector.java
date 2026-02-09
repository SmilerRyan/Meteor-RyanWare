package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ErmActuallyCorrector extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> cooldownSeconds = sgGeneral.add(
        new IntSetting.Builder()
            .name("cooldown-seconds")
            .description("Cooldown before correcting the same player again.")
            .defaultValue(10)
            .min(1)
            .sliderMax(60)
            .build()
    );

    private final Setting<String> format = sgGeneral.add(
        new StringSetting.Builder()
            .name("format")
            .description("Message format. [S] = sender, [C] = corrected sentence.")
            .defaultValue("Erm Actually [S], your sentence does NOT use proper English. This is the proper way \"[C]\"*")
            .build()
    );

    private final Map<String, Long> lastCorrected = new HashMap<>();

    public ErmActuallyCorrector() {
        super(
            RyanWare.CATEGORY,
            RyanWare.modulePrefix_extras + "ErmActually-Corrector",
            "Corrects bad English in chat using the LanguageTool.org API."
        );
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        if (!isActive() || mc.player == null) return;

        String raw = e.getMessage().getString();

        String sender = "unknown";
        if (raw.startsWith("<") && raw.contains(">")) {
            sender = raw.substring(1, raw.indexOf(">"));
        } else {
            return;
        }

        //if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;

        String content = raw;
        if (raw.startsWith("<") && raw.contains("> ")) {
            content = raw.substring(raw.indexOf("> ") + 2);
        }

        long now = System.currentTimeMillis();
        long last = lastCorrected.getOrDefault(sender, 0L);

        if (now - last < cooldownSeconds.get() * 1000L) return;

        String corrected = correctWithLanguageTool(content);

        if (corrected == null || corrected.equalsIgnoreCase(content)) return;

        sender = sender.replace("§", "");
        corrected = corrected.replace("§", "");

        String msg = format.get()
            .replace("[S]", sender)
            .replace("[C]", corrected);

        if (msg.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(msg.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(msg);
        }

        lastCorrected.put(sender, now);
    }

    private String correctWithLanguageTool(String text) {
        try {
            URL url = new URL("https://api.languagetool.org/v2/check");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String params =
                "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8) +
                "&language=en-US";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream())
            );

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            String json = response.toString();
            String corrected = text;

            String[] matches = json.split("\"replacements\":");

            for (int i = 1; i < matches.length; i++) {
                String part = matches[i];

                if (part.contains("\"value\":\"")) {
                    String replacement =
                        part.split("\"value\":\"")[1].split("\"")[0];

                    corrected = corrected.replaceFirst(
                        "\\b\\w+\\b",
                        replacement
                    );
                }
            }

            return corrected;

        } catch (Exception ignored) {
            return null;
        }
    }
}
