package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

public class TotemAutoLeave extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum Action {
        Commands,
        Disconnect,
        Both,
        Notify
    }

    private final Setting<Integer> minTotems = sgGeneral.add(new IntSetting.Builder()
        .name("min-totems")
        .description("Leave if offhand totems drop to this number or lower.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Action> action = sgGeneral.add(new EnumSetting.Builder<Action>()
        .name("action")
        .description("What to do when triggered.")
        .defaultValue(Action.Disconnect)
        .build()
    );

    private final Setting<String> commandText = sgGeneral.add(new StringSetting.Builder()
        .name("commands")
        .description("Commands/messages to send when triggered, separated by ';'.")
        .defaultValue("/spawn;/home")
        .visible(() -> action.get() == Action.Commands || action.get() == Action.Both)
        .build()
    );

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Delay between commands and before disconnect (20 = 1 second).")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 100)
        .visible(() -> action.get() == Action.Commands || action.get() == Action.Both)
        .build()
    );

    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
        .name("message")
        .description("Message used for disconnect reason and notify chat.")
        .defaultValue("TotemAutoLeave")
        .build()
    );

    private boolean triggered = false;
    private String[] commands;
    private int commandIndex;
    private int delayTimer;
    private boolean firstActionDone;

    public TotemAutoLeave() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "TotemAutoLeave", "Leaves, warns, or runs commands when you run out of totems.");
    }

    @Override
    public void onActivate() {
        triggered = false;
        commands = null;
        commandIndex = 0;
        delayTimer = 0;
        firstActionDone = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (!triggered) {
            ItemStack offhand = mc.player.getOffHandStack();
            if (offhand.getItem() == Items.TOTEM_OF_UNDYING && offhand.getCount() <= minTotems.get()) {
                triggered = true;
                commandIndex = 0;
                delayTimer = 0;
                firstActionDone = false;

                // notify mode
                if (action.get() == Action.Notify) {
                    mc.player.sendMessage(Text.literal(message.get() + " (You only have " + offhand.getCount() + " totem(s) left)"), false);
                    toggle();
                    return;
                }

                if (action.get() == Action.Commands || action.get() == Action.Both) {
                    commands = commandText.get().split(";");
                }

                if (action.get() == Action.Disconnect) {
                    mc.player.networkHandler.getConnection().disconnect(Text.literal(message.get()));
                    toggle();
                }
            }
        } else {
            // Commands handling
            if (commands != null && commandIndex < commands.length) {
                if (!firstActionDone) {
                    sendCommand(commands[commandIndex]);
                    commandIndex++;
                    firstActionDone = true;
                    delayTimer = delayTicks.get();
                } else if (delayTimer > 0) {
                    delayTimer--;
                } else {
                    if (commandIndex < commands.length) {
                        sendCommand(commands[commandIndex]);
                        commandIndex++;
                        delayTimer = delayTicks.get();
                    }
                }
            }
            // After commands, handle disconnect if "Both"
            else if (action.get() == Action.Both) {
                if (!firstActionDone) {
                    mc.player.networkHandler.getConnection().disconnect(Text.literal(message.get()));
                    toggle();
                } else if (delayTimer > 0) {
                    delayTimer--;
                } else {
                    mc.player.networkHandler.getConnection().disconnect(Text.literal(message.get()));
                    toggle();
                }
            } else {
                toggle();
            }
        }
    }

    private void sendCommand(String raw) {
        String cmd = raw.trim();
        if (cmd.isEmpty()) return;
        if (cmd.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(cmd.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(cmd);
        }
    }
}
