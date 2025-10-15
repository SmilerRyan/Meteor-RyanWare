package death.effects;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3675.class_307;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class ClickTP implements ClientModInitializer {
   public static final String MOD_ID = "clicktp";
   public static final Logger LOGGER = LoggerFactory.getLogger("clicktp");
   private static class_304 teleportKeyBinding;
   private boolean isProcessing = false;

   public void onInitializeClient() {
      LOGGER.info("Initializing ClickTP mod for Minecraft 1.21.1");
      teleportKeyBinding = KeyBindingHelper.registerKeyBinding(new class_304("key.clicktp.teleport", class_307.field_1668, 76, "category.clicktp.main"));
      ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
   }

   private void onClientTick(class_310 client) {
      try {
         if (teleportKeyBinding.method_1436() && !this.isProcessing) {
            this.handleTeleport(client);
         }
      } catch (Exception var3) {
         LOGGER.error("Error in tick handler", var3);
      }

   }

   private void handleTeleport(class_310 client) {
      if (client.field_1724 != null && client.field_1687 != null) {
         this.isProcessing = true;

         try {
            class_239 hit = client.field_1765;
            if (hit == null || hit.method_17783() == class_240.field_1333) {
               this.sendMessage(client, "§cNo valid target found!");
               return;
            }

            class_243 targetPos = this.calculateSafePosition(hit);
            client.field_1724.method_5814(targetPos.field_1352, targetPos.field_1351, targetPos.field_1350);
            this.sendMessage(client, "§aTeleported successfully!");
         } catch (Exception var7) {
            LOGGER.error("Teleport failed", var7);
            this.sendMessage(client, "§cTeleport failed!");
         } finally {
            this.isProcessing = false;
         }

      }
   }

   private class_243 calculateSafePosition(class_239 hit) {
      class_243 pos = hit.method_17784();
      return hit.method_17783() == class_240.field_1332 ? new class_243(pos.field_1352, pos.field_1351 + 1.0D, pos.field_1350) : pos;
   }

   private void sendMessage(class_310 client, String message) {
      if (client.field_1724 != null) {
         client.field_1724.method_7353(class_2561.method_43470(message), false);
      }

   }
}
