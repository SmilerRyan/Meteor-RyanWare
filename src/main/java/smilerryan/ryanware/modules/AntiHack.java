package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class AntiHack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // User picks modules to whitelist from all loaded modules
    private final Setting<List<Module>> whitelist = sgGeneral.add(new ModuleListSetting.Builder()
        .name("whitelist")
        .description("Modules that will not be disabled by AntiHack.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    public AntiHack() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "AntiHack", "Literally stops you from hacking, forces everything except the allowed modules off.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        List<String> disabled = new ArrayList<>();
        List<Module> allowed = whitelist.get();

        for (Module module : Modules.get().getAll()) {
            if (module != this && module.isActive() && !allowed.contains(module)) {
                module.toggle();
                disabled.add(module.name);
            }
        }

        if (!disabled.isEmpty()) {
            info("Disabled: " + String.join(", ", disabled));
        }
    }
}
