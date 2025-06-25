package smilerryan.ryanware.mixins;

import smilerryan.ryanware.modules.f3_number_hider;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ArrayList;

@Mixin(DebugHud.class)
public class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void hideLeftCoords(CallbackInfoReturnable<List<String>> cir) {
        if (f3_number_hider.INSTANCE != null) {
            List<String> original = cir.getReturnValue();
            List<String> modified = new ArrayList<>();
            
            for (String line : original) {
                modified.add(f3_number_hider.INSTANCE.hideCoordinateString(line));
            }
            
            cir.setReturnValue(modified);
        }
    }

    @Inject(method = "getRightText", at = @At("RETURN"), cancellable = true)
    private void hideRightCoords(CallbackInfoReturnable<List<String>> cir) {
        if (f3_number_hider.INSTANCE != null) {
            List<String> original = cir.getReturnValue();
            List<String> modified = new ArrayList<>();
            
            for (String line : original) {
                modified.add(f3_number_hider.INSTANCE.hideCoordinateString(line));
            }
            
            cir.setReturnValue(modified);
        }
    }
    
}