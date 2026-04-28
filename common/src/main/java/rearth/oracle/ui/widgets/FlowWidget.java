package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Container that arranges children in a single row or column.
 *
 * <ul>
 *   <li>{@link Direction#HORIZONTAL} — left-to-right, all children share the row.</li>
 *   <li>{@link Direction#VERTICAL} — top-to-bottom, all children share the column.</li>
 * </ul>
 *
 * <p>Children are spaced by {@link #gap}. The container resolves its own
 * {@link #getPreferredWidth(int)} / {@link #getPreferredHeight(int)} from its
 * children's preferred sizes plus {@link #padding} and gaps.</p>
 */
public class FlowWidget extends UIComponent {
    
    public enum Direction {HORIZONTAL, VERTICAL}
    
    public enum HorizontalAlignment {LEFT, CENTER, RIGHT}
    
    public enum VerticalAlignment {TOP, CENTER, BOTTOM}
    
    private final Direction direction;
    private final List<UIComponent> children = new ArrayList<>();
    private int gap = 0;
    private HorizontalAlignment horizontalAlignment = HorizontalAlignment.LEFT;
    private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;
    private int laidOutHeight = 0;
    
    public FlowWidget(Direction direction) {
        this.direction = direction;
    }
    
    public static FlowWidget horizontal() {
        return new FlowWidget(Direction.HORIZONTAL);
    }
    
    public static FlowWidget vertical() {
        return new FlowWidget(Direction.VERTICAL);
    }
    
    public FlowWidget gap(int gap) {
        this.gap = gap;
        return this;
    }
    
    public int gap() {
        return gap;
    }
    
    public Direction direction() {
        return direction;
    }
    
    public FlowWidget horizontalAlignment(HorizontalAlignment a) {
        this.horizontalAlignment = a;
        return this;
    }
    
    public FlowWidget verticalAlignment(VerticalAlignment a) {
        this.verticalAlignment = a;
        return this;
    }
    
    public FlowWidget child(UIComponent child) {
        children.add(child);
        return this;
    }
    
    public FlowWidget clearChildren() {
        children.clear();
        return this;
    }
    
    public List<UIComponent> children() {
        return children;
    }
    
    public int laidOutHeight() {
        return laidOutHeight;
    }
    
    // ---------------------------------------------------------------- layout
    
    @Override
    public int getPreferredWidth(int widthHint) {
        if (preferredWidth > 0) return preferredWidth;
        // Always derive from children rather than caching: when collapsibles expand
        // or content changes, the prior `width` would otherwise stick.
        int innerHint = widthHint > 0 ? widthHint - padding.horizontal() : -1;
        if (direction == Direction.HORIZONTAL) {
            int total = padding.horizontal();
            int n = 0;
            for (var c : children) {
                if (!c.isVisible()) continue;
                total += c.getPreferredWidth(-1);
                n++;
            }
            if (n > 1) total += gap * (n - 1);
            return total;
        } else {
            int max = 0;
            for (var c : children) {
                if (!c.isVisible()) continue;
                max = Math.max(max, c.getPreferredWidth(innerHint));
            }
            return max + padding.horizontal();
        }
    }
    
    @Override
    public int getPreferredHeight(int widthHint) {
        if (preferredHeight > 0) return preferredHeight;
        int innerWidth = (widthHint > 0 ? widthHint : getPreferredWidth(-1)) - padding.horizontal();
        if (direction == Direction.VERTICAL) {
            int total = padding.vertical();
            int n = 0;
            for (var c : children) {
                if (!c.isVisible()) continue;
                total += c.getPreferredHeight(innerWidth);
                n++;
            }
            if (n > 1) total += gap * (n - 1);
            return total;
        } else {
            int max = 0;
            for (var c : children) {
                if (!c.isVisible()) continue;
                int cw = c.getPreferredWidth(-1);
                max = Math.max(max, c.getPreferredHeight(cw));
            }
            return max + padding.vertical();
        }
    }
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        // resolve own size if 0
        if (width == 0) width = getPreferredWidth(parentWidthHint);
        if (height == 0) height = getPreferredHeight(width);
        
        int innerX = x + padding.left();
        int innerY = y + padding.top();
        int innerW = width - padding.horizontal();
        int innerH = height - padding.vertical();
        
        if (direction == Direction.VERTICAL) {
            // optionally shrink children width to fit, set position
            int cy = innerY;
            int visibleCount = 0;
            for (var c : children) {
                if (!c.isVisible()) continue;
                int cw = c.getPreferredWidth(innerW);
                if (cw <= 0) cw = c.getWidth();
                cw = Math.min(cw, innerW);
                int ch = c.getPreferredHeight(cw);
                if (ch <= 0) ch = c.getHeight();
                int cx = switch (horizontalAlignment) {
                    case LEFT -> innerX;
                    case CENTER -> innerX + (innerW - cw) / 2;
                    case RIGHT -> innerX + innerW - cw;
                };
                c.setPosition(cx, cy);
                c.setLayoutSize(cw, ch);
                c.layout(cw, ch);
                ch = c.getHeight();
                cy += ch + gap;
                visibleCount++;
            }
            laidOutHeight = visibleCount == 0 ? padding.vertical() : cy - y - gap + padding.bottom();
        } else {
            int usedW = 0;
            int visibleCount = 0;
            for (var c : children) {
                if (!c.isVisible()) continue;
                int cw = c.getPreferredWidth(-1);
                if (cw <= 0) cw = c.getWidth();
                usedW += cw;
                visibleCount++;
            }
            if (visibleCount > 1) usedW += gap * (visibleCount - 1);
            int cx = switch (horizontalAlignment) {
                case LEFT -> innerX;
                case CENTER -> innerX + Math.max(0, (innerW - usedW) / 2);
                case RIGHT -> innerX + Math.max(0, innerW - usedW);
            };
            int maxChildHeight = 0;
            for (var c : children) {
                if (!c.isVisible()) continue;
                int cw = c.getPreferredWidth(-1);
                if (cw <= 0) cw = c.getWidth();
                int ch = c.getPreferredHeight(cw);
                if (ch <= 0) ch = c.getHeight();
                ch = Math.min(ch, innerH);
                int cy = switch (verticalAlignment) {
                    case TOP -> innerY;
                    case CENTER -> innerY + (innerH - ch) / 2;
                    case BOTTOM -> innerY + innerH - ch;
                };
                c.setPosition(cx, cy);
                c.setLayoutSize(cw, ch);
                c.layout(cw, ch);
                ch = c.getHeight();
                maxChildHeight = Math.max(maxChildHeight, ch);
                cx += cw + gap;
            }
            laidOutHeight = padding.vertical() + maxChildHeight;
        }
    }
    
    // ---------------------------------------------------------------- render
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        for (var c : children) c.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void tick() {
        for (var c : children) c.tick();
    }
    
    // ---------------------------------------------------------------- mouse
    
    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            var c = children.get(i);
            if (!c.isVisible()) continue;
            if (c.handleClick(mouseX, mouseY, button)) return true;
        }
        return false;
    }
    
    @Override
    public boolean handleDrag(double mouseX, double mouseY, double dx, double dy, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            var c = children.get(i);
            if (!c.isVisible()) continue;
            if (c.handleDrag(mouseX, mouseY, dx, dy, button)) return true;
        }
        return false;
    }
    
    @Override
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
        boolean any = false;
        for (var c : children) {
            if (!c.isVisible()) continue;
            if (c.handleMouseRelease(mouseX, mouseY, button)) any = true;
        }
        return any;
    }
    
    @Override
    public boolean handleMouseScroll(double mouseX, double mouseY, double scrollDelta) {
        for (int i = children.size() - 1; i >= 0; i--) {
            var c = children.get(i);
            if (!c.isVisible()) continue;
            if (c.handleMouseScroll(mouseX, mouseY, scrollDelta)) return true;
        }
        return false;
    }
    
    @Override
    public List<Text> tooltip(int mouseX, int mouseY) {
        for (int i = children.size() - 1; i >= 0; i--) {
            var c = children.get(i);
            if (!c.isVisible() || !c.isInBounds(mouseX, mouseY)) continue;
            var t = c.tooltip(mouseX, mouseY);
            if (t != null && !t.isEmpty()) return t;
        }
        return super.tooltip(mouseX, mouseY);
    }
}
