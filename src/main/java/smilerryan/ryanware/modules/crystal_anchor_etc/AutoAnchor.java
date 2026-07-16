package smilerryan.ryanware.modules.crystal_anchor_etc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
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
        .description("Automatically interacts with anchors using Glowstone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> explode = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-explode")
        .description("Automatically triggers explosion on charged anchors.")
        .defaultValue(true)
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
        if (fill.get() && currentCharges == 0) {
            FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
            if (glowstone.found()) {
                InvUtils.swap(glowstone.slot(), false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            }
        }
        if (explode.get() && currentCharges > 0) {
            if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                for (int i = 0; i < 9; i++) {
                    if (!mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) {
                        InvUtils.swap(i, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                        break;
                    }
                }
            }
        }
    }
}