package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletionStage;

public class AutoOBSReplays extends Module {

    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Boolean> saveKills = sgGeneral.add(new BoolSetting.Builder()
        .name("save-kills")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveDeaths = sgGeneral.add(new BoolSetting.Builder()
        .name("save-deaths")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .defaultValue(20)
        .min(0)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> host = sgGeneral.add(new StringSetting.Builder()
        .name("obs-host")
        .defaultValue("ws://127.0.0.1:4455")
        .build()
    );

    private final Set<Integer> recentlyHit = new HashSet<>();
    private final Set<Integer> processedDead = new HashSet<>();

    private int timer = 0;
    private boolean wasAlive = true;

    public AutoOBSReplays() {
        super(RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Auto-OBS-Replays",
            "Saves OBS replay on kills or deaths (stateless connection).");
    }

    @Override
    public void onActivate() {
        recentlyHit.clear();
        processedDead.clear();
        wasAlive = true;
        timer = 0;
    }

    // ---------------- ATTACK TRACK ----------------

    @EventHandler
    private void onAttack(AttackEntityEvent e) {
        if (e.entity instanceof PlayerEntity) {
            recentlyHit.add(e.entity.getId());
        }
    }

    // ---------------- TICK LOOP ----------------

    @EventHandler
    private void onTick(TickEvent.Post e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        if (timer > 0) timer--;

        // --- YOUR death
        boolean alive = mc.player.getHealth() > 0 && !mc.player.isDead();

        if (wasAlive && !alive) {
            if (saveDeaths.get() && timer <= 0) {
                triggerReplay("Death");
                timer = cooldown.get();
            }
        }

        wasAlive = alive;

        // --- kills
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;

            int id = player.getId();

            if ((player.isDead() || player.getHealth() <= 0)) {
                if (!processedDead.contains(id) && recentlyHit.contains(id)) {

                    if (saveKills.get() && timer <= 0) {
                        triggerReplay("Kill");
                        timer = cooldown.get();
                    }

                    processedDead.add(id);
                }
            }
        }

        // cleanup
        if (recentlyHit.size() > 100) recentlyHit.clear();
        if (processedDead.size() > 200) processedDead.clear();
    }

    // ---------------- OBS (STATELESS) ----------------

    private void triggerReplay(String reason) {
        if (debug.get()) info("Triggering OBS (" + reason + ")");

        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(host.get()), new OBSHandler(reason))
            .exceptionally(err -> {
                if (debug.get()) info("OBS connect failed: " + err.getMessage());
                return null;
            });
    }

    // ---------------- INNER HANDLER ----------------

    private class OBSHandler implements WebSocket.Listener {
        private final String reason;
        private WebSocket ws;
        private boolean sent = false;

        OBSHandler(String reason) {
            this.reason = reason;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.ws = webSocket;
            if (debug.get()) info("OBS Connected (" + reason + ")");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String msg = data.toString();

            if (debug.get()) info("OBS RECV: " + msg);

            // Hello → Identify (no auth)
            if (msg.contains("\"op\":0")) {
                String identify = "{"
                    + "\"op\":1,"
                    + "\"d\":{"
                    + "\"rpcVersion\":1"
                    + "}}";

                ws.sendText(identify, true);
                if (debug.get()) info("Sent Identify");
            }

            // Identified → send request ONCE then close
            if (!sent && msg.contains("\"op\":2")) {
                sent = true;

                String request = "{"
                    + "\"op\":6,"
                    + "\"d\":{"
                    + "\"requestType\":\"SaveReplayBuffer\","
                    + "\"requestId\":\"1\""
                    + "}}";

                ws.sendText(request, true);

                if (debug.get()) info("Replay saved (" + reason + ")");

                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            }

            webSocket.request(1);
            return null;
        }
    }
}