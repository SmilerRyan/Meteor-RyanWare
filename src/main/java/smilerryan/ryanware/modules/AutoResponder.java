package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;

import java.util.List;
import java.util.Random;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.ByteBuffer;
import java.time.Instant;

public class AutoResponder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> triggerResponses = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-responses")
        .description("List of trigger=response pairs.")
        .defaultValue("yo=Yo.", "To cancel this request=Accept.")
        .build()
    );

    private final Setting<Boolean> fakeBaconwareInstallation = sgGeneral.add(new BoolSetting.Builder()
        .name("fake-baconware-installation")
        .description("Enables the fake Baconware Coord Leakers (1ACT, 2ACT, 69X, 3ACT for /ping test) within the auto-responder.")
        .defaultValue(false)
        .build()
    );

    private final Setting<CoordMode> coordModeSetting = sgGeneral.add(new EnumSetting.Builder<CoordMode>()
        .name("coordinate-mode")
        .description("Choose the coordinate display mode for the auto-responder.")
        .defaultValue(CoordMode.ZERO_Y_ZERO)
        .build()
    );

    public enum CoordMode {
        ZERO_Y_ZERO("0, Real Y, 0"),
        RANDOM_COORDS("'Random' X/Z Coordinates"),
        REAL_COORDS("Real Coordinates (X/Y/Z)");
    
        private final String displayName;
    
        CoordMode(String displayName) {
            this.displayName = displayName;
        }
    
        @Override
        public String toString() {
            return displayName;
        }
    }

    private long secretRandomSeedComponent; 
    private final int SECRET_SEED_UPDATE_INTERVAL_SECONDS = 3600; // Update every hour

    @Override
    public void onActivate() {
        super.onActivate();
        updateSecretSeedComponent(); 
    }

    private void updateSecretSeedComponent() {
        secretRandomSeedComponent = new Random().nextLong(); 
    }

    public AutoResponder() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-Auto-Responder", "Reads and responds to chat.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {

        String message = event.getMessage().getString();
        String responseToSend = null;

        // Actual Auto Responder Logic
        for (String pair : triggerResponses.get()) {
            int idx = pair.indexOf('=');
            if (idx == -1) {continue;}
            String trigger = pair.substring(0, idx);
            String response = pair.substring(idx + 1);
            if (message.contains(trigger)) {
                responseToSend = response;
                break;
            }
        }

        // Baconware Fake 1ACT 2ACT 3ACT 69X Commands
        if (responseToSend == null && fakeBaconwareInstallation.get()) {
            if (message.contains("1ACT") || message.contains("2ACT") || message.contains("69X") || message.contains("3ACT")) {
                
                // Get player position and dimension
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player == null || mc.world == null) {return;}
                BlockPos playerPos = mc.player.getBlockPos();

                CoordMode currentMode = coordModeSetting.get();
                int playerX = 0, playerY = 0, playerZ = 0;
                switch (currentMode) {
                    case ZERO_Y_ZERO:
                        playerX = 0;
                        playerY = playerPos.getY();
                        playerZ = 0;
                        break;
                    case RANDOM_COORDS:
                        int gridSize = 10000;
                        long quantizedX = playerPos.getX() / gridSize;
                        long quantizedZ = playerPos.getZ() / gridSize;
                        long baseSeed = (quantizedX * 73856093L) ^ (quantizedZ * 83492791L);
                        long finalSeed = baseSeed ^ secretRandomSeedComponent; 
                        Random randomOrigin = new Random(finalSeed);
                        int baseRandomX = randomOrigin.nextInt(2000001) - 1000000;
                        int baseRandomZ = randomOrigin.nextInt(2000001) - 1000000;
                        int relativeOffsetX = Math.floorMod(playerPos.getX(), gridSize);
                        int relativeOffsetZ = Math.floorMod(playerPos.getZ(), gridSize);
                        playerX = baseRandomX + relativeOffsetX;
                        playerY = playerPos.getY();
                        playerZ = baseRandomZ + relativeOffsetZ;
                        break;
                    case REAL_COORDS:
                        playerX = playerPos.getX();
                        playerY = playerPos.getY();
                        playerZ = playerPos.getZ();
                        break;
                }

                RegistryEntry<DimensionType> dimensionEntry = mc.world.getDimensionEntry();
                String dimensionIdentifierPath = dimensionEntry.getKey().map(key -> key.getValue().getPath()).orElse("unknown");

                String fakeCoordString1 = + playerX + " " + playerY + " " + playerZ + ", D: " + dimensionIdentifierPath + " 逸ꒋ휵";
                String fakeCoordString2 = "X: " + playerX + " Y:" + playerY + " Z:" + playerZ;
                String fakeResponse1 = "/msg Hyperdevelopment " + fakeCoordString1;
                String fakeResponse2 = "/msg yaud_ " + fakeCoordString1;

                // Send fake responses based on message content
                if (message.contains("1ACT")) {responseToSend = fakeResponse1;}
                if (message.contains("2ACT")) {responseToSend = fakeResponse2;}
                if (message.contains("69X"))  {responseToSend = fakeCoordString2;}

                // 3ACT is not a baconware exclusive, it's just for testing the fake coords
                if (message.contains("3ACT"))  {responseToSend = "/ping " + fakeCoordString2;}

            }
        }

        if (responseToSend != null) {
            info(responseToSend);
            if (responseToSend.startsWith("/")) {
                MinecraftClient.getInstance().player.networkHandler.sendCommand(responseToSend.substring(1));
            } else {
                MinecraftClient.getInstance().getNetworkHandler().sendChatMessage(responseToSend);
            }
        }
    }
}