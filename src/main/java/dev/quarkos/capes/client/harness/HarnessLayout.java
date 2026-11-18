package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
//? if fabric || <1.21.2 {
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
//?}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class HarnessLayout {
    private record Control(String label, Rect area) { }

    private HarnessLayout() { }

    static void check(Screen screen) {
        if (screen == null) throw new AssertionError("Screen is missing");
        Rect limits = new Rect(0, 0, screen.width, screen.height);
        List<Control> controls = new ArrayList<>();
        Set<GuiEventListener> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Object scrollList = null;
        Rect clip = null;
        //? if fabric || <1.21.2 {
        if (screen instanceof ClothConfigScreen cloth && cloth.listWidget != null) {
            scrollList = cloth.listWidget;
            clip = new Rect(0, cloth.listWidget.top, screen.width, cloth.listWidget.bottom - cloth.listWidget.top);
            inside(limits, new Control("Cloth list viewport", clip));
            for (Object row : cloth.listWidget.children()) {
                if (row instanceof GuiEventListener entry) collect(entry, limits, clip, scrollList, clip, controls, seen);
            }
        }
        //?}
        for (var child : screen.children()) collect(child, limits, null, scrollList, clip, controls, seen);
        if (controls.isEmpty()) throw new AssertionError("No interactive widgets on " + screen.getTitle().getString());
        for (int i = 0; i < controls.size(); i++) {
            Control first = controls.get(i);
            for (int j = i + 1; j < controls.size(); j++) {
                Control second = controls.get(j);
                if (intersection(first.area(), second.area()) != null) {
                    throw new AssertionError("Widgets overlap: " + first.label() + " " + first.area() + " and " + second.label() + " " + second.area());
                }
            }
        }
    }

    private static void collect(GuiEventListener item, Rect limits, Rect clipping, Object list, Rect listClip,
                                List<Control> controls, Set<GuiEventListener> seen) {
        if (!seen.add(item)) return;
        if (item instanceof AbstractWidget widget && !widget.visible) return;
        if (item instanceof ContainerEventHandler container) {
            Rect childClip = item == list ? listClip : clipping;
            for (var child : container.children()) collect(child, limits, childClip, list, listClip, controls, seen);
        } else if (item instanceof AbstractWidget widget && item != list) {
            Rect area = new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
            if (clipping != null) {
                // Cloth rows are positioned as they are drawn. An off-screen row only has a hitbox inside the scroll viewport.
                area = intersection(area, clipping);
                if (area == null) return;
            }
            Control control = new Control(widget.getMessage().getString(), area);
            inside(limits, control);
            controls.add(control);
        }
    }

    private static void inside(Rect limits, Control control) {
        Rect area = control.area();
        if (area.x() < limits.x() || area.y() < limits.y() || area.right() > limits.right() || area.bottom() > limits.bottom()
                || area.width() <= 0 || area.height() <= 0) {
            throw new AssertionError("Widget outside screen: " + control.label() + " " + area + " in " + limits);
        }
    }

    private static Rect intersection(Rect first, Rect second) {
        int left = Math.max(first.x(), second.x());
        int top = Math.max(first.y(), second.y());
        int right = Math.min(first.right(), second.right());
        int bottom = Math.min(first.bottom(), second.bottom());
        return left < right && top < bottom ? new Rect(left, top, right - left, bottom - top) : null;
    }
}
