package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;

public class NotesSettings extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> path = sgGeneral.add(new StringSetting.Builder()
        .name("path")
        .description("The path to save notes to.")
        .defaultValue("notes.txt")
        .build()
    );

    public NotesSettings() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Notes-Settings", "Settings for the .note command.");
    }
}
