package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class BungeeJoinPackets extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // List of personalities (uuid;ip)
    private final Setting<List<String>> personalities = sgGeneral.add(
        new StringListSetting.Builder()
            .name("personalities")
            .description("Format: uuid;ip (ip optional, defaults to 127.0.0.1)")
            .defaultValue(new ArrayList<>())
            .build()
    );

    // 1‑based index
    private final Setting<Integer> index = sgGeneral.add(
        new IntSetting.Builder()
            .name("index")
            .description("1-based personality selection")
            .defaultValue(1)
            .min(1)
            .build()
    );

    public BungeeJoinPackets() {
        super(
            RyanWare.CATEGORY,
            RyanWare.modulePrefix_extras + "Bungee-Join-Packets",
            "Spoofs BungeeCord handshake with UUID + IP."
        );
    }

    /** Parsed result */
    public static class Personality {
        public final String uuid;
        public final String ip;

        public Personality(String uuid, String ip) {
            this.uuid = uuid;
            this.ip   = ip;
        }
    }

    /** Returns parsed personality or null if invalid index */
    public Personality getSelected() {
        List<String> list = personalities.get();
        int i = index.get() - 1;

        if (i < 0 || i >= list.size()) return null;

        String entry = list.get(i);
        String[] parts = entry.split(";", -1);

        if (parts.length == 0 || parts[0].isEmpty()) return null;

        String uuid = parts[0];
        String ip   = (parts.length >= 2 && !parts[1].isEmpty()) ? parts[1] : "127.0.0.1";

        return new Personality(uuid, ip);
    }
}
