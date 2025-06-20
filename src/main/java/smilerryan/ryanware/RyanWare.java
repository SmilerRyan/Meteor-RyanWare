package smilerryan.ryanware;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.commands.Commands;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smilerryan.ryanware.modules.*;
import smilerryan.ryanware.modules_plus.*;
import smilerryan.ryanware.commands.*;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RyanWare extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("RyanWare");

    public static String addonName = "RyanWare";
    public static String modulePrefix = "RyanWare";
    public static Item iconItem = Items.SPONGE;
    public static Category CATEGORY;

    static {
        try {
            FabricLoader.getInstance().getModContainer("ryanware").ifPresent(container -> {
                try {
                    LoaderModMetadata meta = (LoaderModMetadata) container.getMetadata();
                    if (meta.getCustomValue("ryanware:addon-name") != null) {
                        addonName = meta.getCustomValue("ryanware:addon-name").getAsString();
                    }
                    if (meta.getCustomValue("ryanware:module-prefix") != null) {
                        modulePrefix = meta.getCustomValue("ryanware:module-prefix").getAsString();
                    }
                    if (meta.getCustomValue("ryanware:icon") != null) {
                        String iconName = meta.getCustomValue("ryanware:icon").getAsString().toLowerCase(Locale.ROOT);
                        Identifier id = Identifier.of("minecraft", iconName);
                        iconItem = Registries.ITEM.getOrEmpty(id).orElse(Items.SPONGE);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to read mod metadata or override values.", e);
                }
            });
        } catch (Exception e) {
            LOG.error("Failed to access mod container", e);
        }

        CATEGORY = new Category(addonName, iconItem.getDefaultStack());
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing {} Addon.", addonName);

        // Register modules
        Modules.get().add(new Aura());
        Modules.get().add(new BeehiveCoordLogger());
        Modules.get().add(new BritishChat());
        Modules.get().add(new ChatCleanup());
        Modules.get().add(new ChatEncryption());
        Modules.get().add(new ClickTP());
        Modules.get().add(new CommandAura());
        Modules.get().add(new CompletionCrash());
        Modules.get().add(new CoordNotifier());
        Modules.get().add(new CringeDetector());
        Modules.get().add(new DeathCommands());
        Modules.get().add(new Excavator());
        Modules.get().add(new FocusCommands());
        Modules.get().add(new ForceOpenTab());
        Modules.get().add(new FullBright());
        Modules.get().add(new UserLookups());
        Modules.get().add(new NiceFlight());
        Modules.get().add(new NoDamage());
        Modules.get().add(new PublicChatTags());
        Modules.get().add(new RedirectPublicChat());
        Modules.get().add(new TabSortedByPing());
        Modules.get().add(new TotemBypass());

        // Register Plus modules
        Modules.get().add(new AutoResponder());
        Modules.get().add(new CrystalAura());
        Modules.get().add(new MaxMaceKill());
        Modules.get().add(new MorePing());

        // Register commands
        // Commands.add(new NoteCommand());
        Commands.add(new Command_RandomNumber());
        // Commands.add(new TAMsgCommand());        
        
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getWebsite() {
        return "https://github.com/SmilerRyan/ryanware";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("SmilerRyan", "ryanware");
    }

    @Override
    public String getCommit() {
        return FabricLoader
            .getInstance()
            .getModContainer("ryanware")
            .get().getMetadata()
            .getCustomValue("github:sha")
            .getAsString().trim();
    }

    public String getPackage() {
        return "smilerryan.ryanware";
    }
}