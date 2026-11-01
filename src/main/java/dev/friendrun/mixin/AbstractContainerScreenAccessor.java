package dev.friendrun.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the live inventory panel geometry for the Draft+ overlay. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("imageWidth")
    int friendrun$getImageWidth();

    @Accessor("leftPos")
    int friendrun$getLeftPos();
}
