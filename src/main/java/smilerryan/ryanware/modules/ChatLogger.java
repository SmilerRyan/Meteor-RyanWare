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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatLogger extends Module {
    private final Setting<String> filenameTemplate =
            settings.getDefaultGroup().add(
                    new StringSetting.Builder()
                            .name("file-name")
                            .description("Filename template. Tokens: %server% %player% %date% %time%")
                            .defaultValue("logs/chat-%server%.txt")
                            .build()
            );

    private ExecutorService writerExecutor;
    private ScheduledExecutorService scheduler;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final StringBuilder buffer = new StringBuilder(8192);

    private BufferedWriter writer;
    private volatile long lastAppendTime = 0;

    private String lastServer = null;
    private String currentFile = null;

    // session timestamp (frozen for filename)
    private LocalDateTime sessionStartTime;

    private static final DateTimeFormatter LINE_TS =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH-mm-ss");

    public ChatLogger() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "ChatLogger",
                "High performance chat logger with session-based filenames.");
    }

    @Override
    public void onActivate() {
        writerExecutor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        active.set(true);

        scheduler.scheduleAtFixedRate(this::tryFlush, 200, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDeactivate() {
        active.set(false);
        flush();
        closeWriter();

        if (scheduler != null) scheduler.shutdownNow();
        if (writerExecutor != null) writerExecutor.shutdown();
    }

    /* ================= EVENTS ================= */

    @EventHandler
    private void onSendMessage(SendMessageEvent e) {
        append("[SendChat] " + e.message);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send e) {
        Packet<?> p = e.packet;

        if (p instanceof CommandExecutionC2SPacket cmd)
            append("[SendCommand] /" + cmd.command());

        if (p instanceof ChatCommandSignedC2SPacket signed)
            append("[SendCommand] /" + signed.command());
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        Text t = e.getMessage();
        if (t != null)
            append("[Chat] " + stripColors(t.getString()));
    }

    /* ================= CORE ================= */

    private void append(String msg) {
        if (!active.get() || mc.player == null) return;

        checkFileRotation();

        String ts = LINE_TS.format(LocalDateTime.now());

        synchronized (buffer) {
            buffer.append('[')
                    .append(ts)
                    .append("] ")
                    .append(msg)
                    .append('\n');
        }

        lastAppendTime = System.currentTimeMillis();
    }

    private void tryFlush() {
        if (!active.get()) return;

        if (System.currentTimeMillis() - lastAppendTime >= 100)
            flush();
    }

    private void flush() {
        String out;

        synchronized (buffer) {
            if (buffer.length() == 0 || writer == null) return;
            out = buffer.toString();
            buffer.setLength(0);
        }

        writerExecutor.execute(() -> {
            try {
                writer.write(out);
                writer.flush();
            } catch (IOException ignored) {}
        });
    }

    /* ================= FILE MANAGEMENT ================= */

    private void checkFileRotation() {
        ServerInfo info = mc.getCurrentServerEntry();
        String server = (info != null && info.address != null)
                ? sanitize(info.address)
                : "singleplayer";

        // rotate only when server changes
        if (!server.equals(lastServer)) {
            sessionStartTime = LocalDateTime.now();

            String resolved = resolveTemplate(server, sessionStartTime);

            rotateFile(resolved);

            String player = mc.player.getName().getString();
            appendImmediate("[Join] Player " + player + " on " + server);

            currentFile = resolved;
            lastServer = server;
        }
    }

    private String resolveTemplate(String server, LocalDateTime time) {
        return filenameTemplate.get()
                .replace("%server%", server)
                .replace("%player%", mc.player.getName().getString())
                .replace("%date%", DATE.format(time))
                .replace("%time%", TIME.format(time));
    }

    private void rotateFile(String file) {
        flush();
        closeWriter();

        try {
            Path path = Paths.get(file);
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            boolean exists = Files.exists(path);

            writer = new BufferedWriter(
                    new OutputStreamWriter(
                            Files.newOutputStream(path,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.APPEND),
                            StandardCharsets.UTF_8),
                    64 * 1024
            );

            if (exists) writer.write("\n");

        } catch (Exception e) {
            error("Failed opening log file.");
        }
    }

    private void closeWriter() {
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}
        writer = null;
    }

    private void appendImmediate(String line) {
        String ts = LINE_TS.format(LocalDateTime.now());

        synchronized (buffer) {
            buffer.append('[')
                    .append(ts)
                    .append("] ")
                    .append(line)
                    .append('\n');
        }
    }

    /* ================= UTIL ================= */

    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

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
}
