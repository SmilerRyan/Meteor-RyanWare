package death.effects;

import java.util.Random;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

@Environment(EnvType.CLIENT)
public class DeatheffectsClient implements ClientModInitializer {
   private final Random random = new Random();

   public void onInitializeClient() {
      ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
      });
   }
}
