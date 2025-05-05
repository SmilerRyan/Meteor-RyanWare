package smilerryan.ryanware;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
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

import java.io.*;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Properties;

public class RyanWare extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("RyanWare");

    public static String addonName = "RyanWare";
    public static String modulePrefix = "RyanWare";
    public static Item iconItem = Items.SPONGE;
    public static Category CATEGORY;

    static {
        try {
            File cfgFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ryanware.properties");

            // Create default config if missing
            if (!cfgFile.exists()) {
                Properties defaultProps = new Properties();
                defaultProps.setProperty("name", "RyanWare");
                defaultProps.setProperty("icon", "sponge");
                defaultProps.setProperty("module-prefix", "RyanWare-");
                try (FileOutputStream out = new FileOutputStream(cfgFile)) {
                    defaultProps.store(out, "RyanWare Addon Configuration");
                }
            }

            // Load config
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(cfgFile)) {
                props.load(in);
            }

            if (props.containsKey("name")) addonName = props.getProperty("name");
            if (props.containsKey("module-prefix")) modulePrefix = props.getProperty("module-prefix");

            if (props.containsKey("icon")) {
                Identifier id = Identifier.of("minecraft", props.getProperty("icon").toLowerCase(Locale.ROOT));
                iconItem = Registries.ITEM.getOrEmpty(id).orElse(Items.SPONGE);
            }
        } catch (Exception e) {
            LOG.error("Failed to load or create ryanware.properties", e);
        }

        // Runtime override of mod metadata (hacky)
        try {
            FabricLoader.getInstance().getModContainer("ryanware").ifPresent(container -> {
                try {
                    LoaderModMetadata meta = (LoaderModMetadata) container.getMetadata();

                    // Set new name
                    if (!addonName.equals("RyanWare")) {
                        var nameField = meta.getClass().getDeclaredField("name");
                        nameField.setAccessible(true);
                        nameField.set(meta, addonName);

                        // Clear authors if not the default, add current player's name as an author
                        var authorsField = meta.getClass().getDeclaredField("authors");
                        authorsField.setAccessible(true);

                        // Get current player name
                        String currentPlayerName = MinecraftClient.getInstance().getSession().getUsername();

                        // Check authors and set current player name if necessary
                        List<String> authors = (List<String>) authorsField.get(meta);
                        authorsField.set(meta, Collections.singletonList(currentPlayerName));

                        // Clear description if not default
                        var descField = meta.getClass().getDeclaredField("description");
                        descField.setAccessible(true);
                        descField.set(meta, "");
                    }
                } catch (Exception e) {
                    LOG.error("Failed to override mod metadata via reflection.", e);
                }
            });
        } catch (Exception e) {
            LOG.error("Mod metadata override failed", e);
        }

        CATEGORY = new Category(addonName, iconItem.getDefaultStack());
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing {} Addon.", addonName);
        Modules.get().add(new CompletionCrash());
        Modules.get().add(new Aura());
        Modules.get().add(new ClickTP());
        Modules.get().add(new NoDamage());
        Modules.get().add(new MorePing());
        Modules.get().add(new TabSortedByPing());
        Modules.get().add(new FocusCommands());
        Modules.get().add(new CommandAura());
        Modules.get().add(new NameMCLink());
        Modules.get().add(new CringeDetector());
        Modules.get().add(new ChatCleanup());
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
