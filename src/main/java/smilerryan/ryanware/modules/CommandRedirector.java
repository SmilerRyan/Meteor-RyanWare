package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import smilerryan.ryanware.RyanWare;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandRedirector extends Module {
    private final SettingGroup sgListen = settings.createGroup("Listen Patterns");
    private final SettingGroup sgReplace = settings.createGroup("Replacement Commands");

    private final Setting<List<String>> listenPatterns = sgListen.add(new StringListSetting.Builder()
        .name("listen-for")
        .description("Commands to listen for (include prefix if needed, e.g. /msg {1}).")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<List<String>> replacementCommands = sgReplace.add(new StringListSetting.Builder()
        .name("run-as")
        .description("Replacement commands matching order of listen-for. Supports {1}, {2}, {2-4}, {3-}.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Boolean> debugLogging = sgListen.add(new BoolSetting.Builder()
        .name("debug-logging")
        .description("Show info messages when commands are redirected.")
        .defaultValue(false)
        .build()
    );

    private boolean redirecting = false;

    public CommandRedirector() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Command-Redirector",
            "Redirects any command (slash or chat) based on ordered pattern matching with argument placeholders.");
    }

    // Handles normal chat-like commands
    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (redirecting) return;
        String msg = event.message.trim();
        if (msg.isEmpty()) return;

        String redirected = tryRedirect(msg);
        if (redirected == null) return;

        event.cancel();
        redirecting = true;

        if (redirected.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(redirected.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(redirected);
        }

        redirecting = false;
        if (debugLogging.get()) info("\n-> " + msg + "\n-> " + redirected);
    }

    // Handles slash commands
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (redirecting) return;
        Packet<?> packet = event.packet;
        String command = null;

        if (packet instanceof CommandExecutionC2SPacket cmdPacket) {
            command = "/" + cmdPacket.command();
        } else if (packet instanceof ChatCommandSignedC2SPacket signedPacket) {
            command = "/" + signedPacket.command();
        }

        if (command == null) return;

        String redirected = tryRedirect(command);
        if (redirected == null) return;

        event.cancel();
        redirecting = true;

        if (redirected.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(redirected.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(redirected);
        }

        redirecting = false;
        if (debugLogging.get()) info("\n-> " + command + "\n-> " + redirected);
    }

    private String tryRedirect(String fullCommand) {
        String[] inputParts = fullCommand.split("\\s+");
        if (inputParts.length == 0) return null;

        List<String> patterns = listenPatterns.get();
        List<String> replacements = replacementCommands.get();

        for (int i = 0; i < patterns.size(); i++) {
            String pattern = patterns.get(i).trim();
            if (pattern.isEmpty()) continue;

            String[] patternParts = pattern.split("\\s+");
            if (patternParts.length == 0) continue;

            // Must match the command keyword (including prefix)
            if (!inputParts[0].equalsIgnoreCase(patternParts[0])) continue;

            Map<String, String> args = new HashMap<>();
            int inputIndex = 1;
            boolean match = true;

            for (int p = 1; p < patternParts.length; p++) {
                String part = patternParts[p];
                if (part.matches("\\{\\d+}")) {
                    if (inputIndex >= inputParts.length) { match = false; break; }
                    args.put(part, inputParts[inputIndex]);
                    inputIndex++;
                } else {
                    if (inputIndex >= inputParts.length || !inputParts[inputIndex].equalsIgnoreCase(part)) {
                        match = false; break;
                    }
                    inputIndex++;
                }
            }

            if (!match) continue;

            String replacement = (i < replacements.size()) ? replacements.get(i) : "";
            if (replacement.isEmpty()) return null;

            // replace {1}, {2}, etc.
            for (int n = 1; n < inputParts.length; n++) {
                replacement = replacement.replace("{" + n + "}", n < inputParts.length ? inputParts[n] : "");
            }

            // handle {X-Y} and {X-}
            Pattern rangePattern = Pattern.compile("\\{(\\d+)-(\\d+)?}");
            Matcher matcher = rangePattern.matcher(replacement);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                int start = Integer.parseInt(matcher.group(1));
                int end = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : inputParts.length - 1;
                start = Math.max(1, start);
                end = Math.min(inputParts.length - 1, end);
                String joined = (start <= end) ? String.join(" ", Arrays.copyOfRange(inputParts, start, end + 1)) : "";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(joined));
            }
            matcher.appendTail(sb);
            replacement = sb.toString();

            return replacement;
        }

        return null;
    }
}
