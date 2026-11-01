package dev.friendrun.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes recipe-book visibility so the Draft+ overlay avoids its expanded panel. */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {
    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> friendrun$getRecipeBookComponent();
}
