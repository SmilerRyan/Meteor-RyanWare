package smilerryan.ryanware.modules_standard;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import smilerryan.ryanware.RyanWare;

import java.util.List;

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

    // Ollama
    private final SettingGroup sg_Ollama = settings.createGroup("Ollama");

    public final Setting<String> s_Ollama_Url = sg_Ollama.add(new StringSetting.Builder()
        .name("ollama-url")
        .description("Base URL of the Ollama server.")
        .defaultValue("http://localhost:11434")
        .build()
    );

    // Chat Masking
    private final SettingGroup sg_ChatMasking = settings.createGroup("Chat Masking");

    public enum ChatMaskMode {
        STAR_REPLACEMENT,
        BOX_OVERLAY
    }

    public final Setting<Boolean> s_MaskChatEnabled = sg_ChatMasking.add(
        new BoolSetting.Builder()
            .name("Enabled")
            .description("Visually masks your chat input after the prefixes.")
            .defaultValue(true)
            .build()
    );

    public final Setting<ChatMaskMode> s_MaskChatMode = sg_ChatMasking.add(
        new EnumSetting.Builder<ChatMaskMode>()
            .name("mode")
            .description("Chat masking mode.")
            .defaultValue(ChatMaskMode.BOX_OVERLAY)
            .build()
    );

    public final Setting<SettingColor> s_BoxOverlayColor = sg_ChatMasking.add(
        new ColorSetting.Builder()
            .name("box-overlay-color")
            .description("Color used for Box Overlay mode.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> s_MaskChatMode.get() == ChatMaskMode.BOX_OVERLAY)
            .build()
    );

    public final Setting<List<String>> s_MaskChatPrefixes = sg_ChatMasking.add(
        new StringListSetting.Builder()
            .name("prefixes")
            .description("Prefixes to obfuscate.")
            .defaultValue(List.of(
                "/l ",
                "/login ",
                "/reg ",
                "/register ",
                "/changepass ",
                "/changepassword "
            ))
            .build()
    );

}