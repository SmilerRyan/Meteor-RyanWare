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
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;

import smilerryan.ryanware.RyanWare;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Chat2Discord_1_21_11 extends Module {

    /* ================= SETTINGS ================= */

    private final Setting<String> webhook =
            settings.getDefaultGroup().add(
                    new StringSetting.Builder()
                            .name("webhook")
                            .description("Discord webhook URL.")
                            .defaultValue("")
                            .build()
            );

    private final Setting<String> usernameTemplate =
            settings.getDefaultGroup().add(
                    new StringSetting.Builder()
                            .name("username-template")
                            .description("Discord username. Variables: %player% %server%")
                            .defaultValue("%player% on %server%")
                            .build()
            );

    /* ================= THREADING ================= */

    private ExecutorService executor;
    private ScheduledExecutorService scheduler;

    private final AtomicBoolean active = new AtomicBoolean(false);

    /* ================= BUFFER ================= */

    private final StringBuilder buffer = new StringBuilder(4096);
    private volatile long lastAppendTime = 0;

    /* ================= MODULE ================= */

    public Chat2Discord_1_21_11() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Chat2Discord=1.21.11",
                "Forwards chat to Discord webhook.");
    }

    @Override
    public void onActivate() {
        executor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        active.set(true);

        scheduler.scheduleAtFixedRate(this::tryFlush, 200, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDeactivate() {
        active.set(false);

        flush();

        if (scheduler != null) scheduler.shutdownNow();
        if (executor != null) executor.shutdownNow();
    }

    /* ================= EVENTS ================= */

    @EventHandler
    private void onSendMessage(SendMessageEvent e) {
        append("**sent chat** " + e.message);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send e) {
        Packet<?> p = e.packet;

        if (p instanceof CommandExecutionC2SPacket cmd)
            append("**sent command** /" + cmd.command());

        if (p instanceof ChatCommandSignedC2SPacket signed)
            append("**sent command** /" + signed.command());
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        Text t = e.getMessage();

        if (t != null)
            append(stripColors(t.getString()));
    }

    /* ================= CORE ================= */

    private void append(String msg) {
        if (!active.get() || mc.player == null || msg == null || msg.isEmpty())
            return;

        synchronized (buffer) {
            buffer.append(msg).append("\n");
        }

        lastAppendTime = System.currentTimeMillis();
    }

    private void tryFlush() {
        if (!active.get()) return;

        if (System.currentTimeMillis() - lastAppendTime >= 1000)
            flush();
    }

    private void flush() {
        String out;

        synchronized (buffer) {
            if (buffer.length() == 0) return;

            out = buffer.toString();
            buffer.setLength(0);
        }

        sendWebhook(out);
    }

    /* ================= WEBHOOK ================= */

    private void sendWebhook(String content) {
        if (webhook.get().isEmpty()) return;

        String username = resolveUsername();

        executor.execute(() -> {
            try {
                if (content.length() <= 2000)
                    sendJsonWebhook(content, username);
                else
                    sendFileWebhook(content);

            } catch (Exception ignored) {}
        });
    }

    private void sendJsonWebhook(String content, String username) throws Exception {
        URL url = new URL(webhook.get());

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String payload = "{\"content\":\"" + escapeJson(content) + "\",\"username\":\"" + escapeJson(username) + "\"}";

        byte[] out = payload.getBytes(StandardCharsets.UTF_8);

        conn.setFixedLengthStreamingMode(out.length);
        conn.connect();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
        }

        conn.getInputStream().close();
    }

    private void sendFileWebhook(String content) throws Exception {
        String boundary = "----ChatBoundary" + System.currentTimeMillis();

        URL url = new URL(webhook.get());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        String header =
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"chat-log.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n";

        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] body = (header + content + footer).getBytes(StandardCharsets.UTF_8);

        conn.setFixedLengthStreamingMode(body.length);
        conn.connect();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        conn.getInputStream().close();
    }

    /* ================= VARIABLES ================= */

    private String resolveUsername() {
        String player = mc.player != null ? mc.player.getName().getString() : "unknown";

        String server = "singleplayer";

        try {
            ServerInfo info = mc.getCurrentServerEntry();

            if (info != null && info.address != null && !info.address.isEmpty())
                server = info.address;

        } catch (Throwable ignored) {}

        return usernameTemplate.get()
                .replace("%player%", player)
                .replace("%server%", server);
    }

    /* ================= UTIL ================= */

    private String stripColors(String s) {
        if (s == null) return "";

        StringBuilder out = new StringBuilder(s.length());

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    private String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());

        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }

        return sb.toString();
    }
}