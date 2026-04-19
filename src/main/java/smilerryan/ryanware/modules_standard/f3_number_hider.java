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
        EXACT_FOR_EACH_CHAR,    // replace each character in the match with replaceWith (can expand)
        EXACT_FOR_WHOLE_MATCH,  // replace the whole matched number with replaceWith
        REPEAT_FOR_WHOLE_MATCH, // repeat pattern to match matched number length
        REPEAT_FOR_WHOLE_LINE   // repeat pattern across the entire line (continues across matches)
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
        .description("How to apply the replacement string.")
        .defaultValue(ReplacementMode.REPEAT_FOR_WHOLE_MATCH)
        .build()
    );

    private final Setting<String> replaceWith = sg.add(new StringSetting.Builder()
        .name("replace-with")
        .description("String used to replace digits or matches. Can be empty to remove numbers.")
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

    // Build a string by repeating pattern until size reached (if pattern empty -> empty)
    private String buildRepeat(int size, String pattern) {
        if (pattern == null || pattern.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(size);
        int plen = pattern.length();
        for (int i = 0; i < size; i++) sb.append(pattern.charAt(i % plen));
        return sb.toString();
    }

    // For EXACT_FOR_EACH_CHAR: repeat replaceWith once per original char (concatenate replaceWith repeatedly)
    private String buildExactEachChar(int charCount, String replace) {
        if (replace == null) replace = "";
        StringBuilder sb = new StringBuilder(charCount * Math.max(1, replace.length()));
        for (int i = 0; i < charCount; i++) sb.append(replace);
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

        String repl = replaceWith.get(); // may be null or empty
        String patternStr = hidePunctuation.get() ? "[0-9\\.\\-]+" : "\\d+";
        Pattern numberPattern = Pattern.compile(patternStr);

        if (mode.get() == Mode.ALL_NUMBERS) {
            // Simple one-pass over the whole text (no line-scoped REPEAT_FOR_WHOLE_LINE)
            StringBuffer result = new StringBuffer();
            Matcher matcher = numberPattern.matcher(text);
            while (matcher.find()) {
                String match = matcher.group();
                String replacement;
                switch (replacementMode.get()) {
                    case EXACT_FOR_EACH_CHAR:
                        replacement = buildExactEachChar(match.length(), repl);
                        break;
                    case EXACT_FOR_WHOLE_MATCH:
                        replacement = (repl == null) ? "" : repl;
                        break;
                    case REPEAT_FOR_WHOLE_MATCH:
                        replacement = buildRepeat(match.length(), repl);
                        break;
                    case REPEAT_FOR_WHOLE_LINE:
                        // REPEAT_FOR_WHOLE_LINE in ALL_NUMBERS mode behaves like REPEAT_FOR_WHOLE_MATCH
                        replacement = buildRepeat(match.length(), repl);
                        break;
                    default:
                        replacement = buildRepeat(match.length(), repl);
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
                    if (replacementMode.get() == ReplacementMode.REPEAT_FOR_WHOLE_LINE) {
                        // Build replacement across the entire line: create a char-by-char transformed line
                        StringBuilder transformed = new StringBuilder();
                        int patternIndex = 0;
                        String pat = (repl == null) ? "" : repl;
                        for (int ci = 0; ci < line.length(); ) {
                            Matcher m = numberPattern.matcher(line);
                            if (m.find(ci) && m.start() == ci) {
                                String match = m.group();
                                // produce replacement by cycling pattern across the match, but continue patternIndex across matches
                                StringBuilder rep = new StringBuilder();
                                if (pat.isEmpty()) {
                                    // deletion: skip adding anything
                                } else {
                                    for (int k = 0; k < match.length(); k++) {
                                        rep.append(pat.charAt(patternIndex % pat.length()));
                                        patternIndex++;
                                    }
                                }
                                transformed.append(rep.toString());
                                ci = m.end();
                            } else {
                                // non-matching char: append as-is and advance; patternIndex not changed
                                transformed.append(line.charAt(ci));
                                ci++;
                            }
                        }
                        out.append(transformed.toString());
                    } else {
                        // Per-match replacements, pattern does not carry across matches
                        StringBuffer lineResult = new StringBuffer();
                        Matcher matcher = numberPattern.matcher(line);
                        while (matcher.find()) {
                            String match = matcher.group();
                            String replacement;
                            switch (replacementMode.get()) {
                                case EXACT_FOR_EACH_CHAR:
                                    replacement = buildExactEachChar(match.length(), repl);
                                    break;
                                case EXACT_FOR_WHOLE_MATCH:
                                    replacement = (repl == null) ? "" : repl;
                                    break;
                                case REPEAT_FOR_WHOLE_MATCH:
                                    replacement = buildRepeat(match.length(), repl);
                                    break;
                                default:
                                    replacement = buildRepeat(match.length(), repl);
                            }
                            matcher.appendReplacement(lineResult, Matcher.quoteReplacement(replacement));
                        }
                        matcher.appendTail(lineResult);
                        out.append(lineResult.toString());
                    }
                } else {
                    out.append(line);
                }
                if (i < lines.length - 1) out.append("\n");
            }
            return out.toString();
        }
    }

}