package dev.quarkos.capes.client.harness;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.*;
import dev.quarkos.capes.client.net.*;
import dev.quarkos.capes.client.render.*;
import dev.quarkos.capes.client.screen.*;
import dev.quarkos.capes.client.screen.ui.CapeStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.PlayerModelPart;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Real client flows against the local site. A failed observation is reported and the next flow still runs. */
public final class FlowHarness {
    private static final String[] NAMES={"open-keybind","tab-my-cape","tab-style","tab-physics","tab-sources","tab-gallery","tab-advanced",
            "bundled-samples","styles","save-reopen","physics-slider","source-toggles","publish-unpublish","gallery-search","browse-page",
            "wear","unwear","other-player","link-claim","show-others","gui-sweep","survival-inventory","first-person","dev-catalog-absent","browse-offline-cta"};
    private record Step(String shot,Runnable start,BooleanSupplier ready,Runnable verify) { }
    private static final Deque<Step> STEPS=new ArrayDeque<>();
    private static final List<String> ERRORS=new ArrayList<>();
    private static final List<Path> SHOTS=new ArrayList<>();
    private static Minecraft mc;
    private static Step step;
    private static int flow,age,pass,fail,finishAge;
    private static boolean started,asyncDone,skip;
    private static String asyncError,detail="";
    private static Object result;
    private static long generation;
    private static BrowseScreen browse;
    private static CapeGalleryScreen gallery;
    private static LinkScreen link;
    private static CatalogClient.Item chosen;
    private static RemotePlayer remote;
    private static final UUID REMOTE_ID=HarnessAuth.offlineUuid("CapeFlowRemote");
    private static String ownId, publishedTitle;
    private static int requests;
    private static String savedSettings;
    private static Path originalSettingsFile;
    private static long deadline;
    private static final Path DIRECTORY=Path.of(System.getProperty("quarkos_capes.captures","build/captures"));
    private FlowHarness() { }
    public static boolean selected() { return Boolean.getBoolean("quarkos_capes.harness") && "flows".equals(System.getProperty("quarkos_capes.only")); }
    public static void tick(Minecraft client) {
        if (!selected()) return; mc=client;
        if (!started) {
            started=true; ClientSettings s=ClientSettings.get(); try { originalSettingsFile=Files.createTempFile("capes-flow-settings-", ".json"); Files.writeString(originalSettingsFile,s.json()); }
            catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); } s.baseUrl=System.getProperty("quarkos_capes.serverUrl",dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL);
            s.localOnly=false;s.showOthers=true;s.enableOptifine=false;s.enableLabyMod=false;s.enableMinecraftCapesMod=false;s.enableCosmetica=false;s.enableCloaksPlus=false;
            client.options.tutorialStep=net.minecraft.client.tutorial.TutorialSteps.NONE;client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.options.framerateLimit().set(60);client.options.hideGui=false;McBridge.showCape(client.options);McBridge.prepareWorld(client);
            ownId=HarnessAuth.offlineUuid(client.getUser().getName()).toString();McBridge.clearChat(client);McBridge.clearToasts(client);
        }
        McBridge.clearChat(client);McBridge.clearToasts(client);
        if (flow==NAMES.length) {
            if (++finishAge<30) return;
            if (SHOTS.stream().allMatch(FlowHarness::written)) {
                System.out.println("CAPES_FLOWS pass="+pass+" fail="+fail);System.exit(0);
            }
            if (finishAge>600) throw new IllegalStateException("Flow screenshot writer did not finish");
            return;
        }
        try {
            if (step==null) {
                if (STEPS.isEmpty()) build(flow+1);
                step=STEPS.removeFirst(); age=0; deadline=System.nanoTime()+20_000_000_000L; step.start.run();
            }
            age++;
            if (age<30 || !step.ready.getAsBoolean()) {
                if (System.nanoTime()>deadline) throw new AssertionError("20s timeout in " + NAMES[flow] + " (" + (step.shot==null?"action":step.shot) + "): " + diagnostics());
                return;
            }
            step.verify.run();
            if (step.shot!=null) capture(step.shot);
            step=null;
            if (STEPS.isEmpty()) complete(ERRORS.isEmpty(),!ERRORS.isEmpty()?String.join("; ",ERRORS):skip?"PASS-SKIP linkCode absent":detail.isEmpty()?"observed":detail);
        } catch (AssertionError | RuntimeException failure) {
            String problem=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();
            ERRORS.add(problem);
            capture(step==null?"failed":step.shot==null?"failed-"+ERRORS.size():step.shot);
            step=null;generation++;
            if(STEPS.isEmpty()) complete(false,String.join("; ",ERRORS));
        }
    }
    private static boolean written(Path file) { try{return Files.size(file)>1000;}catch(Exception e){return false;} }
    private static void capture(String suffix) {
        String name=String.format(Locale.ROOT,"flow-%02d-%s",flow+1,NAMES[flow]);
        if (!suffix.equals("main")) name+="-"+suffix;
        Path file=DIRECTORY.resolve(name+".png");
        try{Files.deleteIfExists(file);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        SHOTS.add(file);McBridge.shot(mc,file);
    }
    private static void complete(boolean ok,String message) {
        System.out.println("CAPES_FLOW "+NAMES[flow]+" "+(ok?"PASS":"FAIL")+" "+message.replace('\n',' '));
        if(ok)pass++;else fail++;generation++;flow++;STEPS.clear();ERRORS.clear();step=null;skip=false;detail="";
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private static void scene(String name,Runnable start,Runnable verify) { STEPS.add(new Step(name,start,()->true,verify)); }
    private static void waitFor(String name,Runnable start,BooleanSupplier ready,Runnable verify) { STEPS.add(new Step(name,start,ready,verify)); }
    private static void async(String name,Consumer<Consumer<Object>> start,Consumer<Object> check) {
        waitFor(name,()->{
            asyncDone=false;asyncError=null;result=null;long ticket=++generation;
            start.accept(value->{if(ticket==generation){result=value;asyncDone=true;}});
        },()->asyncDone || asyncError!=null,()->{require(asyncError==null,asyncError);check.accept(result);});
    }
    private static Consumer<String> failure() {
        long ticket=generation;
        return note->{if(ticket==generation)asyncError=note;};
    }
    private static void world() {
        mc.setScreen(null);CapeBook.preview=false;CapeBook.draft=null;mc.options.hideGui=true;McBridge.thirdPerson(mc.options);
        ShotView.frame(25,8,4f);CapeBook.resetDraws();CapeLayerWatch.reset();
    }
    private static void open(int tab) { mc.options.hideGui=false;mc.setScreen(CapeScreens.openTab(null,tab));CapeBook.resetDraws(); }
    private static void equipSample(String sample) {
        require(mc.screen instanceof CapeScreen, "Wardrobe is closed");
        Button card = ((CapeScreen) mc.screen).sampleCard(sample);
        require(card != null, "Missing sample card: " + sample);
        McBridge.press(card);
        require(CapeBoard.sampleOn(sample), "Card did not equip: " + sample);
    }
    private static String actionNote() {
        return ((CapeScreen) mc.screen).status();
    }
    private static void action(String shot, String key, String progress, String expected) {
        waitFor(shot, () -> click(key),
                () -> !actionNote().equals(status(progress)),
                () -> require(actionNote().equals(status(expected)), "Action status: " + actionNote()));
    }
    private static void localPreviewDraw() { require(CapeBook.previewDraws(mc.player.getUUID())>0,"Local preview cape did not draw"); }
    private static void localDraw() { require(CapeBook.draws(mc.player.getUUID())>0,"Local cape did not draw"); }
    private static void build(int number) {
        switch(number) {
            case 1 -> scene("main",()->{mc.setScreen(null);KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(79));},
                    ()->require(CapeScreens.isSettings(mc.screen),"O mapping did not open settings"));
            case 2,3,4,5,6,7 -> scene("main",()->open(number-2),()->layout(mc.screen));
            case 8 -> {
                for(String sample:List.of("bands","slide","cloak")) {
                    scene(sample+"-preview",()->{open(0);equipSample(sample);},FlowHarness::localPreviewDraw);
                    scene(sample+"-world",FlowHarness::world,FlowHarness::localDraw);
                }
            }
            case 9 -> {
                for(String style:List.of("flat","frames","wave","model")) scene(style,()->{
                    open(0);
                    equipSample(style.equals("frames") ? "slide" : "bands");
                    CapeScreen wardrobe = (CapeScreen) mc.screen;
                    switch (style) {
                        case "frames" -> {
                            McBridge.press(wardrobe.styleChip(CapeStyles.Chip.ANIMATED));
                            require(!OwnerCape.get().animateFrames, "Animation did not turn off");
                            McBridge.press(wardrobe.styleChip(CapeStyles.Chip.ANIMATED));
                        }
                        case "wave" -> McBridge.press(wardrobe.styleChip(CapeStyles.Chip.WAVY));
                        case "model" -> McBridge.press(wardrobe.styleChip(CapeStyles.Chip.MODEL));
                        default -> McBridge.press(wardrobe.styleChip(CapeStyles.Chip.FLAT));
                    }
                    require(style.equals(OwnerCape.get().style), "Style chip did not select " + style);
                    world();
                },FlowHarness::localDraw);
            }
            case 10 -> {
                scene(null, () -> { open(0); savedSettings=ClientSettings.get().json(); }, () -> {});
                action(null, "text.quarkos_capes.button.save", "saving", "saved");
                scene("main",()->{LocalFiles.load();open(5);},()->require(savedSettings.equals(ClientSettings.get().json()),"Settings round trip changed values"));
            }
            case 11 -> {
                scene(null,()->open(2),()->{});
                scene(null,()-> {
                    Object gravity = entry("text.quarkos_capes.physics.gravity");
                    if (gravity != null) setEntry(gravity, 5);
                    else {
                        var slider=widgets(mc.screen).stream().filter(w->w instanceof AbstractSliderButton && hasKey(w, "text.quarkos_capes.physics.gravity")).findFirst();
                        require(slider.isPresent(),"No Gravity entry or slider on Physics page");McBridge.slide((AbstractSliderButton)slider.get(),0);
                    }
                },()->require(ClientSettings.get().cloth.gravity()==5,"Gravity entry change did not apply"));
                scene("main",()->open(2),()->require(ClientSettings.get().cloth.gravity()==5,"Gravity changed on reopen"));
            }
            case 12 -> {
                scene(null,()->{
                    mc.setScreen(null);CapeBoard.localOnly(true);for(String provider:CapeCalls.PROVIDERS)CapeBoard.provider(provider,false);requests=CapeHttp.sent;open(3);
                },()->require(CapeHttp.sent==requests,"Offline mode sent requests"));
                scene("main",()->{for(String provider:CapeCalls.PROVIDERS)setToggle("text.quarkos_capes.toggle."+provider,true);},()->{
                    require(CapeHttp.sent==requests,"Offline provider toggle sent requests");
                    for(String provider:CapeCalls.PROVIDERS)require(ClientSettings.get().providerEnabled(provider),"Provider toggle did not apply: "+provider);
                });
                scene(null,()->{for(String provider:CapeCalls.PROVIDERS)setToggle("text.quarkos_capes.toggle."+provider,false);},()->{});
                scene(null,()->open(5),()->{});
                scene(null,()->setToggle("text.quarkos_capes.toggle.local_only",false),()->require(!ClientSettings.get().localOnly,"Advanced Offline toggle did not turn off"));
                async(null,done->CapeBoard.save(done::accept),value->require(value.equals(status("saved")),String.valueOf(value)));
            }
            case 13 -> {
                scene(null, () -> { open(0); equipSample("bands"); }, () -> {});
                action("published", "text.quarkos_capes.button.publish", "publishing", "published");
                action("main", "text.quarkos_capes.button.unpublish", "unpublishing", "unpublished");
            }
            case 14 -> {
                // Re-publish the fixture after flow 13 proved unpublish, so search has a public item to find.
                scene(null, () -> open(0), () -> {});
                action(null, "text.quarkos_capes.button.publish", "publishing", "published");
                async(null,done->CapeHttp.gallery("","",50,"",done::accept,failure()),value->{
                    var page=(GalleryPages.Page)value;var item=page.entries().stream().filter(e->e.id().equals(ownId)).findFirst();
                    require(item.isPresent(),"Published cape missing from gallery");publishedTitle=item.get().title();
                });
                waitFor("main", () -> {
                    gallery = new CapeGalleryScreen(null);
                    gallery.search(publishedTitle);
                    mc.options.hideGui = false;
                    mc.setScreen(gallery);
                }, () -> !gallery.isLoading(), () -> require(gallery.sharedEntries().stream().anyMatch(e -> e.id().equals(ownId)),
                        "Gallery search did not find published cape: " + gallery.status()));
            }
            case 15 -> {
                async(null,done -> SiteApi.work(() -> {
                    var filters = new CatalogClient.Query("ours","","","newest","","","",20);
                    var page = SiteApi.CLIENT.page(ClientSettings.get().baseUrl,filters);
                    if (page.items().isEmpty()) HarnessAuth.putCatalogUpload(ClientSettings.get().baseUrl, CapeHttp.siteBearer(), ownId, Samples.bands());
                    return true;
                },done::accept,failure()),value -> {});
                waitFor("main",()->{browse=new BrowseScreen(CapeScreens.open(null),"ours");mc.options.hideGui=false;mc.setScreen(browse);},
                    ()->!browse.items().isEmpty(),()->{chosen=browse.items().getFirst();require(chosen.source().equals("ours"),"Wrong source");layout(browse);});
            }
            case 16 -> {
                waitFor(null,()->{require(browse!=null,"Browse unavailable");browse.selectFirst();},()->browse.previewReady(),()->{});
                waitFor(null, () -> click("text.quarkos_capes.site.wear"), () -> !browse.isWriting(),()->require(browse.isWearing(chosen),"Wear action failed: "+browse.status()));
                async(null,done->SiteApi.wearing(done::accept,failure()),value->{
                    var item=(CatalogClient.Item)value;require(item!=null && item.id().equals(chosen.id()) && item.source().equals(chosen.source()),"GET wearing mismatch");
                });
                scene("main",FlowHarness::world,FlowHarness::localDraw);
            }
            case 17 -> {
                waitFor(null, () -> {
                    require(chosen != null, "Browse selection missing");
                    browse = new BrowseScreen(CapeScreens.open(null), "ours");
                    mc.options.hideGui = false;
                    mc.setScreen(browse);
                }, () -> browse.isWearing(chosen), () -> { });
                waitFor(null, () -> click("text.quarkos_capes.site.unwear"), () -> !browse.isWriting(),
                        () -> require(!browse.isWearing(chosen), "Unwear action failed: " + browse.status()));
                async("main",done->SiteApi.wearing(done::accept,failure()),value->require(value==null,"Wearing was not removed"));
            }
            case 18 -> {
                async(null,done->{
                    require(chosen!=null,"No catalog fixture");require(HarnessAuth.active(),"Dev auth is required");
                    SiteApi.work(()->{
                        var session=HarnessAuth.complete(ClientSettings.get().baseUrl,"CapeFlowRemote");
                        return SiteApi.CLIENT.request(ClientSettings.get().baseUrl,"PUT","/v1/me/wearing",
                                session.token(),Map.of("source",chosen.source(),"id",chosen.id()));
                    },done::accept,failure());
                },value->{});
                scene(null,()->{
                    remote=new RemotePlayer(mc.level,new GameProfile(REMOTE_ID,"CapeFlowRemote")) {
                        @Override public boolean isModelPartShown(PlayerModelPart part){return true;}
                    };
                    remote.setId(1_900_001);remote.setPos(mc.player.getX()+1.2,mc.player.getY(),mc.player.getZ()+.5);
                    //? if <1.21 {
                    /*try {
                        var add = mc.level.getClass().getDeclaredMethod("addEntity", int.class, net.minecraft.world.entity.Entity.class);
                        add.setAccessible(true);
                        add.invoke(mc.level, remote.getId(), remote);
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(failure);
                    }
                    *///?} else {
                    mc.level.addEntity(remote);
                    //?}
                    world();
                },()->{});
                waitFor("main",()->SiteApi.poll(List.of(REMOTE_ID)),()->CapeBook.draws(REMOTE_ID)>0,()->require(CapeBook.remote(REMOTE_ID)!=null,"Remote reference did not resolve"));
            }
            case 19 -> {
                String code=System.getProperty("quarkos_capes.linkCode","");
                if(code.isBlank())scene("main",()->{skip=true;mc.options.hideGui=false;mc.setScreen(new LinkScreen(CapeScreens.open(null)));},()->{});
                else waitFor("main", () -> {
                    link = new LinkScreen(CapeScreens.open(null));
                    mc.options.hideGui = false;
                    mc.setScreen(link);
                    var field = widgets(link).stream().filter(w -> w instanceof EditBox && hasKey(w, "text.quarkos_capes.site.code")).findFirst();
                    require(field.isPresent(), "Website code field is missing");
                    ((EditBox) field.get()).setValue(code);
                    click("text.quarkos_capes.site.claim");
                }, () -> !link.isBusy(), () -> require(link.status().contains("Linked"), link.status()));
            }
            case 20 -> scene("main",()->{CapeBoard.showOthers(false);world();},()->{
                require(remote!=null,"Remote fixture missing");require(CapeBook.draws(REMOTE_ID)==0,"Show others off still drew remote cape");CapeBoard.showOthers(true);
            });
            case 21 -> {
                for(int[] size:List.of(new int[]{854,480},new int[]{1920,1080}))for(int scale:List.of(1,3,4))for(int tab=0;tab<8;tab++) {
                    final int category=tab;
                    scene(size[0]+"x"+size[1]+"-scale"+scale+"-tab"+tab,()->{
                        mc.options.guiScale().set(scale);mc.getWindow().setWindowed(size[0],size[1]);mc.resizeDisplay();if(category==7)mc.setScreen(new LinkScreen(CapeScreens.open(null)));else open(category);
                    },()->layout(mc.screen));
                }
                scene("main",()->{mc.options.guiScale().set(2);mc.getWindow().setWindowed(1280,720);mc.resizeDisplay();open(0);},()->layout(mc.screen));
            }
            case 22 -> {
                scene(null,()->{mc.setScreen(null);CapeBoard.equipSample("cloak");mc.player.connection.sendCommand("gamemode survival");},()->require(mc.gameMode.getPlayerMode()==net.minecraft.world.level.GameType.SURVIVAL,"Survival command did not apply"));
                scene("main",()->{mc.options.hideGui=false;mc.setScreen(new InventoryScreen(mc.player));CapeBook.resetDraws();},()->{
                    require(mc.screen instanceof InventoryScreen,"Inventory redirected to creative screen");localPreviewDraw();
                });
            }
            case 23 -> {
                waitFor(null,()->{
                mc.setScreen(null);ShotView.clear();mc.options.hideGui=true;
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(294));
                }, () -> mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON,
                        () -> { });
                scene("main", () -> { CapeBook.resetDraws(); CapeLayerWatch.reset(); }, () -> {
                require(mc.options.getCameraType()==net.minecraft.client.CameraType.FIRST_PERSON,"F5 did not select first person");
                require(CapeBook.draws(mc.player.getUUID())==0,"Local cape rendered in first-person camera");
            });
            }
            case 24 -> {
                for(int tab=0;tab<6;tab++){final int category=tab;scene(tab==5?"main":null,()->open(category),()->HarnessChecks.catalogHidden(mc.screen));}
            }
            case 25 -> {
                scene("offline",()-> {
                    mc.setScreen(null);CapeBoard.localOnly(true);requests=CapeHttp.sent;
                    browse=new BrowseScreen(CapeScreens.open(null),"ours");mc.options.hideGui=false;mc.setScreen(browse);
                },()-> {
                    require(ClientSettings.get().localOnly,"Offline mode was not enabled");
                    require(CapeHttp.sent==requests,"Offline Browse sent requests");
                    require(widgets(browse).stream().anyMatch(w->w instanceof Button && hasKey(w, "text.quarkos_capes.site.go_online")),"Browse has no Go online button");
                });
                waitFor("main",()->click("text.quarkos_capes.site.go_online"),()->!ClientSettings.get().localOnly && !browse.isLoading(),()-> {
                    require(!ClientSettings.get().localOnly,"Go online did not disable Offline");
                    require(!browse.items().isEmpty(),"Go online did not load catalog: "+browse.status());
                    require(CapeHttp.sent>requests,"Go online did not send a request");layout(browse);
                });
                scene(null, browse::onClose, () -> require(!ClientSettings.get().localOnly, "Returning from Browse restored stale Offline state"));
                async(null,done -> {
                    mc.setScreen(null);
                    ClientSettings.load(originalSettingsFile);
                    LocalFiles.saveAsync(done::accept);
                },value -> {
                    require(Boolean.TRUE.equals(value),"Restoring original settings failed");
                    try { Files.deleteIfExists(originalSettingsFile); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                });
            }
            default -> throw new IllegalStateException("Unknown flow "+number);
        }
        // Every flow has a canonical screenshot, as well as any detailed scene captures.
        if(STEPS.stream().noneMatch(s->"main".equals(s.shot)))scene("main",()->{},()->{});
    }
    private static Object entry(String key) {
        String label=net.minecraft.network.chat.Component.translatable(key).getString();
        try {
            Object categories=mc.screen.getClass().getMethod("getCategorizedEntries").invoke(mc.screen);
            if(categories instanceof Map<?,?> map)for(Object value:map.values())if(value instanceof Iterable<?> rows)for(Object row:rows) {
                Object name=row.getClass().getMethod("getFieldName").invoke(row);
                if(name instanceof net.minecraft.network.chat.Component text && text.getString().equals(label))return row;
            }
        }catch(NoSuchMethodException nativeScreen){}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        return null;
    }
    private static void setEntry(Object entry,Object value) {
        try {
            for(var method:entry.getClass().getMethods())if(method.getName().equals("setValue") && method.getParameterCount()==1 && (method.getParameterTypes()[0].isInstance(value) || method.getParameterTypes()[0]==int.class && value instanceof Integer)) {
                method.invoke(entry,value);return;
            }
            throw new IllegalStateException("Entry has no compatible setValue: "+entry.getClass().getSimpleName());
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static boolean hasKey(AbstractWidget widget, String key) {
        return widget.getMessage().getContents() instanceof TranslatableContents label && key.equals(label.getKey());
    }
    private static void click(String key) {
        var button = widgets(mc.screen).stream().filter(w -> w instanceof Button && w.active && hasKey(w, key)).findFirst();
        require(button.isPresent(), "Missing active button: " + key);
        McBridge.press((Button) button.get());
    }
    private static void setToggle(String key,boolean value) {
        Object row=entry(key);
        if(row!=null) {
            try {
                if (!Boolean.valueOf(value).equals(row.getClass().getMethod("getValue").invoke(row))) {
                    var children=((ContainerEventHandler)row).children();
                    require(!children.isEmpty() && children.getFirst() instanceof Button,"Toggle has no button: "+key);
                    McBridge.press((Button)children.getFirst());
                }
            } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        } else click(key);
    }
    private static String diagnostics() {
        String note = mc.screen instanceof BrowseScreen screen ? screen.status() : asyncError;
        return "offline=" + ClientSettings.get().localOnly + ", screen=" + (mc.screen==null?"world":mc.screen.getClass().getSimpleName())
                + ", " + (note==null || note.isBlank()?SiteApi.lastError():note);
    }
    private static String status(String key){return net.minecraft.network.chat.Component.translatable("text.quarkos_capes.status."+key).getString();}
    private static List<AbstractWidget> widgets(Screen screen) {
        Set<GuiEventListener> seen=Collections.newSetFromMap(new IdentityHashMap<>());List<AbstractWidget> out=new ArrayList<>();
        collect(screen,seen,out);
        try {
            Object categories=screen.getClass().getMethod("getCategorizedEntries").invoke(screen);
            if(categories instanceof Map<?,?> map)for(Object entries:map.values())if(entries instanceof Iterable<?> rows)for(Object row:rows)
                if(row instanceof GuiEventListener listener)collect(listener,seen,out);
        }catch(NoSuchMethodException ordinary){}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        return out;
    }
    private static void collect(GuiEventListener item,Set<GuiEventListener> seen,List<AbstractWidget> out) {
        if(!seen.add(item))return;if(item instanceof AbstractWidget widget)out.add(widget);
        if(item instanceof ContainerEventHandler container)for(var child:container.children())collect(child,seen,out);
    }
    private static void layout(Screen screen) {
        HarnessLayout.check(screen);
    }
}
