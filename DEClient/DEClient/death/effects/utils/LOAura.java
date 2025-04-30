package death.effects.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1588;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_239.class_240;
import net.minecraft.class_2828.class_2829;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

@Environment(EnvType.CLIENT)
public class LOAura {
   private final class_310 mc = class_310.method_1551();
   private double range = 50.0D;
   private int attacksPerTick = 3;
   private boolean attackPlayers = true;
   private boolean attackHostiles = true;
   private boolean attackPassives = false;
   private boolean throughWalls = false;
   private boolean autoReturn = true;
   private boolean enabled = false;
   private class_1297 targetOverride = null;
   private class_243 originalPosition;
   private long lastAttackTime = 0L;
   private static final long ATTACK_COOLDOWN_MS = 50L;

   public void onUpdate() {
      if (this.isEnabled() && this.mc.field_1724 != null && this.mc.field_1687 != null) {
         long currentTime = System.currentTimeMillis();
         if (currentTime - this.lastAttackTime >= 50L) {
            this.lastAttackTime = currentTime;
            this.originalPosition = this.mc.field_1724.method_19538();
            if (this.targetOverride != null && this.targetOverride.method_5805()) {
               if (this.teleportAndAttack(this.targetOverride) && this.autoReturn) {
                  this.teleportTo(this.originalPosition);
               }

            } else {
               List<class_1297> targets = this.findValidTargets();
               if (!targets.isEmpty()) {
                  int attackCount = 0;
                  Iterator var5 = targets.iterator();

                  while(var5.hasNext()) {
                     class_1297 target = (class_1297)var5.next();
                     if (attackCount >= this.attacksPerTick) {
                        break;
                     }

                     if (this.teleportAndAttack(target)) {
                        ++attackCount;
                     }
                  }

                  if (this.autoReturn && attackCount > 0) {
                     this.teleportTo(this.originalPosition);
                  }

               }
            }
         }
      }
   }

   private List<class_1297> findValidTargets() {
      if (this.mc.field_1687 == null) {
         return new ArrayList();
      } else {
         class_238 searchBox = new class_238(this.mc.field_1724.method_23317() - this.range, this.mc.field_1724.method_23318() - this.range, this.mc.field_1724.method_23321() - this.range, this.mc.field_1724.method_23317() + this.range, this.mc.field_1724.method_23318() + this.range, this.mc.field_1724.method_23321() + this.range);
         List<class_1297> nearbyEntities = this.mc.field_1687.method_8390(class_1297.class, searchBox, (entity) -> {
            return this.isValidTarget(entity);
         });
         return (List)nearbyEntities.stream().sorted(Comparator.comparingDouble((entity) -> {
            return entity.method_5858(this.mc.field_1724);
         })).collect(Collectors.toList());
      }
   }

   private boolean isValidTarget(class_1297 entity) {
      if (entity != null && !entity.equals(this.mc.field_1724) && entity.method_5805()) {
         if (entity instanceof class_1657) {
            return this.attackPlayers;
         } else if (entity instanceof class_1309) {
            boolean isHostile = entity instanceof class_1588;
            return isHostile ? this.attackHostiles : this.attackPassives;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean hasLineOfSight(class_1297 target) {
      if (this.throughWalls) {
         return true;
      } else if (this.mc.field_1687 != null && this.mc.field_1724 != null) {
         class_243 eyePos = this.mc.field_1724.method_33571();
         class_243 targetPos = target.method_5829().method_1005();
         return this.mc.field_1687.method_17742(new class_3959(eyePos, targetPos, class_3960.field_17558, class_242.field_1348, this.mc.field_1724)).method_17783() == class_240.field_1333;
      } else {
         return false;
      }
   }

   private class_243 calculateAttackPosition(class_1297 target) {
      class_243 targetPos = target.method_19538();
      class_243 targetLook = target.method_5720();
      return new class_243(targetPos.field_1352 - targetLook.field_1352 * 1.2D, targetPos.field_1351, targetPos.field_1350 - targetLook.field_1350 * 1.2D);
   }

   private boolean teleportAndAttack(class_1297 target) {
      if (this.mc.field_1724 != null && this.mc.field_1761 != null) {
         if (!this.throughWalls && !this.hasLineOfSight(target)) {
            return false;
         } else {
            class_243 attackPos = this.calculateAttackPosition(target);
            this.teleportTo(attackPos);
            this.lookAt(target.method_19538().method_1031(0.0D, (double)target.method_17682() * 0.5D, 0.0D));
            this.mc.field_1761.method_2918(this.mc.field_1724, target);
            this.mc.field_1724.method_6104(class_1268.field_5808);
            return true;
         }
      } else {
         return false;
      }
   }

   private void teleportTo(class_243 pos) {
      if (this.mc.field_1724 != null) {
         this.mc.field_1724.field_3944.method_52787(new class_2829(pos.field_1352, pos.field_1351, pos.field_1350, this.mc.field_1724.method_24828()));
      }
   }

   private void lookAt(class_243 pos) {
      if (this.mc.field_1724 != null) {
         class_243 eyePos = this.mc.field_1724.method_33571();
         double diffX = pos.field_1352 - eyePos.field_1352;
         double diffY = pos.field_1351 - eyePos.field_1351;
         double diffZ = pos.field_1350 - eyePos.field_1350;
         double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
         float yaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
         float pitch = (float)(-Math.toDegrees(Math.atan2(diffY, diffXZ)));
         this.mc.field_1724.method_36456(yaw);
         this.mc.field_1724.method_36457(pitch);
      }
   }

   public void toggle() {
      this.enabled = !this.enabled;
      if (this.mc.field_1724 != null) {
         String var10001 = this.enabled ? "a" : "c";
         this.mc.field_1724.method_7353(class_2561.method_43470("§" + var10001 + "LOAura " + (this.enabled ? "enabled" : "disabled")), false);
      }

   }

   public void setTargetOverride(class_1297 entity) {
      this.targetOverride = entity;
   }

   public void clearTargetOverride() {
      this.targetOverride = null;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public double getRange() {
      return this.range;
   }

   public void setRange(double range) {
      this.range = range;
   }

   public int getAttacksPerTick() {
      return this.attacksPerTick;
   }

   public void setAttacksPerTick(int attacksPerTick) {
      this.attacksPerTick = attacksPerTick;
   }

   public boolean isAttackPlayers() {
      return this.attackPlayers;
   }

   public void setAttackPlayers(boolean attackPlayers) {
      this.attackPlayers = attackPlayers;
   }

   public boolean isAttackHostiles() {
      return this.attackHostiles;
   }

   public void setAttackHostiles(boolean attackHostiles) {
      this.attackHostiles = attackHostiles;
   }

   public boolean isAttackPassives() {
      return this.attackPassives;
   }

   public void setAttackPassives(boolean attackPassives) {
      this.attackPassives = attackPassives;
   }

   public boolean isThroughWalls() {
      return this.throughWalls;
   }

   public void setThroughWalls(boolean throughWalls) {
      this.throughWalls = throughWalls;
   }

   public boolean isAutoReturn() {
      return this.autoReturn;
   }

   public void setAutoReturn(boolean autoReturn) {
      this.autoReturn = autoReturn;
   }
}
