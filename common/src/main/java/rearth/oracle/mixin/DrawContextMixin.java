package rearth.oracle.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.oracle.OracleClient;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
@Mixin(DrawContext.class)
public class DrawContextMixin {
		
		@Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;)V", at = @At("HEAD"))
		private void injectTooltipComponents(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y, TooltipPositioner positioner, CallbackInfo ci, @Local(argsOnly = true) LocalRef<List<TooltipComponent>> componentsRef) {
				if (OracleClient.tooltipStack == null) return;
				
				var stackItem = OracleClient.tooltipStack.getItem();
				var stackId = Registries.ITEM.getId(stackItem);
				
				if (!OracleClient.ITEM_LINKS.containsKey(stackId)) return;
				
				OracleClient.tooltipStack = null;
				
				var modifiableComponents = new ArrayList<>(components);
				
				// vanilla-friendly separator: dim dashes line
				var separator = TooltipComponent.of(Text.literal("─".repeat(20)).formatted(Formatting.DARK_GRAY).asOrderedText());
				modifiableComponents.add(separator);
				
				var stackLink = OracleClient.ITEM_LINKS.get(stackId);
				
				var tooltipText = Text.literal("\uD83D\uDCD5 ").append(Text.literal(stackLink.wikiId() + ": ").formatted(Formatting.ITALIC)).append(Text.literal(stackLink.entryName()));
				var tooltip = TooltipComponent.of(tooltipText.formatted(Formatting.GRAY).asOrderedText());
				modifiableComponents.add(tooltip);
				
				if (Screen.hasAltDown()) {
						var dt = MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() * .125f;
						OracleClient.openEntryProgress += (1.25f - OracleClient.openEntryProgress) * dt;
						var progressSteps = 40;
						var progress = (int) (OracleClient.openEntryProgress * progressSteps);
						progress = Math.clamp(progress, 0, 40);
						var missingSteps = progressSteps - progress;
						var progressText = "[" + "|".repeat(progress) + ".".repeat(missingSteps) + "]";
						var altTooltip = TooltipComponent.of(Text.translatable(progressText).asOrderedText());
						modifiableComponents.add(altTooltip);
						
						if (OracleClient.openEntryProgress > 0.95f) {
								OracleClient.openScreen(stackLink.wikiId(), stackLink.linkTarget(), MinecraftClient.getInstance().currentScreen);
						}
						
				} else {
						var altTooltip = TooltipComponent.of(Text.translatable("Hold [ALT] to open").formatted(Formatting.GRAY).asOrderedText());
						modifiableComponents.add(altTooltip);
				}
				
				componentsRef.set(modifiableComponents);
		}
		
}
