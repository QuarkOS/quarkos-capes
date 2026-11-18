package dev.quarkos.capes.client.screen.ui;

/** Layout in GUI pixels. All right and bottom edges are exclusive. */
public record CapeGridLayout(Rect area, int columns, int rows, int cardHeight) {
    public static final int GAP = 6;

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public int centerX() { return x + width / 2; }
        public int centerY() { return y + height / 2; }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public Rect inset(int amount) {
            return new Rect(x + amount, y + amount, Math.max(0, width - 2 * amount), Math.max(0, height - 2 * amount));
        }

        public Rect cell(int index, int count, int gap) {
            int usable = width - (count - 1) * gap;
            int left = index * usable / count;
            int right = (index + 1) * usable / count;
            return new Rect(x + left + index * gap, y, right - left, height);
        }
    }

    public static Rect frame(int width, int height) {
        int margin = width >= 360 ? 12 : 8;
        int top = height >= 360 ? 16 : 8;
        int span = Math.max(0, Math.min(820, width - 2 * margin));
        return new Rect((width - span) / 2, top, span, Math.max(0, height - top * 2));
    }

    public static int previewWidth(Rect frame) {
        if (frame.width() < 382 || frame.height() < 190) return 0;
        return Math.max(132, Math.min(252, frame.width() / 3));
    }

    public static CapeGridLayout fit(Rect area, int minCardWidth, int preferredHeight, int maxColumns) {
        int columns = Math.max(1, Math.min(maxColumns, (area.width() + GAP) / (minCardWidth + GAP)));
        int cardHeight = Math.max(0, Math.min(preferredHeight, area.height()));
        int rows = cardHeight < 36 || area.width() < 32 ? 0 : (area.height() + GAP) / (cardHeight + GAP);
        return new CapeGridLayout(area, columns, rows, cardHeight);
    }

    public int capacity() { return rows * columns; }

    public int pages(int items) {
        return capacity() == 0 ? 1 : Math.max(1, (items - 1) / capacity() + 1);
    }

    public int clampPage(int page, int items) {
        return Math.max(0, Math.min(page, pages(items) - 1));
    }

    public Rect card(int index) {
        int y = area.y() + index / columns * (cardHeight + GAP);
        return new Rect(area.x(), y, area.width(), cardHeight).cell(index % columns, columns, GAP);
    }
}
