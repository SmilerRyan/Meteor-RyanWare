package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

public class BritishChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum CapitalizationMode {
        UPPERCASE,
        LOWERCASE,
        IGNORE
    }

    public enum PunctuationMode {
        ADD_DOT,
        REMOVE_DOT,
        IGNORE
    }

    private final Setting<CapitalizationMode> capitalizationMode = sgGeneral.add(new EnumSetting.Builder<CapitalizationMode>()
        .name("capitalization-mode")
        .description("How to handle capitalization in messages.")
        .defaultValue(CapitalizationMode.IGNORE)
        .build()
    );

    private final Setting<PunctuationMode> punctuationMode = sgGeneral.add(new EnumSetting.Builder<PunctuationMode>()
        .name("punctuation-mode")
        .description("How to handle punctuation in messages.")
        .defaultValue(PunctuationMode.IGNORE)
        .build()
    );

    public BritishChat() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "British-Chat", "Makes your public chat slightly more (or less) British.");
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (event.message.startsWith("/")) return;

        String message = event.message;
        
        // Handle capitalization
        switch (capitalizationMode.get()) {
            case UPPERCASE -> {
                if (!message.isEmpty() && !Character.isUpperCase(message.charAt(0))) {
                    message = Character.toUpperCase(message.charAt(0)) + message.substring(1);
                }
            }
            case LOWERCASE -> {
                if (!message.isEmpty() && Character.isUpperCase(message.charAt(0))) {
                    message = Character.toLowerCase(message.charAt(0)) + message.substring(1);
                }
            }
            case IGNORE -> {}
        }

        // Handle punctuation
        switch (punctuationMode.get()) {
            case ADD_DOT -> {
                if (!message.endsWith(".") && !message.endsWith("!") && !message.endsWith(",") && !message.endsWith("?")) {
                    message += ".";
                }
            }
            case REMOVE_DOT -> {
                if (message.endsWith(".") && !message.endsWith("..")) {
                    message = message.substring(0, message.length() - 1);
                }
            }
            case IGNORE -> {}
        }

        event.message = message;
    }
} 