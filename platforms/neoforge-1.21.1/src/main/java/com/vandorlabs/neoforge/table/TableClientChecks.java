package com.vandorlabs.neoforge.table;

import com.google.gson.GsonBuilder;
import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Real native baked geometry, loaded chunk-cache transitions and rendered world/item evidence. */
public final class TableClientChecks {
    private static int stage,index,started,lastMove;
    private static long beforeInvalidation;
    private static ClientboundLevelChunkWithLightPacket savedChunk;
    private static ChunkPos removed;
    private static VoxelShape connectedCollision;
    private static ModelData oldSnapshot;
    private static BlockState originalGalleryState;
    private static int routedIndex=-1;
    private static final ArrayDeque<Vec3> ROUTE=new ArrayDeque<>();
    private static final List<Object> states=new ArrayList<>(),boundaries=new ArrayList<>();
    private static final List<ItemStack> items=new ArrayList<>();
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern client: industrial table "+message);}
    private static String name(String suffix){return "modern-table-"+suffix+".png";}
    private static void capture(String suffix){ModernClientRuntimeChecks.capture(name(suffix));}
    private static boolean captured(String suffix){return ModernClientRuntimeChecks.captured(name(suffix));}
    private static boolean loaded(Minecraft mc,BlockPos pos){return mc.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);}
    private static List<BakedQuad> quads(Minecraft mc,BlockPos pos,BlockState state,ModelData data){var model=mc.getBlockRenderer().getBlockModel(state);List<BakedQuad> quads=new ArrayList<>();quads.addAll(model.getQuads(state,null,RandomSource.create(0),data,null));for(Direction face:Direction.values())quads.addAll(model.getQuads(state,face,RandomSource.create(0),data,null));return quads;}
    private static ModelData snapshot(Minecraft mc,BlockPos pos,BlockState state){var region=new RenderRegionCache().createRegion(mc.level,SectionPos.of(pos));require(region!=null,"empty native render region");return mc.getBlockRenderer().getBlockModel(state).getModelData(region,pos,state,ModelData.EMPTY);}
    private static List<Object> record(List<BakedQuad> quads){
        List<Object> result=new ArrayList<>();for(BakedQuad quad:quads){Map<String,Object> row=new LinkedHashMap<>();row.put("sprite",quad.getSprite().contents().name().toString());List<Object> vertices=new ArrayList<>();int[] data=quad.getVertices();int stride=data.length/4;
            for(int i=0;i<4;i++){int at=i*stride;vertices.add(new double[]{Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]),quad.getSprite().getUOffset(Float.intBitsToFloat(data[at+4])),quad.getSprite().getVOffset(Float.intBitsToFloat(data[at+5]))});}row.put("vertices",vertices);result.add(row);
        }return result;
    }
    private static boolean approach(Minecraft mc,BlockPos pos,Direction face,int tick){
        Direction right=face.getClockWise();double lateral=stage>=4?0:1.2;Vec3 target=new Vec3(pos.getX()+.5+face.getStepX()*3+right.getStepX()*lateral,79,pos.getZ()+.5+face.getStepZ()*3+right.getStepZ()*lateral),delta=target.subtract(mc.player.position());
        if(stage==1){
            double[] corridors={-4,9.5,15.5,15.5};
            if(routedIndex!=index){int previous=routedIndex;routedIndex=index;ROUTE.clear();double corridor=corridors[index/8];
                if(previous<0||previous/8!=index/8){double old=previous<0?corridor:corridors[previous/8];ROUTE.add(new Vec3(old,79,mc.player.getZ()));ROUTE.add(new Vec3(old,79,-5));ROUTE.add(new Vec3(corridor,79,-5));}
                else ROUTE.add(new Vec3(corridor,79,mc.player.getZ()));
                ROUTE.add(new Vec3(corridor,79,target.z));ROUTE.add(target);
            }
            if(!ROUTE.isEmpty()){if(walk(mc,ROUTE.peek(),tick))ROUTE.remove();return false;}
        }else if(!walk(mc,target,tick))return false;
        Vec3 direction=Vec3.atCenterOf(pos).subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(direction.z,direction.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));return true;
    }
    private static boolean walk(Minecraft mc,Vec3 target,int tick){Vec3 delta=target.subtract(mc.player.position());if(delta.length()<=.02)return true;if(tick-lastMove<5)return false;Vec3 next=delta.length()>4?mc.player.position().add(delta.normalize().scale(4)):target;mc.player.setPos(next);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(next.x,next.y,next.z,true));lastMove=tick;return false;}
    public static boolean tick(Minecraft mc,int tick){
        require(tick<12000,"timed out stage="+stage+" index="+index+" position="+mc.player.position());
        if(tick%200==0)System.out.println("[vandorlabs][modern] table-progress stage="+stage+" index="+index+" position="+mc.player.position());
        if(stage==0){mc.player.closeContainer();mc.player.setShiftKeyDown(false);mc.options.guiScale().set(2);mc.resizeDisplay();started=tick;stage=15;}
        if(stage==15){if(tick-started<60||!mc.levelRenderer.isSectionCompiled(TableRuntimeChecks.gallery(0))||!mc.levelRenderer.hasRenderedAllSections())return false;stage=1;}
        if(stage==1){BlockPos pos=TableRuntimeChecks.gallery(index);Direction face=TableRuntimeChecks.FACES[index/8];if(!approach(mc,pos,face,tick)||!mc.level.getBlockState(pos).is(TableRegistration.BLOCK.get()))return false;started=tick;stage=2;return false;}
        if(stage==2){if(tick-started<8||!mc.levelRenderer.isSectionCompiled(TableRuntimeChecks.gallery(index))||!mc.levelRenderer.hasRenderedAllSections())return false;BlockPos pos=TableRuntimeChecks.gallery(index);BlockState state=mc.level.getBlockState(pos);var emitted=quads(mc,pos,state,snapshot(mc,pos,state));require(emitted.size()==24-6*Integer.bitCount(index%4),"canonical strut count at "+index+": "+emitted.size());
            for(BakedQuad quad:emitted)require(quad.getSprite().contents().name().toString().equals("vandorlabs:block/industrial_table"),"wrong authored material");Map<String,Object> row=new LinkedHashMap<>();row.put("index",index);row.put("quads",record(emitted));List<double[]> collision=new ArrayList<>();for(var box:state.getCollisionShape(mc.level,pos).toAabbs())collision.add(new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ});row.put("collision",collision);states.add(row);
            ItemStack clone=state.getBlock().getCloneItemStack(state,new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false),mc.level,pos,mc.player);require(clone.is(TableRegistration.ITEM.get())&&!clone.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)&&!clone.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),"plain table item identity");require(clone.getHoverName().getString().equals("Industrial Table"),"canonical translated item name");items.add(clone);
            capture("state-"+index);stage=3;return false;
        }
        if(stage==3){if(!captured("state-"+index))return false;BlockPos pos=TableRuntimeChecks.gallery(index);originalGalleryState=mc.level.getBlockState(pos);mc.level.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);started=tick;stage=12;return false;}
        if(stage==12){if(tick-started<8||!mc.levelRenderer.hasRenderedAllSections())return false;capture("state-"+index+"-empty");stage=13;return false;}
        if(stage==13){if(!captured("state-"+index+"-empty"))return false;mc.level.setBlock(TableRuntimeChecks.gallery(index),originalGalleryState,3);started=tick;stage=14;return false;}
        if(stage==14){if(tick-started<4)return false;if(++index<32){stage=1;return false;}index=0;stage=4;}
        if(stage==4){BlockPos pos=TableRuntimeChecks.boundary(index);Direction face=TableRuntimeChecks.FACES[index/2];BlockPos neighbor=pos.relative(TableRuntimeChecks.outward(index));if(!approach(mc,pos,face,tick)||!loaded(mc,neighbor)||!mc.level.getBlockState(pos).is(TableRegistration.BLOCK.get()))return false;
            BlockState state=mc.level.getBlockState(pos);oldSnapshot=snapshot(mc,pos,state);require(quads(mc,pos,state,oldSnapshot).size()==18,"loaded boundary pair missing connection "+index);
            connectedCollision=state.getCollisionShape(mc.level,pos);removed=new ChunkPos(neighbor);var chunk=mc.level.getChunkSource().getChunk(removed.x,removed.z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);require(chunk!=null,"native neighbour chunk unavailable");savedChunk=new ClientboundLevelChunkWithLightPacket(chunk,mc.level.getLightEngine(),null,null);beforeInvalidation=TableChunkRefresh.invalidations();mc.getConnection().handleForgetLevelChunk(new ClientboundForgetLevelChunkPacket(removed));require(!loaded(mc,neighbor)&&loaded(mc,pos),"forget packet must remove neighbour and retain owner chunk "+index);started=tick;stage=5;return false;
        }
        if(stage==5){if(tick-started<4||TableChunkRefresh.invalidations()<=beforeInvalidation||!mc.levelRenderer.hasRenderedAllSections())return false;BlockPos pos=TableRuntimeChecks.boundary(index);BlockState state=mc.level.getBlockState(pos);var data=snapshot(mc,pos,state);var emitted=quads(mc,pos,state,data);
            require(emitted.size()==24,"unloaded neighbour must restore end strut");require(quads(mc,pos,state,ModelData.EMPTY).size()==18,"negative control must expose stale saved connection");require(quads(mc,pos,state,oldSnapshot).size()==18,"published old model data must remain immutable");
            require(Shapes.joinIsNotEmpty(connectedCollision,state.getCollisionShape(mc.level,pos),BooleanOp.NOT_SAME),"loaded-only collision must restore strut too");
            var region=new RenderRegionCache().createRegion(mc.level,SectionPos.of(pos));var model=mc.getBlockRenderer().getBlockModel(state);try{ModelData worker=java.util.concurrent.CompletableFuture.supplyAsync(()->model.getModelData(region,pos,state,ModelData.EMPTY)).get(5,java.util.concurrent.TimeUnit.SECONDS);require(quads(mc,pos,state,worker).size()==24,"immutable render-region worker result differs");}catch(Exception failure){throw new IllegalStateException("Table worker snapshot",failure);}
            capture("boundary-"+index+"-unloaded");stage=6;return false;
        }
        if(stage==6){if(!captured("boundary-"+index+"-unloaded"))return false;beforeInvalidation=TableChunkRefresh.invalidations();mc.getConnection().handleLevelChunkWithLight(savedChunk);started=tick;stage=7;return false;}
        if(stage==7){if(tick-started<4||TableChunkRefresh.invalidations()<=beforeInvalidation||!mc.levelRenderer.hasRenderedAllSections())return false;BlockPos pos=TableRuntimeChecks.boundary(index);BlockState state=mc.level.getBlockState(pos);require(quads(mc,pos,state,snapshot(mc,pos,state)).size()==18,"reloaded native chunk must hide joined strut");require(!Shapes.joinIsNotEmpty(connectedCollision,state.getCollisionShape(mc.level,pos),BooleanOp.NOT_SAME),"reloaded collision differs from original");boundaries.add(Map.of("index",index,"unloadedQuads",24,"storedNegativeQuads",18,"reloadedQuads",18));capture("boundary-"+index+"-reloaded");stage=8;return false;}
        if(stage==8){if(!captured("boundary-"+index+"-reloaded"))return false;if(++index<8){stage=4;return false;}mc.setScreen(new Icons());started=tick;stage=9;return false;}
        if(stage==9){if(tick-started<8)return false;capture("items");stage=10;return false;}
        if(stage==10){if(!captured("items"))return false;var itemModel=mc.getItemRenderer().getModel(items.get(0),mc.level,mc.player,0);List<BakedQuad> itemQuads=new ArrayList<>();itemQuads.addAll(itemModel.getQuads(null,null,RandomSource.create(0)));for(Direction side:Direction.values())itemQuads.addAll(itemModel.getQuads(null,side,RandomSource.create(0)));require(itemQuads.size()==24,"plain table item must use standalone authored model");
            Map<String,Object> result=new LinkedHashMap<>();result.put("states",states);result.put("item",record(itemQuads));result.put("boundaries",boundaries);try{java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("screenshots/industrial-table-native-models.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
            mc.setScreen(null);System.out.println("[vandorlabs][modern] table-client PASS 32 native world states/items, eight actual chunk-cache unload/reload pairs, stale-state negative control and immutable worker snapshots");stage=11;
        }
        return stage==11;
    }
    private static final class Icons extends Screen {
        Icons(){super(Component.literal("Industrial Table items"));}
        @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){graphics.fill(0,0,width,height,0xFF101012);int left=(width-176)/2,top=(height-88)/2;for(int i=0;i<items.size();i++){int xx=left+i%8*22,yy=top+i/8*22;graphics.fill(xx,yy,xx+20,yy+20,0xFF282C32);graphics.renderItem(items.get(i),xx+2,yy+2);}super.render(graphics,x,y,partial);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private TableClientChecks(){}
}
