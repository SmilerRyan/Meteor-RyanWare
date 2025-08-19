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
import smilerryan.ryanware.modules_1.*;
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
        Modules.get().add(new _example());
        Modules.get().add(new AskOllama());
        Modules.get().add(new Aura());
        Modules.get().add(new AutoFollowItems());
        Modules.get().add(new AutoHighwayBuilder());
        Modules.get().add(new AutoMineNearby());
        Modules.get().add(new AutoResponder());
        Modules.get().add(new AutoTotem());
        Modules.get().add(new BeehiveCoordLogger());
        Modules.get().add(new BritishChat());
        Modules.get().add(new ClickTP());
        Modules.get().add(new CommandAura());
        Modules.get().add(new CoordNotifier());
        Modules.get().add(new CringeDetector());
        Modules.get().add(new CrystalAura());
        Modules.get().add(new CrystalAura2());
        Modules.get().add(new CrystalAura3());
        Modules.get().add(new DeathCommands());
        Modules.get().add(new ElytraFakeRockets());
        Modules.get().add(new Excavator());
        Modules.get().add(new FocusCommands());
        Modules.get().add(new FullBright());
        Modules.get().add(new LookDownDropper());
        Modules.get().add(new MaxMaceKill());
        Modules.get().add(new MorePing());
        Modules.get().add(new NoBlockDamage());
        Modules.get().add(new NoItemUsageCooldown());
        Modules.get().add(new PlayerShapeESP());
        Modules.get().add(new Radio());
        Modules.get().add(new Recorder());
        Modules.get().add(new RedirectMsgCommands());
        Modules.get().add(new RemoteViewProxyServer());
        Modules.get().add(new RemoteViewWebServer());
        Modules.get().add(new SkinBlinker());
        Modules.get().add(new TabSortedByPing());
        Modules.get().add(new TntCleaner());
        Modules.get().add(new TotemBypass());
        Modules.get().add(new WorldDownloader());
        // ...

        // Register M1 modules
        // Assume these are done and never to be touched again
        Modules.get().add(new M1_AntiHack());
        Modules.get().add(new M1_AtSomeone());
        Modules.get().add(new M1_ChatCleanup());
        Modules.get().add(new M1_ChatEncryption());
        Modules.get().add(new M1_ChatTranslator());
        Modules.get().add(new M1_CompletionCrash());
        Modules.get().add(new M1_DeathCoordsMessage());
        Modules.get().add(new M1_ElytraFly());
        Modules.get().add(new M1_f3_number_hider());
        Modules.get().add(new M1_ForceColoredChat());
        Modules.get().add(new M1_ForceOpenTab());
        Modules.get().add(new M1_NiceFlight());
        Modules.get().add(new M1_NoAttackDamage());
        Modules.get().add(new M1_PublicChatTags());
        Modules.get().add(new M1_RedirectPublicChat());
        Modules.get().add(new M1_UserLookups());

        // Register commands
        Commands.add(new Command_GMC());
        Commands.add(new Command_Note());
        Commands.add(new Command_RandomNumber());
        Commands.add(new Command_TAMsg1());        
        
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