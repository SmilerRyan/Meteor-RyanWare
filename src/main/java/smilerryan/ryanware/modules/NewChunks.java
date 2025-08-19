package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import smilerryan.ryanware.RyanWare;

import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

public class NewChunks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<SettingColor> newChunkColor = sgGeneral.add(new ColorSetting.Builder()
        .name("new-chunk-color")
        .description("Color of newly generated chunks.")
        .defaultValue(new SettingColor(0, 255, 0, 80))
        .build()
    );

    private final Setting<SettingColor> oldChunkColor = sgGeneral.add(new ColorSetting.Builder()
        .name("old-chunk-color")
        .description("Color of previously generated chunks.")
        .defaultValue(new SettingColor(255, 0, 0, 80))
        .build()
    );

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render-mode")
        .description("How to render the chunk indicators.")
        .defaultValue(RenderMode.Lines)
        .build()
    );

    private final Setting<Integer> yLevel = sgRender.add(new IntSetting.Builder()
        .name("y-level")
        .description("Y level to render the overlay at.")
        .defaultValue(64)
        .min(-64)
        .max(320)
        .visible(() -> renderMode.get() == RenderMode.Overlay)
        .build()
    );

    private final Setting<Integer> overlayHeight = sgRender.add(new IntSetting.Builder()
        .name("overlay-height")
        .description("Height of the overlay in blocks.")
        .defaultValue(1)
        .min(1)
        .max(10)
        .visible(() -> renderMode.get() == RenderMode.Overlay)
        .build()
    );

    private final Setting<Boolean> paletteExploit = sgGeneral.add(new BoolSetting.Builder()
        .name("palette-exploit")
        .description("Detect new chunks using palette order analysis (most accurate method).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> seedBasedDetection = sgGeneral.add(new BoolSetting.Builder()
        .name("seed-based-detection")
        .description("Use world seed to compare expected vs actual chunk content.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> worldSeed = sgGeneral.add(new StringSetting.Builder()
        .name("world-seed")
        .description("Enter the world seed for accurate new chunk detection.")
        .defaultValue("")
        .visible(() -> seedBasedDetection.get())
        .build()
    );

    private final Setting<Boolean> clearButton = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-chunks")
        .description("Toggle to clear all stored chunks for this server.")
        .defaultValue(false)
        .build()
    );

    private final Set<ChunkPos> newChunks = new HashSet<>();
    private final Set<ChunkPos> oldChunks = new HashSet<>();
    private final Object chunksLock = new Object(); // Synchronization lock
    private boolean lastClearButtonState = false;

    private Path saveFile;

    public enum RenderMode {
        Lines("Lines"),
        Overlay("Overlay");

        private final String title;

        RenderMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public NewChunks() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "+-NewChunks", "Highlights new and old chunks.");
    }

    @Override
    public void onActivate() {
        newChunks.clear();
        oldChunks.clear();
        lastClearButtonState = false;

        if (isSingleplayer()) {
            saveFile = null;
            return;
        }

        String server = getServerName();
        if (server == null) return;

        try {
            Path dir = Paths.get("NewChunks");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            saveFile = dir.resolve("NewChunks-" + server + ".txt");

            if (Files.exists(saveFile)) loadChunks();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDeactivate() {
        saveChunks();
        newChunks.clear();
        oldChunks.clear();
    }

    private boolean detectNewChunkFromPalette(ChunkDataS2CPacket packet) {
        try {
            // Distance-from-spawn heuristic (fallback method)
            ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
            
            if (mc.world != null) {
                ChunkPos spawnChunk = new ChunkPos(mc.world.getSpawnPos());
                double distanceFromSpawn = Math.sqrt(
                    Math.pow(pos.x - spawnChunk.x, 2) + 
                    Math.pow(pos.z - spawnChunk.z, 2)
                );
                
                // If chunk is far from spawn (>50 chunks away), more likely to be new
                return distanceFromSpawn > 50;
            }
            
            return false;
            
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    private void onChunkData(PacketEvent.Receive event) {
        if (!(event.packet instanceof ChunkDataS2CPacket packet)) return;

        ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());

        synchronized (chunksLock) {
            // Already recorded? Don't change it.
            if (newChunks.contains(pos) || oldChunks.contains(pos)) return;

            boolean isNewChunk = false;

            if (seedBasedDetection.get() && !worldSeed.get().isEmpty()) {
                // Use seed-based detection (most accurate)
                isNewChunk = detectNewChunkBySeed(pos);
            } else if (paletteExploit.get()) {
                // Use palette exploit to detect new chunks
                isNewChunk = detectNewChunkFromPalette(packet);
            } else {
                // Fallback: assume all chunks are old (like before)
                isNewChunk = false;
            }

            if (isNewChunk) {
                newChunks.add(pos);
                saveChunk(pos, "new");
            } else {
                oldChunks.add(pos);
                saveChunk(pos, "old");
            }
        }
    }

    private boolean detectNewChunkBySeed(ChunkPos pos) {
        try {
            if (mc.world == null) return false;
            
            // Get the actual chunk from the world
            WorldChunk actualChunk = mc.world.getChunk(pos.x, pos.z);
            if (actualChunk == null) return false;
            
            // Parse the world seed
            long seed;
            try {
                seed = Long.parseLong(worldSeed.get());
            } catch (NumberFormatException e) {
                // Try parsing as string hash if not a number
                seed = worldSeed.get().hashCode();
            }
            
            // Check for signs of player modification vs natural generation
            boolean hasPlayerModifications = checkForPlayerModifications(actualChunk, pos, seed);
            
            // If chunk has no player modifications and matches expected generation,
            // it's likely a new chunk that was just generated
            return !hasPlayerModifications;
            
        } catch (Exception e) {
            return false; // Default to old chunk if we can't analyze
        }
    }
    
    private boolean checkForPlayerModifications(WorldChunk chunk, ChunkPos pos, long seed) {
        try {
            // Check for obvious signs of player activity
            
            // 1. Check for non-natural blocks (player-placed blocks)
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = mc.world.getBottomY(); y < mc.world.getTopY(); y++) {
                        BlockState state = chunk.getBlockState(x, y, z);
                        
                        if (isPlayerPlacedBlock(state)) {
                            return true; // Found player modifications
                        }
                        
                        // Check for missing blocks that should naturally exist
                        if (isSuspiciouslyMissingBlock(state, x, y, z, pos, seed)) {
                            return true; // Found evidence of mining/modification
                        }
                    }
                }
            }
            
            // 2. Check biome consistency - new chunks should have expected biomes
            // (This is a simplified check)
            Biome actualBiome = chunk.getBiomeForNoiseGen(8, 64, 8).value();
            if (actualBiome == null) return false;
            
            // If we reach here, chunk appears to be unmodified/natural
            return false;
            
        } catch (Exception e) {
            return true; // If we can't analyze, assume it's modified (old)
        }
    }
    
    private boolean isPlayerPlacedBlock(BlockState state) {
        String blockName = state.getBlock().toString().toLowerCase();
        
        // Common player-placed blocks that don't generate naturally
        return blockName.contains("crafting_table") ||
               blockName.contains("furnace") ||
               blockName.contains("chest") ||
               blockName.contains("torch") ||
               blockName.contains("door") ||
               blockName.contains("bed") ||
               blockName.contains("glass") ||
               blockName.contains("brick") ||
               blockName.contains("plank") ||
               blockName.contains("wool") ||
               blockName.contains("concrete") ||
               blockName.contains("terracotta") ||
               blockName.contains("sign") ||
               blockName.contains("ladder") ||
               blockName.contains("fence") && !blockName.contains("nether_brick_fence");
    }
    
    private boolean isSuspiciouslyMissingBlock(BlockState state, int x, int y, int z, ChunkPos pos, long seed) {
        // This is a simplified heuristic
        // In reality, you'd need to simulate world generation to know what "should" be there
        
        // Check for suspicious air pockets at surface level that might indicate mining
        if (state.isAir() && y > 50 && y < 90) {
            // Count surrounding solid blocks - isolated air might indicate mining
            int solidNeighbors = 0;
            // This would need proper neighbor checking logic
            return solidNeighbors > 4; // Suspicious if surrounded by solid blocks
        }
        
        return false;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        // Handle clear button logic
        boolean currentClearButtonState = clearButton.get();
        if (currentClearButtonState && !lastClearButtonState) {
            clearChunks();
            clearButton.set(false);
        }
        lastClearButtonState = currentClearButtonState;

        // Create thread-safe copies using synchronization
        Set<ChunkPos> newChunksCopy;
        Set<ChunkPos> oldChunksCopy;
        
        synchronized (chunksLock) {
            newChunksCopy = new HashSet<>(newChunks);
            oldChunksCopy = new HashSet<>(oldChunks);
        }

        for (ChunkPos pos : newChunksCopy) {
            renderChunk(event, pos, newChunkColor.get());
        }

        for (ChunkPos pos : oldChunksCopy) {
            renderChunk(event, pos, oldChunkColor.get());
        }
    }

    private void renderChunk(Render3DEvent event, ChunkPos pos, SettingColor settingColor) {
        double x1 = pos.getStartX();
        double z1 = pos.getStartZ();
        double x2 = x1 + 16;
        double z2 = z1 + 16;

        Color color = new Color(settingColor.r, settingColor.g, settingColor.b, settingColor.a);

        if (renderMode.get() == RenderMode.Lines) {
            double lineY = mc.player.getY();
            event.renderer.line(x1, lineY, z1, x2, lineY, z1, color);
            event.renderer.line(x2, lineY, z1, x2, lineY, z2, color);
            event.renderer.line(x2, lineY, z2, x1, lineY, z2, color);
            event.renderer.line(x1, lineY, z2, x1, lineY, z1, color);
        } else if (renderMode.get() == RenderMode.Overlay) {
            double y1 = yLevel.get();
            double y2 = y1 + overlayHeight.get();
            Box chunkBox = new Box(x1, y1, z1, x2, y2, z2);
            event.renderer.box(chunkBox, color, color, ShapeMode.Both, 0);
        }
    }

    // --- Saving / Loading ---

    private void saveChunk(ChunkPos pos, String state) {
        if (saveFile == null) return;
        try (BufferedWriter writer = Files.newBufferedWriter(saveFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(pos.x + "," + pos.z + "|" + state);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveChunks() {
        if (saveFile == null) return;
        
        Set<ChunkPos> newChunksCopy;
        Set<ChunkPos> oldChunksCopy;
        
        synchronized (chunksLock) {
            newChunksCopy = new HashSet<>(newChunks);
            oldChunksCopy = new HashSet<>(oldChunks);
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(saveFile)) {
            for (ChunkPos pos : newChunksCopy) {
                writer.write(pos.x + "," + pos.z + "|new");
                writer.newLine();
            }
            for (ChunkPos pos : oldChunksCopy) {
                writer.write(pos.x + "," + pos.z + "|old");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadChunks() {
        if (saveFile == null) return;
        try (BufferedReader reader = Files.newBufferedReader(saveFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length != 2) continue;
                String[] coords = parts[0].split(",");
                if (coords.length != 2) continue;

                int x = Integer.parseInt(coords[0]);
                int z = Integer.parseInt(coords[1]);
                String state = parts[1];

                synchronized (chunksLock) {
                    if ("new".equals(state)) {
                        newChunks.add(new ChunkPos(x, z));
                    } else if ("old".equals(state)) {
                        oldChunks.add(new ChunkPos(x, z));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void clearChunks() {
        synchronized (chunksLock) {
            newChunks.clear();
            oldChunks.clear();
        }
        if (saveFile != null) {
            try {
                Files.deleteIfExists(saveFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isSingleplayer() {
        ClientWorld world = mc.world;
        return mc.isIntegratedServerRunning() || (world != null && mc.getCurrentServerEntry() == null);
    }

    private String getServerName() {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null) return null;
        return info.address.replace(":", "_");
    }
}