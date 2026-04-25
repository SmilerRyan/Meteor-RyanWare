package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;

import java.util.*;

public class AutoRunFileOnChat extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<List<String>> rules = sgGeneral.add(new StringListSetting.Builder()
        .name("rules")
        .description("Format: keyword|command (e.g. 'you killed|msg * You killed a player!')")
        .defaultValue("")
        .build()
    );

    public AutoRunFileOnChat() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Auto-Run-File-On-Chat", "Runs commands when chat matches keywords.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String msg = e.getMessage().getString();
        for (String rule : rules.get()) {
            if (!rule.contains("|")) continue;
            String[] parts = rule.split("\\|", 2);
            String keyword = parts[0].trim();
            String command = parts[1].trim();
            if (msg.contains(keyword)) {
                runCommand(command);
                break;
            }
        }
    }

    private void runCommand(String command) {
        try {
            List<String> parts = parseCommand(command);
            new ProcessBuilder(parts).start();
        } catch (Exception e) {
            error("Failed to run: " + command);
        }
    }

    private List<String> parseCommand(String command) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : command.toCharArray()) {
            if (c == '"') {inQuotes = !inQuotes;continue;}
            if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}