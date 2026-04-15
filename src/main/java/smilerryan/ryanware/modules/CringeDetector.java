package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.text.Text;
import java.util.List;

public class CringeDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> cringeWords = sgGeneral.add(new StringListSetting.Builder()
        .name("cringe-words")
        .description("Words that trigger the cringe detector.")
        .defaultValue("sus", "gyatt", "rizz", "ez", "lel", "skill issue", "cry about it", "slow down", "get good", "ratio", "cope", "seethe", "mald", "based", "nig")
        .build()
    );

    public CringeDetector() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Cringe-Detector", "Detects and logs cringe words in chat messages.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        String message = event.getMessage().getString().toLowerCase();

        int count = 0;
        for (String word : cringeWords.get()) {
            if (message.contains(word.toLowerCase())) count++;
        }

        if (count > 0) {
            info("Detected " + count + " cringy messages so far.");
        }

    }
}
