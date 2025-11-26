package smilerryan.ryanware.mixins;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import smilerryan.ryanware.modules.BungeeJoinPackets;

@Mixin(HandshakeC2SPacket.class)
public abstract class HandshakeC2SMixin {
    @Mutable @Shadow @Final private String address;
    @Shadow public abstract ConnectionIntent intendedState();

    @Inject(method = "<init>(ILjava/lang/String;ILnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V",
        at = @At("RETURN"))
    private void onInit(int protocol, String host, int port, ConnectionIntent intent, CallbackInfo ci) {
        BungeeJoinPackets mod = Modules.get().get(BungeeJoinPackets.class);

        // module must be on
        if (!mod.isActive()) return;
        // only spoof on LOGIN
        if (this.intendedState() != ConnectionIntent.LOGIN) return;

        BungeeJoinPackets.Personality p = mod.getSelected();
        if (p == null) return; // invalid index = no spoof

        // append \0IP\0UUID
        this.address += "\u0000" + p.ip + "\u0000" + p.uuid;
    }
}
