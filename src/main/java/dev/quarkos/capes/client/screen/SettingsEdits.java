package dev.quarkos.capes.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Applies widget edits once, leaving settings changed by other screens or actions intact. */
public final class SettingsEdits {
    private final List<Runnable> entries = new ArrayList<>();

    public <T> void track(Supplier<T> value, Consumer<T> write) {
        entries.add(new Runnable() {
            private T previous = value.get();

            @Override
            public void run() {
                T next = value.get();
                if (!Objects.equals(previous, next)) {
                    previous = next;
                    write.accept(next);
                }
            }
        });
    }

    public void apply() {
        entries.forEach(Runnable::run);
    }
}
