package smilerryan.ryanware.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import smilerryan.ryanware.modules_standard.Settings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class Command_Note extends Command {

    public Command_Note() {
        super(smilerryan.ryanware.RyanWare.commandPrefix + "note", "Manage notes with customizable paths.");
    }

    private String getCurrentPath() {
        Settings settings = Modules.get().get(Settings.class);
        if (settings == null) return "";
        String path = settings.s_Note_Command_Path.get();
        if (path.startsWith("~")) {
            path = System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {

        builder.then(literal("path")
            .then(argument("filepath", greedyString())
                .executes(context -> {
                    String path = getString(context, "filepath");
                    Settings settings = Modules.get().get(Settings.class);
                    if (settings != null) {
                        settings.s_Note_Command_Path.set(path);
                        info("Note path set to: " + getCurrentPath());
                    } else {
                        error("Settings module not found!");
                    }
                    return SINGLE_SUCCESS;
                })));

        builder.then(literal("open")
            .executes(context -> {
                try {
                    File file = new File(getCurrentPath());
                    if (!file.exists()) {
                        return SINGLE_SUCCESS;
                    }
                    Util.getOperatingSystem().open(file);
                } catch (Exception e) {
                    error("Failed to open file: " + e.getMessage());
                }
                return SINGLE_SUCCESS;
            }));

        builder.then(argument("content", greedyString())
            .executes(context -> {
                String content = getString(context, "content");
                String path = getCurrentPath();

                if (path.isEmpty()) {
                    error("Notes path not set!");
                    return SINGLE_SUCCESS;
                }

                try {
                    // Create parent directories if they don't exist
                    Path filePath = Paths.get(path);
                    Path parentDir = filePath.getParent();
                    
                    // Only create directories if parent exists (not null)
                    if (parentDir != null) {
                        Files.createDirectories(parentDir);
                    }

                    // Format the note with current timestamp
                    String dateFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
                    String formattedNote = String.format("[%s] %s%n", dateFormatted, content);

                    // Write to file
                    try (FileWriter writer = new FileWriter(path, true)) {
                        writer.write(formattedNote);
                    }

                    info("Note added successfully");
                } catch (IOException e) {
                    error("Failed to write note: " + e.getMessage());
                }
                return SINGLE_SUCCESS;
            }));
    }

    private String getString(CommandContext<CommandSource> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private void info(String message) {
        mc.player.sendMessage(Text.literal("§a[Note] " + message), false);
    }

    private void error(String message) {
        mc.player.sendMessage(Text.literal("§c[Note] " + message), false);
    }
}