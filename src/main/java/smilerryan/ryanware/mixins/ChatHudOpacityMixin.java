package smilerryan.ryanware.mixins;

import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smilerryan.ryanware.modules_standard.Settings;

@Mixin(targets = "net.minecraft.class_338$class_12232")
public interface ChatHudOpacityMixin {

    @Unique
    private static Object ryanware$getConstantOpacity() {
        try {
            Class<?> clazz = Class.forName("net.minecraft.class_338$class_12232");
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                if (clazz.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field.get(null);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Inject(method = "timeBased", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void timeBased(int currentTick, CallbackInfoReturnable<Object> cir) {
        Settings settings = Modules.get().get(Settings.class);
        if (settings != null && settings.s_Chat_AlwaysVisible.get()) {
            Object constant = ryanware$getConstantOpacity();
            if (constant != null) {
                cir.setReturnValue(constant);
            }
        }
    }
}
