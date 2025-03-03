package rearth.oracle.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.wispforest.owo.ui.base.BaseOwoTooltipComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.Delta;
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
import rearth.oracle.ui.OracleScreen;

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
				
				var separator = new BaseOwoTooltipComponent<>(() -> {
						var container =  Containers.horizontalFlow(Sizing.content(), Sizing.content());
						var box = Components.box(Sizing.fixed(100), Sizing.fixed(1));
						box.margins(Insets.of(4, 4, 2, 2));
						box.color(new Color(0.5f, 0.5f, 0.5f));
						container.child(box);
						return container;
				}) { };
				
				var modifiableComponents = new ArrayList<>(components);
				
				modifiableComponents.add(separator);
				
				var stackLink = OracleClient.ITEM_LINKS.get(stackId);
				var tooltipText = Text.literal("\uD83D\uDCD5 ").append(Text.literal(stackLink.bookId() + ": ").formatted(Formatting.ITALIC)).append(Text.literal(stackLink.entryName()));
				var tooltip = TooltipComponent.of(tooltipText.formatted(Formatting.GRAY).asOrderedText());
				modifiableComponents.add(tooltip);
				
				if (Screen.hasAltDown()) {
						OracleClient.openEntryProgress += (float) Delta.compute(OracleClient.openEntryProgress, 1.25, MinecraftClient.getInstance().getRenderTickCounter().getLastFrameDuration() * .125f);
						var progressSteps = 40;
						var progress = (int) (OracleClient.openEntryProgress * progressSteps);
						progress = Math.clamp(progress, 0, 40);
						var missingSteps = progressSteps - progress;
						var progressText = "[" + "|".repeat(progress) + ".".repeat(missingSteps) + "]";
						var altTooltip = TooltipComponent.of(Text.translatable(progressText).asOrderedText());
						modifiableComponents.add(altTooltip);
						
						if (OracleClient.openEntryProgress > 0.95f) {
								// open screen
								OracleScreen.activeBook = stackLink.bookId();
								OracleScreen.activeEntry = stackLink.linkTarget();
								MinecraftClient.getInstance().setScreen(new OracleScreen(MinecraftClient.getInstance().currentScreen));
						}
						
				} else {
						var altTooltip = TooltipComponent.of(Text.translatable("Hold [ALT] to open").formatted(Formatting.GRAY).asOrderedText());
						modifiableComponents.add(altTooltip);
				}
				
				componentsRef.set(modifiableComponents);
		}
		
}
