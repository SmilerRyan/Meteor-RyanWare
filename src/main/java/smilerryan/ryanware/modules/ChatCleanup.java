package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.text.Text;
import java.util.List;
import java.util.regex.Pattern;

public class ChatCleanup extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> annoyingChats = sgGeneral.add(new StringListSetting.Builder()
        .name("filtered-patterns")
        .description("Regex patterns to match against chat messages.")
        .defaultValue(" \\| ") // example: bar-separated server ads
        .build()
    );

    public ChatCleanup() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "Chat-Cleanup", "Ignores annoying chat messages using regex patterns.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();

        for (String pattern : annoyingChats.get()) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(message).find()) {
                event.cancel();
                return;
            }
        }
    }
}
