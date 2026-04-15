package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class RedirectPublicChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> prefixCommands = sgGeneral.add(new StringListSetting.Builder()
        .name("prefix-commands")
        .description("Text/Commands to put BEFORE the original message.")
        .defaultValue(List.of(""))
        .build()
    );

    private final Setting<List<String>> suffixCommands = sgGeneral.add(new StringListSetting.Builder()
        .name("suffix-commands")
        .description("Text/Commands to put AFTER the message.")
        .defaultValue(List.of(""))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Delay between sending each command in ticks (20 ticks = 1 second).")
        .defaultValue(10)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Queue<String> messageQueue = new LinkedList<>();
    private boolean sending = false;
    private int tickCounter = 0;

    public RedirectPublicChat() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Redirect-Public-Chat", "Redirects public chat messages to specified commands.");
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (sending) return;
        if (prefixCommands.get().isEmpty() || suffixCommands.get().isEmpty()) return;
        event.cancel();
        for (String prefix : prefixCommands.get()) {
            for (String suffix : suffixCommands.get()) {
                messageQueue.add(prefix + event.message + suffix);
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (messageQueue.isEmpty()) return;
        if (++tickCounter >= delay.get()) {
            tickCounter = 0;
            String next = messageQueue.poll();
            if (next != null && mc.player != null) {
                sending = true;
                if (next.startsWith("/")) {
                    mc.player.networkHandler.sendChatCommand(next.substring(1));
                } else {
                    mc.player.networkHandler.sendChatMessage(next);
                }
                sending = false;
            }
        }
    }
}
