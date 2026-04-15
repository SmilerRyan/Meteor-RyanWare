package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import smilerryan.ryanware.RyanWare;

public class FullBright extends Module {

    public FullBright() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "FullBright", "Night vision based fullbright.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        StatusEffectInstance effect = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (effect == null || effect.getDuration() < 210) {
            mc.player.addStatusEffect(
                new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION,
                    220,   // duration buffer
                    0,
                    false, // ambient
                    false  // particles
                )
            );
        }
    }

}