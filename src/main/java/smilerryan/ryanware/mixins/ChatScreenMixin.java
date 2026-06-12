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

    // STAR REPLACE MODE ONLY
    @Inject(method = "render", at = @At("HEAD"))
    private void ryanware$maskHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (chatField == null) return;

        Settings settings = Modules.get().get(Settings.class);
        if (settings == null || !settings.s_MaskChatEnabled.get()) return;

        if (settings.s_MaskChatMode.get() != Settings.ChatMaskMode.TEXT_REPLACEMENT) return;

        String text = chatField.getText();
        List<String> prefixes = settings.s_MaskChatPrefixes.get();
        if (prefixes == null) return;

        String symbolStr = settings.s_MaskChatSymbol.get();
        char symbol = (symbolStr == null || symbolStr.isEmpty()) ? '*' : symbolStr.charAt(0);

        String masked = mask(text, prefixes, symbol);
        if (masked == null || masked.equals(text)) return;

        ryanware$originalText = text;

        ((TextFieldWidgetAccessor) chatField).setRawText(masked);
    }

    // RESTORE TEXT
    @Inject(method = "render", at = @At("RETURN"))
    private void ryanware$maskReturn(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ryanware$originalText != null && chatField != null) {
            ((TextFieldWidgetAccessor) chatField).setRawText(ryanware$originalText);
            ryanware$originalText = null;
        }
    }

    // BOX OVERLAY
    @Inject(method = "render", at = @At("TAIL"))
    private void ryanware$boxOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (chatField == null) return;

        Settings settings = Modules.get().get(Settings.class);
        if (settings == null || !settings.s_MaskChatEnabled.get()) return;

        Settings.ChatMaskMode mode = settings.s_MaskChatMode.get();
        if (mode == Settings.ChatMaskMode.TEXT_REPLACEMENT) return;

        String text = chatField.getText();

        List<String> prefixes = settings.s_MaskChatPrefixes.get();
        if (prefixes == null) return;

        String matchedPrefix = null;
        int prefixWidth = 0;

        for (String prefix : prefixes) {
            if (prefix != null && text.startsWith(prefix)) {
                matchedPrefix = prefix;
                prefixWidth = client.textRenderer.getWidth(prefix);
                break;
            }
        }

        if (matchedPrefix == null) return;

        int x = chatField.getX();
        int y = chatField.getY();

        int textWidth = client.textRenderer.getWidth(text);
        int fontHeight = client.textRenderer.fontHeight;

        int textY = y + (chatField.getHeight() - fontHeight) / 2;
        int startX = x + prefixWidth;

        int color = settings.s_BoxOverlayColor.get().getPacked();

        context.fill(
            startX,
            textY - 1,
            x + textWidth,
            textY + fontHeight + 1,
            color
        );
    }

    @Unique
    private String mask(String text, List<String> commands, char symbol) {
        if (text == null || commands == null) return null;

        for (String command : commands) {
            if (command == null) continue;

            if (text.startsWith(command)) {
                int offset = command.length();
                char[] chars = text.toCharArray();

                for (int i = offset; i < chars.length; i++) {
                    if (chars[i] != ' ') {
                        chars[i] = symbol;
                    }
                }

                return new String(chars);
            }
        }

        return null;
    }
}