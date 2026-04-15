package smilerryan.ryanware.modules_3;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomTabText extends Module {
    private final SettingGroup sgHeader = settings.createGroup("Header Settings");
    private final SettingGroup sgFooter = settings.createGroup("Footer Settings");

    public enum HeaderMode { Remove, Replace, AddToTop, AddToEnd }
    public enum FooterMode { Remove, Replace, AddToTop, AddToEnd }

    private final Setting<Boolean> customHeaderEnabled = sgHeader.add(new BoolSetting.Builder()
        .name("custom-header").description("Enable custom header modifications.").defaultValue(true).build()
    );

    private final Setting<HeaderMode> headerMode = sgHeader.add(new EnumSetting.Builder<HeaderMode>()
        .name("header-mode").description("How to handle the server's header.").defaultValue(HeaderMode.Replace)
        .visible(customHeaderEnabled::get).build()
    );

    private final Setting<String> headerText = sgHeader.add(new StringSetting.Builder()
        .name("header-text")
        .description(
            "Custom text for the header.\n" +
            "Use & for color codes, \\n for new lines.\n" +
            "Placeholders:\n" +
            "  {players} - online player count\n" +
            "  {ping}    - your current ping\n" +
            "\n" +
            "Example: &aOnline: {players} &7| Ping: {ping}ms\\n&bWelcome to the server!"
        )
        .defaultValue("").visible(customHeaderEnabled::get).build()
    );

    private final Setting<Boolean> customFooterEnabled = sgFooter.add(new BoolSetting.Builder()
        .name("custom-footer").description("Enable custom footer modifications.").defaultValue(true).build()
    );

    private final Setting<FooterMode> footerMode = sgFooter.add(new EnumSetting.Builder<FooterMode>()
        .name("footer-mode").description("How to handle the server's footer.").defaultValue(FooterMode.Replace)
        .visible(customFooterEnabled::get).build()
    );

    private final Setting<String> footerText = sgFooter.add(new StringSetting.Builder()
        .name("footer-text")
        .description(
            "Custom text for the footer.\n" +
            "Use & for color codes, \\n for new lines.\n" +
            "Placeholders:\n" +
            "  {players} - online player count\n" +
            "  {ping}    - your current ping\n" +
            "\n" +
            "Example: &7Have fun!\\n&aPlayers online: {players}"
        )
        .defaultValue("").visible(customFooterEnabled::get).build()
    );

    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or])");

    private Text serverHeader = null;
    private Text serverFooter = null;

    public CustomTabText() {
        super(RyanWare.CATEGORY3, RyanWare.modulePrefix3 + "Custom-Tab-Text",
            "Customize tab overlay header and footer.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerListHeaderS2CPacket packet) {
            serverHeader = packet.header();
            serverFooter = packet.footer();
            event.cancel();
            forceUpdateTabText();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        forceUpdateTabText();
    }

    private void forceUpdateTabText() {
        if (mc == null || mc.inGameHud == null) return;

        PlayerListHud hud = mc.inGameHud.getPlayerListHud();
        if (hud == null) return;

        if (customHeaderEnabled.get()) hud.setHeader(buildFinalText(headerText.get(), serverHeader, headerMode.get()));
        if (customFooterEnabled.get()) hud.setFooter(buildFinalText(footerText.get(), serverFooter, footerMode.get()));
    }

    private Text buildFinalText(String customText, Text serverText, Object mode) {
        String processed = replacePlaceholders(customText).replace("\\n", "\n");

        if (mode == HeaderMode.Remove || mode == FooterMode.Remove) return null;
        if (mode == HeaderMode.Replace || mode == FooterMode.Replace) return parseFormattedText(processed);
        if (mode == HeaderMode.AddToTop || mode == FooterMode.AddToTop) {
            if (serverText == null) return parseFormattedText(processed);
            return Text.empty().append(parseFormattedText(processed)).append("\n").append(serverText);
        }
        if (mode == HeaderMode.AddToEnd || mode == FooterMode.AddToEnd) {
            if (serverText == null) return parseFormattedText(processed);
            return Text.empty().append(serverText).append("\n").append(parseFormattedText(processed));
        }
        return null;
    }

    private String replacePlaceholders(String input) {
        if (input == null || input.isEmpty() || mc == null || mc.getNetworkHandler() == null) return input;

        // Online player count
        Collection<PlayerListEntry> players = mc.getNetworkHandler().getPlayerList();
        int playerCount = players != null ? players.size() : 0;

        // Player ping
        int ping = 0;
        if (mc.player != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) ping = entry.getLatency();
        }

        return input
            .replace("{players}", String.valueOf(playerCount))
            .replace("{ping}", String.valueOf(ping));
    }

    private Text parseFormattedText(String input) {
        if (input == null || input.isEmpty()) return Text.empty();
        Matcher matcher = COLOR_PATTERN.matcher(input);
        return Text.literal(matcher.replaceAll("§$1"));
    }
}
