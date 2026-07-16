package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import smilerryan.ryanware.RyanWare;

public class AutoAnchor extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> fill = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-fill")
        .description("Automatically fills respawn anchors with glowstone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> explode = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-explode")
        .description("Automatically triggers explosion on charged anchors.")
        .defaultValue(false)
        .build()
    );

    public AutoAnchor() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Auto-Anchor", "Automatically fills or explodes respawn anchors.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (!mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) return;

        int currentCharges = mc.world.getBlockState(hit.getBlockPos()).get(RespawnAnchorBlock.CHARGES);

        // Logic for Filling
        if (fill.get() && currentCharges < 4) {
            Hand hand = getGlowstoneHand();
            if (hand != null) {
                mc.interactionManager.interactBlock(mc.player, hand, hit);
                return; // Return to avoid conflicting interactions
            }
        }

        // Logic for Exploding
        if (explode.get() && currentCharges > 0) {
            // Interact with empty hand to trigger the explosion
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        }
    }

    private Hand getGlowstoneHand() {
        if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().isOf(Items.GLOWSTONE)) return Hand.OFF_HAND;
        return null;
    }
}