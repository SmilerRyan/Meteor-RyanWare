package smilerryan.ryanware.modules_1;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class M1_f3_number_hider extends Module {

    public M1_f3_number_hider() {
        super(RyanWare.CATEGORY_M1, RyanWare.modulePrefix + "M1-F3-Number-Hider", "Hide coordinates in F3 menu replacing them with asterisks.");
    }

    public static M1_f3_number_hider INSTANCE;

    @Override
    public void onActivate() {
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        INSTANCE = null;
    }

    public String hideCoordinateString(String text) {
        if (!isActive()) return text;

        StringBuffer result = new StringBuffer();
        Matcher matcher = Pattern.compile("\\d+").matcher(text);

        while (matcher.find()) {
            String match = matcher.group();
            String replacement = "*".repeat(match.length());
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }

}