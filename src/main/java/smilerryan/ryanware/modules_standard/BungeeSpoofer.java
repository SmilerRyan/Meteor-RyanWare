package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BungeeSpoofer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Toggle personalities
    private final Setting<Boolean> usePersonality = sgGeneral.add(
        new BoolSetting.Builder()
            .name("use-personality")
            .description("Use preset uuid;ip instead of current session.")
            .defaultValue(false)
            .build()
    );

    // Personality list (uuid;ip)
    private final Setting<List<String>> personalities = sgGeneral.add(
        new StringListSetting.Builder()
            .name("personalities")
            .description("Format: uuid;ip (ip optional)")
            .defaultValue(new ArrayList<>())
            .visible(usePersonality::get)
            .build()
    );

    // 1-based index
    private final Setting<Integer> index = sgGeneral.add(
        new IntSetting.Builder()
            .name("index")
            .description("1-based personality selection")
            .defaultValue(1)
            .min(1)
            .visible(usePersonality::get)
            .build()
    );

    // Manual IP override
    private final Setting<String> ip = sgGeneral.add(
        new StringSetting.Builder()
            .name("ip")
            .description("Override IP (blank = 127.0.0.1)")
            .defaultValue("")
            .build()
    );

    public BungeeSpoofer() {
        super(
            RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Bungee-Spoofer",
            "Spoofs BungeeCord handshake with UUID + IP."
        );
    }

    public static class Personality {
        public final String uuid;
        public final String ip;

        public Personality(String uuid, String ip) {
            this.uuid = uuid;
            this.ip = ip;
        }
    }

    public Personality getSelected() {
        String finalUuid;
        String finalIp = ip.get().isEmpty() ? "127.0.0.1" : ip.get();

        // Use preset personality
        if (usePersonality.get()) {
            List<String> list = personalities.get();
            int i = index.get() - 1;

            if (i < 0 || i >= list.size()) return null;

            String entry = list.get(i);
            String[] parts = entry.split(";", -1);

            if (parts.length == 0 || parts[0].isEmpty()) return null;

            finalUuid = parts[0];

            // preset IP overrides manual if present
            if (parts.length >= 2 && !parts[1].isEmpty()) {
                finalIp = parts[1];
            }

            return new Personality(finalUuid, finalIp);
        }

        // Default: use current session UUID (auto-fetch)
        String name = MinecraftClient.getInstance().getSession().getUsername();
        try {
            finalUuid = new String(new java.net.URL("https://api.mojang.com/users/profiles/minecraft/" + name).openStream().readAllBytes()).substring(12, 44);
        } catch (Exception e) {
            return null;
        }
        if (finalUuid == null) return null;

        return new Personality(finalUuid, finalIp);
    }
}