package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.regex.Pattern;

public class ExtraScreenshot extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();


    private final Setting<Boolean> autoCopy = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Copy")
        .description("Automatically copy screenshot to clipboard")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> uploadToCatbox = sgGeneral.add(new BoolSetting.Builder()
        .name("Upload to Catbox")
        .description("Uploads screenshot and sends link in chat")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideMessage = sgGeneral.add(new BoolSetting.Builder()
        .name("Hide Message")
        .description("Hide the screenshot message in chat")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> playSound = sgGeneral.add(new BoolSetting.Builder()
        .name("Play Sound")
        .description("Play screenshot sound when taking a screenshot")
        .defaultValue(false)
        .build()
    );

    public ExtraScreenshot() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "ExtraScreenshot",
            "Do extra things every time you take a screenshot.");
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent event) {
        String message = event.getMessage().getString();

        message = message.replaceAll("^<(\\d{1,2}:\\d{2}(:\\d{2})?)>\\s*", "");

        final String prefix = "Saved screenshot as ";
        if (!message.startsWith(prefix)) return;

        String fileName = message.substring(prefix.length()).trim();
        if (fileName.isEmpty()) return;

        if (hideMessage.get()) {
            event.cancel();
        }

        if (playSound.get()) {
            playScreenshotSound();
        }

        File file = new File("screenshots/" + fileName);

        if (autoCopy.get()) {
            copyFileToClipboard(file);
        }

        if (uploadToCatbox.get()) {
            uploadScreenshot(file);
        }
    }

    private void uploadScreenshot(File file) {
        if (file == null || !file.exists()) return;

        new Thread(() -> {
            try {
                String url = CatboxUploader.upload(file);

                // Meteor chat output
                info("Uploaded: " + url);

            } catch (Exception e) {
                info("Upload failed: " + e.getMessage());
            }
        }).start();
    }

    private void playScreenshotSound() {
        try {
            var stream = ExtraScreenshot.class.getResourceAsStream("/sound/screenshot.wav");
            if (stream == null) return;

            AudioInputStream audioStream =
                AudioSystem.getAudioInputStream(new BufferedInputStream(stream));

            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyFileToClipboard(File file) {
        if (file == null || !file.exists()) return;

        try {
            var clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();

            java.awt.datatransfer.Transferable transferable =
                new java.awt.datatransfer.Transferable() {
                    @Override
                    public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                        return new java.awt.datatransfer.DataFlavor[]{
                            java.awt.datatransfer.DataFlavor.javaFileListFlavor
                        };
                    }

                    @Override
                    public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                        return java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(flavor);
                    }

                    @Override
                    public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                        throws java.awt.datatransfer.UnsupportedFlavorException {
                        return java.util.List.of(file);
                    }
                };

            clipboard.setContents(transferable, null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------- CATBOX UPLOADER ----------------

    private static class CatboxUploader {

        public static String upload(File file) throws IOException {
            String boundary = "----catbox" + System.currentTimeMillis();

            HttpURLConnection conn = (HttpURLConnection)
                new URL("https://catbox.moe/user/api.php").openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream out = conn.getOutputStream()) {

                write(out, "--" + boundary + "\r\n");
                write(out, "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n");
                write(out, "fileupload\r\n");

                write(out, "--" + boundary + "\r\n");
                write(out,
                    "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" +
                    file.getName() + "\"\r\n\r\n");

                Files.copy(file.toPath(), out);

                write(out, "\r\n--" + boundary + "--\r\n");
            }

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 400)
                ? conn.getInputStream()
                : conn.getErrorStream();

            String response = new String(in.readAllBytes()).trim();

            if (code != 200) {
                throw new IOException(response);
            }

            return response;
        }

        private static void write(OutputStream out, String s) throws IOException {
            out.write(s.getBytes());
        }
    }
}