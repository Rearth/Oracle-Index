package rearth.oracle.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oracle.OracleClient;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackTooltipMixin {
    
    @Inject(method = "getTooltip", at = @At("HEAD"))
    private void captureTooltipStack(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        OracleClient.tooltipStack = (ItemStack) (Object) this;
    }
    
}
