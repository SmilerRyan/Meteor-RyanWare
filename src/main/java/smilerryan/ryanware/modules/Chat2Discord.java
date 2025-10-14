package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Chat2Discord extends Module {
    private final SettingGroup sgMain = settings.createGroup("Settings");

    private final Setting<String> webhook = sgMain.add(new StringSetting.Builder()
            .name("webhook")
            .description("Discord webhook URL.")
            .defaultValue("")
            .build()
    );

    private final Setting<String> usernameTemplate = sgMain.add(new StringSetting.Builder()
            .name("username")
            .description("Username template, supports %player% and %server%. Leave empty for default.")
            .defaultValue("%player% on %server%")
            .build()
    );

    private final Setting<Boolean> forwardOutgoingChat = sgMain.add(new BoolSetting.Builder()
            .name("forward-outgoing-chat")
            .description("Forward outgoing chat messages.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> forwardOutgoingCommands = sgMain.add(new BoolSetting.Builder()
            .name("forward-outgoing-commands")
            .description("Forward outgoing commands (messages starting with /).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> forwardIncomingChat = sgMain.add(new BoolSetting.Builder()
            .name("forward-incoming-chat")
            .description("Forward incoming chat messages.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> forwardJoinLeave = sgMain.add(new BoolSetting.Builder()
            .name("forward-join-leave")
            .description("Forward join and disconnect messages.")
            .defaultValue(false)
            .build()
    );

    private ExecutorService executor;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private boolean wasConnected = false;
    private String lastServerAddress = null;

    public Chat2Discord() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Chat2Discord",
                "Forward chat, commands, and join/leave to a single Discord webhook with plain text.");
    }

    @Override
    public void onActivate() {
        executor = Executors.newSingleThreadExecutor();
        active.set(true);
        wasConnected = false;
        lastServerAddress = null;
    }

    @Override
    public void onDeactivate() {
        active.set(false);
        if (executor != null) executor.shutdownNow();
        wasConnected = false;
        lastServerAddress = null;
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!active.get() || mc.player == null) return;

        String msg = event.message;
        if (msg == null || msg.isEmpty()) return;

        boolean isCommand = msg.startsWith("/");

        if (isCommand && forwardOutgoingCommands.get()) sendWebhook(msg);
        else if (!isCommand && forwardOutgoingChat.get()) sendWebhook(msg);
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!active.get() || mc.player == null || !forwardIncomingChat.get()) return;
        Text text = event.getMessage();
        if (text == null) return;

        // Get the raw string and remove all color codes (formatting)
        String plain = removeColorCodes(text.getString());
        if (!plain.isEmpty()) sendWebhook(plain);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!active.get() || !forwardJoinLeave.get()) return;

        boolean currentlyConnected = mc.player != null && mc.getCurrentServerEntry() != null;

        if (currentlyConnected && !wasConnected) {
            lastServerAddress = mc.getCurrentServerEntry().address;
            sendWebhook("joined server " + lastServerAddress);
        }

        if (!currentlyConnected && wasConnected) {
            String server = lastServerAddress != null ? lastServerAddress : "unknown server";
            sendWebhook("disconnected from " + server);
            lastServerAddress = null;
        }

        wasConnected = currentlyConnected;
    }

    private void sendWebhook(String message) {
        if (webhook.get() == null || webhook.get().isEmpty() || message == null || !active.get()) return;

        String username = resolveUsername();

        final String finalUsername = username;
        final String finalContent = message;

        executor.execute(() -> {
            try {
                URL url = new URL(webhook.get());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                String payload = "{\"content\":\"" + escapeJson(finalContent) + "\"";
                if (finalUsername != null && !finalUsername.isEmpty()) {
                    payload += ",\"username\":\"" + escapeJson(finalUsername) + "\"";
                }
                payload += "}";

                byte[] out = payload.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(out.length);
                connection.connect();
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(out);
                }

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) notifyPlayerAsync("Webhook responded with HTTP " + code);
            } catch (Exception e) {
                notifyPlayerAsync("Failed to send webhook: " + e.getMessage());
            }
        });
    }

    private String resolveUsername() {
        if (usernameTemplate.get() == null || usernameTemplate.get().isEmpty()) return null;

        String server = "singleplayer";
        try {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null && !info.address.isEmpty()) server = info.address;
        } catch (Throwable ignored) {}

        String player = mc.player != null ? mc.player.getName().getString() : "unknown";
        return usernameTemplate.get().replace("%server%", server).replace("%player%", player);
    }

    private void notifyPlayerAsync(String message) {
        if (mc == null) return;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[Chat2Discord] ").append(Text.literal(message)), false);
            }
        });
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    // Simple color code remover
    private String removeColorCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", ""); // § followed by any char
    }
}
