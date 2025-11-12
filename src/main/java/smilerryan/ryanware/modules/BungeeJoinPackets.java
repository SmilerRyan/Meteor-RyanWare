package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;

public class BungeeJoinPackets extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Setting for specifying UUID manually
    public final Setting<String> uuid = sgGeneral.add(new StringSetting.Builder()
        .name("uuid")
        .description("The UUID to use when joining a BungeeCord server. Leave empty for session UUID.")
        .defaultValue("") // empty = use session UUID
        .build()
    );

    public BungeeJoinPackets() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Bungee-Join-Packets",
            "Lets you join BungeeCord servers without connecting through a proxy.");
    }

    /** Returns the UUID to use: either the specified one or the session's UUID */
    public String getUuidToUse() {
        String customUuid = uuid.get().replace("-", "");
        if (!customUuid.isEmpty()) return customUuid; // use custom UUID
        return net.minecraft.client.MinecraftClient.getInstance().getSession().getUuidOrNull().toString().replace("-", "");
    }
}
