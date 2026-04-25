package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletionStage;

public class AutoOBSReplays extends Module {

    private final SettingGroup sgKills = settings.createGroup("Kills");
    private final SettingGroup sgDeaths = settings.createGroup("Deaths");
    private final SettingGroup sgChat = settings.createGroup("Chat");
    private final SettingGroup sgObs = settings.createGroup("OBS");

    private final Setting<Boolean> saveKills = sgKills.add(new BoolSetting.Builder()
        .name("kills-enabled")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveKillsPlayersOnly = sgKills.add(new BoolSetting.Builder()
        .name("kills-players-only")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> killDelay = sgKills.add(new IntSetting.Builder()
        .name("kills-delay-ticks")
        .defaultValue(20)
        .min(0)
        .build()
    );

    private final Setting<Integer> killsAssumeDeadDelay = sgKills.add(new IntSetting.Builder()
        .name("kills-assume-killed-delay-ticks")
        .defaultValue(20)
        .min(0)
        .build()
    );

    private final Setting<Boolean> saveDeaths = sgDeaths.add(new BoolSetting.Builder()
        .name("deaths-enabled")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> deathDelay = sgDeaths.add(new IntSetting.Builder()
        .name("deaths-delay-ticks")
        .defaultValue(35)
        .min(0)
        .build()
    );

    private final Setting<List<String>> fallbackKeywords = sgChat.add(new StringListSetting.Builder()
        .name("chat-keywords")
        .defaultValue("slain by", "killed by", "you won", "you lost")
        .build()
    );

    private final Setting<Integer> chatDelay = sgChat.add(new IntSetting.Builder()
        .name("chat-delay-ticks")
        .defaultValue(35)
        .min(0)
        .build()
    );

    private final Setting<String> host = sgObs.add(new StringSetting.Builder()
        .name("OBS-host")
        .defaultValue("ws://127.0.0.1:4455")
        .build()
    );

    private final Map<Integer, Integer> lastHitTick = new HashMap<>();
    private final Map<Integer, Integer> entityLastSeenTick = new HashMap<>();

    private final List<ScheduledTrigger> queue = new ArrayList<>();

    private int tickCounter = 0;
    private boolean wasAlive = true;

    public AutoOBSReplays() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Auto-OBS-Replays", "Saves OBS replay on kills, deaths, or chat keywords.");
    }

    @Override
    public void onActivate() {
        lastHitTick.clear();
        entityLastSeenTick.clear();
        queue.clear();
        tickCounter = 0;
        wasAlive = true;
    }


    @EventHandler
    private void onAttack(AttackEntityEvent e) {
        if (e.entity != null) {
            if (saveKillsPlayersOnly.get() && !(e.entity instanceof PlayerEntity)) return;
            lastHitTick.put(e.entity.getId(), tickCounter);
        }
    }

    @EventHandler
    private void onChat(ReceiveMessageEvent e) {
        String msg = e.getMessage().getString().toLowerCase();

        for (String key : fallbackKeywords.get()) {
            if (msg.contains(key.toLowerCase())) {
                schedule(chatDelay.get());
                break;
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        tickCounter++;

        // Death - GUI detection
        if (saveDeaths.get() && mc.currentScreen instanceof DeathScreen) {
            schedule(deathDelay.get());
        }

        // Death - health detection
        boolean alive = mc.player.getHealth() > 0 && !mc.player.isDead();
        if (wasAlive && !alive && saveDeaths.get()) {
            schedule(deathDelay.get());
        }
        wasAlive = alive;

        // kill detection - existence after hit
        for (Entity entity : mc.world.getEntities()) {
            entityLastSeenTick.put(entity.getId(), tickCounter);
        }
        Iterator<Map.Entry<Integer, Integer>> it = lastHitTick.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int entityId = entry.getKey();
            int hitTick = entry.getValue();
            Entity entity = mc.world.getEntityById(entityId);
            if (entity == null) {
                int lastSeen = entityLastSeenTick.getOrDefault(entityId, hitTick);
                if (tickCounter - hitTick < killsAssumeDeadDelay.get() && saveKills.get()) {
                    schedule(killDelay.get());
                }
                it.remove();
                entityLastSeenTick.remove(entityId);
                continue;
            }
            if (entity instanceof PlayerEntity player) {
                if (player.getHealth() <= 0 || player.isDead()) {
                    if (saveKills.get() && tickCounter - hitTick < killsAssumeDeadDelay.get()) {
                        schedule(killDelay.get());
                    }
                    it.remove();
                }
            }
        }
        // process queue
        Iterator<ScheduledTrigger> q = queue.iterator();
        while (q.hasNext()) {
            ScheduledTrigger t = q.next();
            t.ticks--;

            if (t.ticks <= 0) {
                triggerReplay();
                q.remove();
            }
        }
        if (lastHitTick.size() > 200) lastHitTick.clear();
        if (entityLastSeenTick.size() > 300) entityLastSeenTick.clear();
    }

    private void schedule(int delayTicks) {
        queue.add(new ScheduledTrigger(delayTicks));
    }

    private static class ScheduledTrigger {
        int ticks;
        ScheduledTrigger(int ticks) {this.ticks = ticks;}
    }

    private void triggerReplay() {
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(URI.create(host.get()), new OBSHandler())
            .exceptionally(err -> {
                err.printStackTrace();
                return null;
            });
    }

    private class OBSHandler implements WebSocket.Listener {
        private WebSocket ws;
        private boolean sent = false;

        @Override
        public void onOpen(WebSocket webSocket) {
            this.ws = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String msg = data.toString();

            if (msg.contains("\"op\":0")) {
                ws.sendText("{\"op\":1,\"d\":{\"rpcVersion\":1}}", true);
            }

            if (!sent && msg.contains("\"op\":2")) {
                sent = true;
                ws.sendText("{\"op\":6,\"d\":{\"requestType\":\"SaveReplayBuffer\",\"requestId\":\"1\"}}", true);
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            }

            webSocket.request(1);
            return null;
        }
    }
}