package rearth.oracle.ui.components;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Objects;
import java.util.function.Predicate;

public class ScalableLabelComponent extends LabelComponent {
		
		public float scale = 1.0f;
		
		public ScalableLabelComponent(Text text, Predicate<String> linkHandler) {
				super(text);
				
				this.textClickHandler(style -> {
						if (style == null) return false;
						
						var event = style.getClickEvent();
						if (event != null && event.getAction() == ClickEvent.Action.OPEN_URL) {
								return linkHandler.test(event.getValue());
						}
						
						return false;
						
				});
				
		}
		
		@Override
		protected int determineVerticalContentSize(Sizing sizing) {
				return (int) (super.determineVerticalContentSize(sizing) * scale + 0.5);
		}
		
		@Override
		protected int determineHorizontalContentSize(Sizing sizing) {
				return (int) (super.determineHorizontalContentSize(sizing) * scale + 0.5);
		}
		
		@Override
		protected Style styleAt(int mouseX, int mouseY) {
				return super.styleAt(mouseX, mouseY);
		}
		
		// basically a copy of the draw method from LabelComponent with a scale factor added
		@Override
		public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
				MatrixStack matrices = context.getMatrices();
				matrices.push();
				matrices.translate(0.0F, (double) 1.0F / MinecraftClient.getInstance().getWindow().getScaleFactor(), 0.0F);
				int x = this.x;
				int y = this.y;
				if (this.horizontalSizing.get().isContent()) {
						x += this.horizontalSizing.get().value;
				}
				
				if (this.verticalSizing.get().isContent()) {
						y += this.verticalSizing.get().value;
				}
				
				switch (this.verticalTextAlignment) {
						case CENTER -> y += (this.height - this.textHeight()) / 2;
						case BOTTOM -> y += this.height - this.textHeight();
				}
				
				final int finalX = x;
				final int finalY = y;
				context.draw(() -> {
						for (int i = 0; i < this.wrappedText.size(); ++i) {
								OrderedText renderText = this.wrappedText.get(i);
								int renderX = finalX;
								switch (this.horizontalTextAlignment) {
										case CENTER -> renderX = finalX + (this.width - this.textRenderer.getWidth(renderText)) / 2;
										case RIGHT -> renderX = finalX + (this.width - this.textRenderer.getWidth(renderText));
								}
								
								int renderY = finalY + i * (this.lineHeight() + this.lineSpacing());
								int var10001 = this.lineHeight();
								Objects.requireNonNull(this.textRenderer);
								
								matrices.push();
								float translateX = finalX * (1 - scale); // No horizontal translation needed if scaling around top-left
								float translateY = finalY * (1 - scale); // No vertical translation needed if scaling around top-left
								matrices.translate(translateX, translateY, 0);
								matrices.scale(scale, scale, scale);
								
								renderY += var10001 - 9;
								context.drawText(this.textRenderer, renderText, renderX, renderY, this.color.get().argb(), this.shadow);
								matrices.pop();
						}
						
				});
				matrices.pop();
		}
}
