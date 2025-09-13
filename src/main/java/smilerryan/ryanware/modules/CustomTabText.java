package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

public class CustomTabText extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> headerText = sgGeneral.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("header-text")
        .description("Custom text for the header of the tab list.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> footerText = sgGeneral.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("footer-text")
        .description("Custom text for the footer of the tab list.")
        .defaultValue("")
        .build()
    );

    public CustomTabText() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Custom-Tab-Text",
            "Allows customization of the tab overlay text and blocks server changes.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        forceUpdateTabText();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerListHeaderS2CPacket) {
            // Cancel the server's attempt to set header/footer
            event.cancel();
            // Immediately re-apply ours
            forceUpdateTabText();
        }
    }

    private void forceUpdateTabText() {
        if (mc == null || mc.player == null || mc.inGameHud == null) return;
        
        PlayerListHud hud = mc.inGameHud.getPlayerListHud();
        if (hud == null) return;

        // Force update every tick to override server packets
        String currentHeader = headerText.get();
        String currentFooter = footerText.get();

        if (currentHeader.isEmpty()) {
            hud.setHeader(null);
        } else {
            hud.setHeader(Text.of(currentHeader));
        }

        if (currentFooter.isEmpty()) {
            hud.setFooter(null);
        } else {
            hud.setFooter(Text.of(currentFooter));
        }
    }

    @Override
    public void onActivate() {
        forceUpdateTabText();
    }

    @Override
    public void onDeactivate() {
        if (mc != null && mc.inGameHud != null) {
            PlayerListHud hud = mc.inGameHud.getPlayerListHud();
            if (hud != null) {
                hud.setHeader(null);
                hud.setFooter(null);
            }
        }
    }
}