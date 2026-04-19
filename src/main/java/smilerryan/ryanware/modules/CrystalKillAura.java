package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Hand;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.MinecraftClient;
import smilerryan.ryanware.RyanWare;

public class CrystalKillAura extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public CrystalKillAura() {
        super(RyanWare.CATEGORY_EXTRAS, RyanWare.modulePrefix_extras + "Crystal-Kill-Aura", "Breaks end crystals as fast as possible.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity player = mc.player;

        // fixed reach (uses player's normal reach; adjust if you add reach-detect later)
        double reach = 6.0;
        double reachSq = reach * reach;

        for (var ent : mc.world.getEntities()) {
            if (!(ent instanceof EndCrystalEntity)) continue;
            EndCrystalEntity crystal = (EndCrystalEntity) ent;

            if (player.squaredDistanceTo(crystal) <= reachSq) {
                try { mc.interactionManager.attackEntity(player, crystal); } catch (Throwable ignored) {}
                player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
