package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import com.mojang.text2speech.Narrator;

import java.util.List;

public class ChatSpeaker extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> keywords = sgGeneral.add(new StringListSetting.Builder()
        .name("keywords")
        .description("Messages containing any of these keywords will be spoken.")
        .defaultValue(List.of("smiler", "ryan"))
        .build()
    );

    private final Setting<List<String>> ignoreKeywords = sgGeneral.add(new StringListSetting.Builder()
        .name("ignore-keywords")
        .description("Messages containing any of these keywords will always be ignored.")
        .defaultValue(List.of("Player Alerter","User Lookups"))
        .build()
    );

    private final Setting<Boolean> removeTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-remove-timestamp")
        .description("Automatically removes <HH:MM:SS> from the beginning of chat messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> removeFilterPrefix = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-remove-keyword-prefix")
        .description("Automatically removes the matched keyword from the start of the spoken message if it is a prefix.")
        .defaultValue(true)
        .build()
    );

    public ChatSpeaker() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Chat-Speaker",
            "Uses Minecraft narrator for selected chat messages."
        );
    }

    @Override
    public void onDeactivate() {
        Narrator narrator = Narrator.getNarrator();
        narrator.clear();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        Text message = event.getMessage();
        if (message == null) return;

        String text = message.getString();

        if (removeTimestamp.get()) {
            text = text.replaceFirst("^<\\d{1,2}:\\d{2}:\\d{2}>\\s*", "");
        }

        String lower = text.toLowerCase();

        // Ignore keywords always override normal keywords
        for (String ignore : ignoreKeywords.get()) {
            if (ignore != null && !ignore.isEmpty()
                && lower.contains(ignore.toLowerCase())) {
                return;
            }
        }

        String matchedKeyword = null;

        for (String keyword : keywords.get()) {
            if (keyword != null && !keyword.isEmpty()
                && lower.contains(keyword.toLowerCase())) {
                matchedKeyword = keyword;
                break;
            }
        }

        if (matchedKeyword == null) {
            return;
        }

        if (removeFilterPrefix.get()) {
            if (text.regionMatches(true, 0, matchedKeyword, 0, matchedKeyword.length())) {
                text = text.substring(matchedKeyword.length()).stripLeading();
            }
        }

        Narrator narrator = Narrator.getNarrator();
        if (narrator.active()) {
            narrator.say(text, true, 1.0f);
        }
    }
}