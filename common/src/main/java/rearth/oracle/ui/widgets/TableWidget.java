package rearth.oracle.ui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TableWidget extends UIComponent {
    private static final int BORDER = 1;
    private static final int CELL_PAD_X = 4;
    private static final int CELL_PAD_Y = 3;
    private static final int MIN_COLUMN_CONTENT = 24;

    private static final int GRID_COLOR = 0x40FFFFFF;
    private static final int HEADER_RULE_COLOR = 0x80FFFFFF;
    private static final int HEADER_FILL_COLOR = 0x18FFFFFF;

    public record Cell(Component text, FlowWidget.HorizontalAlignment alignment) {
    }

    private final List<List<LabelWidget>> rows = new ArrayList<>();
    private final boolean hasHeader;
    private final int columns;

    private int[] columnContentWidths;
    private int[] rowHeights;
    private int tableWidth;
    private int tableHeight;
    private int lastLayoutWidth = -1;

    public TableWidget(List<List<Cell>> cells, boolean hasHeader, Predicate<String> linkHandler) {
        this.hasHeader = hasHeader;
        int widest = 0;
        for (var row : cells) widest = Math.max(widest, row.size());
        this.columns = Math.max(1, widest);

        for (var row : cells) {
            var labels = new ArrayList<LabelWidget>(columns);
            for (int i = 0; i < columns; i++) {
                var cell = i < row.size() ? row.get(i) : new Cell(Component.empty(), FlowWidget.HorizontalAlignment.LEFT);
                labels.add(new LabelWidget(cell.text())
                             .textAlignment(cell.alignment())
                             .linkHandler(linkHandler));
            }
            rows.add(labels);
        }
    }

    public TableWidget color(int argb) {
        for (var row : rows) {
            for (var label : row) {
                label.color(argb);
            }
        }
        return this;
    }

    private void resolveColumns(int widthHint) {
        int available = widthHint > 0 ? widthHint : Integer.MAX_VALUE / 4;
        if (columnContentWidths != null && lastLayoutWidth == available) return;
        lastLayoutWidth = available;

        var widths = new int[columns];
        for (var row : rows) {
            for (int c = 0; c < columns; c++) {
                widths[c] = Math.max(widths[c], row.get(c).getPreferredWidth(-1));
            }
        }

        // Trim the widest column down to the runner-up until the table fits, so columns lose
        // space in the order that hurts readability least.
        int chrome = (columns + 1) * BORDER + columns * CELL_PAD_X * 2;
        int excess = chrome + sum(widths) - available;
        while (excess > 0) {
            int widest = 0;
            for (int c = 1; c < columns; c++) if (widths[c] > widths[widest]) widest = c;
            if (widths[widest] <= MIN_COLUMN_CONTENT) break;

            int runnerUp = MIN_COLUMN_CONTENT;
            for (int c = 0; c < columns; c++) if (c != widest) runnerUp = Math.max(runnerUp, widths[c]);
            int floor = Math.max(MIN_COLUMN_CONTENT, Math.min(runnerUp, widths[widest] - 1));

            int reduction = Math.min(excess, widths[widest] - floor);
            if (reduction <= 0) break;
            widths[widest] -= reduction;
            excess -= reduction;
        }

        this.columnContentWidths = widths;
        this.tableWidth = sum(widths) + chrome;
        this.rowHeights = new int[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            int tallest = 0;
            for (int c = 0; c < columns; c++) {
                tallest = Math.max(tallest, rows.get(r).get(c).getPreferredHeight(widths[c]));
            }
            rowHeights[r] = tallest + CELL_PAD_Y * 2;
        }
        this.tableHeight = sum(rowHeights) + (rows.size() + 1) * BORDER;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (var value : values) total += value;
        return total;
    }

    @Override
    public int getPreferredWidth(int widthHint) {
        resolveColumns(widthHint);
        return tableWidth;
    }

    @Override
    public int getPreferredHeight(int widthHint) {
        resolveColumns(widthHint);
        return tableHeight;
    }

    @Override
    public void layout(int parentWidthHint, int parentHeightHint) {
        resolveColumns(parentWidthHint);
        int cellY = y + BORDER;
        for (int r = 0; r < rows.size(); r++) {
            int cellX = x + BORDER;
            for (int c = 0; c < columns; c++) {
                var label = rows.get(r).get(c);
                int contentWidth = columnContentWidths[c];
                label.setPosition(cellX + CELL_PAD_X, cellY + CELL_PAD_Y);
                label.setLayoutSize(contentWidth, rowHeights[r] - CELL_PAD_Y * 2);
                label.layout(contentWidth, rowHeights[r] - CELL_PAD_Y * 2);
                cellX += contentWidth + CELL_PAD_X * 2 + BORDER;
            }
            cellY += rowHeights[r] + BORDER;
        }
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (columnContentWidths == null) return;

        // header band, so the first row reads as headings even without a surface behind the table
        if (hasHeader && !rows.isEmpty()) {
            context.fill(x, y, x + tableWidth, y + rowHeights[0] + BORDER * 2, HEADER_FILL_COLOR);
        }

        // horizontal rules, the one under the header row is brighter
        int lineY = y;
        for (int r = 0; r <= rows.size(); r++) {
            int color = hasHeader && r == 1 ? HEADER_RULE_COLOR : GRID_COLOR;
            context.fill(x, lineY, x + tableWidth, lineY + BORDER, color);
            if (r < rows.size()) lineY += rowHeights[r] + BORDER;
        }

        // vertical rules
        int lineX = x;
        for (int c = 0; c <= columns; c++) {
            context.fill(lineX, y, lineX + BORDER, y + tableHeight, GRID_COLOR);
            if (c < columns) lineX += columnContentWidths[c] + CELL_PAD_X * 2 + BORDER;
        }

        for (var row : rows) {
            for (var label : row) label.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean handleClick(double mouseX, double mouseY, int button) {
        for (var row : rows) {
            for (var label : row) {
                if (label.isInBounds(mouseX, mouseY) && label.handleClick(mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    @Override
    public List<Component> tooltip(int mouseX, int mouseY) {
        for (var row : rows) {
            for (var label : row) {
                if (!label.isInBounds(mouseX, mouseY)) continue;
                var tip = label.tooltip(mouseX, mouseY);
                if (tip != null && !tip.isEmpty()) return tip;
            }
        }
        return super.tooltip(mouseX, mouseY);
    }
}
