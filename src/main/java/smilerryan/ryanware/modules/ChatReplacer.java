package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ChatReplacer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        NORMALIZE_ONLY,
        NORMALIZE_AND_REPLACE,
        REPLACE_ONLY
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How chat messages are processed.")
        .defaultValue(Mode.REPLACE_ONLY)
        .build()
    );

    private final Setting<Boolean> autoStars = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-stars")
        .description("Uses *** when no replacement is provided.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> matchPatterns = sgGeneral.add(new StringListSetting.Builder()
        .name("match-patterns")
        .description("Regex patterns to search for in chat messages.")
        .defaultValue("RyanWare") // Example default
        .build()
    );

    private final Setting<Boolean> useRegex = sgGeneral.add(new BoolSetting.Builder()
        .name("use-regex")
        .description("Whether to treat match-patterns as regex.")
        .defaultValue(false)
        .build()
    );

    // List of replacement strings corresponding to patterns
    private final Setting<List<String>> replacements = sgGeneral.add(new StringListSetting.Builder()
        .name("replacements")
        .description("Replacement strings for each pattern.")
        .defaultValue()
        .build()
    );

    public ChatReplacer() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Chat-Replacer", "Replaces matched words in chat with replacements or stars with optional legacy color code conversion."
        );
    }

    private Formatting getFormatting(TextColor color) {
        if (color == null) return null;

        return switch (color.getName()) {
            case "black" -> Formatting.BLACK;
            case "dark_blue" -> Formatting.DARK_BLUE;
            case "dark_green" -> Formatting.DARK_GREEN;
            case "dark_aqua" -> Formatting.DARK_AQUA;
            case "dark_red" -> Formatting.DARK_RED;
            case "dark_purple" -> Formatting.DARK_PURPLE;
            case "gold" -> Formatting.GOLD;
            case "gray" -> Formatting.GRAY;
            case "dark_gray" -> Formatting.DARK_GRAY;
            case "blue" -> Formatting.BLUE;
            case "green" -> Formatting.GREEN;
            case "aqua" -> Formatting.AQUA;
            case "red" -> Formatting.RED;
            case "light_purple" -> Formatting.LIGHT_PURPLE;
            case "yellow" -> Formatting.YELLOW;
            case "white" -> Formatting.WHITE;
            default -> null;
        };
    }

    private String toLegacyString(Text text) {
        StringBuilder out = new StringBuilder();

        text.visit((style, string) -> {
            // Start by appending the reset code to ensure no styles bleed from previous segments
            StringBuilder segment = new StringBuilder("§r");

            // Apply Color
            TextColor color = style.getColor();
            if (color != null) {
                Formatting f = getFormatting(color);
                if (f != null) segment.append("§").append(f.getCode());
            }

            // Apply Decorations
            if (style.isBold()) segment.append("§l");
            if (style.isItalic()) segment.append("§o");
            if (style.isUnderlined()) segment.append("§n");
            if (style.isStrikethrough()) segment.append("§m");
            if (style.isObfuscated()) segment.append("§k");

            out.append(segment).append(string);

            return Optional.empty();
        }, Style.EMPTY);

        return out.toString();
    }

private String replaceText(String text) {
    List<String> patterns = matchPatterns.get();
    List<String> reps = replacements.get();

    for (int i = 0; i < patterns.size(); i++) {
        String target = patterns.get(i);
        String replacement = (i < reps.size() && !reps.get(i).isEmpty()) ? reps.get(i) : (autoStars.get() ? "***" : "");

        if (useRegex.get()) {
            // Regex mode (Case-insensitive)
            text = Pattern.compile(target, Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .replaceAll(replacement);
        } else {
            // Literal mode
            // We use a manual approach for case-insensitivity because 
            // String.replace() is strictly case-sensitive.
            text = text.replaceAll("(?i)" + Pattern.quote(target), replacement);
        }
    }

    return text;
}

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String original = event.getMessage().getString();
        String output;

        switch (mode.get()) {
            case NORMALIZE_ONLY -> output = toLegacyString(event.getMessage());

            case NORMALIZE_AND_REPLACE ->
                output = replaceText(toLegacyString(event.getMessage()));

            case REPLACE_ONLY ->
                output = replaceText(original);

            default -> {
                return;
            }
        }

        if (!output.equals(original) || mode.get() == Mode.NORMALIZE_ONLY) {
            event.setMessage(Text.literal(output));
        }
    }
}
