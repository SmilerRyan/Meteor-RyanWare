package smilerryan.ryanware.modules_1;

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

public class M1_RedirectPublicChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> redirectCommands = sgGeneral.add(new StringListSetting.Builder()
        .name("redirect-commands")
        .description("List of commands to send the message to (e.g., /msg Friend1).")
        .defaultValue(List.of("/msg Friend1"))
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
    private int tickCounter = 0;

    public M1_RedirectPublicChat() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "M1-Redirect-Public-Chat", "Redirects public chat messages to specified commands.");
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String originalMessage = event.message;

        if (originalMessage.startsWith("/")) return;

        event.cancel();

        for (String command : redirectCommands.get()) {
            messageQueue.add(command + " " + originalMessage);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (messageQueue.isEmpty()) return;

        tickCounter++;
        if (tickCounter >= delay.get()) {
            tickCounter = 0;

            String nextCommand = messageQueue.poll();
            if (nextCommand != null && mc.player != null) {
                String rawCommand = nextCommand.startsWith("/") ? nextCommand.substring(1) : nextCommand;
                mc.player.networkHandler.sendChatCommand(rawCommand);
            }
        }
    }

}
