package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.network.packet.Packet;
import smilerryan.ryanware.RyanWare;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;


public class Chat2Discord extends Module {
    private final SettingGroup sgWebhook = settings.createGroup("Webhook Settings");
    private final SettingGroup sgForward = settings.createGroup("Forward Settings");

    private final Setting<String> webhook = sgWebhook.add(new StringSetting.Builder()
            .name("webhook").description("Discord webhook URL.").defaultValue("").build());

    private final Setting<String> usernameTemplate = sgWebhook.add(new StringSetting.Builder()
            .name("username")
            .description("Username for Discord messages. Supports %player% and %server%.")
            .defaultValue("%player% on %server%").build());

    private final Setting<Boolean> forwardOutgoingChat = sgForward.add(new BoolSetting.Builder()
            .name("forward-outgoing-chat").description("Forward outgoing chat messages.").defaultValue(false).build());

    private final Setting<Boolean> forwardOutgoingCommands = sgForward.add(new BoolSetting.Builder()
            .name("forward-outgoing-commands").description("Forward outgoing commands.").defaultValue(false).build());

    private final Setting<Boolean> forwardIncomingChat = sgForward.add(new BoolSetting.Builder()
            .name("forward-incoming-chat").description("Forward incoming chat messages.").defaultValue(false).build());

    private final Setting<Boolean> forwardJoinLeave = sgForward.add(new BoolSetting.Builder()
            .name("forward-join-leave").description("Forward join/disconnect messages.").defaultValue(false).build());

    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private String lastServerAddress = null;

    private final StringBuilder messageBuffer = new StringBuilder();
    private volatile long lastMessageTime = 0;
    private volatile String batchUsername = null;

    public Chat2Discord() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Chat2Discord",
                "Forwards your chat to any Discord webhook.");
    }

    @Override
    public void onActivate() {
        executor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        active.set(true);
        scheduler.scheduleAtFixedRate(this::tryFlushBuffer, 200, 200, TimeUnit.MILLISECONDS);
        handleConnectionChange(true);
    }

    @Override
    public void onDeactivate() {
        active.set(false);
        flushBuffer(); // flush before shutdown
        handleConnectionChange(false);
        if (executor != null) executor.shutdownNow();
        if (scheduler != null) scheduler.shutdownNow();
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        handleMessage(event.message, forwardOutgoingChat.get(), "**sent chat** ");
    }
    
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        Packet<?> packet = event.packet;
        String command = null;

        if (packet instanceof CommandExecutionC2SPacket cmdPacket) {
            command = "/" + cmdPacket.command();
        } else if (packet instanceof ChatCommandSignedC2SPacket signedPacket) {
            command = "/" + signedPacket.command();
        }

        if (command != null) {
            handleMessage(command, forwardOutgoingCommands.get(), "**sent command** ");
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        Text text = event.getMessage();
        handleMessage(text != null ? removeColorCodes(text.getString()) : null, forwardIncomingChat.get(), "");
    }

    private void handleMessage(String message, boolean enabled, String prefix) {
        if (!active.get() || mc.player == null || !enabled || message == null || message.isEmpty()) return;
        handleConnectionChange(true);
        queueMessage(prefix + message);
    }

    private void handleConnectionChange(boolean joinCheck) {
        if (!forwardJoinLeave.get()) return;
        ServerInfo current = mc.getCurrentServerEntry();

        if (joinCheck && current != null && lastServerAddress == null) {
            lastServerAddress = current.address;
            queueMessage("**joined server** " + lastServerAddress);
        } else if (!joinCheck && lastServerAddress != null) {
            queueMessage("**disconnected from** " + lastServerAddress);
            lastServerAddress = null;
        }
    }

    private void queueMessage(String message) {
        batchUsername = resolveUsername(usernameTemplate.get());
        synchronized (messageBuffer) {
            messageBuffer.append(message).append("\n");
        }
        lastMessageTime = System.currentTimeMillis();
    }

    private void tryFlushBuffer() {
        if (!active.get()) return;
        synchronized (messageBuffer) {
            if (messageBuffer.length() == 0) return;
        }
        if (System.currentTimeMillis() - lastMessageTime >= 1000) flushBuffer();
    }

    private void flushBuffer() {
        String content;
        String username = batchUsername;
        synchronized (messageBuffer) {
            if (messageBuffer.length() == 0) return;
            content = messageBuffer.toString();
            messageBuffer.setLength(0);
        }
        batchUsername = null;
        sendWebhook(content, username);
    }

    private void sendWebhook(String content, String username) {
        if (webhook.get().isEmpty() || executor == null || executor.isShutdown()) return;
        executor.execute(() -> {
            try {
                if (content.length() <= 2000) sendJsonWebhook(content, username);
                else sendFileWebhook(content);
            } catch (Exception ignored) {}
        });
    }

    private void sendJsonWebhook(String content, String username) throws Exception {
        URL url = new URL(webhook.get());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String payload = "{\"content\":\"" + escapeJson(content) + "\"";
        if (username != null && !username.isEmpty()) payload += ",\"username\":\"" + escapeJson(username) + "\"";
        payload += "}";

        byte[] out = payload.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(out.length);
        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
        }
    }

    private void sendFileWebhook(String content) throws Exception {
        String boundary = "----ChatBoundary" + System.currentTimeMillis();
        URL url = new URL(webhook.get());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"chat.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] body = (header + content + footer).getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(body.length);
        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
    }

    private String resolveUsername(String template) {
        String server = "singleplayer";
        try {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null && !info.address.isEmpty()) server = info.address;
        } catch (Throwable ignored) {}
        String player = mc.player != null ? mc.player.getName().getString() : "unknown";
        return template.replace("%server%", server).replace("%player%", player);
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
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
