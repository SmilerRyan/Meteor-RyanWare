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
import java.util.regex.Matcher;

public class ChatReplacer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // List of regex patterns to match
    private final Setting<List<String>> matchPatterns = sgGeneral.add(new StringListSetting.Builder()
        .name("match-patterns")
        .description("Regex patterns to search for in chat messages.")
        .defaultValue("badword") // Example default
        .build()
    );

    // List of replacement strings corresponding to patterns
    private final Setting<List<String>> replacements = sgGeneral.add(new StringListSetting.Builder()
        .name("replacements")
        .description("Replacement words for each pattern. If none provided, stars will be used.")
        .defaultValue()
        .build()
    );

    public ChatReplacer() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Chat-Replacer", "Replaces matched words in chat with replacements or stars.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();
        String modified = message;

        List<String> patterns = matchPatterns.get();
        List<String> reps = replacements.get();

        for (int i = 0; i < patterns.size(); i++) {
            String pattern = patterns.get(i);
            String replacement = (i < reps.size() && !reps.get(i).isEmpty()) ? reps.get(i) : "***";

            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(modified);
            modified = matcher.replaceAll(replacement);
        }

        if (!modified.equals(message)) {
            //event.cancel();
            event.setMessage(Text.literal(modified));
        }
    }
}
