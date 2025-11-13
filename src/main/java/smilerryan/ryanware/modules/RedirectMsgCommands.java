package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import smilerryan.ryanware.RyanWare;

import java.util.List;

public class RedirectMsgCommands extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> redirectedCommands = sgGeneral.add(new StringListSetting.Builder()
        .name("redirected-commands")
        .description("Commands to intercept. Example: msg, tell, w")
        .defaultValue(List.of("msg"))
        .build()
    );

    private final Setting<String> globalRedirect = sgGeneral.add(new StringSetting.Builder()
        .name("global-template")
        .description("Default redirect template if no user-specific format. Use {user} and {message}.")
        .defaultValue("/mail send {user} {message}")
        .build()
    );

    private final Setting<List<String>> userRules = sgGeneral.add(new StringListSetting.Builder()
        .name("user-rules")
        .description("List of users and optional custom formats. Example: Steve | Notch /msg Admin {user}: {message}")
        .defaultValue(List.of())
        .build()
    );

    public RedirectMsgCommands() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Redirect-MSG-Commands", "Redirects message commands to any format.");
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        Packet<?> packet = event.packet;
        String command = null;
        if (packet instanceof CommandExecutionC2SPacket cmdPacket) {
            command = cmdPacket.command();
        } else if (packet instanceof ChatCommandSignedC2SPacket signedPacket) {
            command = signedPacket.command();
        }
        if (command == null) return;
        String[] parts = command.split("\\s+", 3);
        if (parts.length < 2) return;
        String base = parts[0].toLowerCase();
        if (!redirectedCommands.get().contains(base)) return;
        String target = parts[1];
        String message = parts.length > 2 ? parts[2] : "";
        String template = null;
        for (String entry : userRules.get()) {
            if (entry.trim().isEmpty()) continue;
            String[] split = entry.split("\\s+", 2);
            if (split[0].equalsIgnoreCase(target)) {
                template = (split.length > 1) ? split[1] : globalRedirect.get();
                break;
            }
        }
        if (!userRules.get().isEmpty() && template == null) return;
        if (template == null) template = globalRedirect.get();
        event.cancel();
        String newCommand = template.replace("{user}", target).replace("{message}", message);
        if (mc.player == null) return;
        if (newCommand.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(newCommand.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(newCommand);
        }
    }
}