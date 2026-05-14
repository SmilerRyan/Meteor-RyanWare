package smilerryan.ryanware.mixins;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.utils.player.TitleScreenCredits;
import smilerryan.ryanware.RyanWare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TitleScreenCredits.class, remap = false)
public class TitleScreenCreditsMixin {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private static void ryanware_skipMeteorCredit(MeteorAddon addon, CallbackInfo ci) {
        if (RyanWare.hideTitleCredits && addon == MeteorClient.ADDON) {
            ci.cancel();
        }
    }
}
