package smilerryan.ryanware.modules;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.*;
import net.minecraft.util.*;
import smilerryan.ryanware.RyanWare;

public class RemoteViewProxyServer extends Module {
    public static RemoteViewProxyServer INSTANCE;

    private ServerSocket serverSocket;
    private ExecutorService executor;

    private List<String> lastTabSnapshot = new ArrayList<>();

    private final List<ViewerHandler> viewers =
            Collections.synchronizedList(new ArrayList<>());

    public RemoteViewProxyServer() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Remote-View-Proxy",
            "Hosts a 1.12.2 Compatible Server on port 25565.");
    }

    // ---------------- SETTINGS ----------------

    public enum FormattingMode {
        PER_STYLE,
        PER_CHARACTER,
        STRIP
    }

    private final SettingGroup sg_Logging = settings.createGroup("Logging");

    private final Setting<Boolean> s_Log_Connections = sg_Logging.add(new BoolSetting.Builder()
        .name("log-connections")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> s_Log_Disconnections = sg_Logging.add(new BoolSetting.Builder()
        .name("log-disconnections")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> s_Log_Incoming_Chat = sg_Logging.add(new BoolSetting.Builder()
        .name("log-incoming-chat")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> s_Log_Incoming_Commands = sg_Logging.add(new BoolSetting.Builder()
        .name("log-incoming-commands")
        .defaultValue(false)
        .build()
    );

    private final SettingGroup sg_Features = settings.createGroup("Features");

    private final Setting<Boolean> s_Features_Send_Position_To_Viewers = sg_Features.add(new BoolSetting.Builder()
        .name("send-position-to-viewers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> s_Features_Send_Chat_To_Viewers = sg_Features.add(new BoolSetting.Builder()
        .name("send-chat-to-viewers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> s_Features_Send_Viewer_Chat = sg_Features.add(new BoolSetting.Builder()
        .name("send-viewer-chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> s_Features_Send_Viewer_Commands = sg_Features.add(new BoolSetting.Builder()
        .name("send-viewer-commands")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> s_Features_Forward_Tab_Completion = sg_Features.add(new BoolSetting.Builder()
        .name("forward-tab-completion-(notify-only-at-this-point)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> s_Features_Forward_Player_List = sg_Features.add(new BoolSetting.Builder()
        .name("forward-player-list-(notify-only-at-this-point)")
        .defaultValue(true)
        .build()
    );

    private final SettingGroup sg_Formatting = settings.createGroup("Formatting");

    private final Setting<FormattingMode> s_Formatting_Mode =
        sg_Formatting.add(new EnumSetting.Builder<FormattingMode>()
            .name("formatting-mode")
            .defaultValue(FormattingMode.PER_CHARACTER)
            .build()
        );

    @Override
    public void onActivate() {
        INSTANCE = this;
        executor = Executors.newCachedThreadPool();
        executor.execute(this::startServer);
    }

    @Override
    public void onDeactivate() {
        INSTANCE = null;

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}

        if (executor != null) executor.shutdownNow();

        disconnectAll();
    }

    // ---------------- FIXED LEGACY CONVERTER ----------------


    private Formatting getFormatting(TextColor color) {
        if (color == null) return null;

        return switch (color.getName()) {
            case "black" -> Formatting.BLACK;
            case "dark_blue" -> Formatting.DARK_BLUE;
            case "dark_green" -> Formatting.DARK_GREEN;
            case "dark_aqua" -> Formatting.DARK_AQUA;
            case "dark_red" -> Formatting.DARK_RED;
            case "dark_purple" -> Formatting.DARK_PURPLE;
            case "gold" -> Formatting.GOLD;
            case "gray" -> Formatting.GRAY;
            case "dark_gray" -> Formatting.DARK_GRAY;
            case "blue" -> Formatting.BLUE;
            case "green" -> Formatting.GREEN;
            case "aqua" -> Formatting.AQUA;
            case "red" -> Formatting.RED;
            case "light_purple" -> Formatting.LIGHT_PURPLE;
            case "yellow" -> Formatting.YELLOW;
            case "white" -> Formatting.WHITE;
            default -> null;
        };
    }


    private String toLegacyString(Text text) {
        StringBuilder out = new StringBuilder();
        FormattingMode mode = s_Formatting_Mode.get();

        text.visit((style, string) -> {

            if (mode == FormattingMode.STRIP) {
                out.append(string);
                return Optional.empty();
            }

            String prefix = "";

            TextColor color = style.getColor();
            if (color != null) {
                Formatting f = getFormatting(color);
                if (f != null) prefix += "§" + f.getCode();
            }

            if (style.isBold()) prefix += "§l";
            if (style.isItalic()) prefix += "§o";
            if (style.isUnderlined()) prefix += "§n";
            if (style.isStrikethrough()) prefix += "§m";
            if (style.isObfuscated()) prefix += "§k";

            if (mode == FormattingMode.PER_CHARACTER) {
                for (int i = 0; i < string.length(); i++) {
                    out.append(prefix).append(string.charAt(i));
                }
            } else {
                out.append(prefix).append(string);
            }

            return Optional.empty();
        }, Style.EMPTY);

        return out.toString();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        if (s_Features_Send_Chat_To_Viewers.get()) {
            broadcastChat(toLegacyString(e.getMessage()));
        }
    }

    // ---------------- SERVER ----------------

    private void startServer() {
        try {
            serverSocket = new ServerSocket(25565);

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();

                ViewerHandler handler = new ViewerHandler(socket);
                viewers.add(handler);

                executor.execute(handler);
            }
        } catch (IOException ignored) {}
    }

    private void disconnectAll() {
        synchronized (viewers) {
            for (ViewerHandler v : viewers) v.disconnect();
            viewers.clear();
        }
    }

    private void broadcastChat(String message) {
        synchronized (viewers) {
            String json = "{\"text\":\"" + escape(message) + "\"}";

            for (ViewerHandler v : viewers) {
                v.sendChat(json);
            }
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }


    @EventHandler
    private void onTick(TickEvent.Post event) {

        if (!s_Features_Forward_Player_List.get()) return;
        if (mc.getNetworkHandler() == null) return;

        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();

        List<String> currentSnapshot = networkHandler.getPlayerList().stream()
            .map(e -> e.getProfile().name() + " (" + e.getLatency() + "ms)")
            .collect(Collectors.toList());

        if (currentSnapshot.equals(lastTabSnapshot)) {
            return; // nothing changed
        }

        lastTabSnapshot = currentSnapshot;

        broadcastChat("Players: " + String.join(", ", currentSnapshot));
    }

    // ---------------- VIEWER ----------------

    private class ViewerHandler implements Runnable {
        private final Socket socket;

        private DataInputStream in;
        private DataOutputStream out;

        private String username;
        private volatile boolean running = true;

        private ScheduledExecutorService positionExecutor;

        ViewerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                readVarInt(in);
                int packetId = readVarInt(in);
                if (packetId != 0x00) return;

                readVarInt(in);
                readString(in);
                in.readUnsignedShort();
                int state = readVarInt(in);

                if (state == 1) {
                    handleStatus();
                    disconnect();
                    return;
                }
                if (state != 2) return;

                readVarInt(in);
                packetId = readVarInt(in);
                if (packetId != 0x00) return;

                username = readString(in);

                UUID viewerUuid = UUID.randomUUID();

                sendPacket(0x02, d -> {
                    writeString(d, viewerUuid.toString());
                    writeString(d, username);
                });

                sendPacket(0x23, d -> {
                    d.writeInt(1);
                    d.writeByte(1);
                    d.writeInt(0);
                    d.writeByte(0);
                    d.writeByte(1);
                    writeString(d, "default");
                    d.writeBoolean(false);
                });

                // sendPacket(0x2E, d -> {
                //     writeVarInt(d, 0); // Action: Add Player
                //     writeVarInt(d, 1); // Number of players
                //     d.writeLong(viewerUuid.getMostSignificantBits());
                //     d.writeLong(viewerUuid.getLeastSignificantBits());
                //     writeString(d, username);
                //     writeVarInt(d, 0); // Number of properties
                //     writeVarInt(d, 3); // Gamemode: Creative
                //     writeVarInt(d, 0); // Ping
                //     d.writeBoolean(false); // Has display name
                // });

                MinecraftClient client = MinecraftClient.getInstance();
                double initialX = (client != null && client.player != null) ? client.player.getX() : 0;
                double initialY = (client != null && client.player != null) ? client.player.getY() : 0;
                double initialZ = (client != null && client.player != null) ? client.player.getZ() : 0;
                float initialYaw = (client != null && client.player != null) ? client.player.getYaw() : 0;
                float initialPitch = (client != null && client.player != null) ? client.player.getPitch() : 0;

                sendPacket(0x2F, d -> {
                    if(s_Features_Send_Position_To_Viewers.get()) {
                        d.writeDouble(initialX);
                        d.writeDouble(initialY);
                        d.writeDouble(initialZ);
                        d.writeFloat(initialYaw);
                        d.writeFloat(initialPitch);
                    } else {
                        d.writeDouble(0);
                        d.writeDouble(0);
                        d.writeDouble(0);
                        d.writeFloat(0);
                        d.writeFloat(0);
                    }  
                    d.writeByte(0); // Flags
                    writeVarInt(d, 999); // Teleport ID
                });

                if (s_Log_Connections.get()) log("§a+ " + username);

                startPositionSync();

                while (running) {
                    try {
                        int length = readVarInt(in);
                        byte[] data = new byte[length];
                        in.readFully(data);

                        DataInputStream pin =
                                new DataInputStream(new ByteArrayInputStream(data));

                        int id = readVarInt(pin);

                        if (id == 0x0B) {
                            long keepAlive = pin.readLong();
                            sendPacket(0x0B, d -> d.writeLong(keepAlive));
                        }
                        else if (id == 0x02) {
                            String msg = readString(pin);
                            if (msg.startsWith("/")) {
                                if (s_Log_Incoming_Commands.get())
                                    log("§eCommand " + username + " " + msg);

                                if (s_Features_Send_Viewer_Commands.get()) {
                                    MinecraftClient.getInstance().execute(() -> {
                                        if (mc.player != null && mc.player.networkHandler != null) {
                                            mc.player.networkHandler.sendChatCommand(msg.substring(1));
                                        }
                                    });
                                }

                            } else {
                                if (s_Log_Incoming_Chat.get())
                                    log("§eChat " + username + " " + msg);

                                if (s_Features_Send_Viewer_Chat.get()) {
                                    MinecraftClient.getInstance().execute(() -> {
                                        if (mc.player != null && mc.player.networkHandler != null) {
                                            mc.player.networkHandler.sendChatMessage(msg);
                                        }
                                    });
                                }
                            }
                        }

                        else if (id == 0x01 && s_Features_Forward_Tab_Completion.get()) {
                            String input = readString(pin);
                            log("§eSuggestion " + username + " " + input);
                        }

                    } catch (Exception e) {
                        break;
                    }
                }

            } catch (Exception ignored) {
            } finally {
                disconnect();
            }
        }

        public void sendChat(String json) {
            try {
                sendPacket(0x0F, d -> {
                    writeString(d, json);
                    d.writeByte(0);
                });
            } catch (IOException ignored) {}
        }

        private void startPositionSync() {
            positionExecutor = Executors.newSingleThreadScheduledExecutor();

            positionExecutor.scheduleAtFixedRate(() -> {
                if (!running) return;

                try {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client == null || client.player == null) return;

                    sendPacket(0x2F, d -> {
                        if(s_Features_Send_Position_To_Viewers.get()) {
                            d.writeDouble(client.player.getX());
                            d.writeDouble(client.player.getY());
                            d.writeDouble(client.player.getZ());
                            d.writeFloat(client.player.getYaw());
                            d.writeFloat(client.player.getPitch());
                        } else {
                            d.writeDouble(0);
                            d.writeDouble(0);
                            d.writeDouble(0);
                            d.writeFloat(0);
                            d.writeFloat(0);
                        }                        
                        d.writeByte(0);
                        writeVarInt(d, 999);
                    });

                } catch (Exception e) {
                    disconnect();
                }

            }, 0, 50, TimeUnit.MILLISECONDS);
        }

        private void handleStatus() throws IOException {
            readVarInt(in);
            int id = readVarInt(in);
            if (id != 0x00) return;

            String json =
                    "{\"version\":{\"name\":\"1.12.2\",\"protocol\":340}," +
                    "\"players\":{\"max\":100,\"online\":0,\"sample\":[]}," +
                    "\"description\":{\"text\":\"Remote View Server\"}}";

            sendPacket(0x00, d -> writeString(d, json));

            readVarInt(in);
            readVarInt(in);
            long ping = in.readLong();

            sendPacket(0x01, d -> d.writeLong(ping));
        }

        private synchronized void sendPacket(int id, PacketWriter writer) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(buf);

            writeVarInt(d, id);
            writer.write(d);

            byte[] data = buf.toByteArray();

            writeVarInt(out, data.length);
            out.write(data);
            out.flush();
        }

        public void disconnect() {
            if (!running) return;
            running = false;

            try { socket.close(); } catch (IOException ignored) {}

            if (positionExecutor != null) {
                positionExecutor.shutdownNow();
                positionExecutor = null;
            }

            viewers.remove(this);

            if (username != null && s_Log_Disconnections.get()) {
                log("§c- " + username);
            }
        }
    }

    private interface PacketWriter {
        void write(DataOutputStream d) throws IOException;
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int pos = 0;
        byte b;

        do {
            b = in.readByte();
            value |= (b & 0x7F) << pos;
            pos += 7;
            if (pos > 35) throw new IOException("VarInt too big");
        } while ((b & 0x80) != 0);

        return value;
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, "UTF-8");
    }

    private void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        writeVarInt(out, b.length);
        out.write(b);
    }

    private void log(String m) {
        String msg = "§6[§cRVPS§6] " + m;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(msg), false);
            }
        });
    }
}
