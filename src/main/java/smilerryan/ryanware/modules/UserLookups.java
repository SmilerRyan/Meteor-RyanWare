package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class UserLookups extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-NameMC-profile").defaultValue(false).build());
    private final Setting<Boolean> autoLookup = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-lookup-names").defaultValue(false).build());

    public UserLookups() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "User-Lookups",
            "Adds a NameMC Auto-Opener or Auto Name Lookup of users from the Laby API for join messages.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().trim();
    
        // Remove optional timestamp like "<12:34>" or "<12:34:56>"
        msg = msg.replaceFirst("^<\\d{1,2}:\\d{2}(?::\\d{2})?>\\s*", "").trim();
    
        if (!msg.matches("^[A-Za-z0-9_]{1,16} joined the game\\.?$")) return;
    
        String name = msg.split(" ")[0];
    
        if (autoOpen.get()) {
            try { Desktop.getDesktop().browse(new URI("https://namemc.com/profile/" + name)); } catch (Exception ignored) {}
        }
    
        if (autoLookup.get()) {
            new Thread(() -> {
                List<String> names = getOldNamesFromLaby(name);
                if (names.size() > 1) {
                    String text = name + ", also known as " + String.join(", ", names) + ".";
                    info(text);
                } else {
                    String text = name + " has no name history.";
                    info(text);
                }
            }).start();
        }
    
    }
    
    private List<String> getOldNamesFromLaby(String name) {
        List<String> names = new ArrayList<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("https://laby.net/api/v3/search/profiles/" + name).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            if (conn.getResponseCode() != 200) return names;

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder resp = new StringBuilder(); String line;
            while ((line = in.readLine()) != null) resp.append(line);
            in.close();

            Matcher m = Pattern.compile("\"name\":\"([^\"]+)\"").matcher(resp.toString());
            while (m.find()) {
                String found = m.group(1);
                if (!names.contains(found)) names.add(found);
            }
        } catch (Exception e) {
            error("Lookup failed: " + e.getMessage());
        }
        return names;
    }
}
