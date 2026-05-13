package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import smilerryan.ryanware.RyanWare;

public class Settings extends Module {

    public Settings() {
        super(RyanWare.CATEGORY_STANDARD, RyanWare.modulePrefix_standard + "Settings", "Settings");
    }

    @Override
    public void onActivate() {
        toggle();
    }

    // Note Command
    private final SettingGroup sg_Note_Command = settings.createGroup("Note Command");

    public final Setting<String> s_Note_Command_Path = sg_Note_Command.add(new StringSetting.Builder()
        .name("path")
        .description("The path to save notes to.")
        .defaultValue("~\\Desktop\\notes.txt")
        .build()
    );

}
