package death.effects;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import death.effects.utils.LOAura;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.class_1297;
import net.minecraft.class_239;
import net.minecraft.class_2561;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_3966;
import net.minecraft.class_3675.class_307;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class AuraClient implements ClientModInitializer {
   public static final String MOD_ID = "loaura";
   public static final Logger LOGGER = LoggerFactory.getLogger("loaura");
   private static class_304 toggleAuraKeybind;
   private final LOAura loAura = new LOAura();
   private class_1297 targetOverride = null;

   public void onInitializeClient() {
      LOGGER.info("Initializing LOAura mod");
      toggleAuraKeybind = KeyBindingHelper.registerKeyBinding(new class_304("key.loaura.toggle", class_307.field_1668, 75, "category.loaura.main"));
      this.registerCommands();
      ClientTickEvents.END_CLIENT_TICK.register((client) -> {
         if (toggleAuraKeybind.method_1436()) {
            this.loAura.toggle();
         }

         if (this.loAura.isEnabled()) {
            if (this.targetOverride != null && this.targetOverride.method_5805()) {
               this.loAura.setTargetOverride(this.targetOverride);
            } else {
               class_1297 crosshairTarget = this.getEntityAtCrosshair(client);
               if (crosshairTarget != null) {
                  this.loAura.setTargetOverride(crosshairTarget);
               } else {
                  this.loAura.clearTargetOverride();
               }
            }

            this.loAura.onUpdate();
         }

      });
   }

   private void registerCommands() {
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
         dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("loaura").then(ClientCommandManager.literal("toggle").executes((context) -> {
            this.loAura.toggle();
            return 1;
         }))).then(ClientCommandManager.literal("range").then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 100)).executes((context) -> {
            int range = IntegerArgumentType.getInteger(context, "value");
            this.loAura.setRange((double)range);
            this.sendFeedback("Range set to " + range);
            return 1;
         })))).then(ClientCommandManager.literal("attacks").then(ClientCommandManager.argument("value", IntegerArgumentType.integer(1, 10)).executes((context) -> {
            int attacks = IntegerArgumentType.getInteger(context, "value");
            this.loAura.setAttacksPerTick(attacks);
            this.sendFeedback("Attacks per tick set to " + attacks);
            return 1;
         })))).then(ClientCommandManager.literal("players").then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes((context) -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            this.loAura.setAttackPlayers(value);
            this.sendFeedback("Attack players: " + value);
            return 1;
         })))).then(ClientCommandManager.literal("hostiles").then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes((context) -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            this.loAura.setAttackHostiles(value);
            this.sendFeedback("Attack hostiles: " + value);
            return 1;
         })))).then(ClientCommandManager.literal("passives").then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes((context) -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            this.loAura.setAttackPassives(value);
            this.sendFeedback("Attack passives: " + value);
            return 1;
         })))).then(ClientCommandManager.literal("walls").then(ClientCommandManager.argument("value", BoolArgumentType.bool()).executes((context) -> {
            boolean value = BoolArgumentType.getBool(context, "value");
            this.loAura.setThroughWalls(value);
            this.sendFeedback("Attack through walls: " + value);
            return 1;
         })))).executes((context) -> {
            this.loAura.setEnabled(true);
            class_310 client = class_310.method_1551();
            class_1297 crosshairTarget = this.getEntityAtCrosshair(client);
            if (crosshairTarget != null) {
               this.targetOverride = crosshairTarget;
               this.loAura.setTargetOverride(crosshairTarget);
               this.sendFeedback("LOAura targeting " + crosshairTarget.method_5477().getString());
            } else {
               this.sendFeedback("LOAura enabled - no target at crosshair");
            }

            return 1;
         }));
      });
   }

   private class_1297 getEntityAtCrosshair(class_310 client) {
      class_239 var3 = client.field_1765;
      if (var3 instanceof class_3966) {
         class_3966 entityHit = (class_3966)var3;
         return entityHit.method_17782();
      } else {
         return null;
      }
   }

   private void sendFeedback(String message) {
      class_310 client = class_310.method_1551();
      if (client.field_1724 != null) {
         client.field_1724.method_7353(class_2561.method_43470("§b[LOAura] §f" + message), false);
      }

   }
}
