package death.effects;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Deatheffects implements ModInitializer {
   public static final String MOD_ID = "deatheffects";
   public static final Logger LOGGER = LoggerFactory.getLogger("deatheffects");

   public void onInitialize() {
      LOGGER.info("Hello Fabric world!");
   }
}
