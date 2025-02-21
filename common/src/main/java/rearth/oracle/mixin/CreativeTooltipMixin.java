package rearth.oracle.mixin;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oracle.OracleClient;

import java.util.List;

@Mixin(CreativeInventoryScreen.class)
public class CreativeTooltipMixin {
		
		@Inject(method = "getTooltipFromItem", at = @At("HEAD"))
		private void captureTooltipStack(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
				OracleClient.tooltipStack = stack;
		}
}