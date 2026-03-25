package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;

import smilerryan.ryanware.RyanWare;

import java.util.Comparator;
import java.util.List;

public class AutoGroom extends Module {

    // GROUPS
    private final SettingGroup sgFollow = settings.createGroup("Follow");
    private final SettingGroup sgMessage = settings.createGroup("Messaging");
    private final SettingGroup sgMisc = settings.createGroup("Misc");

    // FOLLOW SETTINGS
    private final Setting<Boolean> followEnabled = sgFollow.add(new BoolSetting.Builder()
        .name("enabled")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> followRange = sgFollow.add(new DoubleSetting.Builder()
        .name("range")
        .defaultValue(50)
        .min(1)
        .sliderMax(100)
        .visible(followEnabled::get)
        .build()
    );

    private final Setting<Double> speed = sgFollow.add(new DoubleSetting.Builder()
        .name("speed")
        .defaultValue(0.25)
        .min(0.01)
        .sliderMax(1)
        .visible(followEnabled::get)
        .build()
    );

    private final Setting<Double> stopDistance = sgFollow.add(new DoubleSetting.Builder()
        .name("stop-buffer")
        .description("Distance to maintain from the target.")
        .defaultValue(2)
        .min(0)
        .sliderMax(10)
        .visible(followEnabled::get)
        .build()
    );

    // MESSAGE SETTINGS
    private final Setting<Boolean> messageEnabled = sgMessage.add(new BoolSetting.Builder()
        .name("enabled")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgMessage.add(new IntSetting.Builder()
        .name("delay")
        .defaultValue(100)
        .min(1)
        .sliderMax(200)
        .visible(messageEnabled::get)
        .build()
    );

    private final Setting<List<String>> messages = sgMessage.add(new StringListSetting.Builder()
        .name("messages")
        .defaultValue(
            "/msg {player} lemme see that ass!",
            "/msg {player} I'm going to fuck you in the ass till you cry in pleasure!",
            "/msg {player} esex?"
        )
        .visible(messageEnabled::get)
        .build()
    );

    // MISC SETTINGS
    private final Setting<Boolean> autoShift = sgMisc.add(new BoolSetting.Builder()
        .name("auto-shift")
        .description("Automatically holds sneak with toggle delay.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> shiftDelay = sgMisc.add(new IntSetting.Builder()
        .name("shift-delay")
        .description("Ticks between auto-shift toggles.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .visible(autoShift::get)
        .build()
    );

    private final Setting<Double> shiftMaxDistance = sgMisc.add(new DoubleSetting.Builder()
        .name("shift-max-distance")
        .description("Mimumum distance to target before auto-shift stops activating.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .visible(autoShift::get)
        .build()
    );

    private final Setting<Boolean> cheating = sgMisc.add(new BoolSetting.Builder()
        .name("fly-to-target")
        .description("Fly hack to the player.")
        .defaultValue(false)
        .build()
    );

    // INTERNAL
    private int timer = 0;
    private int msgIndex = 0;
    private boolean isShifting = false;
    private int shiftTimer = 0;

    public AutoGroom() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "AutoGroom",
            "Follows the nearest player, sends messages, and does the shifting.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        PlayerEntity target = getClosestPlayer();
        if (target == null) return;

        if (followEnabled.get()) followPlayer(target);
        if (messageEnabled.get()) handleMessaging(target, target);

        handleAutoShift(target);
    }

    private PlayerEntity getClosestPlayer() {
        ClientPlayerEntity self = mc.player;

        return mc.world.getPlayers().stream()
            .filter(p -> p != self)
            .filter(p -> !p.isSpectator())
            .filter(p -> p.distanceTo(self) <= followRange.get())
            .min(Comparator.comparingDouble(p -> p.distanceTo(self)))
            .orElse(null);
    }

    private void followPlayer(PlayerEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dy = target.getY() + target.getEyeHeight(mc.player.getPose()) - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = target.getZ() - mc.player.getZ();

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < stopDistance.get() && !cheating.get()) return;

        double moveSpeed = speed.get();

        double velX = (dx / horizontalDistance) * moveSpeed;
        double velZ = (dz / horizontalDistance) * moveSpeed;
        double velY = mc.player.getVelocity().y;

        if (cheating.get()) {
            // Fly directly toward target (up or down)
            double totalDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            velX = (dx / totalDistance) * moveSpeed;
            velY = (dy / totalDistance) * moveSpeed;
            velZ = (dz / totalDistance) * moveSpeed;
        } else {
            // Normal jumping if there's a block in the way
            if (!mc.player.isOnGround()) {
                velY = mc.player.getVelocity().y; // keep current vertical motion
            } else {
                // check block above
                if (!mc.world.isAir(mc.player.getBlockPos().up())) {
                    mc.player.jump();
                }
            }
        }

        mc.player.setVelocity(velX, velY, velZ);
    }

    private void handleMessaging(PlayerEntity target, PlayerEntity ignored) {
        timer++;

        if (timer < delay.get()) return;
        timer = 0;

        List<String> msgs = messages.get();
        if (msgs.isEmpty()) return;

        if (msgIndex >= msgs.size() || msgIndex < 0) msgIndex = 0;

        String msg = msgs.get(msgIndex);

        if (msg == null || msg.trim().isEmpty()) {
            msgIndex = (msgIndex + 1) % msgs.size();
            return;
        }

        msg = msg.trim().replace("{player}", target.getName().getString());

        if (msg.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(msg.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(msg);
        }

        msgIndex = (msgIndex + 1) % msgs.size();
    }

    private void handleAutoShift(PlayerEntity target) {
        if (!autoShift.get() || target == null) {
            isShifting = false;
            mc.options.sneakKey.setPressed(false);
            shiftTimer = 0;
            return;
        }

        double distance = mc.player.distanceTo(target);
        if (distance > shiftMaxDistance.get()) {
            isShifting = false;
            mc.options.sneakKey.setPressed(false);
            shiftTimer = 0;
            return;
        }

        shiftTimer++;
        if (shiftTimer >= shiftDelay.get()) {
            isShifting = !isShifting;
            mc.options.sneakKey.setPressed(isShifting);
            shiftTimer = 0;
        }
    }

    @Override
    public void onDeactivate() {
        // Ensure shift is released when module disables
        mc.options.sneakKey.setPressed(false);
    }
}