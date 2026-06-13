package smilerryan.ryanware.mixins;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smilerryan.ryanware.modules_standard.Settings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    private int lastWidth = -1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        try {
            Settings settings = Modules.get().get(Settings.class);
            if (settings == null || !settings.s_Chat_MaxWidth.get()) return;
            Class<?> clazz = ChatHud.class;
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    Class<?> t = f.getType();
                    if (List.class.isAssignableFrom(t)) {
                        f.setAccessible(true);
                        Object cur = f.get(this);
                        if (cur instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Object> curList = (List<Object>) cur;
                            ArrayList<Object> newList = new ArrayList<>(curList);
                            f.set(this, newList);
                        }
                    }
                } catch (Throwable inner) {}
            }
        } catch (Throwable t) {}
    }

    @Inject(method = "getWidth", at = @At("RETURN"), cancellable = true)
    private void onGetWidth(CallbackInfoReturnable<Integer> cir) {
        try {
            Settings settings = Modules.get().get(Settings.class);
            if (settings == null || !settings.s_Chat_MaxWidth.get()) return;
            cir.setReturnValue(MinecraftClient.getInstance().getWindow().getScaledWidth() - 10);
            MinecraftClient.getInstance().options.getChatWidth().setValue(1.0);
        } catch (Throwable ignored) {}
    }

}
