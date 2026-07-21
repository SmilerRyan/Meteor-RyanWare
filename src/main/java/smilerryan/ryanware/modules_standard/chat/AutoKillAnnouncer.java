package smilerryan.ryanware.modules_standard.chat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class AutoKillAnnouncer extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enablePlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-players")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-mobs")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> playerMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("player-messages")
        .defaultValue("i gooned {name}")
        .build()
    );

    private final Setting<List<String>> mobMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("mob-messages")
        .defaultValue("i gooned a {name}")
        .build()
    );

    private final Random random = new Random();

    private Entity lastHitEntity;
    private int lastHitTick;
    private int tickCounter;

    private static final int KILL_CHECK_TICKS = 20;

    public AutoKillAnnouncer() {
        super(
            RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Auto-Kill-Announcer",
            "Automatically announces when you kill players or mobs."
        );
    }

    @Override
    public void onActivate() {
        lastHitEntity = null;
        lastHitTick = 0;
        tickCounter = 0;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity entity = event.entity;

        if (entity == null) return;

        if (entity instanceof PlayerEntity) {
            if (!enablePlayers.get()) return;
        } else {
            if (!enableMobs.get()) return;
        }

        lastHitEntity = entity;
        lastHitTick = tickCounter;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null) return;

        tickCounter++;

        if (lastHitEntity == null) return;

        if (tickCounter - lastHitTick > KILL_CHECK_TICKS) {
            lastHitEntity = null;
            return;
        }

        boolean dead = false;

        if (lastHitEntity.isRemoved()) {
            dead = true;
        } else if (lastHitEntity instanceof PlayerEntity player) {
            dead = player.isDead() || player.getHealth() <= 0;
        } else {
            dead = !lastHitEntity.isAlive();
        }

        if (!dead) return;

        String name = lastHitEntity.getName().getString();

        List<String> messages;

        if (lastHitEntity instanceof PlayerEntity) {
            messages = playerMessages.get();
        } else {
            messages = mobMessages.get();
        }

        if (!messages.isEmpty()) {
            String message = messages
                .get(random.nextInt(messages.size()))
                .replace("{name}", name);

            ChatUtils.sendPlayerMsg(message);
        }

        lastHitEntity = null;
    }
}