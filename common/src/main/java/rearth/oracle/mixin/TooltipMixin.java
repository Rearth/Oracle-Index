package rearth.oracle.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.oracle.OracleClient;

import java.util.List;

@Mixin(Screen.class)
public class TooltipMixin {
		
		@Inject(method = "getTooltipFromItem", at = @At("HEAD"))
		private static void captureTooltipStack(MinecraftClient client, ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
				OracleClient.tooltipStack = stack;
		}
}