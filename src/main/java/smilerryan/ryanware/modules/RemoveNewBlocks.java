package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;

import smilerryan.ryanware.RyanWare;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class RemoveNewBlocks extends Module {

    private static final int RADIUS = 10;

    // snapshot: block -> was air last tick
    private final Map<BlockPos, Boolean> lastAirState = new HashMap<>();

    private final Queue<BlockPos> queue = new LinkedList<>();
    private final Set<BlockPos> queued = new HashSet<>();

    private BlockPos current = null;

    public RemoveNewBlocks() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Remove-New-Blocks",
            "Automatically breaks newly placed blocks around you."
        );
    }

    @Override
    public void onActivate() {
        lastAirState.clear();
        queue.clear();
        queued.clear();
        current = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        BlockPos center = mc.player.getBlockPos();

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {

                    BlockPos pos = center.add(x, y, z);

                    boolean isAir = mc.world.isAir(pos);
                    Boolean wasAir = lastAirState.get(pos);

                    if (wasAir == null) {
                        lastAirState.put(pos, isAir);
                        continue;
                    }

                    if (wasAir && !isAir) {
                        if (queued.add(pos)) {
                            queue.add(pos);
                        }
                    }

                    lastAirState.put(pos, isAir);
                }
            }
        }

        if (current == null) {
            current = queue.poll();
            if (current != null) queued.remove(current);
        }

        if (current == null) return;

        if (mc.world.isAir(current)) {
            current = null;
            return;
        }

        if (mc.player.getBlockPos().getManhattanDistance(current) > RADIUS) {
            current = null;
            return;
        }
        
        // break the block
        if (mc.interactionManager.isBreakingBlock()) {return;}
        mc.interactionManager.attackBlock(current, Direction.UP);
        mc.interactionManager.updateBlockBreakingProgress(current, Direction.UP);

    }
}