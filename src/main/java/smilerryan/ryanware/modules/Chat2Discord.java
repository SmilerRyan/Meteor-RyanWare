package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smilerryan.ryanware.RyanWare;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat2Discord for RyanWare - final version
 */
public class Chat2Discord extends Module {
    private final SettingGroup sgGlobal = settings.createGroup("Global");
    private final SettingGroup sgOutgoingChat = settings.createGroup("Outgoing Chat");
    private final SettingGroup sgOutgoingCmd = settings.createGroup("Outgoing Commands");
    private final SettingGroup sgIncoming = settings.createGroup("Incoming Chat");

    // ----- Global -----
    private final Setting<String> globalWebhook = sgGlobal.add(new StringSetting.Builder()
            .name("global-webhook")
            .description("Global webhook URL used as fallback when a specific webhook is empty.")
            .defaultValue("")
            .build()
    );

    private final Setting<String> globalName = sgGlobal.add(new StringSetting.Builder()
            .name("global-name")
            .description("Global username template (supports %server% and %player%). If empty, no username field is sent.")
            .defaultValue("")
            .build()
    );

    private final Setting<Boolean> escapeChat = sgGlobal.add(new BoolSetting.Builder()
            .name("escape-chat")
            .description("Escape Discord markdown and @/# mentions before sending.")
            .defaultValue(true)
            .build()
    );

    // ----- Outgoing Chat -----
    private final Setting<String> webhookOutgoingChat = sgOutgoingChat.add(new StringSetting.Builder()
            .name("webhook-outgoing-chat")
            .description("Webhook for outgoing chat (falls back to global webhook).")
            .defaultValue("")
            .build()
    );

    private final Setting<String> nameOutgoingChat = sgOutgoingChat.add(new StringSetting.Builder()
            .name("name-outgoing-chat")
            .description("Username template for outgoing chat (supports %server% & %player%). Leave empty to use global.")
            .defaultValue("")
            .build()
    );

    private final Setting<Boolean> forwardOutgoingChat = sgOutgoingChat.add(new BoolSetting.Builder()
            .name("forward-outgoing-chat")
            .description("Forward outgoing chat messages (non-commands) to webhook.")
            .defaultValue(false)
            .build()
    );

    // ----- Outgoing Commands -----
    private final Setting<String> webhookOutgoingCommand = sgOutgoingCmd.add(new StringSetting.Builder()
            .name("webhook-outgoing-command")
            .description("Webhook for outgoing commands (falls back to global webhook).")
            .defaultValue("")
            .build()
    );

    private final Setting<String> nameOutgoingCommand = sgOutgoingCmd.add(new StringSetting.Builder()
            .name("name-outgoing-command")
            .description("Username template for outgoing commands (supports %server% & %player%). Leave empty to use global.")
            .defaultValue("")
            .build()
    );

    private final Setting<Boolean> forwardOutgoingCommands = sgOutgoingCmd.add(new BoolSetting.Builder()
            .name("forward-outgoing-commands")
            .description("Forward outgoing commands (messages starting with /) to webhook.")
            .defaultValue(false)
            .build()
    );

    // ----- Incoming Chat -----
    private final Setting<String> webhookIncomingChat = sgIncoming.add(new StringSetting.Builder()
            .name("webhook-incoming-chat")
            .description("Webhook for incoming server chat (falls back to global webhook).")
            .defaultValue("")
            .build()
    );

    private final Setting<String> nameIncomingChat = sgIncoming.add(new StringSetting.Builder()
            .name("name-incoming-chat")
            .description("Username template for incoming chat (supports %server% & %player%). Leave empty to use global.")
            .defaultValue("")
            .build()
    );

    private final Setting<Boolean> forwardIncomingChat = sgIncoming.add(new BoolSetting.Builder()
            .name("forward-incoming-chat")
            .description("Forward incoming server chat to webhook.")
            .defaultValue(false)
            .build()
    );

    private ExecutorService executor;
    private final AtomicBoolean active = new AtomicBoolean(false);

    public Chat2Discord() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Chat2Discord", "Forward chat and commands to one or more Discord webhooks.");
    }

    @Override
    public void onActivate() {
        executor = Executors.newSingleThreadExecutor();
        active.set(true);
    }

    @Override
    public void onDeactivate() {
        active.set(false);
        if (executor != null) executor.shutdownNow();
    }

    // ---------------------------
    // Event handlers
    // ---------------------------

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!active.get() || mc.player == null) return;
        String raw = event.message;
        if (raw == null || raw.isEmpty()) return;

        boolean isCommand = raw.startsWith("/");

        // ----- Outgoing Commands -----
        if (isCommand && forwardOutgoingCommands.get()) {
            String webhook = resolveWebhook(webhookOutgoingCommand.get(), globalWebhook.get());
            String username = resolveName(nameOutgoingCommand.get(), globalName.get());
            if (webhook != null && !webhook.isEmpty()) {
                String content = escapeChat.get() ? escapeForDiscord(raw) : raw;
                final String finalContent = content;
                final String finalWebhook = webhook;
                final String finalUsername = username;
                executor.execute(() -> sendToWebhook(finalWebhook, finalUsername, finalContent));
            }
        }

        // ----- Outgoing Chat -----
        if (!isCommand && forwardOutgoingChat.get()) {
            String webhook = resolveWebhook(webhookOutgoingChat.get(), globalWebhook.get());
            String username = resolveName(nameOutgoingChat.get(), globalName.get());
            if (webhook != null && !webhook.isEmpty()) {
                String content = escapeChat.get() ? escapeForDiscord(raw) : raw;
                final String finalContent = content;
                final String finalWebhook = webhook;
                final String finalUsername = username;
                executor.execute(() -> sendToWebhook(finalWebhook, finalUsername, finalContent));
            }
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!active.get() || mc.player == null) return;
        if (!forwardIncomingChat.get()) return;

        try {
            Text text = event.getMessage();
            if (text == null) return;

            String legacy = textToLegacy(text);
            if (legacy == null || legacy.isEmpty()) return;

            String webhook = resolveWebhook(webhookIncomingChat.get(), globalWebhook.get());
            String username = resolveName(nameIncomingChat.get(), globalName.get());

            if (webhook == null || webhook.isEmpty()) return;

            String content = escapeChat.get() ? escapeForDiscord(legacy) : legacy;
            final String finalContent = content;
            final String finalWebhook = webhook;
            final String finalUsername = username;
            executor.execute(() -> sendToWebhook(finalWebhook, finalUsername, finalContent));
        } catch (Throwable ignored) {}
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private String resolveWebhook(String specific, String global) {
        if (specific != null && !specific.trim().isEmpty()) return specific.trim();
        if (global != null && !global.trim().isEmpty()) return global.trim();
        return null;
    }

    private String resolveName(String specificName, String globalNameValue) {
        String src = (specificName != null && !specificName.trim().isEmpty()) ? specificName.trim() :
                (globalNameValue != null && !globalNameValue.trim().isEmpty() ? globalNameValue.trim() : null);

        if (src == null || src.isEmpty()) return null;

        String server = "singleplayer";
        try {
            ServerInfo info = mc.getCurrentServerEntry();
            if (info != null && info.address != null && !info.address.isEmpty()) server = info.address;
        } catch (Throwable ignored) {}

        String player = mc.player != null ? mc.player.getName().getString() : "unknown";
        return src.replace("%server%", server).replace("%player%", player);
    }

    private String textToLegacy(Text text) {
        if (text == null) return "";
        try {
            return text.getString(); // preserve as much formatting as possible
        } catch (Throwable t) {
            return text.toString();
        }
    }

    private String escapeForDiscord(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': case '*': case '_': case '~': case '`':
                case '>': case '|': case '@': case '#':
                    sb.append('\\').append(c); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private void sendToWebhook(String webhookUrl, String username, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty() || content == null) return;
        if (!active.get()) return;

        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            StringBuilder jb = new StringBuilder();
            jb.append("{\"content\":\"").append(escapeJson(content)).append("\"");
            if (username != null && !username.isEmpty()) {
                jb.append(",\"username\":\"").append(escapeJson(username)).append("\"");
            }
            jb.append("}");

            byte[] out = jb.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(out.length);
            connection.connect();
            try (OutputStream os = connection.getOutputStream()) { os.write(out); }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                notifyPlayerAsync("Webhook responded with HTTP " + code);
            }
        } catch (Exception e) {
            notifyPlayerAsync("Failed to send webhook: " + e.getMessage());
        }
    }

    private void notifyPlayerAsync(String message) {
        if (mc == null) return;
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("[Chat2Discord] ").formatted(Formatting.RED)
                        .append(Text.literal(message).formatted(Formatting.GRAY)), false);
            }
        });
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int)c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
// TODO: Dont strip colors, make commands get sent.
