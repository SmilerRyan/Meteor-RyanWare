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
    private final SettingGroup sgGeneral   = settings.createGroup("General");
    private final SettingGroup sgProtected = settings.createGroup("Protected");
    private final SettingGroup sgLeaker    = settings.createGroup("Leaker");

    // --- General ---
    private final Setting<String> message = sgGeneral.add(new StringSetting.Builder()
        .name("death-message")
        .description("Message sent on death outside protected coords. Supports {x}, {y}, {z} placeholders and color codes (&).")
        .defaultValue("&fYour death location is: &cX:{x} Y:{y} Z:{z}")
        .build()
    );

    private final Setting<String> protectedMessage = sgGeneral.add(new StringSetting.Builder()
        .name("protected-death-message")
        .description("Message sent on death inside protected coords. Supports {x}, {y}, {z} and color codes (&).")
        .defaultValue("&fYour death location is: &chidden")
        .build()
    );

    // --- Protected ---
    private final Setting<List<String>> protectedCoords = sgProtected.add(new StringListSetting.Builder()
        .name("protected-coords")
        .description("Comma-separated coords to ignore. Format: x,z,radius")
        .defaultValue("0,0,1000")
        .build()
    );

    private final Setting<Boolean> debug = sgProtected.add(new BoolSetting.Builder()
        .name("protected-debug")
        .description("Shows info about protected coords, ONLY USE WHILST TESTING.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> protectedMessageToggle = sgProtected.add(new BoolSetting.Builder()
        .name("protected-message-toggle")
        .description("Block messages/leaks and show protected-death-message if you die in protected coords.")
        .defaultValue(true)
        .build()
    );

    // --- Leaker ---
    private final Setting<Boolean> leakerMode = sgLeaker.add(new BoolSetting.Builder()
        .name("leaker-mode")
        .description("Enable leaker mode to send coords into chat/commands instead of client-only messages.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> leakerMessages = sgLeaker.add(new StringListSetting.Builder()
        .name("leaker-messages")
        .description("Messages/commands sent in leaker mode. Lines starting with '/' are treated as commands.")
        .defaultValue("/msg friend I died at X:{x} Y:{y} Z:{z}")
        .build()
    );

    private final Setting<Integer> leakerDelay = sgLeaker.add(new IntSetting.Builder()
        .name("leaker-delay-ticks")
        .description("Delay between each leak message in ticks (20 ticks = 1 second). The first message is sent instantly.")
        .defaultValue(20)
        .min(0)
        .build()
    );

    private boolean wasAlive = true;
    private int leakIndex = 0;
    private int leakTimer = 0;
    private double lastDeathX, lastDeathY, lastDeathZ;
    private boolean leaking = false;

    public M1_DeathCoordsMessage() {
        super(RyanWare.CATEGORY_M1, RyanWare.modulePrefix + "M1-Death-Coords", "Sends coordinates on death.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        boolean isAlive = player.isAlive();

        // Detect death
        if (wasAlive && !isAlive) {
            lastDeathX = player.getX();
            lastDeathY = player.getY();
            lastDeathZ = player.getZ();

            boolean inProtected = isInProtected(lastDeathX, lastDeathZ);

            if (leakerMode.get()) {
                if (inProtected && protectedMessageToggle.get()) {
                    // block leaking, just send protected message
                    sendClientMessage(protectedMessage.get(), lastDeathX, lastDeathY, lastDeathZ);
                } else {
                    leakIndex = 0;
                    leakTimer = 0;
                    leaking = true;
                }
            } else {
                if (inProtected && protectedMessageToggle.get()) {
                    sendClientMessage(protectedMessage.get(), lastDeathX, lastDeathY, lastDeathZ);
                } else {
                    sendClientMessage(message.get(), lastDeathX, lastDeathY, lastDeathZ);
                }
            }
        }

        // Process leaking
        if (leaking && player != null) {
            if (leakIndex < leakerMessages.get().size()) {
                if (leakIndex == 0 || leakTimer >= leakerDelay.get()) {
                    String raw = leakerMessages.get().get(leakIndex);
                    String msg = format(raw, lastDeathX, lastDeathY, lastDeathZ);

                    if (!msg.isEmpty()) {
                        if (msg.startsWith("/")) {
                            MinecraftClient.getInstance().player.networkHandler.sendChatCommand(msg.substring(1));
                        } else {
                            MinecraftClient.getInstance().player.networkHandler.sendChatMessage(msg);
                        }
                    }

                    leakIndex++;
                    leakTimer = 0; // reset for next delay
                } else {
                    leakTimer++; // count ticks
                }
            } else {
                leaking = false; // finished
            }
        }

        wasAlive = isAlive;
    }

    private boolean isInProtected(double px, double pz) {
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
                        sendDebug(px, lastDeathY, pz, distance, radius, entry, distance <= radius);
                    }

                    if (distance <= radius) return true;
                }
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private void sendClientMessage(String raw, double px, double py, double pz) {
        if (raw == null) return;
        String msg = format(raw, px, py, pz);
        if (!msg.isEmpty()) {
            MinecraftClient.getInstance().player.sendMessage(Text.of(msg), false);
        }
    }

    private String format(String msg, double px, double py, double pz) {
        return msg
            .replace("{x}", String.valueOf((int) px))
            .replace("{y}", String.valueOf((int) py))
            .replace("{z}", String.valueOf((int) pz))
            .replace("&", "§");
    }

    private void sendDebug(double px, double py, double pz, double distance, double radius, String entry, boolean inside) {
        String debugMsg = inside ?
            String.format("[IN] %.1f, %.1f, %.1f | D %.1f R %.1f E %s", px, py, pz, distance, radius, entry)
            : String.format("[OUT] %.1f, %.1f, %.1f | D %.1f R %.1f E %s", px, py, pz, distance, radius, entry);

        MinecraftClient.getInstance().player.sendMessage(Text.of(debugMsg), false);
    }
}
