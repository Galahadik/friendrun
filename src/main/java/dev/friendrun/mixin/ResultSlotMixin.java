package dev.friendrun.mixin;

import dev.friendrun.DraftPlusChaos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the Draft+ fast-crafting bonus only when a crafted result is actually taken. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Inject(
        method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("RETURN")
    )
    private void friendrun$draftPlusFastCraft(Player player, ItemStack carried, CallbackInfo ci) {
        DraftPlusChaos.onCrafted(player, carried);
    }
}
