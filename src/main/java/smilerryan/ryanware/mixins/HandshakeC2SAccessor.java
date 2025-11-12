package smilerryan.ryanware.mixins;

import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandshakeC2SPacket.class)
public interface HandshakeC2SAccessor {
    @Accessor("address")
    @Mutable
    void setAddress(String address);

    @Accessor("address")
    String getAddress();

    @Accessor("intendedState")
    ConnectionIntent getIntendedState();
}
