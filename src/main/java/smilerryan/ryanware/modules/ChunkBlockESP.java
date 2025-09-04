package smilerryan.ryanware.modules;

import smilerryan.ryanware.RyanWare;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;

import java.util.*;

public class ChunkBlockESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBlocks = settings.createGroup("Blocks");
    private final SettingGroup sgEntities = settings.createGroup("Entities");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocksToTrack = sgBlocks.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks to highlight in chunks.")
            .defaultValue(Collections.emptyList())
            .build()
    );

    private final Setting<List<String>> entitiesToTrack = sgEntities.add(new StringListSetting.Builder()
            .name("entities")
            .description("Entity types to highlight in chunks (e.g., zombie, skeleton, creeper).")
            .defaultValue(Collections.emptyList())
            .build()
    );

    private final Setting<SettingColor> overlayColor = sgRender.add(new ColorSetting.Builder()
            .name("overlay-color")
            .description("Color of chunk overlay.")
            .defaultValue(new SettingColor(0, 255, 0, 50))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Color of chunk lines.")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .build()
    );

    private final Map<ChunkPos, Set<BlockPos>> detectedBlocks = new HashMap<>();
    private final Map<ChunkPos, Set<Integer>> detectedEntities = new HashMap<>();
    private final Object lock = new Object();

    // Caches to reduce repeated notifications
    private final Set<BlockPos> notifiedBlocks = new HashSet<>();
    private final Set<Integer> notifiedEntities = new HashSet<>();

    public ChunkBlockESP() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "ChunkBlockESP", "Highlights chunks with specific blocks/entities.");
    }

    @Override
    public void onDeactivate() {
        synchronized (lock) {
            detectedBlocks.clear();
            detectedEntities.clear();
            notifiedBlocks.clear();
            notifiedEntities.clear();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        synchronized (lock) {
            detectedBlocks.clear();
            detectedEntities.clear();

            BlockPos playerPos = mc.player.getBlockPos();

            // Scan blocks in a 64-block radius cube
            for (BlockPos pos : BlockPos.iterate(
                    playerPos.add(-64, -64, -64),
                    playerPos.add(64, 64, 64))) {
                Block block = mc.world.getBlockState(pos).getBlock();
                if (blocksToTrack.get().contains(block)) {
                    ChunkPos chunkPos = new ChunkPos(pos);
                    detectedBlocks.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(pos);
                    if (!notifiedBlocks.contains(pos)) {
                        notifiedBlocks.add(pos);
                        notifyFoundBlock(pos, block);
                    }
                }
            }

            // Scan entities
            for (Entity entity : mc.world.getEntities()) {
                String entityName = entity.getType().getName().getString().toLowerCase();
                for (String trackedEntityName : entitiesToTrack.get()) {
                    if (entityName.contains(trackedEntityName.toLowerCase())) {
                        ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
                        detectedEntities.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(entity.getId());
                        if (!notifiedEntities.contains(entity.getId())) {
                            notifiedEntities.add(entity.getId());
                            notifyFoundEntity(entity);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void notifyFoundBlock(BlockPos pos, Block block) {

    }

    private void notifyFoundEntity(Entity entity) {

    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null) return;

        synchronized (lock) {
            for (ChunkPos pos : detectedBlocks.keySet()) {
                renderChunk(event, pos, detectedBlocks.get(pos));
            }
            for (ChunkPos pos : detectedEntities.keySet()) {
                renderChunk(event, pos, null);
            }
        }
    }

    private void renderChunk(Render3DEvent event, ChunkPos chunkPos, Set<BlockPos> blockPositions) {
        double x1 = chunkPos.getStartX();
        double z1 = chunkPos.getStartZ();
        double x2 = x1 + 16;
        double z2 = z1 + 16;
        double y1 = mc.world.getBottomY();
        double y2 = mc.world.getTopY();

        // Box overlay
        Box box = new Box(x1, y1, z1, x2, y2, z2);
        event.renderer.box(box, overlayColor.get(), overlayColor.get(), ShapeMode.Both, 0);

        // Chunk lines
        event.renderer.line(x1, y1, z1, x2, y1, z1, lineColor.get());
        event.renderer.line(x2, y1, z1, x2, y1, z2, lineColor.get());
        event.renderer.line(x2, y1, z2, x1, y1, z2, lineColor.get());
        event.renderer.line(x1, y1, z2, x1, y1, z1, lineColor.get());

        // Indicators for blocks
        if (blockPositions != null) {
            for (BlockPos bPos : blockPositions) {
                Box bBox = new Box(bPos.getX(), bPos.getY(), bPos.getZ(),
                        bPos.getX() + 1, bPos.getY() + 1, bPos.getZ() + 1);
                event.renderer.box(bBox, lineColor.get(), lineColor.get(), ShapeMode.Both, 0);
            }
        }
    }
}
