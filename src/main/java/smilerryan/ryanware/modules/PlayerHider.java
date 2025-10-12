package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import smilerryan.ryanware.RyanWare;

import java.util.*;

public class PlayerHider extends Module {
    private final SettingGroup sg = settings.createGroup("Settings");

    private final Setting<List<String>> playersToHide = sg.add(new StringListSetting.Builder()
        .name("players-to-hide")
        .description("Players to replace in the tab list.")
        .onChanged(v -> needsUpdate = true)
        .build()
    );

    private final Setting<List<String>> replacementNames = sg.add(new StringListSetting.Builder()
        .name("replacement-names")
        .description("Replacement names (same order). Empty = ignore.")
        .onChanged(v -> needsUpdate = true)
        .build()
    );

    private final Map<String, String> replacements = new HashMap<>();
    private boolean needsUpdate = true;

    public PlayerHider() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Player-Hider", "Replace players in the tab list.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (needsUpdate) {
            updateReplacements();
            needsUpdate = false;
        }
        applyTabChanges();
    }

    private void updateReplacements() {
        replacements.clear();
        List<String> hide = playersToHide.get();
        List<String> repl = replacementNames.get();

        for (int i = 0; i < hide.size(); i++) {
            String original = hide.get(i).trim();
            String replacement = i < repl.size() ? repl.get(i).trim() : "";
            if (!original.isEmpty() && !replacement.isEmpty()) {
                replacements.put(original.toLowerCase(), replacement);
            }
        }
    }

    private void applyTabChanges() {
        if (mc == null || mc.getNetworkHandler() == null) return;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() == null) continue;

            String name = entry.getProfile().getName().toLowerCase();
            if (replacements.containsKey(name)) {
                entry.setDisplayName(Text.literal(replacements.get(name)));
            }
        }
    }
}
