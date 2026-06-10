package smilerryan.ryanware.modules_standard.automation;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DeathScreen;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class AutoRespawn extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
        .name("commands")
        .description("Commands to run after respawning. One command is sent per tick.")
        .defaultValue()
        .build()
    );

    private boolean waitingForCommands = false;

    private final List<String> commandQueue = new ArrayList<>();
    private int commandIndex = 0;

    // NEW: ignore initial join/respawn burst
    private int joinGraceTicks = 0;
    private boolean worldActive = false;

    public AutoRespawn() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Auto-Respawn", "Automatically requests a respawn if on the death screen and optionally sends chat/commands.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        if (!worldActive) {
            worldActive = true;
            joinGraceTicks = 0;
            commandQueue.clear();
            commandIndex = 0;
            waitingForCommands = false;
            return;
        }
        if (mc.currentScreen instanceof DeathScreen) {
            mc.player.requestRespawn();
            mc.setScreen(null);
            waitingForCommands = true;
            commandQueue.clear();
            commandQueue.addAll(commands.get());
            commandIndex = 0;
            return;
        }
        if (joinGraceTicks < 20) {
            joinGraceTicks++;
            return;
        }
        if (waitingForCommands && commandIndex < commandQueue.size()) {
            String command = commandQueue.get(commandIndex);
            if (command != null && !command.isBlank()) {
                if (command.startsWith("/")) {
                    mc.player.networkHandler.sendChatCommand(command.substring(1));
                } else {
                    mc.player.networkHandler.sendChatMessage(command);
                }
            }
            commandIndex++;
            if (commandIndex >= commandQueue.size()) {
                waitingForCommands = false;
            }
        }
    }

    @Override
    public void onDeactivate() {
        waitingForCommands = false;
        commandQueue.clear();
        commandIndex = 0;
        worldActive = false;
        joinGraceTicks = 0;
    }
}
