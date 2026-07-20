package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import com.mojang.text2speech.Narrator;

public class ChatSpeaker extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> filter = sgGeneral.add(new StringSetting.Builder()
        .name("filter")
        .description("Only speak messages containing this text.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> removeTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-remove-timestamp")
        .description("Automatically removes <HH:MM:SS> from the beginning of chat messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> removeFilterPrefix = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-remove-filter-prefix")
        .description("Automatically removes the filter text from the start of the spoken message if it is a prefix.")
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

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        Text message = event.getMessage();
        if (message == null) return;

        String text = message.getString();

        if (removeTimestamp.get()) {
            text = text.replaceFirst("^<\\d{1,2}:\\d{2}:\\d{2}>\\s*", "");
        }

        String wanted = filter.get();

        if (!wanted.isEmpty() && !text.toLowerCase().contains(wanted.toLowerCase())) {
            return;
        }

        if (removeFilterPrefix.get() && !wanted.isEmpty()) {
            if (text.regionMatches(true, 0, wanted, 0, wanted.length())) {
                text = text.substring(wanted.length()).stripLeading();
            }
        }

        Narrator narrator = Narrator.getNarrator();
        if (narrator.active()) {
            narrator.say(text, true, 1.0f);
        }
    }
}