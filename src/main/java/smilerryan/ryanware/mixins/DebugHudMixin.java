package smilerryan.ryanware.mixins;

import smilerryan.ryanware.modules_3.f3_number_hider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(value = DebugHud.class, priority = 1001)
public class DebugHudMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(
        method = "drawText",
        at = @At("HEAD"),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void ryanware_hideNumbers(
        DrawContext context,
        List<String> text,
        boolean left,
        CallbackInfo ci
    ) {
        if (f3_number_hider.INSTANCE == null || !f3_number_hider.INSTANCE.isActive()) return;

        for (int i = 0; i < text.size(); i++) {
            text.set(i, f3_number_hider.INSTANCE.hideCoordinateString(text.get(i)));
        }
    }
}
