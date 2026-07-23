package rearth.oracle.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oracle.OracleClient;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackTooltipMixin {
    
    @Inject(method = "getTooltipLines", at = @At("HEAD"))
    private void captureTooltipStack(Item.TooltipContext context, @Nullable Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> cir) {
        OracleClient.tooltipStack = (ItemStack) (Object) this;
    }
    
}
