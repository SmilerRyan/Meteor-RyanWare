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
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "F3-Number-Hider", "Hide coordinates in F3 menu replacing them with asterisks or custom string.");
    }

    public static f3_number_hider INSTANCE;

    // Settings
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode {
        ALL_NUMBERS,
        LINES_CONTAINING
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

    private final Setting<String> replaceWith = sg.add(new StringSetting.Builder()
        .name("replace-with")
        .description("String used to replace each digit. Default is \"*\". Characters are used per-digit cyclically (e.g., \"67\" -> digits become 6,7,6,7...).")
        .defaultValue("*")
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

    private String buildReplacement(int length, String repl) {
        if (repl == null || repl.isEmpty()) repl = "*";
        StringBuilder sb = new StringBuilder(length);
        int rlen = repl.length();
        for (int i = 0; i < length; i++) {
            sb.append(repl.charAt(i % rlen));
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

        String repl = replaceWith.get();
        if (repl == null || repl.isEmpty()) repl = "*";

        Pattern digits = Pattern.compile("\\d+");

        if (mode.get() == Mode.ALL_NUMBERS) {
            StringBuffer result = new StringBuffer();
            Matcher matcher = digits.matcher(text);
            while (matcher.find()) {
                String match = matcher.group();
                String replacement = buildReplacement(match.length(), repl);
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
                    Matcher matcher = digits.matcher(line);
                    while (matcher.find()) {
                        String match = matcher.group();
                        String replacement = buildReplacement(match.length(), repl);
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