package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class ChatSpam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Delay between each message in ticks (20 ticks = 1 second).")
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("messages")
        .description("Messages or commands to send in order.")
        .defaultValue(List.of(
            "1", "2", "3", "4", "5"
        ))
        .build()
    );

    private int index = 0;
    private int ticksLeft = 0;

    public ChatSpam() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Chat-Spam",
            "Sends each chat message/command with delay, once, then disables itself.");
    }

    @Override
    public void onActivate() {
        index = 0;
        ticksLeft = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        List<String> list = messages.get();
        if (list.isEmpty()) {
            toggle();
            return;
        }

        if (index >= list.size()) {
            toggle();
            return;
        }

        if (ticksLeft > 0) {
            ticksLeft--;
            return;
        }

        String msg = list.get(index);

        if (msg.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(msg.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(msg);
        }

        index++;
        ticksLeft = delay.get();
    }

    @Override
    public void onDeactivate() {
        index = 0;
        ticksLeft = 0;
    }
}
