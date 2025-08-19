package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.client.texture.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RemoteViewWebServer extends Module {
    public static RemoteViewWebServer INSTANCE;

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private ScheduledExecutorService screenshotScheduler;
    private final List<String> chatMessages = Collections.synchronizedList(new ArrayList<>());
    private byte[] lastScreenshotBytes = new byte[0];
    private long lastScreenshotTime = 0;
    private int screenshotDelay = 250;

    public RemoteViewWebServer() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-Remote-View-Web-Server", "A Remote View Web Server that works on 1.21.1");
        INSTANCE = this;
        //MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onActivate() {
        chatMessages.clear();
        executor = Executors.newCachedThreadPool();
        screenshotScheduler = Executors.newSingleThreadScheduledExecutor();
        screenshotScheduler.scheduleAtFixedRate(this::takeScreenshotAsync, 0, screenshotDelay, TimeUnit.MILLISECONDS);
        executor.execute(this::startServer);
    }

    @Override
    public void onDeactivate() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        if (executor != null) executor.shutdownNow();
        if (screenshotScheduler != null) screenshotScheduler.shutdownNow();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.isModified()) return;
        chatMessages.add(event.getMessage().getString());
        if (chatMessages.size() > 100) chatMessages.remove(0);
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(8080);
            log("HTTP Server started on port 8080");

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                executor.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            if (serverSocket != null && !serverSocket.isClosed()) {
                error("Server error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            log("Request from " + clientSocket.getInetAddress().getHostAddress() + " to " + requestLine);

            if (requestLine.startsWith("GET / ") || requestLine.startsWith("GET / HTTP/1.")) {
                sendHtmlResponse(out, buildHtmlPage());
            } else if (requestLine.startsWith("POST /chat ")) {
                handleChatPost(in, out);
            } else if (requestLine.startsWith("GET /screenshot")) {
                handleScreenshotRequest(out);
            } else if (requestLine.startsWith("GET /chat")) {
                handleChatRequest(out);
            } else if (requestLine.startsWith("GET /disconnect")) {
                handleDisconnect(out);
            } else if (requestLine.startsWith("GET /quitgame")) {
                handleQuitGame(out);
            } else if (requestLine.startsWith("GET /setspeed")) {
                String speed = requestLine.split("speed=")[1].split(" ")[0];
                screenshotDelay = Integer.parseInt(URLDecoder.decode(speed, "UTF-8"));
                // Reschedule with new delay
                if (screenshotScheduler != null) {
                    screenshotScheduler.shutdownNow();
                }
                screenshotScheduler = Executors.newSingleThreadScheduledExecutor();
                screenshotScheduler.scheduleAtFixedRate(this::takeScreenshotAsync, 0, screenshotDelay, TimeUnit.MILLISECONDS);
                sendRedirectResponse(out);
            } else {
                send404Response(out, requestLine);
            }

        } catch (Exception e) {
            error("Client handling error: " + e.toString());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleDisconnect(OutputStream out) throws IOException {
        if (MeteorClient.mc.world != null) {
            MeteorClient.mc.execute(() -> {
                MeteorClient.mc.world.disconnect();
                MeteorClient.mc.disconnect(new DisconnectedScreen(
                    new MultiplayerScreen(new TitleScreen()),
                    Text.of("Disconnected"),
                    Text.of("Disconnected by remote command")
                ));
            });
        }
        sendRedirectResponse(out);
    }

    private void handleQuitGame(OutputStream out) throws IOException {
        MeteorClient.mc.execute(MeteorClient.mc::scheduleStop);
        sendRedirectResponse(out);
    }

    private void handleChatRequest(OutputStream out) throws IOException {
        log("Handling /chat request...");
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
                    "Content-Type: image/png\r\n" +
                    "Content-Length: " + lastScreenshotBytes.length + "\r\n" +
                    "\r\n";
            out.write(header.getBytes());
            out.write(lastScreenshotBytes);
        } else {
            error("No screenshot available.");
            send404Response(out, "GET /screenshot (no screenshot available)");
        }
    }


private void takeScreenshotAsync() {
    if (MeteorClient.mc.player == null || MeteorClient.mc.getFramebuffer() == null) {
        //log("Cannot take screenshot, player or framebuffer is null.");
        return;
    }

    MeteorClient.mc.execute(() -> {
        try {
            NativeImage nativeImage = ScreenshotRecorder.takeScreenshot(MeteorClient.mc.getFramebuffer());

            if (nativeImage == null) {
                //error("ScreenshotRecorder.takeScreenshot returned null image");
                return;
            }

            // Process off-thread
            new Thread(() -> {
                try {
                    BufferedImage bufferedImage = nativeImageToBufferedImage(nativeImage);
                    nativeImage.close();

                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(bufferedImage, "png", baos);
                        lastScreenshotBytes = baos.toByteArray();
                        lastScreenshotTime = System.currentTimeMillis();
                    } catch (IOException e) {
                        error("Failed to encode screenshot: " + e.getMessage());
                    }
                } catch (Exception e) {
                    error("Exception during image processing: " + e.getMessage());
                }
            }, "ScreenshotProcessor").start();

        } catch (Exception e) {
            error("Exception during screenshot capture: " + e.getMessage());
        }
    });
}


    private void log(String message) {
    System.out.println("[RemoteViewWebServer] " + message);
}

private void error(String message) {
    System.err.println("[RemoteViewWebServer] ERROR: " + message);
}


    private BufferedImage nativeImageToBufferedImage(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = nativeImage.getColor(x, y);

                int alpha = (argb >> 24) & 0xFF;
                int red   = (argb >> 16) & 0xFF;
                int green = (argb >> 8)  & 0xFF;
                int blue  = (argb)       & 0xFF;

                // Swap red and blue
                int fixedArgb = (alpha << 24) | (blue << 16) | (green << 8) | red;

                bufferedImage.setRGB(x, y, fixedArgb);
            }
        }

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
    html.append("function refreshScreenshot(){document.getElementById('screenshot').src='/screenshot?'+new Date().getTime();}");
    html.append("function refreshChat(){fetch('/chat').then(r=>r.text()).then(h=>document.getElementById('chat-messages').innerHTML=h);}");
    html.append("setInterval(refreshScreenshot,").append(screenshotDelay).append(");");
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

    // Viewer Section
    html.append("<div class='viewer'>");
    html.append("<button class='fullscreen-btn' onclick='toggleFullscreen()'>Toggle Fullscreen</button>");
    html.append("<img id='screenshot' src='/screenshot' alt='Screenshot'/>");

    html.append("<form onsubmit='event.preventDefault(); sendChatMessage();'>");
    html.append("<input id='chat-input' type='text' placeholder='Send chat message' autocomplete='off'/>");
    html.append("<button type='submit' style='background:#428bca;'>Send</button>");
    html.append("</form>");

    html.append("</div>");

    // Chat History moved to right side box
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

    // Server list and controls
    html.append("<h2>Servers</h2>");
    html.append("<form method='GET' action='/setspeed'>");
    html.append("Screenshot Delay (ms): <input name='speed' type='number' value='").append(screenshotDelay).append("' min='50' max='5000'/>");
    html.append("<button type='submit' class='refresh-btn'>Set</button>");
    html.append("</form>");

    for (int i = 0; i < servers.size(); i++) {
        String serverName = escapeHtml(servers.get(i).name);
        String serverAddress = escapeHtml(servers.get(i).address);
        html.append("<form method='GET' action=''>");
        html.append("<button class='server-btn' type='submit' name='connect' value='").append(i).append("'>");
        html.append(serverName).append(" (").append(serverAddress).append(")");
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
