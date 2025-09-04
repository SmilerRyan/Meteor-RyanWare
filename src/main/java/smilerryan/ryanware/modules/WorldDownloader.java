package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.*;
import net.minecraft.util.math.ChunkPos;
import smilerryan.ryanware.RyanWare;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class WorldDownloader extends Module {
    private final Set<ChunkPos> savedChunks = new HashSet<>();
    private File saveDir;
    private File regionDir;

    public WorldDownloader() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "WorldDownloader", "Saves loaded chunks as a valid singleplayer world.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null || mc.getServer() != null) {
            error("Not in multiplayer world.");
            toggle();
            return;
        }

        ServerInfo server = mc.getCurrentServerEntry();
        String name = (server != null) ? server.address.replace(":", "_") : "unknown_server";
        saveDir = new File(mc.runDirectory, "saves/downloaded_" + name);
        regionDir = new File(saveDir, "region");
        regionDir.mkdirs();

        saveLevelDat();

        info("World download started: " + saveDir.getAbsolutePath());
    }

    @Override
    public void onDeactivate() {
        savedChunks.clear();
        info("World download stopped.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        int playerChunkX = mc.player.getChunkPos().x;
        int playerChunkZ = mc.player.getChunkPos().z;

        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int cx = playerChunkX + dx;
                int cz = playerChunkZ + dz;
                ChunkPos pos = new ChunkPos(cx, cz);

                if (savedChunks.contains(pos)) continue;

                saveEmptyChunk(pos);
                savedChunks.add(pos);
            }
        }
    }

    private void saveEmptyChunk(ChunkPos pos) {
        try {
            NbtCompound root = new NbtCompound();
            NbtCompound level = new NbtCompound();

            level.putInt("xPos", pos.x);
            level.putInt("zPos", pos.z);
            level.putLong("LastUpdate", System.currentTimeMillis());
            level.putString("Status", "full");
            level.putInt("DataVersion", 3465); // Minecraft 1.20.6

            // Create empty air section at Y=0
            NbtCompound section = new NbtCompound();
            section.putByte("Y", (byte) 0);

            NbtList palette = new NbtList();
            NbtCompound air = new NbtCompound();
            air.putString("Name", "minecraft:air");
            palette.add(air);

            section.put("Palette", palette);
            section.putLongArray("BlockStates", new long[256]); // all air blocks

            NbtList sections = new NbtList();
            sections.add(section);

            level.put("Sections", sections);

            root.put("Level", level);
            root.putInt("DataVersion", 3465);

            saveToRegionFile(pos, root);
        } catch (IOException e) {
            error("Failed to save chunk: " + pos);
        }
    }

    private void saveToRegionFile(ChunkPos pos, NbtCompound chunkData) throws IOException {
        int regionX = pos.x >> 5;
        int regionZ = pos.z >> 5;
        File file = new File(regionDir, "r." + regionX + "." + regionZ + ".mca");

        // Initialize file with 8KB header if new or empty
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        if (raf.length() < 8192) {
            raf.setLength(8192);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);
        DataOutputStream dos = new DataOutputStream(gos);
        NbtIo.write(chunkData, dos);
        dos.flush();
        gos.close();

        byte[] data = baos.toByteArray();
        int length = data.length + 1; // +1 for compression type byte
        int sectorCount = (length + 4095) / 4096;
        int offset;

        // Find free sectors or append to end
        offset = (int) (raf.length() / 4096);

        // Write location entry in header
        int headerOffset = 4 * ((pos.x & 31) + (pos.z & 31) * 32);
        raf.seek(headerOffset);
        raf.writeByte((offset >> 16) & 0xFF);
        raf.writeByte((offset >> 8) & 0xFF);
        raf.writeByte(offset & 0xFF);
        raf.writeByte(sectorCount);

        // Write timestamp
        raf.seek(4096 + headerOffset);
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        raf.writeInt(timestamp);

        // Write chunk data
        raf.seek(offset * 4096L);
        raf.writeInt(data.length);
        raf.writeByte(2); // GZIP compression
        raf.write(data);

        raf.close();
    }

    private void saveLevelDat() {
        try {
            File file = new File(saveDir, "level.dat");
            file.getParentFile().mkdirs();

            NbtCompound data = new NbtCompound();
            data.putLong("RandomSeed", new Random().nextLong());
            data.putString("generatorName", "default");
            data.putBoolean("MapFeatures", true);
            data.putInt("GameType", 1);
            data.putInt("SpawnX", 0);
            data.putInt("SpawnY", 80);
            data.putInt("SpawnZ", 0);
            data.putLong("Time", 1);
            data.putLong("LastPlayed", System.currentTimeMillis());
            data.putString("LevelName", "Downloaded World");
            data.putInt("version", 19133);
            data.putInt("Difficulty", 1);
            data.putBoolean("initialized", true);

            NbtCompound root = new NbtCompound();
            root.put("Data", data);
            root.putInt("DataVersion", 3465);

            try (FileOutputStream fos = new FileOutputStream(file);
                 GZIPOutputStream gos = new GZIPOutputStream(fos);
                 DataOutputStream dos = new DataOutputStream(gos)) {
                NbtIo.write(root, dos);
            }
        } catch (IOException e) {
            error("Failed to save level.dat");
        }
    }
}
