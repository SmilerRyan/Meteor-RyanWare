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
        String content = text.getString();
        String replaced = colorCodePattern.matcher(content).replaceAll("§$1");
        if (replaced.equals(content)) return text;
        MutableText newText = Text.literal(replaced).setStyle(text.getStyle());
        for (Text sibling : text.getSiblings()) {
            newText.append(replaceColorCodes(sibling));
        }
        return newText;
    }
}