package smilerryan.ryanware.commands.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.meteorclient.MeteorClient;

import java.io.*;
import java.util.*;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class command_autoLogin extends Command {

    private static final File ryanwareDir = new File(MeteorClient.FOLDER, "ryanware");
    private static final File f = new File(ryanwareDir, "autologin.txt");

    public command_autoLogin() {
        super(smilerryan.ryanware.RyanWare.commandPrefix + "autoLogin", "Auto login per server + account");
    }

    @EventHandler
    private void onMsg(ReceiveMessageEvent e) {
        if (!e.getMessage().getString().contains("login")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getCurrentServerEntry() == null) return;
        String key = mc.getCurrentServerEntry().address + "|" + mc.getSession().getUsername() + "|";
        for (String line : smilerryan.ryanware.commands.chat.command_autoLogin.load()) {
            if (line.startsWith(key)) {
                mc.player.networkHandler.sendChatCommand("login " + line.replace(key,""));
            }
        }
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> b) {
        b.then(argument("password/off/open", StringArgumentType.greedyString()).executes(c -> {

            String argument = StringArgumentType.getString(c, "password/off/open");

            if (argument.equals("open")) {
                if (f.exists()) {Util.getOperatingSystem().open(f);}
                return SINGLE_SUCCESS;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.getCurrentServerEntry() == null) return SINGLE_SUCCESS;

            String server = mc.getCurrentServerEntry().address;
            String username = mc.getSession().getUsername();
            String key = server + "|" + username + "|";
            
            List<String> l = load();

            l.removeIf(s -> s.startsWith(key));
            if (!argument.equals("off")) {
                l.add(key + argument);
            }

            f.getParentFile().mkdirs();

            try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
                for (String s : l) {w.write(s+"\n");}
            } catch (IOException ignored) {}

            info(
                (argument.equals("off") ? "Cleared" : "Saved") + 
                " password for " + username + 
                " at " + server + "."
            );

            return SINGLE_SUCCESS;
        }));
    }

    public static List<String> load() {
        List<String> l = new ArrayList<>();
        if (!f.exists()) return l;

        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String s;
            while ((s = r.readLine()) != null) l.add(s);
        } catch (IOException ignored) {}
        return l;
    }
}