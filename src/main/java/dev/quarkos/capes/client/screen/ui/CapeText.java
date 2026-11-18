package dev.quarkos.capes.client.screen.ui;

import java.util.function.ToIntFunction;

public final class CapeText {
    private static final String ELLIPSIS = "…";

    private CapeText() { }

    public static String ellipsis(String value, int available, ToIntFunction<String> width) {
        String line = value.replace('\n', ' ').replace('\r', ' ');
        if (available <= 0) return "";
        if (width.applyAsInt(line) <= available) return line;
        if (width.applyAsInt(ELLIPSIS) > available) return "";

        int low = 0;
        int high = line.codePointCount(0, line.length());
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            String candidate = line.substring(0, line.offsetByCodePoints(0, middle)) + ELLIPSIS;
            if (width.applyAsInt(candidate) <= available) low = middle;
            else high = middle - 1;
        }
        return line.substring(0, line.offsetByCodePoints(0, low)).stripTrailing() + ELLIPSIS;
    }
}
