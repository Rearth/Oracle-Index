package rearth.oracle.tooltip;

import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import rearth.oracle.OracleClient;
import rearth.oracle.ui.OracleScreen;

import java.util.List;
import java.util.Objects;

public final class DocumentationTooltipHandler {

    private static final int PROGRESS_STEPS = 40;

    private static Identifier previousItemId;
    private static float openProgress;

    private DocumentationTooltipHandler() {
    }

    public static void register() {
        ClientTooltipEvent.ITEM.register(DocumentationTooltipHandler::appendTooltip);
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (!client.hasAltDown()) {
                openProgress += (0f - openProgress) * 0.13f;
            }
        });
    }

    private static void appendTooltip(ItemStack stack, List<Component> lines, Item.TooltipContext context, TooltipFlag flag) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof OracleScreen) return;

        Identifier itemId = stack.typeHolder()
          .unwrapKey()
          .map(ResourceKey::identifier)
          .orElse(null);
        if (!Objects.equals(previousItemId, itemId)) {
            previousItemId = itemId;
            openProgress = 0;
        }

        OracleClient.ItemArticleRef article = itemId == null ? null : OracleClient.ITEM_LINKS.get(itemId);
        if (article == null) return;

        lines.add(Component.literal("─".repeat(20)).withStyle(ChatFormatting.DARK_GRAY));

        Component icon = Component.literal("\uD83D\uDCD5 ").withStyle(ChatFormatting.GRAY);
        if (minecraft.hasAltDown()) {
            float delta = minecraft.getDeltaTracker().getRealtimeDeltaTicks() * 0.125f;
            openProgress += (1.25f - openProgress) * delta;

            int progress = Math.clamp((int) (openProgress * PROGRESS_STEPS), 0, PROGRESS_STEPS);
            String progressText = "[" + "|".repeat(progress) + ".".repeat(PROGRESS_STEPS - progress) + "]";
            lines.add(icon.copy().append(Component.literal(progressText)).withStyle(ChatFormatting.GRAY));

            if (openProgress > 0.95f) {
                openProgress = 0;
                OracleClient.openScreen(article.wikiId(), article.linkTarget(), minecraft.screen);
            }
        } else {
            lines.add(icon.copy()
              .append(Component.translatable("oracle_index.tooltip.docs").withStyle(ChatFormatting.GRAY)));
        }
    }
}
