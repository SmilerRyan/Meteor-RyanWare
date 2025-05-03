package smilerryan.ryanware;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smilerryan.ryanware.modules.*;

public class RyanWare extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("RyanWare");
    public static final Category CATEGORY = new Category("RyanWare", Items.SPONGE.getDefaultStack());

    @Override
    public void onInitialize() {
        LOG.info("Initializing RyanWare Addon.");
        Modules.get().add(new CompletionCrash());
        Modules.get().add(new Aura());
        Modules.get().add(new ClickTP());
        Modules.get().add(new MorePing());
        Modules.get().add(new FocusCommands());
        Modules.get().add(new CommandAura());
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
        String commit = FabricLoader
            .getInstance()
            .getModContainer("ryanware")
            .get().getMetadata()
            .getCustomValue("github:sha")
            .getAsString();
        return commit.isEmpty() ? null : commit.trim();

    }

    public String getPackage() {
        return "smilerryan.ryanware";
    }
}
