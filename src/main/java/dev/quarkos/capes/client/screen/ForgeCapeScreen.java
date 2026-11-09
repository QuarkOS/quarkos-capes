//? if forge {

/*package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
//? if dev_catalog {
/^import dev.quarkos.capes.client.net.LabyBrowse;
^///?}
//? if dev_catalog {
/^import dev.quarkos.capes.client.net.LabyCatalog;
^///?}
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public final class ForgeCapeScreen extends Screen {
    private final Screen parent;
    private int tab;
    private String note = "";
    private int page;
    private int rowIndex;
    private int rowLimit;
    private final java.util.Map<Button, String> samples = new java.util.LinkedHashMap<>();
    private final java.util.Map<Button, Component> hints = new java.util.HashMap<>();

    public ForgeCapeScreen(Screen parent) {
        super(Component.translatable("text.quarkos_capes.title"));
        this.parent = parent;
    }

    ForgeCapeScreen(Screen parent, int tab) { this(parent); this.tab = tab; }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        if (tab == 6 && minecraft != null) { minecraft.setScreen(new BrowseScreen(parent)); return; }
        if (tab == 4 && minecraft != null) {
            minecraft.setScreen(new CapeGalleryScreen(parent)); return;
        }
        clearWidgets();
        hints.clear();
        samples.clear();
        int tabWidth = Math.max(1, (width - 16) / CapeBoard.TABS.length);
        int tabX = Math.max(8, (width - CapeBoard.TABS.length * tabWidth) / 2);
        for (int i = 0; i < CapeBoard.TABS.length; i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.translatable(CapeBoard.TABS[i]), button -> {
                tab = index; page = 0;
                rebuild();
            }).bounds(tabX + i * tabWidth, 28, tabWidth - 2, 20).build());
        }
        rowIndex = 0;
        rowLimit = Math.max(1, (height - 130) / 22);
        int x = 8;
        int y = 56;
        int rowWidth = Math.max(100, width - (width < 480 || height < 300 ? 16 : 130));
        switch (tab) {
            case 0 -> {
                y = sample(x, y, rowWidth, "bands");
                y = sample(x, y, rowWidth, "slide");
                y = sample(x, y, rowWidth, "cloak");
                y = action(x, y, rowWidth, "text.quarkos_capes.button.save", () -> CapeBoard.save(value -> note = value));
                y = action(x, y, rowWidth, "text.quarkos_capes.button.publish", () -> CapeBoard.publish(value -> note = value));
                action(x, y, rowWidth, "text.quarkos_capes.button.unpublish", () -> CapeBoard.unpublish(value -> note = value));
            }
            case 1 -> {
                y = choice(x, y, rowWidth, "style", CapeChoices.style(), value -> CapeBoard.style(CapeChoices.id(value)));
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.animation", OwnerCape.get().animateFrames, () -> CapeBoard.animation(!OwnerCape.get().animateFrames));
                toggle(x, y, rowWidth, "text.quarkos_capes.preview.back", dev.quarkos.capes.client.render.CapeBook.previewBack, () -> dev.quarkos.capes.client.render.CapeBook.previewBack = !dev.quarkos.capes.client.render.CapeBook.previewBack);
            }
            case 2 -> {
                y = choice(x, y, rowWidth, "model", ClientSettings.get().cloth.movement(), CapeBoard::movement);
                y = choice(x, y, rowWidth, "look", ClientSettings.get().cloth.mesh(), CapeBoard::mesh);
                y = choice(x, y, rowWidth, "wind", ClientSettings.get().cloth.wind(), CapeBoard::wind);
                y = slider(x, y, rowWidth, "text.quarkos_capes.physics.gravity", ClientSettings.get().cloth.gravity(), 5, 32, CapeBoard::gravity);
                y = slider(x, y, rowWidth, "text.quarkos_capes.physics.height", ClientSettings.get().cloth.heightMultiplier(), 4, 16, CapeBoard::height);
                y = slider(x, y, rowWidth, "text.quarkos_capes.physics.strafe", ClientSettings.get().cloth.straveMultiplier(), 1, 8, CapeBoard::strafe);
                toggle(x, y, rowWidth, "text.quarkos_capes.toggle.gravity_vector", ClientSettings.get().cloth.computeGravityVector(), () -> CapeBoard.gravityVector(!ClientSettings.get().cloth.computeGravityVector()));
            }
            case 3 -> {
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.optifine", ClientSettings.get().enableOptifine, () -> CapeBoard.provider("optifine", !ClientSettings.get().enableOptifine));
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.labymod", ClientSettings.get().enableLabyMod, () -> CapeBoard.provider("labymod", !ClientSettings.get().enableLabyMod));
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.minecraftcapes", ClientSettings.get().enableMinecraftCapesMod, () -> CapeBoard.provider("minecraftcapes", !ClientSettings.get().enableMinecraftCapesMod));
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.cosmetica", ClientSettings.get().enableCosmetica, () -> CapeBoard.provider("cosmetica", !ClientSettings.get().enableCosmetica));
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.cloaksplus", ClientSettings.get().enableCloaksPlus, () -> CapeBoard.provider("cloaksplus", !ClientSettings.get().enableCloaksPlus));
        //? if dev_catalog {
        /^if (LabyCatalog.enabled()) {
                    if (!LabyBrowse.status().isBlank()) {
                        note = LabyBrowse.status();
                    }
                    y = action(x, y, rowWidth, "text.quarkos_capes.laby.previous", () -> LabyBrowse.previous(this::rebuild));
                    action(x, y, rowWidth, "text.quarkos_capes.laby.next", () -> LabyBrowse.next(this::rebuild));
                }
^///?}

            }
            case 4 -> {
                y = action(x, y, rowWidth, "text.quarkos_capes.button.publish", () -> CapeBoard.publish(value -> note = value));
                y = action(x, y, rowWidth, "text.quarkos_capes.button.unpublish", () -> CapeBoard.unpublish(value -> note = value));
                action(x, y, rowWidth, "text.quarkos_capes.gallery.report", () -> CapeBoard.report(null, value -> note = value));
            }
            default -> {
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.show_others", ClientSettings.get().showOthers, () -> CapeBoard.showOthers(!ClientSettings.get().showOthers));
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.local_only", ClientSettings.get().localOnly, () -> CapeBoard.localOnly(!ClientSettings.get().localOnly));
                y = toggle(x, y, rowWidth, "text.quarkos_capes.toggle.elytra", ClientSettings.get().enableElytraTexture, () -> CapeBoard.elytra(!ClientSettings.get().enableElytraTexture));
                int row = rowIndex++;
                if (row / rowLimit == page) {
                    EditBox service = addRenderableWidget(new EditBox(font, x, 56 + row % rowLimit * 22, rowWidth, 20,
                            Component.translatable("text.quarkos_capes.field.base_url")));
                    service.setMaxLength(2048);
                    service.setValue(ClientSettings.get().baseUrl);
                    service.setResponder(value -> {
                        if (dev.quarkos.capes.client.net.ServiceUrl.valid(value)) {
                            CapeBoard.baseUrl(value); note = "";
                        } else note = Component.translatable("text.quarkos_capes.status.invalid_url").getString();
                    });
                    service.setTooltip(Tooltip.create(Component.translatable("text.quarkos_capes.field.base_url")));
                }
            }
        }
        if (rowIndex > rowLimit) {
            addRenderableWidget(Button.builder(Component.translatable("text.quarkos_capes.gallery.prev"), b -> { page = Math.max(0, page - 1); rebuild(); })
                    .bounds(8, height - 76, 76, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.translatable("text.quarkos_capes.gallery.next"), b -> { page++; rebuild(); })
                    .bounds(88, height - 76, 76, 20).build()).active = (page + 1) * rowLimit < rowIndex;
        }
        int revertWidth = Math.min(150, Math.max(1, (width - 20) * 2 / 5));
        addRenderableWidget(Button.builder(Component.translatable("text.quarkos_capes.button.discard"), button -> {
            CapeBoard.discard();
            note = Component.translatable("text.quarkos_capes.status.discarded").getString();
            rebuild();
        }).bounds(8, height - 28, revertWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("text.quarkos_capes.tooltip.revert"))).build());
        addRenderableWidget(Button.builder(Component.translatable("text.quarkos_capes.button.save_quit"), button -> {
            CapeBoard.save(value -> { note = value; if (value.equals(Component.translatable("text.quarkos_capes.status.saved").getString())) onClose(); });
        }).bounds(12 + revertWidth, height - 28, Math.max(1, width - revertWidth - 20), 20).build());
        CapeBoard.show();
    }

    public Button sampleButton(String id) {
        return samples.entrySet().stream().filter(entry -> id.equals(entry.getValue())).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
    }

    private int sample(int x, int y, int width, String id) {
        int row = rowIndex++;
        if (row / rowLimit != page) return y + 22;
        y = 56 + row % rowLimit * 22;
        boolean equipped = CapeBoard.sampleOn(id);
        Button button = addRenderableWidget(Button.builder(Component.translatable(equipped
                ? "text.quarkos_capes.gallery.equipped" : "text.quarkos_capes.gallery.equip"), b -> {
            CapeBoard.equipSample(id); rebuild();
        }).bounds(x + Math.max(28, width - 82), y, Math.min(82, width - 28), 20).build());
        button.active = !equipped;
        samples.put(button, id);
        hint(button, Component.translatable("text.quarkos_capes.tooltip.equip"));
        return y + 22;
    }

    private int action(int x, int y, int width, String key, Runnable action) {
        int row = rowIndex++;
        if (row / rowLimit != page) return y + 22;
        y = 56 + row % rowLimit * 22;
        addRenderableWidget(Button.builder(Component.translatable(key), button -> {
            action.run(); rebuild();
        }).bounds(x, y, width, 20).build());
        return y + 22;
    }

    private <T extends Enum<T>> int choice(int x, int y, int width, String key, T value, java.util.function.Consumer<T> changed) {
        int row = rowIndex++;
        if (row / rowLimit != page) return y + 22;
        y = 56 + row % rowLimit * 22;
        Button button = addRenderableWidget(Button.builder(Component.translatable("text.quarkos_capes.selector." + key).append(": ").append(CapeChoices.name(value)), b -> {
            changed.accept(CapeChoices.next(value)); rebuild();
        }).bounds(x, y, width, 20).build());
        hint(button, CapeChoices.help(value));
        return y + 22;
    }

    private int toggle(int x, int y, int width, String key, boolean on, Runnable action) {
        int row = rowIndex++;
        if (row / rowLimit != page) return y + 22;
        y = 56 + row % rowLimit * 22;
        String name = Component.translatable(key).getString();
        Button control = addRenderableWidget(Button.builder(CapeBoard.state(name, on), button -> {
            action.run(); rebuild();
        }).bounds(x, y, width, 20).build());
        hint(control, Component.translatable(key.contains(".sample.") ? "text.quarkos_capes.tooltip.equip" : key.contains(".button.") ? key : "text.quarkos_capes.tooltip." + key.substring(key.lastIndexOf('.') + 1)));
        return y + 22;
    }

    private void hint(Button button, Component help) {
        hints.put(button, help);
        if (width >= 480 && height >= 300) button.setTooltip(Tooltip.create(help));
    }

    private int slider(int x, int y, int width, String name, int value, int min, int max, IntConsumer changed) {
        int row = rowIndex++;
        if (row / rowLimit != page) return y + 22;
        y = 56 + row % rowLimit * 22;
        var slider = addRenderableWidget(new NamedSlider(x, y, width, name, value, min, max, changed));
        slider.setTooltip(Tooltip.create(Component.translatable("text.quarkos_capes.tooltip." + name.substring(name.lastIndexOf('.') + 1))));
        return y + 22;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF10141C);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (var entry : samples.entrySet()) {
            Button button = entry.getKey();
            CapeThumbs.drawOne(graphics, entry.getValue(), 10, button.getY() + 2);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("text.quarkos_capes.sample." + entry.getValue()).getString(),
                    Math.max(1, button.getX() - 28)), 26, button.getY() + 6, 0xFFFFFFFF);
        }
        graphics.drawCenteredString(font, getTitle(), width / 2, 8, 0xFFFFFF);
        graphics.drawString(font, font.plainSubstrByWidth(note, width - 16), 8, height - 44, 0xE8B84A);
        if ((width < 480 || height < 300) && note.isBlank()) for (var hint : hints.entrySet()) {
            if (hint.getKey().isMouseOver(mouseX, mouseY)) graphics.drawString(font, font.plainSubstrByWidth(hint.getValue().getString(), width - 16), 8, height - 44, 0xFFB6C8DD);
        }
        int previewX = Math.max(8, width - 100);
        if (minecraft != null && minecraft.player != null && width >= 480 && height >= 300) {
            CapeBoard.show();
            McBridge.preview(graphics, width - 120, 54, width - 8, height - 84, minecraft.player);
        }
        if (tab == 6 && minecraft != null) { minecraft.setScreen(new BrowseScreen(parent)); return; }
        if (tab == 4) {
            CapeThumbs.draw(graphics, previewX, 56);
        }
    }

    @Override
    public void onClose() {
        McBridge.setScreen(minecraft, parent);
    }

    private static final class NamedSlider extends AbstractSliderButton {
        private final String name;
        private final int min;
        private final int max;
        private final IntConsumer changed;

        private NamedSlider(int x, int y, int width, String name, int value, int min, int max, IntConsumer changed) {
            super(x, y, width, 20, Component.empty(), (value - min) / (double) (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.changed = changed;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(name).append(" " + current()));
        }

        @Override
        protected void applyValue() {
            changed.accept(current());
        }

        private int current() {
            return min + (int) Math.round(value * (max - min));
        }
    }
}
*///?}
