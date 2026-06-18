package smilerryan.ryanware.mixins;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smilerryan.ryanware.modules_standard.Settings;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), index = 0)
    private int modifyWidth(int width) {
        if (Modules.get().get(Settings.class).s_TabList_showPingAsNumber.get()) {
            return width + 30;
        }
        return width;
    }

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void onRenderLatencyIcon(DrawContext context, int x, int width, int y, PlayerListEntry entry, CallbackInfo ci) {
        if (!Modules.get().get(Settings.class).s_TabList_showPingAsNumber.get()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer textRenderer = mc.textRenderer;

        int latency = MathHelper.clamp(entry.getLatency(), 0, 9999);
        int color = latency < 150 ? 0xFF00E970 :
                    latency < 300 ? 0xFFE7D020 : 0xFFD74238;
        String text = latency + "ms";
        context.drawTextWithShadow(textRenderer, text, x + width - textRenderer.getWidth(text), y, color);
        ci.cancel();
    }
}
