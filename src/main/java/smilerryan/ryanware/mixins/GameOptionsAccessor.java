package smilerryan.ryanware.mixins;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.entity.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(GameOptions.class)
public interface GameOptionsAccessor {
    @Accessor("enabledPlayerModelParts")
    Set<PlayerModelPart> getEnabledPlayerModelParts();
}