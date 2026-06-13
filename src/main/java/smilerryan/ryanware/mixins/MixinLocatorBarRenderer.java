package smilerryan.ryanware.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.LocatorBar;
import net.minecraft.client.resource.waypoint.WaypointStyleAsset;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.waypoint.EntityTickProgress;
import net.minecraft.world.waypoint.TrackedWaypoint;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smilerryan.ryanware.modules_standard.Settings;

@Mixin(LocatorBar.class)
public class MixinLocatorBarRenderer {
    @Shadow @Final private MinecraftClient client;
    @Unique private Identifier blitOverride;
    @Unique private TrackedWaypoint waypoint;

    @Inject(method = "method_70870", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V", shift = At.Shift.BEFORE))
    private void injectRender(Entity entity, World world, EntityTickProgress tickProgress, DrawContext context, int i, TrackedWaypoint trackedWaypoint, CallbackInfo ci) {
        this.waypoint = trackedWaypoint;
        this.blitOverride = null;
        if (Modules.get().get(Settings.class).s_WaypointHeads.get()) {
            var connection = MinecraftClient.getInstance().getNetworkHandler();
            if (connection != null && trackedWaypoint.getSource().left().isPresent()) {
                var info = connection.getPlayerListEntry(trackedWaypoint.getSource().left().get());
                if (info != null) {
                    this.blitOverride = info.getSkinTextures().body().texturePath();
                }
            }
        }
    }

    @Redirect(method = "method_70870", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V"))
    private void redirectBlit(DrawContext instance, RenderPipeline renderPipeline, Identifier resourceLocation, int i, int j, int k, int l, int m) {
        if (this.blitOverride == null) {
            instance.drawGuiTexture(renderPipeline, resourceLocation, i, j, k, l, m);
        } else {
            float distance = MathHelper.sqrt((float)this.waypoint.squaredDistanceTo(this.client.getCameraEntity()));
            float progress = 1 - MathHelper.clamp((distance - WaypointStyleAsset.DEFAULT_NEAR_DISTANCE) / (WaypointStyleAsset.DEFAULT_FAR_DISTANCE - WaypointStyleAsset.DEFAULT_NEAR_DISTANCE), 0, 1);
            k = MathHelper.lerp(progress, 5, Math.max(k, 5));
            l = MathHelper.lerp(progress, 5, Math.max(l, 5));
            instance.drawTexturedQuad(this.blitOverride, i, j, i + k, j + l, 8f / 64, 16f / 64, 8f / 64, 16f / 64);
            instance.drawTexturedQuad(this.blitOverride, i, j, i + k, j + l, 40f / 64, 48f / 64, 8f / 64, 16f / 64);
        }
        this.blitOverride = null;
    }
}
