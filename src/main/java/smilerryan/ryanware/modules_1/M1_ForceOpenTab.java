package smilerryan.ryanware.modules_1;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import smilerryan.ryanware.RyanWare;

import java.lang.reflect.Field;

public class M1_ForceOpenTab extends Module {

    private KeyBinding playerListKey;

    public M1_ForceOpenTab() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "M1-Force-Open-Tab", "Forces the tab list to stay open.");
    }

    @Override
    public void onActivate() {
        playerListKey = findPlayerListKey();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (playerListKey != null) {
            playerListKey.setPressed(true);
        }
    }

    @Override
    public void onDeactivate() {
        if (playerListKey != null) {
            playerListKey.setPressed(false);
        }
    }

    private KeyBinding findPlayerListKey() {
        try {
            for (Field field : mc.options.getClass().getDeclaredFields()) {
                if (KeyBinding.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    KeyBinding key = (KeyBinding) field.get(mc.options);
                    if (key.getTranslationKey().toLowerCase().contains("playerlist") ||
                        key.getTranslationKey().toLowerCase().contains("player_list") ||
                        key.getTranslationKey().toLowerCase().contains("tab")) {
                        return key;
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
