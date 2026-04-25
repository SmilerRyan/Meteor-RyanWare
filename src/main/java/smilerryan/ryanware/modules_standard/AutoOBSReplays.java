package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
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

    private final SettingGroup sgKills = settings.createGroup("Kills");
    private final SettingGroup sgDeaths = settings.createGroup("Deaths");
    private final SettingGroup sgChat = settings.createGroup("Chat");
    private final SettingGroup sgObs = settings.createGroup("OBS");


    private final Setting<Boolean> saveKills = sgKills.add(new BoolSetting.Builder()
        .name("kills-enabled")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> killDelay = sgKills.add(new IntSetting.Builder()
        .name("kills-delay-ticks")
        .defaultValue(35)
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
        .description("Triggers OBS if these appear in chat.")
        .defaultValue("slain by", "killed by", "you won", "you lost")
        .build()
    );

    private final Setting<Integer> sgChat = sgChat.add(new IntSetting.Builder()
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

    private final Set<Integer> recentlyHit = new HashSet<>();
    private final Set<Integer> processedDead = new HashSet<>();
    private final List<ScheduledTrigger> queue = new ArrayList<>();
    private boolean wasAlive = true;

    public AutoOBSReplays() {
        super(RyanWare.CATEGORY_STANDARD,
            RyanWare.modulePrefix_standard + "Auto-OBS-Replays", "Saves OBS replay on kills, deaths, or chat keywords.");
    }

    @Override
    public void onActivate() {
        recentlyHit.clear();
        processedDead.clear();
        queue.clear();
        wasAlive = true;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent e) {
        if (e.entity instanceof PlayerEntity) {
            recentlyHit.add(e.entity.getId());
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
        boolean alive = mc.player.getHealth() > 0 && !mc.player.isDead();
        if (wasAlive && !alive && saveDeaths.get()) {
            schedule(deathDelay.get());
        }
        wasAlive = alive;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;
            int id = player.getId();
            if ((player.isDead() || player.getHealth() <= 0)) {
                if (!processedDead.contains(id) && recentlyHit.contains(id)) {
                    if (saveKills.get()) {
                        schedule(killDelay.get());
                    }
                    processedDead.add(id);
                }
            }
        }

        Iterator<ScheduledTrigger> it = queue.iterator();
        while (it.hasNext()) {
            ScheduledTrigger t = it.next();
            t.ticks--;
            if (t.ticks <= 0) {
                triggerReplay();
                it.remove();
            }
        }

        if (recentlyHit.size() > 100) recentlyHit.clear();
        if (processedDead.size() > 200) processedDead.clear();
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