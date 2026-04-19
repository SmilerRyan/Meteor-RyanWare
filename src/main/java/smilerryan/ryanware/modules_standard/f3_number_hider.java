package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class f3_number_hider extends Module {

    public f3_number_hider() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "F3-Number-Hider", "Hide coordinates in F3 menu replacing them with custom string or pattern.");
    }

    public static f3_number_hider INSTANCE;

    // Settings
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode {
        ALL_NUMBERS,
        LINES_CONTAINING
    }

    public enum ReplacementMode {
        REPEAT_PATTERN, // use replacement string cyclically per character to match original length
        EXACT           // replace the whole matched number with the exact replacement string (no length matching)
    }

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Choose whether to hide all numbers or only numbers on lines containing a filter string.")
        .defaultValue(Mode.ALL_NUMBERS)
        .build()
    );

    private final Setting<String> lineFilter = sg.add(new StringSetting.Builder()
        .name("line-filter")
        .description("When mode is LINES_CONTAINING, only lines containing any of these comma-separated strings will have their numbers hidden.")
        .defaultValue("XYZ")
        .visible(() -> mode.get() == Mode.LINES_CONTAINING)
        .build()
    );

    private final Setting<ReplacementMode> replacementMode = sg.add(new EnumSetting.Builder<ReplacementMode>()
        .name("replacement-mode")
        .description("Choose how the replacement string is applied: repeat per-character to match length, or use exact string for each match.")
        .defaultValue(ReplacementMode.REPEAT_PATTERN)
        .build()
    );

    private final Setting<String> replaceWith = sg.add(new StringSetting.Builder()
        .name("replace-with")
        .description("String used to replace digits or matches. Can be empty to remove numbers. In REPEAT_PATTERN mode characters are used per-character cyclically (e.g., \"67\" -> 6,7,6,7...).")
        .defaultValue("*")
        .build()
    );

    private final Setting<Boolean> hidePunctuation = sg.add(new BoolSetting.Builder()
        .name("hide-punctuation")
        .description("If enabled, treat '.' and '-' as part of numbers and hide them too.")
        .defaultValue(false)
        .build()
    );

    @Override
    public void onActivate() {
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        INSTANCE = null;
    }

    /**
     * Build a replacement string of exactly 'length' characters by cycling through 'pattern'.
     * If pattern is empty, returns an empty string.
     */
    private String buildRepeatReplacement(int length, String pattern) {
        if (pattern == null || pattern.isEmpty()) return ""; // allow empty => deletion
        StringBuilder sb = new StringBuilder(length);
        int plen = pattern.length();
        for (int i = 0; i < length; i++) {
            sb.append(pattern.charAt(i % plen));
        }
        return sb.toString();
    }

    private boolean lineMatchesFilters(String line, String filtersCsv) {
        if (filtersCsv == null) return false;
        String[] parts = filtersCsv.split(",");
        for (String p : parts) {
            String f = p.trim();
            if (!f.isEmpty() && line.contains(f)) return true;
        }
        return false;
    }

    public String hideCoordinateString(String text) {
        if (!isActive()) return text;

        String repl = replaceWith.get(); // may be null or empty (empty allowed)

        // Choose regex: digits only or digits plus '.' and '-'
        String patternStr = hidePunctuation.get() ? "[0-9\\.\\-]+" : "\\d+";
        Pattern numberPattern = Pattern.compile(patternStr);

        if (mode.get() == Mode.ALL_NUMBERS) {
            StringBuffer result = new StringBuffer();
            Matcher matcher = numberPattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group();
                String replacement;
                if (replacementMode.get() == ReplacementMode.REPEAT_PATTERN) {
                    replacement = buildRepeatReplacement(match.length(), repl);
                } else { // EXACT
                    replacement = (repl == null) ? "" : repl;
                }
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);
            return result.toString();
        } else { // LINES_CONTAINING
            StringBuilder out = new StringBuilder();
            String[] lines = text.split("\\r?\\n", -1);
            String filters = lineFilter.get();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (filters != null && !filters.isEmpty() && lineMatchesFilters(line, filters)) {
                    StringBuffer lineResult = new StringBuffer();
                    Matcher matcher = numberPattern.matcher(line);
                    while (matcher.find()) {
                        String match = matcher.group();
                        String replacement;
                        if (replacementMode.get() == ReplacementMode.REPEAT_PATTERN) {
                            replacement = buildRepeatReplacement(match.length(), repl);
                        } else { // EXACT
                            replacement = (repl == null) ? "" : repl;
                        }
                        matcher.appendReplacement(lineResult, Matcher.quoteReplacement(replacement));
                    }
                    matcher.appendTail(lineResult);
                    out.append(lineResult.toString());
                } else {
                    out.append(line);
                }
                if (i < lines.length - 1) out.append("\n");
            }
            return out.toString();
        }
    }

}
