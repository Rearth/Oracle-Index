package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-cell grid layout. Caller specifies {@link #rows} × {@link #columns}
 * and the cell size; children are placed by index (row-major).
 *
 * <p>Used for crafting recipe slots — every slot is the same size.</p>
 */
public class GridWidget extends UIComponent {
    
    private final int rows;
    private final int columns;
    private final int cellWidth;
    private final int cellHeight;
    private int gapX = 0;
    private int gapY = 0;
    
    private final UIComponent[] cells;
    
    public GridWidget(int rows, int columns, int cellWidth, int cellHeight) {
        this.rows = rows;
        this.columns = columns;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.cells = new UIComponent[rows * columns];
    }
    
    public GridWidget gap(int gx, int gy) { this.gapX = gx; this.gapY = gy; return this; }
    
    public GridWidget set(int row, int column, UIComponent child) {
        cells[row * columns + column] = child;
        return this;
    }
    
    @Override
    public int getPreferredWidth(int widthHint) {
        return columns * cellWidth + Math.max(0, columns - 1) * gapX + padding.horizontal();
    }
    
    @Override
    public int getPreferredHeight(int widthHint) {
        return rows * cellHeight + Math.max(0, rows - 1) * gapY + padding.vertical();
    }
    
    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        if (width == 0) width = getPreferredWidth(parentWidthHint);
        if (height == 0) height = getPreferredHeight(width);
        int baseX = x + padding.left();
        int baseY = y + padding.top();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                var child = cells[r * columns + c];
                if (child == null) continue;
                int cx = baseX + c * (cellWidth + gapX);
                int cy = baseY + r * (cellHeight + gapY);
                child.setPosition(cx, cy);
                child.setLayoutSize(cellWidth, cellHeight);
                child.layout(cellWidth, cellHeight);
            }
        }
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        for (var c : cells) if (c != null) c.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void tick() {
        for (var c : cells) if (c != null) c.tick();
    }
    
    private List<UIComponent> visibleChildren() {
        var list = new ArrayList<UIComponent>(cells.length);
        for (var c : cells) if (c != null && c.isVisible()) list.add(c);
        return list;
    }
    
    @Override
    public boolean handleClick(double mx, double my, int button) {
        var list = visibleChildren();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).handleClick(mx, my, button)) return true;
        return false;
    }
    
    @Override
    public boolean handleDrag(double mx, double my, double dx, double dy, int button) {
        var list = visibleChildren();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).handleDrag(mx, my, dx, dy, button)) return true;
        return false;
    }
    
    @Override
    public boolean handleMouseRelease(double mx, double my, int button) {
        boolean any = false;
        for (var c : visibleChildren()) if (c.handleMouseRelease(mx, my, button)) any = true;
        return any;
    }
    
    @Override
    public boolean handleMouseScroll(double mx, double my, double sd) {
        var list = visibleChildren();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).handleMouseScroll(mx, my, sd)) return true;
        return false;
    }
    
    @Override
    public List<Text> tooltip(int mouseX, int mouseY) {
        for (var c : visibleChildren()) {
            if (!c.isInBounds(mouseX, mouseY)) continue;
            var t = c.tooltip(mouseX, mouseY);
            if (t != null && !t.isEmpty()) return t;
        }
        return super.tooltip(mouseX, mouseY);
    }
}
