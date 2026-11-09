package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.net.CatalogClient;
import dev.quarkos.capes.client.net.SiteApi;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Shared, keyboard-navigable site browser for every loader and version. */
public final class BrowseScreen extends Screen {
    private final Screen parent;
    private final Map<String,byte[]> images = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final List<String> history = new ArrayList<>();
    private CatalogClient.Page page = new CatalogClient.Page(List.of(),null,Map.of());
    private CatalogClient.Item selected, wearing;
    private OwnerCape draft;
    private String query = "", cursor = "", note = "";
    private int source, kind, sort, offset, generation, selection;
    private boolean loaded, busy, writing;
    private EditBox search;
    public BrowseScreen(Screen parent) { super(text("browse_title")); this.parent = parent; }
    public BrowseScreen(Screen parent,String source) { this(parent); this.source=source.equals("ours")?1:source.equals("labymod")?2:0; }
    public static Component text(String key) { return Component.translatable("text.quarkos_capes.site." + key); }
    @Override protected void init() {
        rebuild();
        if (!loaded) {
            loaded = true; load();
            if (!ClientSettings.get().localOnly) SiteApi.wearing(item -> { if (minecraft.screen == this) { wearing = item; rebuild(); } }, this::note);
        }
    }
    public List<CatalogClient.Item> items() { return page.items(); }
    public String status() { return note; }
    public boolean isLoading() { return busy; }
    public boolean previewReady() { return draft!=null; }
    public boolean isWriting() { return writing; }
    public boolean isWearing(CatalogClient.Item item) { return wearing!=null && wearing.id().equals(item.id()) && wearing.source().equals(item.source()); }
    public CatalogClient.Item selected() { return selected; }
    public void search(String value, String sourceValue) {
        query = value; source = sourceValue.equals("ours") ? 1 : sourceValue.equals("labymod") ? 2 : 0; reset();
    }
    public void selectFirst() { if (!page.items().isEmpty()) select(page.items().getFirst()); }
    public void wearSelected() { if (selected != null && draft != null && !isWearing(selected) && !writing) {
        writing = true; note = SiteApi.message("working"); rebuild();
        SiteApi.wear(selected, item -> { writing = false; wearing = item; note(SiteApi.message("worn")); }, error -> { writing = false; note(error); });
    } }
    public void unwear() { if (wearing != null && !writing) {
        writing = true; note = SiteApi.message("working"); rebuild();
        SiteApi.unwear(value -> { writing = false; wearing = null; note(value); }, error -> { writing = false; note(error); });
    } }
    private int divider() { return width < 480 || height < 300 ? width : Math.max(150, width * 3 / 5); }
    private boolean hasPreview() { return divider() < width; }
    private int columns() { return Math.max(1,(divider()-16)/72); }
    private int rows() { return Math.max(1,(height-(hasPreview()?142:170))/50); }
    private int capacity() { return rows()*columns(); }
    private void button(String key,int x,int y,int w,Runnable action) {
        addRenderableWidget(Button.builder(text(key),b -> action.run()).bounds(x,y,w,20).build());
    }
    private void rebuild() {
        clearWidgets();
        int left = divider(), right = width-left-8;
        boolean offline = ClientSettings.get().localOnly;
        button("back",8,6,Math.min(80,left-16),this::onClose);
        button("link",width-88,6,80,() -> minecraft.setScreen(new LinkScreen(this)));
        search = addRenderableWidget(new EditBox(font,8,30,width-94,20,text("search")));
        search.setMaxLength(48); search.setValue(query); search.setResponder(value -> query=value);
        button("search",width-82,30,74,this::reset);
        String[] sources = {"all_sources","ours","labymod"}, kinds={"all_kinds","cape","cloak"}, sorts={"popular","newest","name"};
        int chip = (width-16)/3;
        button(sources[source],8,54,chip-2,() -> { source=(source+1)%3; reset(); });
        button(kinds[kind],8+chip,54,chip-2,() -> { kind=(kind+1)%3; reset(); });
        button(sorts[sort],8+chip*2,54,chip-2,() -> { sort=(sort+1)%3; reset(); });
        if (!offline) for (int i=offset;i<Math.min(page.items().size(),offset+capacity());i++) {
            CatalogClient.Item item = page.items().get(i); int cell=i-offset;
            int x=8+cell%columns()*((left-16)/columns()), y=80+cell/columns()*50;
            Button tile=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(item.title(),(left-16)/columns()-8)),b -> select(item))
                    .bounds(x,y,(left-16)/columns()-4,46).tooltip(Tooltip.create(Component.literal(item.title()).append("\n").append(credit(item)).append(item.source().equals("labymod") ? Component.literal("\n").append(text("non_affiliation")) : Component.empty()))).build());
            thumbnail(item);
        }
        button("previous",8,height-56,Math.max(50,(left-20)/2),this::previous);
        button("next",10+(left-20)/2,height-56,Math.max(50,(left-20)/2),this::next);
        if (hasPreview()) {
            Button wear=addRenderableWidget(Button.builder(text("wear"),b -> wearSelected()).bounds(left,height-70,Math.max(1,right/2-1),20).build());
            wear.active=!offline && selected!=null && draft!=null && !isWearing(selected) && !writing;
            Button unwear=addRenderableWidget(Button.builder(text("unwear"),b -> unwear()).bounds(left+right/2+1,height-70,Math.max(1,right/2-1),20).build());
            unwear.active=!offline && wearing!=null && !writing;
            button("retry_button",left,height-46,right,this::load);
        } else {
            int actionWidth = Math.max(1, (width-20)/2);
            Button wear=addRenderableWidget(Button.builder(text("wear"),b -> wearSelected()).bounds(8,height-80,actionWidth,20).build());
            wear.active=!offline && selected!=null && draft!=null && !isWearing(selected) && !writing;
            Button unwear=addRenderableWidget(Button.builder(text("unwear"),b -> unwear()).bounds(12+actionWidth,height-80,actionWidth,20).build());
            unwear.active=!offline && wearing!=null && !writing;
        }
        if (offline) button("go_online",Math.max(8,(left-120)/2),emptyY()+28,Math.min(120,left-16),this::goOnline);

    }
    private int emptyY() { return Math.max(82, (80 + height - 100) / 2 - 20); }
    private void goOnline() {
        CapeBoard.localOnly(false);
        CapeBoard.save(this::note);
        load();
        SiteApi.wearing(item -> { if (minecraft.screen == this) { wearing = item; rebuild(); } }, this::note);
    }
    private String key(CatalogClient.Item item) { return item.source()+"/"+item.id(); }
    private Component credit(CatalogClient.Item item) { return item.source().equals("labymod") ? text("credit") : Component.literal(item.author()); }
    private void thumbnail(CatalogClient.Item item) {
        String key=key(item);
        if (images.containsKey(key) || !loading.add(key)) return;
        int request=generation;
        SiteApi.image(item,png -> {
            if (minecraft.screen!=this || request!=generation) return;
            loading.remove(key); images.put(key,png); CapeTextures.upload("browse/"+key,png,false,false);
        }, error -> { if (request==generation) { loading.remove(key); note=error; } });
    }
    private void select(CatalogClient.Item item) {
        selected=item; draft=null; int request=++selection;
        note=SiteApi.message("loading"); rebuild();
        SiteApi.detail(item,detail -> {
            if (minecraft.screen!=this || request!=selection) return;
            selected=detail;
            SiteApi.image(detail,png -> {
                if (minecraft.screen!=this || request!=selection) return;
                draft=SiteApi.owner(detail,png); note=""; rebuild();
            },this::note);
        },this::note);
    }
    private void reset() { cursor=""; history.clear(); offset=0; load(); }
    private void load() {
        int request=++generation;
        if (ClientSettings.get().localOnly) {
            busy=false; note=""; selected=null; draft=null; clearImages();
            page=new CatalogClient.Page(List.of(),null,Map.of()); rebuild(); return;
        }
        busy=true; note=SiteApi.message("loading");
        clearImages(); page=new CatalogClient.Page(List.of(),null,Map.of()); rebuild();
        CatalogClient.Query filters=new CatalogClient.Query(new String[]{"","ours","labymod"}[source],new String[]{"","cape","cloak"}[kind],query,
                new String[]{"popular","newest","name"}[sort],"","",cursor,20);
        SiteApi.page(filters,result -> {
            if (minecraft.screen!=this || request!=generation) return;
            page=result; offset=0; busy=false;
            note=result.items().isEmpty()?SiteApi.message(result.nextCursor()==null?"empty":"empty_page"):"";
            if (result.sources().containsValue("unavailable")) note=SiteApi.message("source_unavailable");
            rebuild();
        },error -> { if (request==generation && minecraft.screen==this) { busy=false; note(error); } });
    }
    private void note(String value) { if (minecraft!=null && minecraft.screen==this) { note=value; rebuild(); } }
    private void next() {
        if (busy) return;
        if (offset+capacity()<page.items().size()) { offset+=capacity(); rebuild(); }
        else if (page.nextCursor()!=null) { history.add(cursor); cursor=page.nextCursor(); load(); }
    }
    private void previous() {
        if (busy) return;
        if (offset>0) { offset=Math.max(0,offset-capacity()); rebuild(); }
        else if (!history.isEmpty()) { cursor=history.removeLast(); load(); }
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if (vertical<0) next(); else if (vertical>0) previous(); return true;
    }
    //? if >=1.21.9 {
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (search.isFocused() && (event.key()==257 || event.key()==335)) { reset(); return true; }
        return super.keyPressed(event);
    }
    //?} else {
    /*@Override public boolean keyPressed(int key,int scan,int modifiers) {
        if (search.isFocused() && (key==257 || key==335)) { reset(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    *///?}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta) {
        graphics.fill(0,0,width,height,0xF01A2029);
        super.render(graphics,mouseX,mouseY,delta);
        int left=divider();
        graphics.drawCenteredString(font,getTitle(),width/2,12,0xFFFFFFFF);
        if (ClientSettings.get().localOnly) {
            graphics.drawCenteredString(font,text("offline_title"),left/2,emptyY(),0xFFFFFFFF);
            graphics.drawCenteredString(font,Component.literal(font.plainSubstrByWidth(text("offline_help").getString(),left-16)),left/2,emptyY()+14,0xFFB6C8DD);
        }
        for (int i=offset;i<Math.min(page.items().size(),offset+capacity());i++) {
            CatalogClient.Item item=page.items().get(i); var image=CapeTextures.get("browse/"+key(item)); if (image==null) continue;
            int cell=i-offset, x=8+cell%columns()*((left-16)/columns()), y=80+cell/columns()*50;
            McBridge.capeThumb(graphics,image.id(),x+((left-16)/columns()-14)/2,y+1,10,16,image.width(),image.height(),item.source().equals("labymod")?image.width():item.frameWidth(),item.source().equals("labymod")?image.height():item.frameHeight());
        }
        if (hasPreview() && draft!=null && minecraft.player!=null) {
            CapeBook.preview=true; CapeBook.previewBack=true; CapeBook.draft=draft;
            McBridge.preview(graphics,left,80,width-8,height-114,minecraft.player);
        }
        if (hasPreview() && draft==null) graphics.drawCenteredString(font,
                Component.literal(font.plainSubstrByWidth(text("select_preview").getString(),width-left-12)),(left+width-8)/2,Math.max(84,(height-30)/2),0xFFB6C8DD);
        if (hasPreview()) graphics.drawString(font,font.plainSubstrByWidth(
                Component.translatable("text.quarkos_capes.site.wearing",wearing==null?text("nothing"):Component.literal(wearing.title())).getString(),width-left-8),left,height-108,0xFFB6C8DD);
        if (!hasPreview()) graphics.drawString(font,font.plainSubstrByWidth(
                Component.translatable("text.quarkos_capes.site.wearing",wearing==null?text("nothing"):Component.literal(wearing.title())).getString(),width-16),8,height-94,0xFFB6C8DD);
        if (hasPreview() && selected!=null) {
            graphics.drawString(font,font.plainSubstrByWidth(selected.title(),width-left-8),left,height-94,0xFFFFFFFF);
            graphics.drawString(font,font.plainSubstrByWidth(credit(selected).getString(),width-left-8),left,height-83,0xFFB6C8DD);
            if (selected.source().equals("labymod")) graphics.drawString(font,font.plainSubstrByWidth(text("non_affiliation").getString(),width-16),8,height-16,0xFFB6C8DD);
        }
        graphics.drawString(font,font.plainSubstrByWidth(note,width-16),8,height-30,0xFFFFD980);
    }
    private void clearImages() { for (String key:images.keySet()) CapeTextures.clear("browse/"+key); images.clear(); loading.clear(); }
    @Override public void removed() { clearImages(); generation++; selection++; }
    @Override public void onClose() { CapeBook.draft=null; CapeBook.preview=false; minecraft.setScreen(CapeScreens.refreshed(parent)); }
}
