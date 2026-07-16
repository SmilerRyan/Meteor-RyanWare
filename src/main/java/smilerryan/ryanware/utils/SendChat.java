package smilerryan.ryanware.utils;

import meteordevelopment.meteorclient.MeteorClient;

public class SendChat {
    
    public static void any(String message) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.player.networkHandler == null) return;

        if (message.startsWith("/")) {
            command(message.substring(1));
        } else {
            chat(message);
        }
    }

    public static void chat(String message) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.player.networkHandler == null) return;
        smilerryan.ryanware.utils.SendChat.chat(message);
    }

    public static void command(String command) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.player.networkHandler == null) return;
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        smilerryan.ryanware.utils.SendChat.command(command);
    }

}