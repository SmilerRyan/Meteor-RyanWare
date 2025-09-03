package smilerryan.ryanware.modules_essentials;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import smilerryan.ryanware.RyanWare;

import java.io.File;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Recorder extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<String> folder = sgGeneral.add(new StringSetting.Builder()
        .name("Save Folder")
        .defaultValue("recordings")
        .build()
    );

    private final Setting<Integer> resolution = sgGeneral.add(new IntSetting.Builder()
        .name("Resolution (Height)")
        .description("Video height. Width is auto-calculated.")
        .defaultValue(720)
        .min(1)
        .sliderMax(2160)
        .build()
    );

    private final Setting<Integer> fps = sgGeneral.add(new IntSetting.Builder()
        .name("Frames Per Second (FPS)")
        .description("Video frames per second to record at.")
        .defaultValue(30)
        .min(5)
        .sliderMax(240)
        .build()
    );

    private final Setting<Integer> bitrate = sgGeneral.add(new IntSetting.Builder()
        .name("Bitrate (kbps)")
        .description("Video bitrate in kilobits per second.")
        .defaultValue(4000)
        .min(100)
        .sliderMax(50000)
        .build()
    );

    private final Setting<Integer> maxFileSize = sgGeneral.add(new IntSetting.Builder()
        .name("Auto Stop Recording at Size (MB)")
        .description("Automatically stop recording when this size is reached (0 = unlimited).")
        .defaultValue(0)
        .min(0)
        .sliderMax(5000)
        .build()
    );

    private Process ffmpegProcess;
    private OutputStream ffmpegInput;
    private File outputFile;

    public Recorder() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix + "E-Recorder", "Records the desktop using ffmpeg (no dependencies).");
    }

    @Override
    public void onActivate() {
        
        try {
            File dir = new File(folder.get());
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
            outputFile = new File(dir, timestamp + ".ts");

            ProcessBuilder builder = new ProcessBuilder(
                "ffmpeg",
                // "-y",
                "-f", "gdigrab",
                "-framerate", "" + fps.get(),
                "-i", "desktop",
                "-vf", "scale=-1:" + resolution.get(),
                // "-c:v", "libx264",
                // "-preset", "ultrafast",
                // "-pix_fmt", "yuv420p",
                "-b:v", bitrate.get() + "k"
            );

            int maxSizeMB = maxFileSize.get();
            if (maxSizeMB > 0) {
                builder.command().add("-fs");
                builder.command().add(maxSizeMB + "M");
            }

            builder.command().add(outputFile.getAbsolutePath());

            ffmpegProcess = builder.start();
            ffmpegInput = ffmpegProcess.getOutputStream();

            info(outputFile.getName() + " (Recording)");

            // wait for it to exit
            ffmpegProcess.onExit().thenRun(() -> {
                if (outputFile.exists()) {
                    long sizeBytes = outputFile.length();
                    double sizeMB = sizeBytes / 1024.0 / 1024.0;
                    DecimalFormat df = new DecimalFormat("#.##");
                    info(outputFile.getName() + " (Raw " + df.format(sizeMB) + " MB)");

                    // run "ffmpeg -i filename.ts filenamec.mp4"
                    File convertedFile = new File(dir, outputFile.getName().replace(".ts", "c.mp4"));
                    try {
                        ProcessBuilder convertBuilder = new ProcessBuilder(
                            "ffmpeg",
                            "-i", outputFile.getAbsolutePath(),
                            convertedFile.getAbsolutePath()
                        );
                        convertBuilder.inheritIO();
                        Process convertProcess = convertBuilder.start();
                        convertProcess.waitFor();
                        // if the file is more than 1kb assume it worked and delete the original
                        if (convertedFile.exists() && convertedFile.length() > 1024) {
                            if (outputFile.delete()) {
                                double sizeMB2 = convertedFile.length() / 1024.0 / 1024.0;
                                DecimalFormat df2 = new DecimalFormat("#.##");
                                info(convertedFile.getName() + " (Converted " + df.format(sizeMB2) + " MB)");
                            }
                        }
                    } catch (Exception e) {
                        error("Failed to convert video: " + e.getMessage());
                    }


                }
                if (this.isActive()) this.toggle();
            });

        } catch (Exception e) {
            error("Failed to start ffmpeg. Make sure it's installed and in your PATH.");
            if (this.isActive()) this.toggle();
            return;
        }

    }

    @Override
    public void onDeactivate() {
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }
    }
}
