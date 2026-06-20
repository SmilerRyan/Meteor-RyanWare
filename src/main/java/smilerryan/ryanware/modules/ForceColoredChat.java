package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class ForceColoredChat extends Module {
    private final Pattern colorCodePattern = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);

    public ForceColoredChat() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Force-Colored-Chat", "Replaces & with § in received messages.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        Text original = e.getMessage();
        String content = original.getString();
        if (colorCodePattern.matcher(content).find()) {
            e.setMessage(replaceColorCodes(original));
        }
    }

    private Text replaceColorCodes(Text text) {
        MutableText result = Text.empty().setStyle(text.getStyle());
        text.visit((style, string) -> {
            String replaced = colorCodePattern.matcher(string).replaceAll("§$1");
            result.append(Text.literal(replaced).setStyle(style));
            return java.util.Optional.empty();
        }, text.getStyle());
        return result;
    }
}