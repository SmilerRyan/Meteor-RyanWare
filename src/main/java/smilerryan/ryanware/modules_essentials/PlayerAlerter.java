package smilerryan.ryanware.modules_essentials;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;

import smilerryan.ryanware.RyanWare;

import java.util.*;

public class PlayerAlerter extends Module {
    private final SettingGroup sgVisual = settings.createGroup("Visual Range");
    private final SettingGroup sgTab = settings.createGroup("Tab List");

    private final Setting<Boolean> vrEnabled = sgVisual.add(new BoolSetting.Builder()
        .name("vr-enable")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vrChat = sgVisual.add(new BoolSetting.Builder()
        .name("vr-chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vrSound = sgVisual.add(new BoolSetting.Builder()
        .name("vr-sound")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vrIgnoreFriends = sgVisual.add(new BoolSetting.Builder()
        .name("vr-ignore-friends")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vrIgnoreFakePlayers = sgVisual.add(new BoolSetting.Builder()
        .name("vr-ignore-fake-players")
        .defaultValue(true)
        .description("Ignores fake players (e.g. NPCs) in your visual range.")
        .build()
    );

    private final Setting<Integer> vrDelay = sgVisual.add(new IntSetting.Builder()
        .name("vr-leave-delay-ms")
        .defaultValue(500)
        .min(0)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Boolean> tabEnabled = sgTab.add(new BoolSetting.Builder()
        .name("tab-enable")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tabChat = sgTab.add(new BoolSetting.Builder()
        .name("tab-chat")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tabSound = sgTab.add(new BoolSetting.Builder()
        .name("tab-sound")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tabIgnoreFriends = sgTab.add(new BoolSetting.Builder()
        .name("tab-ignore-friends")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tabIgnoreFakePlayers = sgTab.add(new BoolSetting.Builder()
        .name("tab-ignore-fake-players")
        .defaultValue(true)
        .description("Ignores fake players (e.g. NPCs) in the tab list.")
        .build()
    );

    private final Setting<Integer> tabDelay = sgTab.add(new IntSetting.Builder()
        .name("tab-leave-delay-ms")
        .defaultValue(500)
        .min(0)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Boolean> tabShowReenter = sgTab.add(new BoolSetting.Builder()
        .name("tab-show-reenter")
        .defaultValue(false)
        .description("If true, shows 'Re-entered tab' when a player comes back within delay.")
        .build()
    );

    private final Set<String> inVisual = new HashSet<>();
    private final Map<String, Long> vrLeftTimes = new HashMap<>();

    private final Set<String> inTab = new HashSet<>();
    private final Map<String, Long> tabLeftTimes = new HashMap<>();


    public PlayerAlerter() {
        super(RyanWare.CATEGORY_ESSENTIALS, RyanWare.modulePrefix_essentials + "Player-Alerter", "Alerts you when players enter/leave range or tab.");
    }

    private boolean isFriend(String name) {
        return Friends.get().get(name) != null;
    }

    private boolean isFakePlayer(String name) {
        String stripped = name.replaceAll("[§&].", "");

        boolean nameIsEmpty = stripped.isEmpty();
        boolean nameStartsWithCIT = stripped.startsWith("CIT-");

        return nameIsEmpty || nameStartsWithCIT;
    }


    private void ping(boolean entering, boolean flashed) {
        if (mc.player == null) return;

        if (flashed) {
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 0.6f);
            return;
        }

        if (entering)
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        else
            mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 0.7f);
    }


    private void notify(boolean chat, boolean sound, boolean entering, boolean flashed, String msg) {
        if (chat) info(msg);
        if (sound) ping(entering, flashed);
    }


    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        // ---------- VISUAL RANGE ----------
        if (vrEnabled.get()) {
            List<String> visible = new ArrayList<>();

            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;

                String name = p.getName().getString();
                visible.add(name);

                if (vrIgnoreFriends.get() && isFriend(name)) continue;
                if (vrIgnoreFakePlayers.get() && isFakePlayer(name)) continue;

                if (!inVisual.contains(name)) {
                    inVisual.add(name);
                    notify(vrChat.get(), vrSound.get(), true, false,
                        "Entered range: " + name);
                }
            }

            for (String name : new HashSet<>(inVisual)) {
                if (!visible.contains(name)) {
                    vrLeftTimes.putIfAbsent(name, now);

                    if (now - vrLeftTimes.get(name) >= vrDelay.get()) {
                        inVisual.remove(name);
                        vrLeftTimes.remove(name);

                        if (!(vrIgnoreFriends.get() && isFriend(name))) {
                            notify(vrChat.get(), vrSound.get(), false, false,
                                "Left range: " + name);
                        }
                    }
                } else {
                    vrLeftTimes.remove(name);
                }
            }
        }


        // ---------- TAB LIST ----------
        if (tabEnabled.get()) {
            List<String> tab = new ArrayList<>();

            // gather current tab and process joins
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String name = e.getProfile().getName();
                tab.add(name);

                if (tabIgnoreFriends.get() && isFriend(name)) continue;
                if (tabIgnoreFakePlayers.get() && isFakePlayer(name)) continue;

                // if we previously thought they left, and they re-appeared:
                if (tabLeftTimes.containsKey(name)) {
                    long leftTime = tabLeftTimes.get(name);
                    // re-enter within delay
                    if (now - leftTime < tabDelay.get()) {
                        if (tabShowReenter.get()) {
                            notify(tabChat.get(), tabSound.get(), true, false,
                                "Re-entered tab: " + name);
                        }
                        // they rejoined; remove left timestamp and mark present
                        tabLeftTimes.remove(name);
                        inTab.add(name);
                        continue;
                    } else {
                        // left long enough ago — treat as flash if desired (kept behavior)
                        notify(tabChat.get(), tabSound.get(), false, true,
                            "flashed tab: " + name);
                        tabLeftTimes.remove(name);
                        inTab.add(name);
                        continue;
                    }
                }

                // normal join (not tracked as inTab)
                if (!inTab.contains(name)) {
                    notify(tabChat.get(), tabSound.get(), true, false,
                        "Entered tab: " + name);
                    inTab.add(name);
                }
            }

            // detect leaves: remove from inTab immediately and record leave time
            for (String name : new HashSet<>(inTab)) {
                if (!tab.contains(name)) {
                    // mark left time (if not already)
                    tabLeftTimes.putIfAbsent(name, now);
                    // remove from inTab now so future re-joins are detected
                    inTab.remove(name);
                }
            }

            // confirm leaves after delay
            Iterator<Map.Entry<String, Long>> it = tabLeftTimes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> e = it.next();
                String name = e.getKey();
                long leftTime = e.getValue();

                if (now - leftTime >= tabDelay.get()) {
                    // time passed => confirm left
                    it.remove();
                    if (!(tabIgnoreFriends.get() && isFriend(name))) {
                        notify(tabChat.get(), tabSound.get(), false, false,
                            "Left tab: " + name);
                    }
                }
            }
        }
    }
}
