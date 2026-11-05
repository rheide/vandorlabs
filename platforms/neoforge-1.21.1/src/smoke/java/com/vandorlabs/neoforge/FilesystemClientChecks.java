package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeCubeScreen;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.tiles.FilesystemTextureDiscovery;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Installed-file picker/reload matrix; all images/worlds are disposable and opt-in. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class FilesystemClientChecks {
    private static int ticks,stage,wait,transition;
    private static String screenshot;
    private static volatile boolean captured;
    private static NativeCubeScreen picker;
    private static byte[] original;
    private static BakedModel previousModel;
    private static BakedQuad previousQuad,previousItemQuad;
    private static int[] previousItemVertices;
    private static int[] previousVertices;
    private static float previousU0,previousU1,previousV0,previousV1;
    private static Resource oldResource;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static String peer(){return System.getProperty("vandorlabs.filesystemPeer","installed");}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native filesystem client: "+message);}
    private static Path file(){return FilesystemMaterials.catalog().file(FilesystemRuntimeChecks.SOURCE);}
    private static ResourceLocation location(){return ResourceLocation.parse("vandorlabs:textures/filesystem/file_"+Integer.toHexString(FilesystemRuntimeChecks.CHOICE)+".png");}
    private static int color(int x,int y,int phase){return 0xff000000|((x*7+phase*31)&255)<<16|((y*3+phase*13)&255)<<8|((x+y+phase*19)&255);}
    private static void write(Path file,int width,int height,int phase)throws Exception{
        Files.createDirectories(file.getParent());BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,color(x,y,phase));ImageIO.write(image,"PNG",file.toFile());
    }
    private static void capture(String name){screenshot=name;captured=false;}
    private static void query(NativeCubeScreen screen,String text){
        int x=screen.panel().listX+15,y=com.vandorlabs.ui.ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+8;
        screen.mouseClicked(x,y,0);screen.keyPressed(269,0,0);for(int i=0;i<128;i++)screen.keyPressed(259,0,0);for(char c:text.toCharArray())screen.charTyped(c,0);
    }
    private static void choose(NativeCubeScreen screen,int choice){int index=screen.visibleRows().indexOf(choice)-screen.scroll();require(index>=0&&index<com.vandorlabs.ui.ProgrammableBlockDialog.listRows(screen.panel(),screen.editingState())-1,"filesystem row not visible "+choice);
        screen.mouseClicked(screen.panel().listX+35,com.vandorlabs.ui.ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+22+index*22+8,0);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!System.getProperty("vandorlabs.filesystemPhase","").equals("client"))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
        if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        require(++ticks<9000,"timeout peer="+peer()+" stage="+stage+" transition="+transition+" held="+mc.player.getMainHandItem());
        if(stage==0){
            if(ticks<100||!(mc.level.getBlockEntity(FilesystemRuntimeChecks.POS) instanceof ProgrammableBlockEntity tile)||tile.settings().mainTexture!=FilesystemRuntimeChecks.CHOICE)return;
            require(mc.gameMode.getPlayerMode()==net.minecraft.world.level.GameType.CREATIVE&&mc.player.getInventory().selected==0&&mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()),"fixture mode/slot/tool not synchronized");
            require(FilesystemMaterials.catalog().entries.size()>256,"installed discovery capped at256");CubeReferenceChecks.run(mc);
            if(peer().equals("missing")){require(ModernFinishCatalog.renderChoice(FilesystemRuntimeChecks.CHOICE)==0,"startup missing peer did not use builtin fallback");}
            else{original=Files.readAllBytes(file());measure(mc,32,64,0);remember(mc);oldResource=mc.getResourceManager().getResource(location()).orElseThrow();}
            mc.getToasts().clear();mc.gui.getChat().clearMessages(true);capture("filesystem-"+peer()+"-world.png");stage=1;wait=ticks;
        }else if(stage==1&&captured&&ticks>wait+10){
            mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,80.5,0),Direction.NORTH,FilesystemRuntimeChecks.POS,false));stage=2;wait=ticks;
        }else if(stage==2){
            if(!(mc.screen instanceof NativeCubeScreen screen))return;picker=screen;
            if(peer().equals("missing")){require(!screen.visibleRows().contains(FilesystemRuntimeChecks.CHOICE),"startup-missing row appeared");write(file(),32,64,0);reload=mc.reloadResourcePacks();stage=20;wait=ticks;return;}
            if(peer().equals("restored")){query(screen,"Native portrait");require(screen.visibleRows().contains(FilesystemRuntimeChecks.CHOICE),"restored startup did not rediscover stable ID");choose(screen,FilesystemRuntimeChecks.CHOICE);capture("filesystem-restored-peer-picker.png");stage=30;wait=ticks;return;}
            query(screen,"Bulk probe");require(screen.visibleRows().stream().filter(id->id>=0).count()==257,"complete persistent PNG rows missing");int y=com.vandorlabs.ui.ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+22,height=(com.vandorlabs.ui.ProgrammableBlockDialog.listRows(screen.panel(),screen.editingState())-1)*22;
            int before=screen.scroll();screen.mouseScrolled(screen.panel().listX+20,y+30,0,-1);require(screen.scroll()>before,"filesystem wheel scrolling");double x=screen.panel().listX+screen.panel().listWidth+3;screen.mouseClicked(x,y+height-2,0);int bottom=screen.scroll();screen.mouseDragged(x,y+2,0,0,-height);screen.mouseReleased(x,y+2,0);require(screen.scroll()<bottom,"filesystem scrollbar dragging");
            query(screen,"peer");choose(screen,FilesystemTextureDiscovery.identifier("Native/peer.png"));stage=3;wait=ticks;
        }else if(stage==3&&ticks>wait+15){
            require(((ProgrammableBlockEntity)mc.level.getBlockEntity(FilesystemRuntimeChecks.POS)).settings().mainTexture==FilesystemTextureDiscovery.identifier("Native/peer.png"),"real filesystem picker peer click did not roundtrip");
            query(picker,"Native portrait");choose(picker,FilesystemRuntimeChecks.CHOICE);stage=4;wait=ticks;
        }else if(stage==4&&ticks>wait+15){
            require(((ProgrammableBlockEntity)mc.level.getBlockEntity(FilesystemRuntimeChecks.POS)).settings().mainTexture==FilesystemRuntimeChecks.CHOICE,"real portrait row click did not roundtrip");capture("filesystem-portrait-picker.png");stage=5;wait=ticks;
        }else if(stage==5&&captured&&ticks>wait+10){Files.delete(file());transition=0;reload=mc.reloadResourcePacks();stage=6;wait=ticks;
        }else if(stage==6&&reload.isDone()&&mc.getOverlay()==null&&ticks>wait+20){
            reload.join();require(mc.screen==picker&&picker.editingState().mainTexture==FilesystemRuntimeChecks.CHOICE&&picker.visibleRows().contains(FilesystemRuntimeChecks.CHOICE),"same open picker lost known selected ID during reload");unchanged();
            switch(transition){case 0->measure(mc,16,16,-1);case 1,3->measure(mc,32,64,0);case 2->{measure(mc,64,16,4);try(var stream=oldResource.open()){ImageIO.read(stream);}var current=FilesystemMaterials.snapshotForBake().get(FilesystemRuntimeChecks.CHOICE);require(current.u()==1&&current.v()==.25f,"late old pack replaced latest loading epoch");System.out.println("[vandorlabs][filesystem] loader epoch PASS late old supplier cannot overwrite landscape descriptor");}default->throw new AssertionError();}
            capture("filesystem-reload-"+transition+".png");stage=7;wait=ticks;
        }else if(stage==7&&captured&&ticks>wait+10){
            if(transition==3){
                var current=ModernFinishCatalog.option(FilesystemRuntimeChecks.CHOICE);require(current.u()==.5f&&current.v()==1,"restored portrait dimensions");
                // A late open from the ORIGINAL pack must not overwrite the latest loader epoch.
                try(var stream=oldResource.open()){var image=ImageIO.read(stream);require(image.getWidth()==64&&image.getHeight()==64,"old pack lost pinned first-open canvas");}
                write(file(),64,16,4);try(var stream=mc.getResourceManager().getResource(location()).orElseThrow().open()){var image=ImageIO.read(stream);require(image.getWidth()==64&&image.getHeight()==64,"same-epoch changed file broke pinned canvas");for(int y=0;y<64;y++)for(int x=0;x<64;x++)require(image.getRGB(x,y)==color(Math.min(x,31),Math.min(y,15),4),"same-epoch changed file broke pinned source shape");}
                var staged=FilesystemMaterials.snapshotForBake().get(FilesystemRuntimeChecks.CHOICE);require(staged.u()==.5f&&staged.v()==1,"same-epoch changed file leaked new dimensions");Files.write(file(),original);
                int added=FilesystemTextureDiscovery.identifier("New/after_startup.png");write(FilesystemMaterials.catalog().file("New/after_startup.png"),16,16,7);reload=mc.reloadResourcePacks();stage=8;wait=ticks;
            }else{
                remember(mc);transition++;if(transition==2)write(file(),64,16,4);else Files.write(file(),original);reload=mc.reloadResourcePacks();stage=6;wait=ticks;
            }
        }else if(stage==8&&reload.isDone()&&mc.getOverlay()==null&&ticks>wait+20){
            reload.join();int added=FilesystemTextureDiscovery.identifier("New/after_startup.png");require(ModernFinishCatalog.renderChoice(added)==0&&FilesystemMaterials.catalog().entries.stream().noneMatch(entry->entry.key==added),"reload silently rescanned new files");
            require(mc.screen==picker&&picker.editingState().mainTexture==FilesystemRuntimeChecks.CHOICE,"final open picker identity");measure(mc,32,64,0);CubeReferenceChecks.run(mc);mc.player.closeContainer();
            System.out.println("[vandorlabs][filesystem] client-"+peer()+" PASS >256startup rows, real category/search/row/wheel/drag, portrait32x64/delete fallback/restore/64x16/restore, same-open ID/dimension refresh, immutable old UVs, pinned loader epochs and no reload discovery");mc.stop();stage=99;
        }else if(stage==20&&reload.isDone()&&mc.getOverlay()==null&&ticks>wait+20){
            reload.join();require(Files.exists(file())&&ModernFinishCatalog.renderChoice(FilesystemRuntimeChecks.CHOICE)==0&&FilesystemMaterials.catalog().entries.stream().noneMatch(entry->entry.key==FilesystemRuntimeChecks.CHOICE),"restoring an unknown startup file was falsely discovered by reload");
            require(mc.screen==picker&&picker.editingState().mainTexture==FilesystemRuntimeChecks.CHOICE,"missing-peer open picker lost saved identity");query(picker,"Native portrait");require(!picker.visibleRows().contains(FilesystemRuntimeChecks.CHOICE),"missing peer row appeared before restart");
            capture("filesystem-missing-peer-picker.png");stage=21;wait=ticks;
        }else if(stage==30&&captured&&ticks>wait+15){mc.player.closeContainer();System.out.println("[vandorlabs][filesystem] client-restored PASS separate restart discovers restored PNG with original stable ID and native picker row");mc.stop();stage=99;
        }else if(stage==21&&captured&&ticks>wait+10){mc.player.closeContainer();System.out.println("[vandorlabs][filesystem] client-missing PASS separate startup missing-peer fallback, saved ID retention and no discovery until restart");mc.stop();stage=99;}
    }
    private static void remember(Minecraft mc){previousModel=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(FilesystemRuntimeChecks.POS);previousQuad=previousModel.getQuads(tile.getBlockState(),Direction.NORTH,net.minecraft.util.RandomSource.create(0),tile.getModelData(),null).getFirst();previousVertices=previousQuad.getVertices().clone();previousU0=previousQuad.getSprite().getU0();previousU1=previousQuad.getSprite().getU1();previousV0=previousQuad.getSprite().getV0();previousV1=previousQuad.getSprite().getV1();ItemStack stack=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());var tag=new net.minecraft.nbt.CompoundTag();tag.putInt(HousingSettings.MAIN,FilesystemRuntimeChecks.CHOICE);tag.putInt(HousingSettings.LAYOUT,0);stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tag));previousItemQuad=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0).getQuads(null,Direction.NORTH,net.minecraft.util.RandomSource.create(0)).getFirst();previousItemVertices=previousItemQuad.getVertices().clone();}
    private static void unchanged(){require(Arrays.equals(previousItemVertices,previousItemQuad.getVertices())&&Arrays.equals(previousVertices,previousQuad.getVertices())&&previousU0==previousQuad.getSprite().getU0()&&previousU1==previousQuad.getSprite().getU1()&&previousV0==previousQuad.getSprite().getV0()&&previousV1==previousQuad.getSprite().getV1(),"reload mutated original quad/sprite UV records");}
    private static void measure(Minecraft mc,int width,int height,int phase)throws Exception{
        int canvas=1;while(canvas<Math.max(width,height))canvas*=2;var entry=ModernFinishCatalog.option(FilesystemRuntimeChecks.CHOICE);require(entry.u()==(float)width/canvas&&entry.v()==(float)height/canvas,"published reload dimensions "+width+"x"+height+" entry="+entry);
        var sprite=mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(entry.texture()));require(sprite.contents().width()==canvas&&sprite.contents().height()==canvas,"actual atlas canvas");
        try(var stream=mc.getResourceManager().getResource(location()).orElseThrow().open()){var image=ImageIO.read(stream);require(image.getWidth()==canvas&&image.getHeight()==canvas,"served PNG canvas");for(int y=0;y<canvas;y++)for(int x=0;x<canvas;x++)require(image.getRGB(x,y)==(phase<0?0xff323944:color(Math.min(x,width-1),Math.min(y,height-1),phase)),"edge padding pixel "+x+","+y);}
        var model=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());if(previousModel!=null)require(model!=previousModel,"resource reload reused stale model cache");
        var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(FilesystemRuntimeChecks.POS);var world=model.getQuads(tile.getBlockState(),Direction.NORTH,net.minecraft.util.RandomSource.create(0),tile.getModelData(),null).getFirst();uv(world,entry);
        ItemStack stack=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());var tag=new net.minecraft.nbt.CompoundTag();tag.putInt(HousingSettings.MAIN,FilesystemRuntimeChecks.CHOICE);tag.putInt(HousingSettings.LAYOUT,0);stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tag));var item=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);uv(item.getQuads(null,Direction.NORTH,net.minecraft.util.RandomSource.create(0)).getFirst(),entry);
        System.out.println("[vandorlabs][filesystem] pixels/UV PASS "+width+"x"+height+" canvas="+canvas+" used="+entry.u()+","+entry.v());
    }
    private static void uv(BakedQuad quad,ModernFinishCatalog.Entry entry){require(quad.getSprite().contents().name().toString().equals(entry.texture()),"wrong filesystem model sprite");int stride=quad.getVertices().length/4;for(int axis=0;axis<2;axis++){double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,low=axis==0?quad.getSprite().getU0():quad.getSprite().getV0(),high=axis==0?quad.getSprite().getU1():quad.getSprite().getV1();for(int vertex=0;vertex<4;vertex++){double value=(Float.intBitsToFloat(quad.getVertices()[vertex*stride+4+axis])-low)/(high-low);min=Math.min(min,value);max=Math.max(max,value);}require(Math.abs(min)<2e-4&&Math.abs(max-(axis==0?entry.u():entry.v()))<2e-4,"world/item UV stale after dimension reload axis="+axis+" range="+min+","+max);}}
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(screenshot==null)return;var mc=Minecraft.getInstance();String name=screenshot;screenshot=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{captured=true;System.out.println("[vandorlabs][filesystem] screenshot "+name);});}
}
