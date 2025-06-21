package smilerryan.ryanware.modules;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

import java.lang.reflect.Field;
import java.util.Map;

public class NoItemUsageCooldown extends Module {
    private Field itemUseCooldownField;
    private Field interactionManagerCooldownField;

    public NoItemUsageCooldown() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "no-item-usage-cooldown", "Removes and bypasses all possible item usage cooldowns.");
        try {
            interactionManagerCooldownField = ClientPlayerInteractionManager.class.getDeclaredField("itemUseCooldown");
            interactionManagerCooldownField.setAccessible(true);
        } catch (Exception ignored) {}

        try {
            itemUseCooldownField = MinecraftClient.class.getDeclaredField("itemUseCooldown");
            itemUseCooldownField.setAccessible(true);
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;
    
        try {
            interactionManagerCooldownField.setInt(mc.interactionManager, 0);
        } catch (Exception ignored) {}
    
        try {
            itemUseCooldownField.setInt(mc, 0);
        } catch (Exception ignored) {}
    
        try {
            Field cooldownsField = mc.player.getItemCooldownManager().getClass().getDeclaredField("cooldowns");
            cooldownsField.setAccessible(true);
            Map<Item, ?> cooldowns = (Map<Item, ?>) cooldownsField.get(mc.player.getItemCooldownManager());
            cooldowns.clear();
        } catch (Exception ignored) {}
    
        mc.player.resetLastAttackedTicks();
    
        if (mc.player.isUsingItem()) {
            mc.player.clearActiveItem();
        }

        try {
            Field usingItemField = mc.player.getClass().getDeclaredField("usingItem");
            usingItemField.setAccessible(true);
            usingItemField.set(mc.player, false);
        
            Field itemUseTimeLeftField = mc.player.getClass().getDeclaredField("itemUseTimeLeft");
            itemUseTimeLeftField.setAccessible(true);
            itemUseTimeLeftField.setInt(mc.player, 0);
        } catch (Exception ignored) {}

    }
    
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket)) return;
        if (mc.player == null) return;

        // Remove cooldown on main hand item after use
        ItemStack stack = mc.player.getMainHandStack();
        if (!stack.isEmpty()) {
            try {
                Field cooldownsField = mc.player.getItemCooldownManager().getClass().getDeclaredField("cooldowns");
                cooldownsField.setAccessible(true);
                Map<Item, ?> cooldowns = (Map<Item, ?>) cooldownsField.get(mc.player.getItemCooldownManager());
                cooldowns.remove(stack.getItem());
            } catch (Exception ignored) {}
        }
    }
}
