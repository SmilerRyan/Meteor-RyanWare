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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;
import smilerryan.ryanware.RyanWare;

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

    private final Set<ChunkPos> newChunks = new HashSet<>();
    private final Set<ChunkPos> oldChunks = new HashSet<>();
    private final Object chunksLock = new Object();
    
    private boolean lastClearButtonState = false;

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
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "NewChunks", "Highlights new and old chunks.");
    }

    @Override
    public void onActivate() {
        newChunks.clear();
        oldChunks.clear();
        lastClearButtonState = false;
    }

    @Override
    public void onDeactivate() {
        newChunks.clear();
        oldChunks.clear();
    }

    @EventHandler
    private void onChunkData(PacketEvent.Receive event) {
        if (!(event.packet instanceof ChunkDataS2CPacket packet)) return;

        ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());

        synchronized (chunksLock) {
            if (newChunks.contains(pos) || oldChunks.contains(pos)) return;

            boolean isNewChunk = detectNewChunkFromPacket(packet);

            if (isNewChunk) {
                newChunks.add(pos);
            } else {
                oldChunks.add(pos);
            }
        }
    }

    private boolean detectNewChunkFromPacket(ChunkDataS2CPacket packet) {
        ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
        if (mc.world != null) {
            ChunkPos spawnChunk = new ChunkPos(mc.world.getSpawnPos());
            double distanceFromSpawn = Math.sqrt(
                Math.pow(pos.x - spawnChunk.x, 2) +
                Math.pow(pos.z - spawnChunk.z, 2)
            );
            return distanceFromSpawn > 50;
        }
        return false;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        // Clear button handling
        boolean currentClearButtonState = false;
        if (currentClearButtonState && !lastClearButtonState) {
            synchronized (chunksLock) {
                newChunks.clear();
                oldChunks.clear();
            }
        }
        lastClearButtonState = currentClearButtonState;

        Set<ChunkPos> newChunksCopy;
        Set<ChunkPos> oldChunksCopy;

        synchronized (chunksLock) {
            newChunksCopy = new HashSet<>(newChunks);
            oldChunksCopy = new HashSet<>(oldChunks);
        }

        for (ChunkPos pos : newChunksCopy) renderChunk(event, pos, newChunkColor.get());
        for (ChunkPos pos : oldChunksCopy) renderChunk(event, pos, oldChunkColor.get());
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
