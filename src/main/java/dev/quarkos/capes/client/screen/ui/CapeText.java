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

    /** Word wrap that keeps blank lines between paragraphs; long words are split by width. */
    public static java.util.List<String> wrap(String text, int width, ToIntFunction<String> measure) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isBlank()) { out.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split(" +")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (measure.applyAsInt(next) <= width) { line.setLength(0); line.append(next); continue; }
                if (!line.isEmpty()) { out.add(line.toString()); line.setLength(0); }
                while (measure.applyAsInt(word) > width && word.length() > 1) {
                    int cut = word.length() - 1;
                    while (cut > 1 && measure.applyAsInt(word.substring(0, cut)) > width) cut--;
                    out.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                line.append(word);
            }
            if (!line.isEmpty()) out.add(line.toString());
        }
        return out;
    }

}
