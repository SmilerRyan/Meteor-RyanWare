package smilerryan.ryanware.mixins;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smilerryan.ryanware.modules_standard.Settings;

import java.util.List;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private String ryanware$originalText;

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ryanware$maskChatHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (chatField == null) return;
        Settings settings = Modules.get().get(Settings.class);
        if (settings == null || !settings.s_MaskChatEnabled.get()) return;
        String text = chatField.getText();
        String masked = ryanware$mask(text, settings.s_MaskChatPrefixes.get());
        if (masked != null && !masked.equals(text)) {
            ryanware$originalText = text;
            chatField.setText(masked);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void ryanware$maskChatReturn(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ryanware$originalText != null) {
            chatField.setText(ryanware$originalText);
            ryanware$originalText = null;
        }
    }

    @Unique
    private String ryanware$mask(String text, List<String> commands) {
        if (text == null || commands == null) return null;
        String lower = text.toLowerCase();
        for (String command : commands) {
            if (command == null) continue;
            String cmdLower = command.toLowerCase();
            if (lower.startsWith(cmdLower)) {
                String password = text.substring(command.length());
                return command + "*".repeat(password.length());
            }
        }
        return null;
    }
}