package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;

import smilerryan.ryanware.RyanWare;

import java.util.List;

public class TabCompletePrivacy extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blockAll = sgGeneral.add(new BoolSetting.Builder()
        .name("block-all")
        .description("Blocks all tab completion packets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> blockedPrefixes = sgGeneral.add(new StringListSetting.Builder()
        .name("blocked-prefixes")
        .description("Block tab completion after your command starts with these prefixes.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<List<String>> blockedWords = sgGeneral.add(new StringListSetting.Builder()
        .name("blocked-words")
        .description("Block tab completion if the command contains any of these words.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<String> blockedSymbols = sgGeneral.add(new StringSetting.Builder()
        .name("blocked-symbols")
        .description("Block tab completion if any of these symbols exist.")
        .defaultValue("@[]{}=")
        .build()
    );

    public TabCompletePrivacy() {
        super(
            RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Tab-Complete-Privacy",
            "Blocks tab completion packets containing private or dangerous input."
        );
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (!(event.packet instanceof RequestCommandCompletionsC2SPacket packet)) return;

        String command = packet.getPartialCommand();
        String lower = command.toLowerCase();

        // Block all
        if (blockAll.get()) {
            event.cancel();
            return;
        }

        // Block prefixes
        for (String prefix : blockedPrefixes.get()) {
            prefix = prefix.toLowerCase();

            if (prefix.isEmpty()) continue;

            if (lower.startsWith(prefix)) {
                event.cancel();
                return;
            }
        }
        
        // Block words
        for (String word : blockedWords.get()) {
            if (!word.isEmpty() && lower.contains(word.toLowerCase())) {
                event.cancel();
                return;
            }
        }

        // Block symbols
        String symbols = blockedSymbols.get();
        for (char symbol : symbols.toCharArray()) {
            if (command.indexOf(symbol) != -1) {
                event.cancel();
                return;
            }
        }


    }
}