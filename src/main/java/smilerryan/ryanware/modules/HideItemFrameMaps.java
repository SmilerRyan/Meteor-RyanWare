package smilerryan.ryanware.modules;

import java.util.HashSet;
import java.util.Set;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import smilerryan.ryanware.RyanWare;

public class HideItemFrameMaps extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Set<Integer> allowed = new HashSet<>();

    public HideItemFrameMaps() {
        super(
            RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "Hide-Item-Frame-Maps",
            "Hides maps from item frames until you choose to unblock them."
        );
    }

    // TOGGLE NOW HANDLED BY ATTACK INSTEAD OF INTERACT EVENT
    @EventHandler
    private void onAttack(meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent event) {
        if (mc.player == null) return;
        if (!(event.entity instanceof ItemFrameEntity frame)) return;

        // SHIFT = normal vanilla behavior (do nothing)
        if (mc.player.isSneaking()) return;

        ItemStack stack = frame.getHeldItemStack();
        Integer id = extractMapId(stack);
        if (id == null) return;

        if (allowed.contains(id)) {
            allowed.remove(id);
        } else {
            allowed.add(id);
        }

        event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;
        mc.world.getEntitiesByClass(ItemFrameEntity.class, mc.player.getBoundingBox().expand(64), e -> true)
            .forEach(this::updateFrame);
    }

    private void updateFrame(ItemFrameEntity frame) {
        ItemStack stack = frame.getHeldItemStack();
        Integer id = extractMapId(stack);
        if (id == null) return;

        if (stack.getItem() == Items.FILLED_MAP) {
            if (allowed.contains(id)) return;

            ItemStack barrier = Items.BARRIER.getDefaultStack();
            barrier.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§4" + id));
            frame.setHeldItemStack(barrier);
        }

        if (stack.getItem() == Items.BARRIER) {
            if (!allowed.contains(id)) return;

            ItemStack map = Items.FILLED_MAP.getDefaultStack();
            map.set(DataComponentTypes.MAP_ID, new MapIdComponent(id));
            frame.setHeldItemStack(map);
        }
    }

    private Integer extractMapId(ItemStack stack) {
        if (stack.getItem() == Items.FILLED_MAP) {
            MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
            if (mapId != null) return mapId.id();
        }

        if (stack.getItem() == Items.BARRIER) {
            String stripped = stack.get(DataComponentTypes.CUSTOM_NAME)
                .getString()
                .replaceAll("§4", "")
                .trim();

            try {
                return Integer.parseInt(stripped);
            } catch (Exception ignored) {}
        }

        return null;
    }
}