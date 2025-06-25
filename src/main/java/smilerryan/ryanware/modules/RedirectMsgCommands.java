package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class RedirectMsgCommands extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> whitelistedUsers = sgGeneral.add(new StringListSetting.Builder()
        .name("whitelisted-users")
        .description("Usernames to redirect /msg for, If empty this applies to all users.")
        .defaultValue(List.of())
        .build()
    );

    public RedirectMsgCommands() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "Redirect-MSG-Commands", "Intercepts /msg commands to log messages.");
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String msg = event.message.trim();
        if (!msg.toLowerCase().startsWith("/msg ")) return;

        String[] split = msg.substring(5).trim().split("\\s+", 2);
        if (split.length < 2) return;

        String target = split[0];
        String message = split[1];

        List<String> whitelist = whitelistedUsers.get();
        if (!whitelist.isEmpty() && !whitelist.contains(target)) return;

        event.cancel();
        info("Redirected /msg to " + target + ": " + message);
    }
}
