package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
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
    private final SettingGroup sgForward = settings.createGroup("Forward Settings");
    private final SettingGroup sgNames = settings.createGroup("Username Templates");

    // Main settings
    private final Setting<String> webhook = sgMain.add(new StringSetting.Builder()
            .name("webhook")
            .description("Discord webhook URL.")
            .defaultValue("")
            .build()
    );

    // Forward settings
    private final Setting<Boolean> forwardOutgoingChat = sgForward.add(new BoolSetting.Builder()
            .name("forward-outgoing-chat")
            .description("Forward outgoing chat messages.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> forwardIncomingChat = sgForward.add(new BoolSetting.Builder()
            .name("forward-incoming-chat")
            .description("Forward incoming chat messages.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> forwardJoinLeave = sgForward.add(new BoolSetting.Builder()
            .name("forward-join-leave")
            .description("Forward join and disconnect messages.")
            .defaultValue(false)
            .build()
    );

    // Username templates
    private final Setting<String> outgoingTemplate = sgNames.add(new StringSetting.Builder()
            .name("outgoing-username")
            .description("Username for outgoing chat messages")
            .defaultValue("%player% on %server%")
            .build()
    );

    private final Setting<String> incomingTemplate = sgNames.add(new StringSetting.Builder()
            .name("incoming-username")
            .description("Username for incoming chat messages")
            .defaultValue("%server%")
            .build()
    );

    private ExecutorService executor;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private String lastServerAddress = null;

    public Chat2Discord() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Chat2Discord",
                "Forward chat and join/leave messages to a Discord webhook with plain text.");
    }

    @Override
    public void onActivate() {
        executor = Executors.newSingleThreadExecutor();
        active.set(true);

        // Detect join immediately if connected
        ServerInfo current = mc.getCurrentServerEntry();
        if (forwardJoinLeave.get() && current != null && lastServerAddress == null) {
            lastServerAddress = current.address;
            sendWebhook("joined server " + lastServerAddress, outgoingTemplate.get());
        }
    }

    @Override
    public void onDeactivate() {
        active.set(false);
        if (executor != null) executor.shutdownNow();

        // Detect disconnect immediately
        if (forwardJoinLeave.get() && lastServerAddress != null) {
            sendWebhook("disconnected from " + lastServerAddress, outgoingTemplate.get());
            lastServerAddress = null;
        }
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!active.get() || mc.player == null) return;

        // Detect connection changes (for switching servers while active)
        detectConnectionChange();

        if (!forwardOutgoingChat.get()) return;

        String msg = event.message;
        if (msg != null && !msg.isEmpty()) sendWebhook(msg, outgoingTemplate.get());
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!active.get() || mc.player == null) return;

        // Detect connection changes (for switching servers while active)
        detectConnectionChange();

        if (!forwardIncomingChat.get()) return;

        Text text = event.getMessage();
        if (text == null) return;

        String plain = removeColorCodes(text.getString());
        if (!plain.isEmpty()) sendWebhook(plain, incomingTemplate.get());
    }

    private void detectConnectionChange() {
        if (!forwardJoinLeave.get()) return;

        ServerInfo current = mc.getCurrentServerEntry();

        // Joined a server
        if (current != null && lastServerAddress == null) {
            lastServerAddress = current.address;
            sendWebhook("joined server " + lastServerAddress, outgoingTemplate.get());
        }

        // Disconnected from server
        if (current == null && lastServerAddress != null) {
            sendWebhook("disconnected from " + lastServerAddress, outgoingTemplate.get());
            lastServerAddress = null;
        }
    }

    private void sendWebhook(String message, String usernameTemplate) {
        if (webhook.get() == null || webhook.get().isEmpty() || message == null || !active.get()) return;

        final String username = resolveUsername(usernameTemplate);
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
                if (username != null && !username.isEmpty()) {
                    payload += ",\"username\":\"" + escapeJson(username) + "\"";
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

    private String resolveUsername(String template) {
        if (template == null || template.isEmpty()) return null;

        String server = "singleplayer";
        try {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null && !info.address.isEmpty()) server = info.address;
        } catch (Throwable ignored) {}

        String player = mc.player != null ? mc.player.getName().getString() : "unknown";
        return template.replace("%server%", server).replace("%player%", player);
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

    private String removeColorCodes(String s) {
        if (s == null) return "";
        return s.replaceAll("§.", "");
    }
}
