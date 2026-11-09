package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.net.SiteApi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/** A website's single-use linking code is claimed with the Minecraft bearer. */
public final class LinkScreen extends Screen {
    private final Screen parent;
    private EditBox code;
    private String value="",note="";
    private boolean busy;
    public LinkScreen(Screen parent) { super(BrowseScreen.text("link")); this.parent=parent; }
    @Override protected void init() {
        int x=width/2-100;
        code=addRenderableWidget(new EditBox(font,x,60,200,20,BrowseScreen.text("code")));
        code.setMaxLength(8); code.setValue(value); code.setResponder(text -> value=text);
        Button claim=addRenderableWidget(Button.builder(BrowseScreen.text("claim"),b -> claim()).bounds(x,86,200,20).build()); claim.active=!busy;
        addRenderableWidget(Button.builder(BrowseScreen.text("back"),b -> onClose()).bounds(x,height-30,200,20).build());
        setInitialFocus(code);
    }
    private void claim() {
        if (busy) return; busy=true; note=SiteApi.message("working");
        SiteApi.claim(value,this::done,this::done);
    }
    private void done(String message) { if (minecraft.screen!=this) return; busy=false; note=message; clearWidgets(); init(); }
    //? if >=1.21.9 {
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (code.isFocused() && (event.key()==257 || event.key()==335)) { claim(); return true; }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (code.isFocused() && (key==257 || key==335)) { claim(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    *///?}
    @Override public void render(GuiGraphics graphics,int x,int y,float delta) {
        graphics.fill(0,0,width,height,0xF01A2029); super.render(graphics,x,y,delta);
        graphics.drawCenteredString(font,getTitle(),width/2,20,0xFFFFFFFF);
        graphics.drawString(font,font.plainSubstrByWidth(BrowseScreen.text("code_help").getString(),width-16),8,44,0xFFB6C8DD);
        graphics.drawString(font,font.plainSubstrByWidth(note,width-16),8,116,0xFFFFD980);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
