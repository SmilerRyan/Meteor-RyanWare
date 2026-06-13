package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import smilerryan.ryanware.RyanWare;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.client.texture.NativeImage;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteViewWebServer extends Module {
    public static RemoteViewWebServer INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> port = sgGeneral.add(new IntSetting.Builder()
        .name("port")
        .description("Port number for the web server.")
        .defaultValue(80)
        .min(1)
        .max(65535)
        .build()
    );

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-link")
        .description("Automatically open the web server link in the browser when enabled.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> screenshotMode = sgGeneral.add(new IntSetting.Builder()
        .name("screenshot-mode")
        .description("Screenshot mode. 1 = normal capture, 2 = blank image.")
        .defaultValue(1)
        .min(1)
        .max(2)
        .build()
    );

    private final Setting<Double> imageScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("image-scale")
        .description("Scales the resolution of the image to improve stream performance (e.g., 0.5 is half resolution).")
        .defaultValue(1.0)
        .min(0.1)
        .max(1.0)
        .sliderRange(0.1, 1.0)
        .build()
    );

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private ScheduledExecutorService screenshotScheduler;
    private ExecutorService imageEncoderExecutor; // Dedicated thread for encoding
    private final List<String> chatMessages = Collections.synchronizedList(new ArrayList<>());
    
    private volatile byte[] lastScreenshotBytes = new byte[0];
    private volatile int lastScreenshotHash = 0;
    
    private final AtomicBoolean isEncoding = new AtomicBoolean(false); // Prevents thread queueing

    // Track connected clients by IP and last activity time
    private final Map<String, Long> activeClients = new ConcurrentHashMap<>();

    public RemoteViewWebServer() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Remote-View-Web-Server", "A Remote View Web Server that works on 1.21.1");
        INSTANCE = this;
        //MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onActivate() {
        chatMessages.clear();
        executor = Executors.newCachedThreadPool();
        imageEncoderExecutor = Executors.newSingleThreadExecutor(); 
        updateScreenshotTimer();
        executor.execute(this::startServer);

        if (autoOpen.get()) {
            String url = "http://localhost:" + port.get() + "/";
            try {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("cmd", "/c", "start", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else {
                    new ProcessBuilder("xdg-open", url).start();
                }
            } catch (Exception e) {
                error("Failed to start browser process: " + e);
            }
        }
    }

    @Override
    public void onDeactivate() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        if (executor != null) executor.shutdownNow();
        if (screenshotScheduler != null) screenshotScheduler.shutdownNow();
        if (imageEncoderExecutor != null) imageEncoderExecutor.shutdownNow();
        activeClients.clear();
        isEncoding.set(false);
    }

    private void updateScreenshotTimer() {
        if (screenshotScheduler != null && !screenshotScheduler.isShutdown()) {
            screenshotScheduler.shutdownNow();
        }
        if (isActive()) {
            screenshotScheduler = Executors.newSingleThreadScheduledExecutor();
            screenshotScheduler.scheduleAtFixedRate(this::takeScreenshotAsync, 0, 1, TimeUnit.MILLISECONDS);
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.isModified()) return;
        chatMessages.add(event.getMessage().getString());
        if (chatMessages.size() > 100) chatMessages.remove(0);
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(port.get());
            log("HTTP Server started on port " + port.get());

            // Background monitor for inactive clients
            executor.execute(this::monitorClients);

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                String ip = clientSocket.getInetAddress().getHostAddress();

                if (!activeClients.containsKey(ip)) {
                    log("Client connected: " + ip);
                }
                activeClients.put(ip, System.currentTimeMillis());

                executor.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            if (serverSocket != null && !serverSocket.isClosed()) {
                error("Server error: " + e.getMessage());
            }
        }
    }

    private void monitorClients() {
        try {
            while (!serverSocket.isClosed()) {
                long now = System.currentTimeMillis();
                for (Iterator<Map.Entry<String, Long>> it = activeClients.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, Long> entry = it.next();
                    if (now - entry.getValue() > 60_000) { // 1 minute inactivity
                        log("Client disconnected (timeout): " + entry.getKey());
                        it.remove();
                    }
                }
                Thread.sleep(5000); // check every 5s
            }
        } catch (InterruptedException ignored) {}
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            if (requestLine.startsWith("GET / ") || requestLine.startsWith("GET / HTTP/1.")) {
                sendHtmlResponse(out, buildHtmlPage());
            } else if (requestLine.startsWith("POST /chat ")) {
                handleChatPost(in, out);
            } else if (requestLine.startsWith("GET /screenshot")) {
                handleScreenshotRequest(out);
            } else if (requestLine.startsWith("GET /stream")) {
                handleStreamRequest(out);
            } else if (requestLine.startsWith("GET /chat")) {
                handleChatRequest(out);
            } else if (requestLine.startsWith("GET /disconnect")) {
                handleDisconnect(out);
            } else if (requestLine.startsWith("GET /quitgame")) {
                handleQuitGame(out);
            } else if (requestLine.startsWith("GET /?connect=")) {
                handleConnect(requestLine, out);
            } else send404Response(out, requestLine);

        } catch (Exception e) {
            error("Client handling error: " + e.toString());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
    
    private void handleConnect(String requestLine, OutputStream out) throws IOException {
        try {
            String param = requestLine.split("\\?connect=")[1].split(" ")[0];
            String[] parts = param.split(":");
            String ip = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 25565; // default port

            MinecraftClient mc = MinecraftClient.getInstance();

            mc.execute(() -> {
                mc.setScreen(new MultiplayerScreen(new TitleScreen()));

                try {
                    // Create ServerInfo; pass null instead of ServerType for compatibility
                    ServerInfo serverInfo = new ServerInfo(ip + ":" + port, ip + ":" + port, null);

                    // Call the private connect(ServerInfo) method via reflection
                    Method connectMethod = MultiplayerScreen.class.getDeclaredMethod("connect", ServerInfo.class);
                    connectMethod.setAccessible(true);
                    connectMethod.invoke(mc.currentScreen, serverInfo);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // Send redirect
            out.write("HTTP/1.1 303 See Other\r\nLocation: /\r\n\r\n".getBytes());

        } catch (Exception e) {
            e.printStackTrace();
            // Simple inline error response for compatibility
            out.write(("HTTP/1.1 500 Internal Server Error\r\n\r\nFailed to connect to server").getBytes());
        }
    }

    private void handleDisconnect(OutputStream out) throws IOException {
        if (MeteorClient.mc.world != null) {
            MeteorClient.mc.execute(() -> {
                MeteorClient.mc.world.disconnect(Text.literal("Disconnected"));
                MeteorClient.mc.disconnect(Text.literal("Disconnected"));
            });
        }
        sendRedirectResponse(out);
    }

    private void handleQuitGame(OutputStream out) throws IOException {
        MeteorClient.mc.execute(MeteorClient.mc::scheduleStop);
        sendRedirectResponse(out);
    }

    private void handleChatRequest(OutputStream out) throws IOException {
        StringBuilder chatHtml = new StringBuilder();
        synchronized (chatMessages) {
            for (String message : chatMessages) {
                chatHtml.append("<p>").append(escapeHtml(message)).append("</p>");
            }
        }

        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + chatHtml.toString().getBytes().length + "\r\n" +
                "\r\n" +
                chatHtml.toString();
        out.write(response.getBytes());
    }

    private void handleChatPost(BufferedReader in, OutputStream out) throws IOException {
        int contentLength = 0;
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length: ")) {
                contentLength = Integer.parseInt(line.substring("Content-Length: ".length()).trim());
            }
        }

        if (contentLength > 0) {
            char[] body = new char[contentLength];
            in.read(body, 0, contentLength);
            String postData = new String(body);

            if (postData.startsWith("message=")) {
                String message = URLDecoder.decode(postData.substring("message=".length()), "UTF-8");
                sendChatMessage(message);
            }
        }

        sendRedirectResponse(out);
    }

    private void handleScreenshotRequest(OutputStream out) throws IOException {
        if (lastScreenshotBytes.length > 0) {
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/jpeg\r\n" + // Changed to JPEG
                    "Content-Length: " + lastScreenshotBytes.length + "\r\n" +
                    "\r\n";
            out.write(header.getBytes());
            out.write(lastScreenshotBytes);
        } else {
            error("No screenshot available.");
            send404Response(out, "GET /screenshot (no screenshot available)");
        }
    }

    private void handleStreamRequest(OutputStream out) {
        try {
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=--BoundaryString\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: close\r\n" +
                    "Pragma: no-cache\r\n\r\n";
            out.write(header.getBytes());
            out.flush();

            int lastSentHash = 0;

            while (!serverSocket.isClosed()) {
                if (lastScreenshotBytes.length > 0 && lastScreenshotHash != lastSentHash) {
                    byte[] imageBytes = lastScreenshotBytes;

                    out.write("--BoundaryString\r\n".getBytes());
                    out.write("Content-Type: image/jpeg\r\n".getBytes());
                    out.write(("Content-Length: " + imageBytes.length + "\r\n\r\n").getBytes());
                    out.write(imageBytes);
                    out.write("\r\n".getBytes());
                    out.flush();

                    lastSentHash = lastScreenshotHash;
                }

                Thread.sleep(1);
            }
        } catch (Exception e) {}
    }

    private void takeScreenshotAsync() {
        if (screenshotMode.get() == 2) {
            if (!isEncoding.compareAndSet(false, true)) return;
            imageEncoderExecutor.execute(() -> {
                try {
                    BufferedImage blank = new BufferedImage(854, 480, BufferedImage.TYPE_INT_RGB);
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(blank, "jpeg", baos); // Changed to JPEG
                        lastScreenshotBytes = baos.toByteArray();
                    }
                } catch (Exception e) {
                    error("Failed to generate blank screenshot: " + e.getMessage());
                } finally {
                    isEncoding.set(false);
                }
            });
            return;
        }

        if (MeteorClient.mc.player == null || MeteorClient.mc.getFramebuffer() == null) {
            return;
        }

        // Drop the frame if the previous frame is still being compressed
        if (!isEncoding.compareAndSet(false, true)) {
            return;
        }

        MeteorClient.mc.execute(() -> {
            ScreenshotRecorder.takeScreenshot(MeteorClient.mc.getFramebuffer(), nativeImage -> {
                if (nativeImage == null) {
                    isEncoding.set(false);
                    return;
                }

                imageEncoderExecutor.execute(() -> {
                    try {
                        BufferedImage bufferedImage = nativeImageToBufferedImage(nativeImage);
                        nativeImage.close();

                        // Optimize BAOS creation size to prevent internal array copying
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 100)) { 
                            ImageIO.write(bufferedImage, "jpeg", baos); // Compresses drastically faster than PNG

                            byte[] newBytes = baos.toByteArray();
                            int newHash = Arrays.hashCode(newBytes);

                            if (newHash != lastScreenshotHash) {
                                lastScreenshotBytes = newBytes;
                                lastScreenshotHash = newHash;
                            }
                        }
                    } catch (Exception e) {
                        error("Exception during image processing: " + e.getMessage());
                    } finally {
                        isEncoding.set(false);
                    }
                });
            });
        });
    }

    private void log(String message) {
        System.out.println("[RemoteViewWebServer] " + message);
    }

    private void error(String message) {
        System.err.println("[RemoteViewWebServer] ERROR: " + message);
    }

    private BufferedImage nativeImageToBufferedImage(NativeImage nativeImage) {
        int originalWidth = nativeImage.getWidth();
        int originalHeight = nativeImage.getHeight();
        double scale = imageScale.get();

        // Fast path for unscaled images
        if (scale >= 1.0) {
            BufferedImage bufferedImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_RGB);
            int[] pixels = new int[originalWidth * originalHeight];

            for (int y = 0; y < originalHeight; y++) {
                for (int x = 0; x < originalWidth; x++) {
                    pixels[y * originalWidth + x] = nativeImage.getColorArgb(x, y);
                }
            }
            bufferedImage.setRGB(0, 0, originalWidth, originalHeight, pixels, 0, originalWidth);
            return bufferedImage;
        }

        // Fast nearest-neighbor scaling path
        int newWidth = Math.max(1, (int) (originalWidth * scale));
        int newHeight = Math.max(1, (int) (originalHeight * scale));

        BufferedImage bufferedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[newWidth * newHeight];

        for (int y = 0; y < newHeight; y++) {
            // Calculate the corresponding Y coordinate on the original image
            int srcY = Math.min((int) (y / scale), originalHeight - 1);
            
            for (int x = 0; x < newWidth; x++) {
                // Calculate the corresponding X coordinate on the original image
                int srcX = Math.min((int) (x / scale), originalWidth - 1);
                
                pixels[y * newWidth + x] = nativeImage.getColorArgb(srcX, srcY);
            }
        }

        bufferedImage.setRGB(0, 0, newWidth, newHeight, pixels, 0, newWidth);
        return bufferedImage;
    }

    private void sendChatMessage(String message) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.getNetworkHandler() == null) return;

        if (message.startsWith("/")) {
            MeteorClient.mc.getNetworkHandler().sendChatCommand(message.substring(1));
        } else {
            MeteorClient.mc.getNetworkHandler().sendChatMessage(message);
        }
    }

    private void sendHtmlResponse(OutputStream out, String html) throws IOException {
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + html.getBytes().length + "\r\n" +
                "\r\n";
        out.write(header.getBytes());
        out.write(html.getBytes());
    }

    private void sendRedirectResponse(OutputStream out) throws IOException {
        String response = "HTTP/1.1 303 See Other\r\n" +
                "Location: /\r\n" +
                "\r\n";
        out.write(response.getBytes());
    }

    private void send404Response(OutputStream out, String request) throws IOException {
        String response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "404 Not Found for request: " + request;
        out.write(response.getBytes());
        error("404 Not Found for request: " + request);
    }

    private String buildHtmlPage() {
        ServerList servers = new ServerList(MeteorClient.mc);
        servers.loadFile();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Minecraft Remote View</title><style>");
        html.append("body{font-family:Arial,sans-serif;max-width:1200px;margin:0 auto;padding:20px;}");
        html.append(".container{display:flex;gap:20px;}");
        html.append(".chat-box{border:1px solid #ccc;padding:10px;width:400px;height:600px;overflow-y:scroll;}");
        html.append(".viewer{flex-grow:1;border:1px solid #ccc;padding:10px;}");
        html.append("#screenshot{max-width:100%;max-height:550px;display:block;margin-bottom:10px;cursor:pointer;}");
        html.append("#chat-messages{height:600px;overflow-y:auto;}");
        html.append("form{display:flex;margin-top:10px;}input{flex-grow:1;padding:8px;}");
        html.append("button{padding:8px 15px;border:none;color:white;}");
        html.append(".server-btn{background:#428bca;margin:5px 0;width:100%;}");
        html.append(".disconnect-btn{background:#f0ad4e;margin:5px 0;width:100%;}");
        html.append(".quit-btn{background:#d9534f;margin:5px 0;width:100%;}");
        html.append(".refresh-btn{background:#5cb85c;margin-bottom:10px;}");
        html.append(".fullscreen-btn{background:#666;margin-bottom:10px;cursor:pointer;}");
        html.append("</style><script>");
        html.append("let isStream = true;");
        html.append("function toggleView() {");
        html.append("  isStream = !isStream;");
        html.append("  const img = document.getElementById('screenshot');");
        html.append("  img.src = '/' + (isStream?'stream':'screenshot') + '?' + new Date().getTime();");
        html.append("}");
        html.append("function refreshChat(){fetch('/chat').then(r=>r.text()).then(h=>document.getElementById('chat-messages').innerHTML=h);}");
        html.append("setInterval(refreshChat,1000);");
        html.append("function sendChatMessage(){let msg=document.getElementById('chat-input').value.trim();if(msg==='')return;");
        html.append("fetch('/chat',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'message='+encodeURIComponent(msg)});");
        html.append("document.getElementById('chat-input').value='';}");
        html.append("function toggleFullscreen() {");
        html.append("  const elem = document.getElementById('screenshot');");
        html.append("  if (!document.fullscreenElement) {");
        html.append("    elem.requestFullscreen().catch(() => {});");
        html.append("  } else {");
        html.append("    document.exitFullscreen();");
        html.append("  }");
        html.append("}");
        html.append("</script></head><body>");
        html.append("<h1>Minecraft Remote View Web Server</h1>");
        html.append("<div class='container'>");

        html.append("<div class='viewer'>");
        html.append("<button class='fullscreen-btn' onclick='toggleFullscreen()'>Toggle Fullscreen</button>");
        html.append("<img id='screenshot' src='/stream' alt='Screenshot' onclick='toggleView()'/>");

        html.append("<form onsubmit='event.preventDefault(); sendChatMessage();'>");
        html.append("<input id='chat-input' type='text' placeholder='Send chat message' autocomplete='off'/>");
        html.append("<button type='submit' style='background:#428bca;'>Send</button>");
        html.append("</form>");

        html.append("</div>");

        html.append("<div class='chat-box'>");
        html.append("<h2>Chat History</h2>");
        html.append("<div id='chat-messages'>");
        synchronized (chatMessages) {
            for (String msg : chatMessages) {
                html.append("<p>").append(escapeHtml(msg)).append("</p>");
            }
        }
        html.append("</div>");
        html.append("</div>");

        html.append("</div>");

        html.append("<h2>Servers</h2>");

        for (int i = 0; i < servers.size(); i++) {
            String serverName = escapeHtml(servers.get(i).name);
            String serverAddress = escapeHtml(servers.get(i).address);
            html.append("<form method='GET' action=''>");
            html.append("<button class='server-btn' type='submit' name='connect' value='").append(serverAddress).append("'>");
            html.append(serverName);
            html.append("</button>");
            html.append("</form>");
        }

        html.append("<form method='GET' action='/disconnect'>");
        html.append("<button class='disconnect-btn' type='submit'>Disconnect</button>");
        html.append("</form>");

        html.append("<form method='GET' action='/quitgame'>");
        html.append("<button class='quit-btn' type='submit'>Quit Game</button>");
        html.append("</form>");

        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}