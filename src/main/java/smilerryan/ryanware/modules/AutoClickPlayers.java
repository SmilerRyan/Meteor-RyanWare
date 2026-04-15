package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import smilerryan.ryanware.RyanWare;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AutoClickPlayers extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum AttackMode {
        Everyone, Friends, Specific, EveryoneExceptSpecific
    }

    public enum ClickType {
        LeftClick, RightClick
    }

    public enum DelayMode {
        Fixed, Random
    }

    private final Setting<AttackMode> attackMode = sgGeneral.add(new EnumSetting.Builder<AttackMode>()
        .name("attack-mode")
        .defaultValue(AttackMode.Everyone)
        .build()
    );

    private final Setting<List<String>> playerList = sgGeneral.add(new StringListSetting.Builder()
        .name("player-list")
        .defaultValue()
        .build()
    );

    private final Setting<ClickType> clickType = sgGeneral.add(new EnumSetting.Builder<ClickType>()
        .name("click-type")
        .defaultValue(ClickType.LeftClick)
        .build()
    );

    private final Setting<DelayMode> delayMode = sgGeneral.add(new EnumSetting.Builder<DelayMode>()
        .name("delay-mode")
        .defaultValue(DelayMode.Fixed)
        .build()
    );

    private final Setting<Integer> fixedTicks = sgGeneral.add(new IntSetting.Builder()
        .name("fixed-ticks")
        .defaultValue(10)
        .min(1)
        .visible(() -> delayMode.get() == DelayMode.Fixed)
        .build()
    );

    private final Setting<Integer> minTicks = sgGeneral.add(new IntSetting.Builder()
        .name("min-ticks")
        .defaultValue(6)
        .min(1)
        .visible(() -> delayMode.get() == DelayMode.Random)
        .build()
    );

    private final Setting<Integer> maxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-ticks")
        .defaultValue(12)
        .min(1)
        .visible(() -> delayMode.get() == DelayMode.Random)
        .build()
    );

    private int tickTimer = 0;
    private int nextDelay = 0;

    public AutoClickPlayers() {
        super(RyanWare.CATEGORY,
            RyanWare.modulePrefix_extras + "AutoClickPlayers",
            "Automatically clicks on players you are looking at.");
    }

    @Override
    public void onActivate() {
        resetDelay();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        Entity target = mc.targetedEntity;
        if (!(target instanceof PlayerEntity player)) return;
        if (player == mc.player) return;
        if (!passesFilter(player)) return;

        tickTimer++;
        if (tickTimer < nextDelay) return;

        if (clickType.get() == ClickType.LeftClick) {
            mc.interactionManager.attackEntity(mc.player, player);
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.interactionManager.interactEntity(mc.player, player, Hand.MAIN_HAND);
        }

        resetDelay();
    }

    private void resetDelay() {
        tickTimer = 0;
        nextDelay = delayMode.get() == DelayMode.Fixed
            ? fixedTicks.get()
            : ThreadLocalRandom.current().nextInt(minTicks.get(), maxTicks.get() + 1);
    }

    private boolean passesFilter(PlayerEntity player) {
        String name = player.getGameProfile().getName();

        return switch (attackMode.get()) {
            case Everyone -> true;
            case Friends -> Friends.get().isFriend(player);
            case Specific -> playerList.get().stream().anyMatch(s -> name.equalsIgnoreCase(s));
            case EveryoneExceptSpecific -> playerList.get().stream().noneMatch(s -> name.equalsIgnoreCase(s));
        };
    }
}
