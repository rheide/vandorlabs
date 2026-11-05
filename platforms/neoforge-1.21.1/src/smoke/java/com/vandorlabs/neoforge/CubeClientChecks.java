package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeCubeScreen;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CubeClientChecks {
    private static int ticks,stage,wait,mode,guiStage,stableTicks;
    private static String capture;
    private static volatile boolean captured;
    private static long idleScans,idlePublications;
    private static com.vandorlabs.neoforge.client.JoinedCubeSnapshots.Stats idleStats;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native cube client: "+message);}
    private static void open(Minecraft mc){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(.5,80.5,0),Direction.NORTH,CubeClientFixture.POS,false));wait=ticks;}
    private static void click(NativeCubeScreen screen,int operation,int button){for(var control:ProgrammableBlockDialog.controls(screen.panel(),screen.editingState(),"Done"))if(control.id==operation){screen.mouseClicked(control.x+control.width/2.,control.y+control.height/2.,button);return;}throw new IllegalStateException("Missing control "+operation);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!System.getProperty("vandorlabs.cubePhase","").equals("client"))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
        if(stage==15&&mc.level==null){com.vandorlabs.neoforge.client.JoinedCubeSnapshots.request(net.minecraft.core.BlockPos.ZERO);require(com.vandorlabs.neoforge.client.JoinedCubeSnapshots.queued()==0,"late worker request retained after world unload");System.out.println("[vandorlabs][cube] client PASS actual GUI search/face/left-right layout input, creative/survival Configurizer, adventure allow/deny, spectator refusal, empty-hand creative shift-click, joined edits/mining/holes/Fit/Tile, idle queue drain and resource/world unload");mc.stop();stage=99;return;}
        if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        require(++ticks<4800,"timeout stage="+stage+" mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem());
        if(stage==0){if(ticks<100||!(mc.level.getBlockEntity(CubeClientFixture.POS) instanceof ProgrammableBlockEntity))return;
            for(int y=0;y<3;y++)for(int x=0;x<2;x++){var raw=mc.level.getBlockEntity(CubeClientFixture.JOINED.offset(x,y,0));if(!(raw instanceof ProgrammableBlockEntity tile)||tile.settings().mainTexture!=8||tile.settings().layout!=0)return;}
            require(mc.gameMode.getPlayerMode()==GameType.CREATIVE&&mc.player.getInventory().selected==0&&mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()),"initial synchronized game mode/slot/tool");
            CubeReferenceChecks.run(mc);geometry(mc);cacheChecks(mc);mc.getToasts().clear();mc.gui.getChat().clearMessages(true);capture="cube-native.png";captured=false;stage=1;wait=ticks;
        }else if(stage==1&&captured&&ticks>wait+10){open(mc);stage=2;
        }else if(stage==2){if(!(mc.screen instanceof NativeCubeScreen screen))return;
            click(screen,ProgrammableBlockDialog.FACE_OVERRIDES,0);require(screen.editingState().facesEnabled&&screen.editingState().target==-1,"toggle main target");
            click(screen,ProgrammableBlockDialog.FIRST_FACE+2,0);require(screen.editingState().target==2,"front tab click");
            // Search uses ordinary EditBox input; material selection uses its real rendered row.
            screen.mouseClicked(screen.panel().listX+20,ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+8,0);
            for(char c:"Industrial Block".toCharArray())screen.charTyped(c,0);
            require(screen.visibleRows().contains(9),"current builtin search result");int row=screen.visibleRows().indexOf(9)-screen.scroll();require(row>=0,"search row visible");
            screen.mouseClicked(screen.panel().listX+40,ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+22+row*22+8,0);require(screen.editingState().face(2)==9,"face material click");
            click(screen,ProgrammableBlockDialog.INHERIT,1);require(screen.editingState().face(2)==9,"right-click inherit changed state");
            click(screen,ProgrammableBlockDialog.INHERIT,0);require(screen.editingState().face(2)==-1,"newly selected override did not enable inherit");
            row=screen.visibleRows().indexOf(9)-screen.scroll();screen.mouseClicked(screen.panel().listX+40,ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+22+row*22+8,0);
            screen.mouseClicked(screen.panel().listX+20,ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+8,0);screen.keyPressed(269,0,0);for(int erase=0;erase<32;erase++)screen.keyPressed(259,0,0);
            require(screen.scroll()==0,"search scroll reset");int before=screen.scroll();screen.mouseScrolled(screen.panel().listX+20,ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+50,0,-1);require(screen.scroll()>before,"native picker wheel rows="+screen.visibleRows().size()+" scroll="+screen.scroll()+" before="+before);
            int trackTop=ProgrammableBlockDialog.listY(screen.panel(),screen.editingState())+22,trackHeight=(ProgrammableBlockDialog.listRows(screen.panel(),screen.editingState())-1)*22;double trackX=screen.panel().listX+screen.panel().listWidth+3;
            screen.mouseClicked(trackX,trackTop+trackHeight-2,0);int bottom=screen.scroll();screen.mouseDragged(trackX,trackTop+2,0,0,-trackHeight);screen.mouseReleased(trackX,trackTop+2,0);require(screen.scroll()<bottom,"native picker scrollbar dragging");
            click(screen,ProgrammableBlockDialog.TEXTURE_LAYOUT,1);require(screen.editingState().layout==0,"right-click previous layout");click(screen,ProgrammableBlockDialog.TEXTURE_LAYOUT,0);require(screen.editingState().layout==1,"left-click next layout");
            capture="cube-native-dialog.png";captured=false;stage=3;wait=ticks;
        }else if(stage==3&&captured&&ticks>wait+20){
            require(mc.level.getBlockEntity(CubeClientFixture.POS) instanceof ProgrammableBlockEntity tile&&tile.settings().facesEnabled&&tile.settings().face(2)==9&&tile.settings().layout==1,"server/client GUI roundtrip");
            if(guiStage<2){mc.options.guiScale().set(++guiStage+1);mc.resizeDisplay();capture="cube-native-dialog-gui"+(guiStage+1)+".png";captured=false;wait=ticks;return;}
            if(guiStage==2){mc.getWindow().setWindowed(640,480);mc.options.guiScale().set(2);mc.resizeDisplay();guiStage=3;wait=ticks;return;}
            if(guiStage==3){if(mc.getWindow().getGuiScaledWidth()!=320||mc.getWindow().getGuiScaledHeight()!=240)return;capture="cube-native-dialog-compact.png";captured=false;guiStage=4;wait=ticks;return;}
            mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(1);mc.resizeDisplay();mc.player.closeContainer();PacketDistributor.sendToServer(new CubeClientFixture.Mode(1));mode=1;wait=ticks;stage=4;
        }else if(stage==4){
            GameType expected=mode==1||mode==5?GameType.SURVIVAL:mode==2||mode==3?GameType.ADVENTURE:mode==4?GameType.SPECTATOR:GameType.CREATIVE;
            if(ticks<wait+12||mc.gameMode.getPlayerMode()!=expected)return;
            boolean expectsTool=mode!=5&&mode!=6;if(mc.player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())!=expectsTool)return;
            if(mode==6){mc.player.setShiftKeyDown(true);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));}
            open(mc);stage=5;
        }else if(stage==5&&ticks>wait+15){
            boolean allowed=mode==1||mode==3||mode==6;require((mc.screen instanceof NativeCubeScreen)==allowed,"mode authorization "+mode+" screen="+mc.screen);
            if(allowed)mc.player.closeContainer();
            if(mode==6){mc.player.setShiftKeyDown(false);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));PacketDistributor.sendToServer(new CubeClientFixture.Mode(7));stage=6;wait=ticks;}
            else{PacketDistributor.sendToServer(new CubeClientFixture.Mode(++mode));stage=4;wait=ticks;}
        }else if(stage==6&&ticks>wait+20){PacketDistributor.sendToServer(new CubeClientFixture.Mode(14));stage=16;wait=ticks;
        }else if(stage==16&&ticks>wait+15){
            if(mc.gameMode.getPlayerMode()!=GameType.CREATIVE||!mc.player.getMainHandItem().is(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get()))return;
            var placed=new net.minecraft.core.BlockPos(2,80,-1);var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(2.5,80,-.5),Direction.UP,placed.below(),false));require(result.consumesAction(),"configured real placement packet refused");stage=17;wait=ticks;
        }else if(stage==17&&ticks>wait+20){
            require(mc.level.getBlockEntity(new net.minecraft.core.BlockPos(2,80,-1)) instanceof ProgrammableBlockEntity tile&&tile.settings().mainTexture==8&&tile.settings().layout==1&&tile.settings().facesEnabled&&tile.settings().face(2)==9,"native configured placement lost item components");
            PacketDistributor.sendToServer(new CubeClientFixture.Mode(15));stage=18;wait=ticks;
        }else if(stage==18&&ticks>wait+15){
            if(mc.gameMode.getPlayerMode()!=GameType.SURVIVAL||!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_PICKAXE))return;
            mc.player.setYRot((float)-Math.toDegrees(Math.atan2(2,3)));mc.player.setXRot((float)Math.toDegrees(Math.atan2(mc.player.getEyeY()-80.5,Math.sqrt(13))));mc.options.keyAttack.setDown(true);stage=19;wait=ticks;
        }else if(stage==19){
            if(!mc.level.getBlockState(new net.minecraft.core.BlockPos(2,80,-1)).isAir()){require(ticks<wait+150,"native survival mining did not progress");return;}
            mc.options.keyAttack.setDown(false);stage=20;wait=ticks;
        }else if(stage==20&&ticks>wait+20){
            var drops=mc.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(1,79,-2,4,83,1));
            require(drops.stream().anyMatch(entity->{var data=entity.getItem().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);return entity.getItem().is(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get())&&data!=null&&data.copyTag().getIntArray(com.vandorlabs.persistence.HousingSettings.FACES)[2]==9;}),"native mined drop lost configured face components");
            System.out.println("[vandorlabs][cube] placement-mining PASS real configured BlockItem placement, synchronized survival iron-pickaxe mining and native dropped components");
            PacketDistributor.sendToServer(new CubeClientFixture.Mode(8));stage=7;wait=ticks;
        }else if(stage==7&&ticks>wait+60){
            var root=(ProgrammableBlockEntity)mc.level.getBlockEntity(CubeClientFixture.JOINED);var plane=root.joinedFaces().face(Direction.NORTH);
            require(plane==null||plane.size(0)*plane.size(1)*plane.size(2)<6,"material edit did not invalidate neighboring joined image");
            PacketDistributor.sendToServer(new CubeClientFixture.Mode(9));stage=8;wait=ticks;
        }else if(stage==8&&ticks>wait+60){require(joinedArea(mc)==6,"restored material did not rejoin");PacketDistributor.sendToServer(new CubeClientFixture.Mode(10));stage=9;wait=ticks;
        }else if(stage==9&&ticks>wait+60){require(mc.level.getBlockState(CubeClientFixture.JOINED.offset(1,2,0)).isAir()&&joinedArea(mc)<6,"mining retained stale six-cell plane");PacketDistributor.sendToServer(new CubeClientFixture.Mode(11));stage=10;wait=ticks;
        }else if(stage==10&&ticks>wait+60){require(joinedArea(mc)==6,"rebuilding through temporary hole did not rejoin");PacketDistributor.sendToServer(new CubeClientFixture.Mode(12));stage=11;wait=ticks;
        }else if(stage==11&&ticks>wait+60){require(((ProgrammableBlockEntity)mc.level.getBlockEntity(CubeClientFixture.JOINED)).settings().layout==2&&joinedArea(mc)==6,"Fit topology invalidation");PacketDistributor.sendToServer(new CubeClientFixture.Mode(13));stage=12;wait=ticks;
        }else if(stage==12&&ticks>wait+60){require(((ProgrammableBlockEntity)mc.level.getBlockEntity(CubeClientFixture.JOINED)).settings().layout==1&&joinedArea(mc)==1,"Tile kept joined image");if(com.vandorlabs.neoforge.client.JoinedCubeSnapshots.queued()!=0||!mc.levelRenderer.getSectionRenderDispatcher().isQueueEmpty()){stableTicks=0;return;}if(++stableTicks<20)return;
            idleScans=com.vandorlabs.neoforge.client.JoinedCubeSnapshots.scans();idlePublications=com.vandorlabs.neoforge.client.JoinedCubeSnapshots.publications();idleStats=com.vandorlabs.neoforge.client.JoinedCubeSnapshots.stats();System.out.println("[vandorlabs][cube] idle start tick="+ticks+" "+idleStats+" renderer="+mc.levelRenderer.getSectionStatistics());stage=13;wait=ticks;
        }else if(stage==13&&ticks>wait+100){System.out.println("[vandorlabs][cube] idle end tick="+ticks+" "+com.vandorlabs.neoforge.client.JoinedCubeSnapshots.stats());require(idleStats.requests()==com.vandorlabs.neoforge.client.JoinedCubeSnapshots.stats().requests()&&idleStats.samples()==com.vandorlabs.neoforge.client.JoinedCubeSnapshots.stats().samples()&&idleStats.invalidations()==com.vandorlabs.neoforge.client.JoinedCubeSnapshots.stats().invalidations()&&com.vandorlabs.neoforge.client.JoinedCubeSnapshots.queued()==0&&idleScans==com.vandorlabs.neoforge.client.JoinedCubeSnapshots.scans()&&idlePublications==com.vandorlabs.neoforge.client.JoinedCubeSnapshots.publications(),"joined snapshots continuously rescan while idle queued="+com.vandorlabs.neoforge.client.JoinedCubeSnapshots.queued()+" scans="+idleScans+"->"+com.vandorlabs.neoforge.client.JoinedCubeSnapshots.scans()+" publications="+idlePublications+"->"+com.vandorlabs.neoforge.client.JoinedCubeSnapshots.publications());reload=mc.reloadResourcePacks();stage=14;wait=ticks;
        }else if(stage==14&&reload.isDone()&&ticks>wait+40){reload.join();CubeReferenceChecks.run(mc);mc.disconnect();stage=15;}


    }
    private static int joinedArea(Minecraft mc){var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(CubeClientFixture.JOINED);var plane=tile.joinedFaces().face(Direction.NORTH);return plane==null?1:plane.size(0)*plane.size(1)*plane.size(2);}
    private static void geometry(Minecraft mc){
        for(var entry:ModernFinishCatalog.ENTRIES){var sprite=mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(net.minecraft.resources.ResourceLocation.parse(entry.texture()));require(!sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),"missing builtin atlas material "+entry.id());}
        var model=mc.getBlockRenderer().getBlockModel(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());require(model!=mc.getModelManager().getMissingModel(),"missing cube model");int cases=0;
        for(Direction facing:Direction.values())for(int layout=0;layout<3;layout++)for(boolean enabled:new boolean[]{false,true}){
            var state=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState().setValue(ProgrammableBlock.FACING,facing);var settings=CubeRuntimeChecks.state(8,layout,enabled);var data=net.neoforged.neoforge.client.model.data.ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,settings).build();
            for(Direction face:Direction.values()){var quads=model.getQuads(state,face,net.minecraft.util.RandomSource.create(0),data,net.minecraft.client.renderer.RenderType.solid());require(quads.size()==1,"cube face count");var expected=ModernFinishCatalog.entry(enabled?settings.face(ProgrammableModel.localFace(face,facing)):8);require(quads.getFirst().getSprite().contents().name().toString().equals(expected.texture()),"six-face builtin material");cases++;}
        }
        for(int y=0;y<3;y++)for(int x=0;x<2;x++){var pos=CubeClientFixture.JOINED.offset(x,y,0);require(mc.level.getBlockEntity(pos) instanceof ProgrammableBlockEntity,"joined fixture chunk not synchronized");com.vandorlabs.neoforge.client.JoinedCubeSnapshots.sample(mc.level,pos);}
        for(int y=0;y<3;y++)for(int x=0;x<2;x++){var tile=(ProgrammableBlockEntity)mc.level.getBlockEntity(CubeClientFixture.JOINED.offset(x,y,0));var plane=tile.joinedFaces().face(Direction.NORTH);require(plane!=null&&plane.size(0)==2&&plane.size(1)==3,"native two-by-three plane across chunk boundary");}
        System.out.println("[vandorlabs][cube] native geometry PASS faces="+cases+" six facings, layouts, enabled and dormant overrides, actual cross-chunk joined discovery");
    }
    private static void cacheChecks(Minecraft mc){
        var state=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState();var model=mc.getBlockRenderer().getBlockModel(state);
        for(int index=0;index<4096;index++){
            int width=1+index%16,height=1+(index/16)%16;
            var planes=new com.vandorlabs.render.HousingFacePlane[6];planes[2]=new com.vandorlabs.render.HousingFacePlane(new int[]{-(index/256)%width,-(index/512)%height,0},new int[]{width,height,1});
            var data=net.neoforged.neoforge.client.model.data.ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,CubeRuntimeChecks.state(index%ModernFinishCatalog.ENTRIES.size(),0,false)).with(ProgrammableBlockEntity.JOINED,new JoinedCubeFaces(planes)).build();
            model.getQuads(state,Direction.NORTH,net.minecraft.util.RandomSource.create(0),data,null);
        }
        var usage=ProgrammableModel.cacheUsage(model);require(usage.worldBytes()<=1024*1024,"world quad retained-cost limit");
        var stack=new net.minecraft.world.item.ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());var base=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);
        for(int index=0;index<600;index++){var tag=new net.minecraft.nbt.CompoundTag();tag.putInt(com.vandorlabs.persistence.HousingSettings.MAIN,index%ModernFinishCatalog.ENTRIES.size());tag.putInt(com.vandorlabs.persistence.HousingSettings.LAYOUT,index%3);stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(tag));base.getOverrides().resolve(base,stack,mc.level,mc.player,0);}
        var itemUsage=ProgrammableModel.cacheUsage(base);require(itemUsage.itemModels()<=256&&itemUsage.visualKeys()<=256,"item model/projection limits");
        var numeric=new net.minecraft.nbt.CompoundTag();numeric.putByte(com.vandorlabs.persistence.HousingSettings.MAIN,(byte)8);numeric.putInt(com.vandorlabs.persistence.HousingSettings.LAYOUT,2);
        stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(numeric));
        var numericModel=base.getOverrides().resolve(base,stack,mc.level,mc.player,0);require(numericModel.getQuads(null,Direction.NORTH,net.minecraft.util.RandomSource.create(0)).getFirst().getSprite().contents().name().toString().equals(ModernFinishCatalog.entry(8).texture()),"item numeric main fallback changed");
        var opaque=new net.minecraft.nbt.CompoundTag();opaque.putInt(com.vandorlabs.persistence.HousingSettings.MAIN,8);opaque.putInt(com.vandorlabs.persistence.HousingSettings.LAYOUT,2);opaque.putIntArray("FutureOpaqueData",new int[65536]);
        var custom=net.minecraft.world.item.component.CustomData.of(opaque);stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,custom);var resolved=base.getOverrides().resolve(base,stack,mc.level,mc.player,0);
        for(int index=0;index<1000;index++)base.getOverrides().resolve(base,stack,mc.level,mc.player,0);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);int checksum=0;
        for(int index=0;index<200;index++)checksum+=custom.copyTag().getIntArray("FutureOpaqueData").length;
        long copied=bean.getThreadAllocatedBytes(thread)-before;before=bean.getThreadAllocatedBytes(thread);
        for(int index=0;index<200;index++)require(base.getOverrides().resolve(base,stack,mc.level,mc.player,0)==resolved,"cached item identity");
        long cached=bean.getThreadAllocatedBytes(thread)-before;require(checksum>0&&cached<copied/100,"opaque NBT copied on cached item draw");
        System.out.println("[vandorlabs][cube] cache PASS worldBytes="+usage.worldBytes()+"/1048576 worldEntries="+usage.worldEntries()+" itemModels="+itemUsage.itemModels()+"/256 weakVisualKeys="+itemUsage.visualKeys()+"/256 itemResolve200 allocatedBytes="+copied+" -> "+cached+" (opaque NBT copy control vs cached projection)");
    }
    @SubscribeEvent public static void icons(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event){
        if(!System.getProperty("vandorlabs.cubePhase","").equals("client"))return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var graphics=event.getGuiGraphics();
        java.util.List<net.minecraft.world.item.ItemStack> items=java.util.List.of(new net.minecraft.world.item.ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get()),mc.player.getInventory().getItem(2),new net.minecraft.world.item.ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        for(int index=0;index<items.size();index++){int x=20+24*index;graphics.fill(x,20,x+18,38,0xFF21384B);}graphics.flush();
        for(int index=0;index<items.size();index++)graphics.renderItem(items.get(index),21+24*index,21);graphics.flush();
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{captured=true;System.out.println("[vandorlabs][cube] screenshot "+name);});}
}
