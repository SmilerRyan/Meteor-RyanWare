package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.*;

import smilerryan.ryanware.RyanWare;

public class AutoChestMover extends Module {

    private final Setting<String> sourceCoords = settings.getDefaultGroup().add(new StringSetting.Builder()
        .name("source-coordinates")
        .description("Source coordinates (x,y,z)")
        .defaultValue("0,64,0")
        .build()
    );

    private final Setting<String> destCoords = settings.getDefaultGroup().add(new StringSetting.Builder()
        .name("destination-coordinates")
        .description("Destination coordinates (x,y,z)")
        .defaultValue("100,64,100")
        .build()
    );

    private final Setting<Double> sourceRadius = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("source-radius")
        .description("Radius to search for chests at source location.")
        .defaultValue(10)
        .min(1)
        .sliderMax(50)
        .build()
    );

    private final Setting<Double> destRadius = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("destination-radius")
        .description("Radius to search for chests at destination location.")
        .defaultValue(10)
        .min(1)
        .sliderMax(50)
        .build()
    );

    private final Setting<Double> walkSpeed = settings.getDefaultGroup().add(new DoubleSetting.Builder()
        .name("walk-speed")
        .description("Speed multiplier for walking between locations.")
        .defaultValue(1.0)
        .min(0.1)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Integer> waitTicks = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("wait-ticks")
        .description("Ticks to wait when no destination chests are available.")
        .defaultValue(60)
        .min(10)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> collectFloorItems = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("collect-floor-items")
        .description("Collect items on the floor near source location.")
        .defaultValue(true)
        .build()
    );

    private enum State {
        GOING_TO_SOURCE,
        COLLECTING_ITEMS,
        GOING_TO_DEST,
        DEPOSITING_ITEMS,
        WAITING_FOR_SPACE
    }

    private State currentState = State.GOING_TO_SOURCE;
    private BlockPos sourcePos;
    private BlockPos destPos;
    private final Set<BlockPos> checkedChests = new HashSet<>();
    private final Set<BlockPos> emptyChests = new HashSet<>();
    private final Queue<BlockPos> chestsToCheck = new ArrayDeque<>();
    private BlockPos currentTarget = null;
    private int waitCounter = 0;
    private final Random rand = new Random();

    public AutoChestMover() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "AutoChestMover", "Automatically moves items from source chests to destination chests.");
    }

    @Override
    public void onActivate() {
        parseCoordinates();
        currentState = State.GOING_TO_SOURCE;
        checkedChests.clear();
        emptyChests.clear();
        chestsToCheck.clear();
        currentTarget = null;
        waitCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        switch (currentState) {
            case GOING_TO_SOURCE:
                if (moveToPosition(sourcePos)) {
                    currentState = State.COLLECTING_ITEMS;
                    findChestsAtSource();
                    if (collectFloorItems.get()) {
                        collectNearbyItems();
                    }
                }
                break;

            case COLLECTING_ITEMS:
                if (isInventoryFull() || !hasMoreItemsToCollect()) {
                    if (hasItemsInInventory()) {
                        currentState = State.GOING_TO_DEST;
                    } else {
                        toggle(); // No more items to move
                    }
                } else {
                    collectFromNextChest();
                    if (collectFloorItems.get()) {
                        collectNearbyItems();
                    }
                }
                break;

            case GOING_TO_DEST:
                if (moveToPosition(destPos)) {
                    currentState = State.DEPOSITING_ITEMS;
                }
                break;

            case DEPOSITING_ITEMS:
                if (!hasItemsInInventory()) {
                    currentState = State.GOING_TO_SOURCE;
                } else {
                    if (!depositItems()) {
                        currentState = State.WAITING_FOR_SPACE;
                        waitCounter = 0;
                    }
                }
                break;

            case WAITING_FOR_SPACE:
                waitCounter++;
                if (waitCounter >= waitTicks.get()) {
                    currentState = State.DEPOSITING_ITEMS;
                    waitCounter = 0;
                }
                break;
        }
    }

    private void parseCoordinates() {
        try {
            String[] sourceParts = sourceCoords.get().split(",");
            sourcePos = new BlockPos(
                Integer.parseInt(sourceParts[0].trim()),
                Integer.parseInt(sourceParts[1].trim()),
                Integer.parseInt(sourceParts[2].trim())
            );

            String[] destParts = destCoords.get().split(",");
            destPos = new BlockPos(
                Integer.parseInt(destParts[0].trim()),
                Integer.parseInt(destParts[1].trim()),
                Integer.parseInt(destParts[2].trim())
            );
        } catch (Exception e) {
            sourcePos = new BlockPos(0, 64, 0);
            destPos = new BlockPos(100, 64, 100);
        }
    }

    private boolean moveToPosition(BlockPos target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d targetPos = Vec3d.ofCenter(target);
        double distance = playerPos.distanceTo(targetPos);

        if (distance < 2.0) {
            return true;
        }

        double dx = targetPos.x - playerPos.x;
        double dz = targetPos.z - playerPos.z;
        double dy = targetPos.y - playerPos.y;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double speed = 0.1 * walkSpeed.get() + rand.nextDouble() * 0.02;

        if (distXZ > 0.5) {
            double angle = Math.atan2(dz, dx);
            double vx = Math.cos(angle) * speed;
            double vz = Math.sin(angle) * speed;
            mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);

            // Look towards target
            float yaw = (float) Math.toDegrees(angle) - 90f;
            mc.player.setYaw(smooth(mc.player.getYaw(), yaw));
        }

        if (dy > 0.5 && mc.player.getVelocity().y <= 0.01) {
            mc.player.jump();
        }

        return false;
    }

    private void findChestsAtSource() {
        chestsToCheck.clear();
        int r = (int) Math.ceil(sourceRadius.get());
        BlockPos.Mutable mut = new BlockPos.Mutable();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    mut.set(sourcePos.getX() + x, sourcePos.getY() + y, sourcePos.getZ() + z);
                    if (isChestBlock(mut) && !emptyChests.contains(mut)) {
                        chestsToCheck.add(mut.toImmutable());
                    }
                }
            }
        }
    }

    private void collectFromNextChest() {
        if (chestsToCheck.isEmpty()) {
            findChestsAtSource(); // Refresh chest list
            if (chestsToCheck.isEmpty()) return;
        }

        BlockPos chestPos = chestsToCheck.poll();
        if (chestPos == null) return;

        if (!isChestBlock(chestPos)) return;

        double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(chestPos));
        if (distance > 4.5) {
            // Move closer to chest
            moveToPosition(chestPos);
            chestsToCheck.add(chestPos); // Re-add to queue
            return;
        }

        // Look at and interact with chest
        lookAt(chestPos);
        
        BlockHitResult hitResult = new BlockHitResult(
            Vec3d.ofCenter(chestPos),
            Direction.UP,
            chestPos,
            false
        );

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

        // Mark as checked and try to take items
        checkedChests.add(chestPos);
        
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            takeAllItemsFromContainer();
        }
    }

    private void takeAllItemsFromContainer() {
        if (mc.player.currentScreenHandler == null) return;

        // Take items from container slots (usually first 27 or 54 slots for chests)
        int containerSize = mc.player.currentScreenHandler.slots.size() - 36; // Exclude player inventory
        
        for (int i = 0; i < containerSize; i++) {
            if (isInventoryFull()) break;
            
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
            }
        }

        mc.player.closeHandledScreen();
    }

    private boolean depositItems() {
        List<BlockPos> destChests = findDestinationChests();
        
        if (destChests.isEmpty()) {
            return false; // No chests available
        }

        for (BlockPos chestPos : destChests) {
            double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(chestPos));
            if (distance > 4.5) {
                moveToPosition(chestPos);
                continue;
            }

            lookAt(chestPos);
            
            BlockHitResult hitResult = new BlockHitResult(
                Vec3d.ofCenter(chestPos),
                Direction.UP,
                chestPos,
                false
            );

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);

            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                boolean deposited = depositAllItemsToContainer();
                mc.player.closeHandledScreen();
                
                if (deposited && !hasItemsInInventory()) {
                    return true;
                }
            }
        }

        return false; // Couldn't deposit all items
    }

    private boolean depositAllItemsToContainer() {
        if (mc.player.currentScreenHandler == null) return false;

        boolean depositedAny = false;
        
        // Deposit items from player inventory (slots 36+ in screen handler)
        for (int i = 36; i < mc.player.currentScreenHandler.slots.size(); i++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                depositedAny = true;
            }
        }

        return depositedAny;
    }

    private List<BlockPos> findDestinationChests() {
        List<BlockPos> chests = new ArrayList<>();
        int r = (int) Math.ceil(destRadius.get());
        BlockPos.Mutable mut = new BlockPos.Mutable();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    mut.set(destPos.getX() + x, destPos.getY() + y, destPos.getZ() + z);
                    if (isChestBlock(mut)) {
                        chests.add(mut.toImmutable());
                    }
                }
            }
        }

        return chests;
    }

    private void collectNearbyItems() {
        for (ItemEntity item : mc.world.getEntitiesByClass(ItemEntity.class, 
             Box.of(Vec3d.ofCenter(sourcePos), sourceRadius.get() * 2, sourceRadius.get() * 2, sourceRadius.get() * 2),
             entity -> true)) {
            
            double distance = mc.player.getPos().distanceTo(item.getPos());
            if (distance < 4.0 && !isInventoryFull()) {
                Vec3d itemPos = item.getPos();
                double dx = itemPos.x - mc.player.getX();
                double dz = itemPos.z - mc.player.getZ();
                double speed = 0.08;
                
                if (Math.abs(dx) > 0.3 || Math.abs(dz) > 0.3) {
                    double angle = Math.atan2(dz, dx);
                    double vx = Math.cos(angle) * speed;
                    double vz = Math.sin(angle) * speed;
                    mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);
                }
            }
        }
    }

    private boolean isChestBlock(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.CHEST || 
               block == Blocks.TRAPPED_CHEST ||
               block == Blocks.ENDER_CHEST ||
               block.toString().contains("shulker_box");
    }

    private boolean isInventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasItemsInInventory() {
        for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMoreItemsToCollect() {
        return !chestsToCheck.isEmpty() || 
               (collectFloorItems.get() && hasItemsOnFloor());
    }

    private boolean hasItemsOnFloor() {
        return !mc.world.getEntitiesByClass(ItemEntity.class,
            Box.of(Vec3d.ofCenter(sourcePos), sourceRadius.get() * 2, sourceRadius.get() * 2, sourceRadius.get() * 2),
            entity -> true).isEmpty();
    }

    private void lookAt(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d eye = mc.player.getCameraPosVec(1f);
        Vec3d delta = center.subtract(eye);
        double distXZ = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, distXZ));

        mc.player.setYaw(smooth(mc.player.getYaw(), yaw));
        mc.player.setPitch(smooth(mc.player.getPitch(), pitch));
    }

    private float smooth(float current, float target) {
        float diff = MathHelper.wrapDegrees(target - current);
        float randomness = (float) ((rand.nextDouble() - 0.5) * 0.5);
        return current + diff * 0.3f + randomness;
    }
}