package smilerryan.ryanware.mixins;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smilerryan.ryanware.modules.BungeeJoinPackets;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(HandshakeC2SPacket.class)
public abstract class HandshakeC2SMixin {
    @Mutable @Shadow @Final private String address;
    @Shadow public abstract ConnectionIntent intendedState();

    @Inject(method = "<init>(ILjava/lang/String;ILnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V", at = @At("RETURN"))
    private void onHandshakeC2SPacket(int i, String string, int j, ConnectionIntent connectionIntent, CallbackInfo ci) {
        BungeeJoinPackets bungeeJoinPackets = Modules.get().get(BungeeJoinPackets.class);
        if (!bungeeJoinPackets.isActive()) return;
        if (this.intendedState() != ConnectionIntent.LOGIN) return;

        // Use custom UUID if set, otherwise session UUID
        String spoofedUUID = bungeeJoinPackets.getUuidToUse();
        this.address += "\u0000" + "127.0.0.1" + "\u0000" + spoofedUUID;
    }
}
