package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.MeteorClient;

import smilerryan.ryanware.RyanWare;

public class Radio extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Radios> radioChannel = sgGeneral.add(new EnumSetting.Builder<Radios>()
        .name("Radio Channel")
        .defaultValue(Radios.Radio1)
        .build()
    );

    private final Setting<String> customUrl = sgGeneral.add(new StringSetting.Builder()
        .name("Custom URL")
        .description("Only used if Custom is selected.")
        .defaultValue("")
        .visible(() -> radioChannel.get() == Radios.Custom)
        .build()
    );

    private final Setting<Integer> volume = sgGeneral.add(new IntSetting.Builder()
        .name("Volume")
        .description("Adjusts the volume of the radio.")
        .defaultValue(50)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private Process ffplayProcess;
    private String currentUrl = "";
    private int lastVolume = -1;

    public Radio() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Radio", "It's a fucking in-game radio!");
    }

    @Override
    public void onActivate() {
        startRadio();
    }

    @Override
    public void onDeactivate() {
        stopRadio();
    }

    @EventHandler
    private void onTick(TickEvent.Pre e) {
        String newUrl = getActiveUrl();
        int vol = volume.get();

        if (!newUrl.equals(currentUrl) || vol != lastVolume) {
            currentUrl = newUrl;
            lastVolume = vol;
            stopRadio();
            startRadio();
            return;
        }

        if (ffplayProcess != null && !ffplayProcess.isAlive()) {
            stopRadio();
            startRadio();
        }
    }

    private String getActiveUrl() {
        if (radioChannel.get() == Radios.Custom) {
            String custom = customUrl.get().trim();
            return custom.isEmpty() ? "" : custom;
        }
        return radioChannel.get().URL;
    }

    private void startRadio() {
        currentUrl = getActiveUrl();
        if (currentUrl.isEmpty()) return;
        try {
            ffplayProcess = new ProcessBuilder(
                "ffplay",
                "-nodisp",
                "-autoexit",
                "-loglevel", "quiet",
                "-volume", String.valueOf(volume.get()),
                currentUrl
            ).start();
        } catch (Exception e) {
            error("Failed to start ffplay. Make sure ffplay is installed and in your PATH.");
            toggle();
        }
    }

    private void stopRadio() {
        if (ffplayProcess != null) {
            ffplayProcess.destroy();
            ffplayProcess = null;
        }
    }

    public enum Radios {
        Radio1("https://icast.connectmedia.hu/5202/live.mp3"),
        SlagerFM("https://slagerfm.netregator.hu:7813/slagerfm128.mp3"),
        RetroRadio("https://icast.connectmedia.hu/5002/live.mp3"),
        BestFM("https://icast.connectmedia.hu/5102/live.mp3"),
        RockFM("https://icast.connectmedia.hu/5301/live.mp3"),
        KossuthRadio("https://icast.connectmedia.hu/4736/mr1.mp3"),
        CapitalUK("https://icecast.thisisdax.com/CapitalUK"),
        HeartLondon("https://icecast.thisisdax.com/HeartLondonMP3"),
        SmoothLondon("https://icecast.thisisdax.com/SmoothLondonMP3"),
        Custom("");

        public final String URL;
        Radios(String url) { this.URL = url; }
    }
}