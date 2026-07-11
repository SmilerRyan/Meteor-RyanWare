package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ChatReplacer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Mode {
        NORMALIZE_ONLY,
        NORMALIZE_AND_REPLACE,
        REPLACE_ONLY,
        NORMALIZE_AND_CUSTOM_PROCESS
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.REPLACE_ONLY)
        .build()
    );

    private final Setting<String> externalCmd = sgGeneral.add(new StringSetting.Builder()
        .name("external-command")
        .description("The command to run. Use {SERVER} for the current server address.")
        .defaultValue("cmd /c .\\meteor-client\\ryanware\\chat-replacer\\start.bat {SERVER}")
        .visible(() -> mode.get() == Mode.NORMALIZE_AND_CUSTOM_PROCESS)
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
        .defaultValue("RyanWare")
        .build()
    );

    private final Setting<List<String>> replacements = sgGeneral.add(new StringListSetting.Builder()
        .name("replacements")
        .defaultValue()
        .build()
    );

    private final Setting<Boolean> useRegex = sgGeneral.add(new BoolSetting.Builder()
        .name("use-regex")
        .description("Whether to treat match-patterns as regex.")
        .defaultValue(false)
        .build()
    );

    private ExternalProcessManager processManager;

    public ChatReplacer() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Chat-Replacer", "Replaces chat with built-in patterns or an external process.");
    }

    @Override
    public void onDeactivate() {
        if (processManager != null) {
            processManager.stop();
            processManager = null;
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String original = event.getMessage().getString();
        String output;

        switch (mode.get()) {
            case NORMALIZE_ONLY -> output = toLegacyString(event.getMessage());
            case NORMALIZE_AND_REPLACE -> output = replaceText(toLegacyString(event.getMessage()));
            case REPLACE_ONLY -> output = replaceText(original);
            case NORMALIZE_AND_CUSTOM_PROCESS -> {
                if (processManager == null) {
                    String cmd = externalCmd.get().replace("{SERVER}", getServerIdentifier());
                    processManager = new ExternalProcessManager(cmd);
                }

                output = processManager.send(toLegacyString(event.getMessage()));
            }
            default -> { return; }
        }

        if (!output.equals(original) || mode.get() == Mode.NORMALIZE_ONLY) {
            event.setMessage(Text.literal(output));
        }
    }

    private String getServerIdentifier() {
        if (mc.isInSingleplayer()) return "singleplayer";

        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null || info.address == null || info.address.isBlank())
            return "unknown";

        return info.address
            .replace('.', '_')
            .replace(':', '_')
            .replace('/', '_')
            .replace('\\', '_');
    }

    private String replaceText(String text) {
        List<String> patterns = matchPatterns.get();
        List<String> reps = replacements.get();
        for (int i = 0; i < patterns.size(); i++) {
            String target = patterns.get(i);
            String replacement = (i < reps.size() && !reps.get(i).isEmpty()) ? reps.get(i) : (autoStars.get() ? "***" : "");
            text = useRegex.get() ? text.replaceAll("(?i)" + target, replacement) : text.replaceAll("(?i)" + Pattern.quote(target), replacement);
        }
        return text;
    }

    private String toLegacyString(Text text) {
        StringBuilder out = new StringBuilder();
        text.visit((style, string) -> {
            StringBuilder segment = new StringBuilder("§r");
            TextColor color = style.getColor();
            if (color != null) {
                Formatting f = getFormatting(color);
                if (f != null) segment.append("§").append(f.getCode());
            }
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

    private Formatting getFormatting(TextColor color) {
        if (color == null) return null;
        return switch (color.getName()) {
            case "black" -> Formatting.BLACK; case "dark_blue" -> Formatting.DARK_BLUE;
            case "dark_green" -> Formatting.DARK_GREEN; case "dark_aqua" -> Formatting.DARK_AQUA;
            case "dark_red" -> Formatting.DARK_RED; case "dark_purple" -> Formatting.DARK_PURPLE;
            case "gold" -> Formatting.GOLD; case "gray" -> Formatting.GRAY;
            case "dark_gray" -> Formatting.DARK_GRAY; case "blue" -> Formatting.BLUE;
            case "green" -> Formatting.GREEN; case "aqua" -> Formatting.AQUA;
            case "red" -> Formatting.RED; case "light_purple" -> Formatting.LIGHT_PURPLE;
            case "yellow" -> Formatting.YELLOW; case "white" -> Formatting.WHITE;
            default -> null;
        };
    }

    private static class ExternalProcessManager {
        private final String command;
        private Process process;
        private BufferedWriter writer;
        private BufferedReader reader;

        public ExternalProcessManager(String command) { this.command = command; }

        private synchronized void ensureProcess() throws IOException {
            if (process == null || !process.isAlive()) {
                process = new ProcessBuilder(command.split(" ")).start();
                writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            }
        }

        public synchronized String send(String input) {
            try {
                ensureProcess();
                writer.write(input + "\n");
                writer.flush();
                String res = reader.readLine();
                return (res != null) ? res : input;
            } catch (IOException e) {
                process = null;
                return input;
            }
        }

        public void stop() { if (process != null) process.destroy(); }
    }
}