package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.ArrayList;
import java.util.List;

public class ClearLag extends Module {

    public enum Mode {
        WHITELIST, BLACKLIST
    }

    private final SettingGroup sGeneral = settings.getDefaultGroup();
    private final SettingGroup sEntities = settings.createGroup("Entities");

    private final Setting<Boolean> item = sEntities.add(new BoolSetting.Builder().name("items").defaultValue(true).build());
    private final Setting<Boolean> arrow = sEntities.add(new BoolSetting.Builder().name("arrows").defaultValue(true).build());
    private final Setting<Boolean> pearl = sEntities.add(new BoolSetting.Builder().name("ender-pearls").defaultValue(true).build());
    private final Setting<Boolean> snowball = sEntities.add(new BoolSetting.Builder().name("snowballs").defaultValue(true).build());

    private final Setting<Boolean> minecarts = sEntities.add(new BoolSetting.Builder().name("minecarts").defaultValue(false).build());
    private final Setting<Boolean> armorStands = sEntities.add(new BoolSetting.Builder().name("armor-stands").defaultValue(false).build());

    private final Setting<List<String>> customEntities = sEntities.add(new StringListSetting.Builder()
        .name("custom-entities")
        .description("Manually added entity types (e.g. zombie, creeper, xp_orb)")
        .build());

    private final Setting<Boolean> useMinecraftPrefix = sGeneral.add(new BoolSetting.Builder()
        .name("use-minecraft-prefix")
        .defaultValue(false)
        .build());

    private final Setting<Integer> delay = sGeneral.add(new IntSetting.Builder()
        .name("delay")
        .defaultValue(20)
        .min(1)
        .max(200)
        .sliderMax(200)
        .build());

    private final Setting<Mode> mode = sGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Whitelist = only selected entities, Blacklist = all except selected")
        .defaultValue(Mode.BLACKLIST)
        .build());

    private final Setting<Boolean> debug = sGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Prints the generated command to chat instead of executing it.")
        .defaultValue(false)
        .build());

    private int tickCounter = 0;

    public ClearLag() {
        super(RyanWare.CATEGORY_EXTRAS,
            RyanWare.modulePrefix_extras + "ClearLag", "Kills selected entities with a delay.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (++tickCounter < delay.get()) return;
        tickCounter = 0;

        List<String> types = new ArrayList<>();

        // presets
        if (item.get()) types.add("item");
        if (arrow.get()) types.add("arrow");
        if (pearl.get()) types.add("ender_pearl");
        if (snowball.get()) types.add("snowball");

        if (minecarts.get()) {
            types.add("minecart");
            types.add("hopper_minecart");
            types.add("chest_minecart");
            types.add("furnace_minecart");
            types.add("tnt_minecart");
        }

        if (armorStands.get()) {
            types.add("armor_stand");
        }

        for (String s : customEntities.get()) {
            if (s == null || s.isEmpty()) continue;
            types.add(s);
        }

        StringBuilder cmd = new StringBuilder();

        cmd.append(useMinecraftPrefix.get() ? "/minecraft:" : "/").append("kill @e[");
        
        boolean hasPrev = false;

        if (mode.get() == Mode.BLACKLIST) {
            cmd.append("type=!player");
            hasPrev = true;
        }

        for (String t : types) {
            if (t == null || t.isEmpty()) continue;
            if (hasPrev) {cmd.append(",");}
            hasPrev = true;
            cmd.append("type=");
            if (mode.get() == Mode.BLACKLIST) {cmd.append("!");}
            cmd.append(t);
        }

        cmd.append("]");


        if (!cmd.toString().endsWith("@e[]")) {
            if (debug.get()) {
                info("Run: " + cmd.toString());
            } else {
                mc.player.networkHandler.sendChatCommand(cmd.substring(1));                
            }
        } else {
            if (debug.get()) {
                info("Empty: " + cmd.toString());
            }
        }


    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive() || mc.player == null) return;
        String msg = event.getMessage().getString();
        if (msg.startsWith("Killed ") || msg.equals("No entity was found")) {
            event.setCancelled(true);
        }
    }

}