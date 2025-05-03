package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public class NameMCLink extends Module {
    public NameMCLink() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "namemc-link", "Adds a NameMC link next to player join messages.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString().trim();
        if (message.matches("^[A-Za-z0-9_]{1,16} joined the game\\.?$")) {
            String name = message.split(" ")[0];
            Text namemc = Text.literal(" [NameMC]")
                .setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://namemc.com/profile/" + name)));
            event.setMessage(Text.literal("").append(event.getMessage()).append(namemc));
        }
    }
}
