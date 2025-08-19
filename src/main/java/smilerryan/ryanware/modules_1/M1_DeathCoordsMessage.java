package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import java.util.List;

public class M1_DeathCoordsMessage extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
        .name("death-message")
        .description("Message sent on death. Supports {x}, {y}, {z} placeholders and color codes (&).")
        .defaultValue("&fYour death location is: &cX:{x} Y:{y} Z:{z}")
        .build()
    );

    private final Setting<List<String>> protectedCoords = sgGeneral.add(new StringListSetting.Builder()
        .name("protected-coords")
        .description("Comma-separated coords to ignore. Format: x,z,radius")
        .defaultValue("0,0,1000")
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("protected-coords-debug")
        .description("Shows info about protected coords, ONLY USE WHILST TESTING.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> protectedMessageToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("protected-message-toggle")
        .description("Send a different message if you die in protected coords.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> protectedMessage = sgGeneral.add(new StringSetting.Builder()
        .name("protected-death-message")
        .description("Message sent when dying inside protected coords. Supports {x}, {y}, {z} and color codes (&).")
        .defaultValue("&fYour death location is: &chidden")
        .build()
    );

    private boolean wasAlive = true;

    public M1_DeathCoordsMessage() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "M1-Death-Coords", "Sends coordinates to your chat on death.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        boolean isAlive = player.isAlive();
        if (wasAlive && !isAlive) {
            double deathX = player.getX();
            double deathY = player.getY();
            double deathZ = player.getZ();
            sendDeathMessage(deathX, deathY, deathZ);
        }
        wasAlive = isAlive;
    }

    private void sendDeathMessage(double px, double py, double pz) {
        boolean inRange = false;

        for (String entry : protectedCoords.get()) {
            try {
                String[] parts = entry.split(",");
                if (parts.length == 3) {
                    double wx = Double.parseDouble(parts[0]);
                    double wz = Double.parseDouble(parts[1]);
                    double radius = Double.parseDouble(parts[2]);

                    double dx = px - wx;
                    double dz = pz - wz;
                    double distance = Math.sqrt(dx*dx + dz*dz);

                    if (debug.get()) {
                        sendDebug(px, py, pz, distance, radius, entry, distance <= radius);
                    }

                    if (distance <= radius) inRange = true;
                }
            } catch (NumberFormatException ignored) {}
        }

        String finalMessage = null;
        if (inRange) {
            if (protectedMessageToggle.get()) {
                finalMessage = protectedMessage.get();
            }
        } else {
            finalMessage = message.get();
        }

        if (finalMessage != null) {
            finalMessage = finalMessage
                .replace("{x}", String.valueOf((int) px))
                .replace("{y}", String.valueOf((int) py))
                .replace("{z}", String.valueOf((int) pz))
                .replace("&", "§");
            MinecraftClient.getInstance().player.sendMessage(Text.of(finalMessage), false);
        }

    }

    private void sendDebug(double px, double py, double pz, double distance, double radius, String entry, boolean inside) {
        String debugMsg = inside ?
            String.format("[IN] %.1f, %.1f, %.1f | D %.1f R %.1f E %s", px, py, pz, distance, radius, entry)
            : String.format("[OUT] %.1f, %.1f, %.1f | D %.1f R %.1f E %s", px, py, pz, distance, radius, entry);

        MinecraftClient.getInstance().player.sendMessage(Text.of(debugMsg), false);
    }
}
