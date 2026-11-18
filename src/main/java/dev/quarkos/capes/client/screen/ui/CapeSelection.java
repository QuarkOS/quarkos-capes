package dev.quarkos.capes.client.screen.ui;

import java.util.Objects;

/** Stable item identities outlive cards recreated by paging, search or resize. */
public final class CapeSelection {
    private String selected;
    private String equipped;
    private long revision;

    public long select(String id) {
        selected = id;
        return ++revision;
    }

    public void equip(String id) { equipped = id; }
    public boolean selected(String id) { return id != null && Objects.equals(id, selected); }
    public boolean equipped(String id) { return id != null && Objects.equals(id, equipped); }
    public boolean current(long request) { return request == revision && selected != null; }
}
