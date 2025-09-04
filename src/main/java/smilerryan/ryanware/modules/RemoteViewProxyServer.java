package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RemoteViewProxyServer extends Module {
    public static RemoteViewProxyServer INSTANCE;
    
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private List<ViewerHandler> viewers = Collections.synchronizedList(new ArrayList<>());

    public RemoteViewProxyServer() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Remote-View-Proxy", "1.12.2 Remote View Server");
    }

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
        disconnectAllViewers();
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(25565);
            log("Server started on port 25565 (1.12.2)");
            
            while (!serverSocket.isClosed()) {
                Socket viewerSocket = serverSocket.accept();
                ViewerHandler handler = new ViewerHandler(viewerSocket);
                viewers.add(handler);
                executor.execute(handler);
            }
        } catch (IOException e) {
            if (!serverSocket.isClosed()) {
                error("Server error: " + e.getMessage());
            }
        }
    }

    public void forwardToViewers(String message) {
        synchronized (viewers) {
            for (ViewerHandler viewer : viewers) {
                viewer.sendChatMessage("§8[Server] §f" + message);
            }
        }
    }

    private void disconnectAllViewers() {
        synchronized (viewers) {
            for (ViewerHandler viewer : viewers) {
                viewer.disconnect();
            }
            viewers.clear();
        }
    }

    private class ViewerHandler implements Runnable {
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;
        private boolean running = true;

        public ViewerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                // --- Handshake ---
                int packetLength = readVarInt(in);
                int packetId = readVarInt(in);
                if (packetId != 0x00) {
                    disconnect();
                    return;
                }

                int protocol = readVarInt(in);
                String host = readString(in);
                int port = in.readUnsignedShort();
                int nextState = readVarInt(in);
                
                if (nextState == 1) { // Status ping
                    handleStatusRequest();
                    disconnect();
                    return;
                } else if (nextState != 2) { // Login
                    disconnect();
                    return;
                }

                // --- Login Start ---
                packetLength = readVarInt(in);
                packetId = readVarInt(in);
                if (packetId != 0x00) {
                    disconnect();
                    return;
                }

                username = readString(in);
                log("Viewer connecting: " + username);

                // --- Login Success --- (Fixed UUID format)
                sendPacket(0x02, d -> {
                    String uuid = UUID.randomUUID().toString(); // With hyphens
                    log("Sending UUID: " + uuid);
                    writeString(d, uuid);
                    writeString(d, username);
                });

                // --- Join Game ---
                sendPacket(0x23, d -> {
                    d.writeInt(0); // Entity ID
                    d.writeByte(3); // Gamemode (Spectator)
                    d.writeInt(0); // Dimension (Overworld)
                    d.writeByte(0); // Difficulty (Peaceful)
                    d.writeByte(1); // Max players
                    writeString(d, "flat"); // Level type
                    d.writeBoolean(false); // Reduced debug
                    d.writeByte(1); // Enable respawn screen
                });

                // --- Spawn Position ---
                sendPacket(0x05, d -> {
                    d.writeInt(0); // X
                    d.writeInt(64); // Y
                    d.writeInt(0); // Z
                });

                // --- Player Abilities ---
                sendPacket(0x2B, d -> {
                    d.writeByte(0x0F); // Invulnerable, Flying, Allow Flying, Creative
                    d.writeFloat(0.05f); // Flying speed
                    d.writeFloat(0.1f); // FOV modifier
                });

                // --- Player Position and Look ---
                sendPacket(0x2E, d -> {
                    d.writeDouble(0); // X
                    d.writeDouble(64); // Y
                    d.writeDouble(0); // Z
                    d.writeFloat(0); // Yaw
                    d.writeFloat(0); // Pitch
                    d.writeByte(0); // Flags
                    writeVarInt(d, 0); // Teleport ID
                });

                log("Viewer joined: " + username);

                // Main processing loop
                while (running) {
                    try {
                        int length = readVarInt(in);
                        byte[] packetData = new byte[length];
                        in.readFully(packetData);
                        
                        if (packetData.length > 0) {
                            int id = packetData[0] & 0xFF;
                            
                            if (id == 0x0B) { // Keep-alive
                                sendRawPacket(packetData);
                            } 
                            else if (id == 0x02) { // Chat
                                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packetData));
                                dis.readByte(); // Skip ID
                                String message = readString(dis);
                                log("Viewer chat: " + username + ": " + message);
                            }
                        }
                    } catch (Exception e) {
                        error("Packet processing error: " + e.toString());
                        break;
                    }
                }

            } catch (Exception e) {
                error("Connection error: " + e.toString());
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }

        private void handleStatusRequest() throws IOException {
            // Read status request
            readVarInt(in);
            readVarInt(in);
            
            // Send status response
            String motd = "§aRemote View Server\n§7Connected viewers: " + viewers.size();
            String jsonResponse = String.format(
                "{\"version\":{\"name\":\"1.12.2\",\"protocol\":340},\"players\":{\"max\":%d,\"online\":%d,\"sample\":[]},\"description\":{\"text\":\"%s\"}}",
                100, viewers.size() + 1, motd
            );
            
            sendPacket(0x00, d -> writeString(d, jsonResponse));
            
            // Handle ping
            readVarInt(in);
            readVarInt(in);
            long payload = in.readLong();
            sendPacket(0x01, d -> d.writeLong(payload));
        }

        private void sendPacket(int packetId, PacketWriter writer) throws IOException {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(buf);
            d.writeByte(packetId);
            writer.write(d);
            byte[] packet = buf.toByteArray();
            log("Sending packet 0x" + Integer.toHexString(packetId) + " (" + packet.length + " bytes)");
            sendRawPacket(packet);
        }

        private void sendRawPacket(byte[] packet) throws IOException {
            writeVarInt(out, packet.length);
            out.write(packet);
            out.flush();
        }

        public void sendChatMessage(String message) {
            try {
                sendPacket(0x0F, d -> {
                    writeString(d, message);
                    d.writeByte(0); // Position: chat
                });
            } catch (IOException e) {
                error("Failed to send chat to " + username);
                disconnect();
            }
        }

        public void disconnect() {
            if (!running) return;
            running = false;
            
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
            
            viewers.remove(this);
            if (username != null) {
                log("Viewer disconnected: " + username);
            }
        }
    }

    private interface PacketWriter {
        void write(DataOutputStream d) throws IOException;
    }

    // Utility methods
    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0, pos = 0;
        byte b;
        do {
            b = in.readByte();
            value |= (b & 0x7F) << pos;
            pos += 7;
            if (pos > 35) throw new IOException("VarInt too long");
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
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, "UTF-8");
    }

    private void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        writeVarInt(out, b.length);
        out.write(b);
    }

    private void log(String message) {
        System.out.println("[RemoteView] " + message);
    }

    private void error(String message) {
        System.err.println("[RemoteView] " + message);
    }
}