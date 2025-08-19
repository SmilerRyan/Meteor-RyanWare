package smilerryan.ryanware.modules;

import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import smilerryan.ryanware.RyanWare;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

import java.util.concurrent.atomic.AtomicBoolean;

public class M1_NoAttackDamage extends Module {
    public enum Mode {
        Everyone,
        FriendsOnly
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Who not to attack.")
        .defaultValue(Mode.Everyone)
        .build());

    public M1_NoAttackDamage() {
        super(RyanWare.CATEGORY_M1, RyanWare.modulePrefix+"M1-No-Attack-Damage", "Prevents dealing damage to others.");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
            // Create an atomic boolean to track if this is an attack packet
            AtomicBoolean isAttack = new AtomicBoolean(false);
            
            // Use the handler to check if it's an attack packet
            packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
                @Override
                public void attack() {
                    isAttack.set(true);
                }
                
                @Override
                public void interact(net.minecraft.util.Hand hand) {
                    // Not an attack, just a regular interaction
                }
                
                @Override
                public void interactAt(net.minecraft.util.Hand hand, net.minecraft.util.math.Vec3d pos) {
                    // Not an attack, just a specific position interaction
                }
            });
            
            // Only proceed if it's an attack
            if (isAttack.get()) {
                if (mc.targetedEntity instanceof PlayerEntity player) {
                    String playerName = player.getName().getString();
                    
                    if (mode.get() == Mode.Everyone) {
                        // Cancel all attack packets in Everyone mode
                        event.cancel();
                        info("Blocked attack on player: " + playerName);
                    } 
                    else if (mode.get() == Mode.FriendsOnly) {
                        // In FriendsOnly mode, check if the target is a friend
                        if (Friends.get().isFriend(player)) {
                            event.cancel();
                            info("Blocked attack on friend: " + playerName);
                        }
                    }
                }
            }
        }
    }
}