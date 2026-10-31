package dev.friendrun.mixin;

import dev.friendrun.FriendRunClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures server-confirmed item pickups before the item entity is removed client-side. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleTakeItemEntity(Lnet/minecraft/network/protocol/game/ClientboundTakeItemEntityPacket;)V",
            at = @At("HEAD"), require = 0)
    private void friendrun$trackDraftPlusPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || packet.getPlayerId() != mc.player.getId()) return;
        Entity entity = mc.level.getEntity(packet.getItemId());
        if (entity instanceof ItemEntity itemEntity) {
            FriendRunClient.recordDraftPlusPickup(itemEntity.getItem().copyWithCount(packet.getAmount()));
        }
    }
}
