package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.Random;

public class AternosOnliner extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("Command to send.")
        .defaultValue("/ping &6[&bAternosOnliner&6]&a")
        .build()
    );

    private final Setting<Boolean> antiSpamSuffix = sgGeneral.add(new BoolSetting.Builder()
        .name("add-random-suffix")
        .description("Append a random mixed-case 18 character suffix to bypass spam filters.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideSuffixMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-own-suffix-messages")
        .description("Hide chat messages that contain your random suffix.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> commandInterval = sgGeneral.add(new IntSetting.Builder()
        .name("command-interval-ticks")
        .description("Ticks between sending the command. (20 ticks = 1 second) Set to 0 to disable.")
        .defaultValue(2400)
        .min(0)
        .sliderMax(5000)
        .build()
    );

    private final Setting<Integer> jumpInterval = sgGeneral.add(new IntSetting.Builder()
        .name("jump-interval-ticks")
        .description("Ticks between jumps. Set to 0 to disable jumping.")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private int ticks;
    private final Random rand = new Random();
    private String lastSuffix = null;
    private boolean isAternos = false;
    private String displayName = "none";

    public AternosOnliner() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Aternos-Onliner",
            "Keeps Aternos servers online by jumping and sending periodic chat commands.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        lastSuffix = null;

        if (mc.getCurrentServerEntry() != null) {
            String ip = mc.getCurrentServerEntry().address;

            if (ip.contains(":")) ip = ip.split(":")[0];

            if (ip.endsWith(".aternos.me")) {
                isAternos = true;
                displayName = ip.substring(0, ip.length() - ".aternos.me".length());
            } else {
                isAternos = false;
                displayName = "none";
            }
        } else {
            isAternos = false;
            displayName = "none";
        }
    }

    @Override
    public String getInfoString() {
        return "[" + displayName + "]";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !isAternos) return;

        ticks++;

        if (jumpInterval.get() > 0 && ticks % jumpInterval.get() == 0) {
            mc.player.jump();
        }

        if (commandInterval.get() > 0 && ticks % commandInterval.get() == 0) {
            String msg = command.get();

            if (antiSpamSuffix.get()) {
                StringBuilder suffix = new StringBuilder(18);
                for (int i = 0; i < 18; i++) {
                    char base = rand.nextBoolean() ? 'A' : 'a';
                    suffix.append((char) (base + rand.nextInt(26)));
                }
                lastSuffix = suffix.toString();
                msg += " " + lastSuffix;
            } else {
                lastSuffix = null;
            }

            if (msg.startsWith("/")) {
                mc.player.networkHandler.sendChatCommand(msg.substring(1));
            } else {
                mc.player.networkHandler.sendChatMessage(msg);
            }
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isAternos) return;
        String text = event.getMessage().getString();
        if (hideSuffixMessages.get()) {
            if (lastSuffix != null && text.contains(lastSuffix)) {
                event.cancel();
                return;
            }
        }
    }
}
