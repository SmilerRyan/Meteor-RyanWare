package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem; // <-- NEW IMPORT
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.Hand;
import smilerryan.ryanware.RyanWare;

import java.util.HashMap;
import java.util.Map;

public class EventAnnouncer extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    private final Setting<Boolean> announceBlockPlace = sg.add(new BoolSetting.Builder()
            .name("block-place")
            .description("Announce when you place blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> announceItemUse = sg.add(new BoolSetting.Builder()
            .name("item-use")
            .description("Announce when you use items.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> announceDamageTaken = sg.add(new BoolSetting.Builder()
            .name("damage-taken")
            .description("Announce when you take damage.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> announceMovement = sg.add(new BoolSetting.Builder()
            .name("movement")
            .description("Announce blocks walked.")
            .defaultValue(false)
            .build()
    );

    private final Setting<String> blockPlaceMessage = sg.add(new StringSetting.Builder()
            .name("block-place-message")
            .description("Message format for block placement. {player}, {count}, {block}")
            .defaultValue("/say {player} placed {count}x {block}!")
            .build()
    );

    private final Setting<String> itemUseMessage = sg.add(new StringSetting.Builder()
            .name("item-use-message")
            .description("Message format for item usage. {player}, {count}, {item}")
            .defaultValue("/say {player} used {count}x {item}!")
            .build()
    );

    private final Setting<String> damageTakenMessage = sg.add(new StringSetting.Builder()
            .name("damage-taken-message")
            .description("Message format for taking damage. {player}, {amount}")
            .defaultValue("/say {player} took {amount} damage!")
            .build()
    );

    private final Setting<String> movementMessage = sg.add(new StringSetting.Builder()
            .name("movement-message")
            .description("Message format for walking. {count}")
            .defaultValue("/say I walked {count} blocks!")
            .build()
    );

    private final Setting<Integer> tickTimeout = sg.add(new IntSetting.Builder()
            .name("tick-timeout")
            .description("Ticks to accumulate events before sending messages.")
            .defaultValue(20)
            .min(1)
            .sliderMax(200)
            .build()
    );

    // Accumulators
    private final Map<Block, Integer> blockPlaceCounts = new HashMap<>();
    private final Map<Item, Integer> itemUseCounts = new HashMap<>();
    private double damageTakenAmount = 0;
    private int walkedBlocks = 0;

    private int tickCounter = 0;
    private double lastX, lastY, lastZ;
    private float lastHealth = 20f;
    private boolean initPos = false;

    public EventAnnouncer() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Event-Announcer",
                "Announce game events with customizable messages and batching.");
    }

    @Override
    public void onActivate() {
        blockPlaceCounts.clear();
        itemUseCounts.clear();
        damageTakenAmount = 0;
        walkedBlocks = 0;
        tickCounter = 0;
        initPos = false;

        if (mc.player != null) lastHealth = mc.player.getHealth();
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (!announceBlockPlace.get()) return;
        if (mc.player == null) return;

        // FIX: Check if the item held is a BlockItem to ensure it is a placement attempt.
        ItemStack itemStack = mc.player.getStackInHand(event.hand);

        if (itemStack.getItem() instanceof BlockItem) {
            // If the player is holding a block item, this is a placement. 
            // Use the item to determine the block type, not the world position clicked.
            Block block = ((BlockItem) itemStack.getItem()).getBlock();
            blockPlaceCounts.put(block, blockPlaceCounts.getOrDefault(block, 0) + 1);
        }
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (!announceItemUse.get()) return;

        if (mc.player == null) return;

        ItemStack stack = mc.player.getStackInHand(event.hand);

        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        itemUseCounts.put(item, itemUseCounts.getOrDefault(item, 0) + 1);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        tickCounter++;
        if (tickCounter < tickTimeout.get()) return;
        tickCounter = 0;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        String playerName = player.getName().getString();

        // Block place
        if (!blockPlaceCounts.isEmpty()) {
            for (Map.Entry<Block, Integer> entry : blockPlaceCounts.entrySet()) {
                String msg = blockPlaceMessage.get()
                        .replace("{player}", playerName)
                        .replace("{count}", String.valueOf(entry.getValue()))
                        .replace("{block}", entry.getKey().toString());
                sendMessage(msg);
            }
            blockPlaceCounts.clear();
        }

        // Item use
        if (!itemUseCounts.isEmpty()) {
            for (Map.Entry<Item, Integer> entry : itemUseCounts.entrySet()) {
                String msg = itemUseMessage.get()
                        .replace("{player}", playerName)
                        .replace("{count}", String.valueOf(entry.getValue()))
                        .replace("{item}", entry.getKey().toString());
                sendMessage(msg);
            }
            itemUseCounts.clear();
        }

        // Damage taken
        if (announceDamageTaken.get()) {
            float currentHealth = player.getHealth();
            if (currentHealth < lastHealth) {
                damageTakenAmount += (lastHealth - currentHealth);
            }
            if (damageTakenAmount > 0) {
                String msg = damageTakenMessage.get()
                        .replace("{player}", playerName)
                        .replace("{amount}", String.valueOf((int) damageTakenAmount));
                sendMessage(msg);
                damageTakenAmount = 0;
            }
            lastHealth = currentHealth;
        }

        // Movement
        if (announceMovement.get()) {
            if (!initPos) {
                lastX = player.getX();
                lastY = player.getY();
                lastZ = player.getZ();
                initPos = true;
            } else {
                double dx = player.getX() - lastX;
                double dz = player.getZ() - lastZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                walkedBlocks += (int) distance;

                lastX = player.getX();
                lastY = player.getY();
                lastZ = player.getZ();
            }

            if (walkedBlocks > 0) {
                String msg = movementMessage.get().replace("{count}", String.valueOf(walkedBlocks));
                sendMessage(msg);
                walkedBlocks = 0;
            }
        }
    }

    // Helper function to send message or run as command
    private void sendMessage(String msg) {
        if (mc.player == null) return;

        if (msg.startsWith("/")) mc.player.networkHandler.sendChatCommand(msg.substring(1));
        else mc.player.networkHandler.sendChatMessage(msg);
    }
}