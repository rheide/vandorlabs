package com.vandorlabs.neoforge.shape;

import com.google.gson.Gson;
import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Real native chunk packets, worker snapshots, collision/fluid complement and asserted world captures. */
public final class ShapeChunkClientChecks {
    private static int stage,index,started,lastMove;
    private static long frames,beforeInvalidations,beforeRevision;
    private static BlockState stored;
    private static ModelData oldData;
    private static com.vandorlabs.fluids.FluidVolume oldVolume;
    private static String loadedGeometry;
    private static Vec3 originalPosition;
    private static int[] ownerBounds,neighborBounds,controlBounds;
    private static long oldVolumeHash;
    private static net.minecraft.nbt.CompoundTag wetSlot;
    private static ClientboundLevelChunkWithLightPacket saved;
    private static final ArrayDeque<Vec3> route=new ArrayDeque<>();
    private static final List<Object> results=new ArrayList<>();
    private ShapeChunkClientChecks(){}
    private static void need(boolean b,String message){if(!b)throw new IllegalStateException("Shape chunk client: "+message+" index="+index+" stage="+stage);}
    private static String name(String phase){return "modern-shape-chunk-"+index+"-"+phase+".png";}
    private static boolean loaded(Minecraft mc,BlockPos pos){return mc.level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);}
    private static ProgrammableShapeEntity tile(Minecraft mc,BlockPos pos){need(mc.level.getBlockEntity(pos) instanceof ProgrammableShapeEntity,"installed owner identity");return (ProgrammableShapeEntity)mc.level.getBlockEntity(pos);}
    private static List<BakedQuad> quads(Minecraft mc,BlockState state,ModelData data){var model=mc.getBlockRenderer().getBlockModel(state);List<BakedQuad> result=new ArrayList<>();for(Direction side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})result.addAll(model.getQuads(state,side,RandomSource.create(0),data,net.minecraft.client.renderer.RenderType.solid()));return result;}
    private static String signature(List<BakedQuad> quads){List<String> rows=new ArrayList<>();for(var q:quads){StringBuilder row=new StringBuilder(q.getDirection()+"/"+q.isShade()+"/"+q.hasAmbientOcclusion()+"/"+q.getSprite().contents().name());int[] data=q.getVertices();int stride=data.length/4;for(int i=0;i<4;i++)for(int at:new int[]{0,1,2,4,5})row.append('/').append(data[i*stride+at]);rows.add(row.toString());}Collections.sort(rows);return rows.toString();}
    private static double volume(List<BakedQuad> quads){double result=0;for(var q:quads){int[] data=q.getVertices();int stride=data.length/4;Vec3[] p=new Vec3[4];for(int i=0;i<4;i++)p[i]=new Vec3(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2]));result+=p[0].dot(p[1].cross(p[2]))/6+p[0].dot(p[2].cross(p[3]))/6;}return result;}
    private static long volumeHash(com.vandorlabs.fluids.FluidVolume volume){long hash=1;for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++)hash=hash*31+volume.sideAt((x+.5)/16,(y+.5)/16,(z+.5)/16);return hash;}
    private static ModelData snapshot(Minecraft mc,BlockPos pos,BlockState state,ModelData data){var region=new RenderRegionCache().createRegion(mc.level,SectionPos.of(pos));need(region!=null,"nonempty real render region");return mc.getBlockRenderer().getBlockModel(state).getModelData(region,pos,state,data);}
    private static void consistent(Minecraft mc,BlockPos pos,boolean joined){
        var owner=tile(mc,pos);BlockState state=mc.level.getBlockState(pos),actual=ShapeRegistration.STAIRS.get().connected(state,mc.level,pos);need(actual.getValue(StairBlock.SHAPE)==(joined?StairsShape.OUTER_LEFT:StairsShape.STRAIGHT),"actual loaded join");ModelData input=owner.getModelData(),data=snapshot(mc,pos,state,input);need(data.get(ShapeModel.ACTUAL_POSE)==actual,"immutable derived model pose");
        var emitted=quads(mc,state,data);need(signature(emitted).equals(signature(quads(mc,actual,input))),"real model snapshot differs from canonical actual pose");VoxelShape collision=state.getCollisionShape(mc.level,pos);double solid=0;for(var box:collision.toAabbs())solid+=(box.maxX-box.minX)*(box.maxY-box.minY)*(box.maxZ-box.minZ);need(Math.abs(volume(emitted)-solid)<1e-6,"actual emitted mesh/collision volume");ShapeChunkRuntimeChecks.complement(owner,collision);
        if(index==0)need(Objects.equals(wetSlot,owner.containedFluids().raw(0)),"complete wet slot survives chunk cache changes");
    }
    private static Vec3 camera(BlockPos pos){Direction face=ShapeChunkRuntimeChecks.FACINGS[index%4],right=face.getClockWise();return new Vec3(pos.getX()+.5-face.getStepX()*3+right.getStepX()*2,80,pos.getZ()+.5-face.getStepZ()*3+right.getStepZ()*2);}
    private static boolean walk(Minecraft mc,Vec3 target,int tick){Vec3 delta=target.subtract(mc.player.position());if(delta.length()<.02)return true;if(tick-lastMove<5)return false;Vec3 next=delta.length()>4?mc.player.position().add(delta.normalize().scale(4)):target;mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setPos(next);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(next.x,next.y,next.z,true));lastMove=tick;return false;}
    private static void aim(Minecraft mc,BlockPos pos){Vec3 delta=Vec3.atCenterOf(pos).subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,true));}
    private static int[] projectBox(Minecraft mc,BlockPos pos){
        var camera=mc.gameRenderer.getMainCamera();double yaw=Math.toRadians(camera.getYRot()),pitch=Math.toRadians(camera.getXRot());
        Vec3 forward=new Vec3(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        Vec3 right=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw)),up=right.cross(forward);double tangent=Math.tan(Math.toRadians(mc.options.fov().get())*.5);
        int width=mc.getWindow().getWidth(),height=mc.getWindow().getHeight();double aspect=width/(double)height,x0=width,y0=height,x1=0,y1=0;
        for(int x=0;x<=1;x++)for(int y=0;y<=1;y++)for(int z=0;z<=1;z++){Vec3 relative=new Vec3(pos.getX()+x,pos.getY()+y,pos.getZ()+z).subtract(camera.getPosition());double depth=relative.dot(forward);need(depth>0,"fixture box behind camera");double px=(1+relative.dot(right)/(depth*tangent*aspect))*width*.5,py=(1-relative.dot(up)/(depth*tangent))*height*.5;x0=Math.min(x0,px);x1=Math.max(x1,px);y0=Math.min(y0,py);y1=Math.max(y1,py);}
        return new int[]{Math.max(0,(int)Math.floor(x0)-3),Math.max(0,(int)Math.floor(y0)-3),Math.min(width,(int)Math.ceil(x1)+3),Math.min(height,(int)Math.ceil(y1)+3)};
    }
    private static void start(int tick){started=tick;frames=ModernClientRuntimeChecks.renderedFrames();}
    private static boolean settled(Minecraft mc,BlockPos pos,int tick){return tick-started>=12&&ModernClientRuntimeChecks.renderedFrames()-frames>=4&&mc.levelRenderer.isSectionCompiled(pos)&&mc.levelRenderer.hasRenderedAllSections();}
    public static boolean tick(Minecraft mc,int tick){
        need(tick<16000,"bounded chunk suite timeout");BlockPos pos=ShapeChunkRuntimeChecks.owner(index),neighbor=ShapeChunkRuntimeChecks.neighbor(index);
        if(tick%200==0)System.out.println("[vandorlabs][modern] shape-chunk-progress stage="+stage+" index="+index+" position="+mc.player.position());
        if(stage==0){if(index==0)originalPosition=mc.player.position();mc.player.closeContainer();mc.player.setShiftKeyDown(false);mc.options.guiScale().set(2);mc.resizeDisplay();route.clear();Vec3 target=camera(pos);route.add(new Vec3(mc.player.getX(),80,-5));route.add(new Vec3(target.x,80,-5));route.add(target);stage=1;}
        if(stage==1){if(!route.isEmpty()){if(walk(mc,route.peek(),tick))route.remove();return false;}if(!loaded(mc,pos)||!loaded(mc,neighbor)||!(mc.level.getBlockEntity(pos) instanceof ProgrammableShapeEntity))return false;aim(mc,pos);start(tick);stage=2;return false;}
        if(stage==2){if(!settled(mc,pos,tick))return false;var owner=tile(mc,pos);wetSlot=index==0?owner.containedFluids().raw(0):null;need(mc.level.getBlockState(ShapeChunkRuntimeChecks.vanillaControl(index)).is(net.minecraft.world.level.block.Blocks.LIME_CONCRETE),"loaded independent vanilla control");ownerBounds=projectBox(mc,pos);neighborBounds=projectBox(mc,neighbor);controlBounds=projectBox(mc,ShapeChunkRuntimeChecks.vanillaControl(index));consistent(mc,pos,true);stored=mc.level.getBlockState(pos);oldData=snapshot(mc,pos,stored,owner.getModelData());oldVolume=owner.fluidVolumeSnapshot();oldVolumeHash=volumeHash(oldVolume);loadedGeometry=signature(quads(mc,stored,oldData));ModernClientRuntimeChecks.capture(name("loaded"));stage=3;return false;}
        if(stage==3){if(!ModernClientRuntimeChecks.captured(name("loaded")))return false;var chunk=mc.level.getChunkSource().getChunk(neighbor.getX()>>4,neighbor.getZ()>>4,ChunkStatus.FULL,false);need(chunk!=null,"loaded neighbor native chunk");saved=new ClientboundLevelChunkWithLightPacket(chunk,mc.level.getLightEngine(),null,null);beforeInvalidations=NativeFluidWorld.chunkInvalidations(mc.level);beforeRevision=tile(mc,pos).fluidGeometryRevision();mc.getConnection().handleForgetLevelChunk(new ClientboundForgetLevelChunkPacket(new ChunkPos(neighbor)));need(!loaded(mc,neighbor)&&loaded(mc,pos),"real forget packet keeps owner and removes neighbor");need(mc.level.getBlockState(ShapeChunkRuntimeChecks.vanillaControl(index)).isAir(),"actual vanilla control is absent from client chunk storage");start(tick);stage=4;return false;}
        if(stage==4){if(tick-started>400)need(false,"unload did not converge to refreshed volume/mesh");if(!settled(mc,pos,tick)||NativeFluidWorld.chunkInvalidations(mc.level)<=beforeInvalidations||tile(mc,pos).fluidGeometryRevision()<=beforeRevision)return false;consistent(mc,pos,false);var owner=tile(mc,pos);ModelData now=snapshot(mc,pos,stored,owner.getModelData());String unloaded=signature(quads(mc,stored,now));need(!unloaded.equals(loadedGeometry),"missing neighbor changes actual geometry");need(signature(quads(mc,stored,owner.getModelData())).equals(loadedGeometry),"stored-state negative control exposes old join");need(signature(quads(mc,stored,oldData)).equals(loadedGeometry)&&volumeHash(oldVolume)==oldVolumeHash,"published old worker snapshots remain immutable");
            var region=new RenderRegionCache().createRegion(mc.level,SectionPos.of(pos));var model=mc.getBlockRenderer().getBlockModel(stored);ModelData immutable=owner.getModelData();try{var worker=CompletableFuture.supplyAsync(()->model.getModelData(region,pos,stored,immutable)).get(5,TimeUnit.SECONDS);need(signature(quads(mc,stored,worker)).equals(unloaded),"immutable worker region snapshot mismatch");}catch(Exception failure){throw new IllegalStateException("Shape worker snapshot",failure);}ModernClientRuntimeChecks.capture(name("unloaded"));stage=5;return false;}
        if(stage==5){if(!ModernClientRuntimeChecks.captured(name("unloaded")))return false;
            // Independent renderer control AFTER the ordinary publication frame has been retained.
            mc.levelRenderer.allChanged();start(tick);stage=13;return false;}
        if(stage==13){if(!settled(mc,pos,tick))return false;ModernClientRuntimeChecks.capture(name("forced-reference"));stage=14;return false;}
        if(stage==14){if(!ModernClientRuntimeChecks.captured(name("forced-reference")))return false;beforeInvalidations=NativeFluidWorld.chunkInvalidations(mc.level);beforeRevision=tile(mc,pos).fluidGeometryRevision();mc.getConnection().handleLevelChunkWithLight(saved);start(tick);stage=6;return false;}
        if(stage==6){if(tick-started>400)need(false,"reload did not converge to refreshed volume/mesh");if(!loaded(mc,neighbor)||!settled(mc,pos,tick)||NativeFluidWorld.chunkInvalidations(mc.level)<=beforeInvalidations||tile(mc,pos).fluidGeometryRevision()<=beforeRevision)return false;consistent(mc,pos,true);need(mc.level.getBlockState(ShapeChunkRuntimeChecks.vanillaControl(index)).is(net.minecraft.world.level.block.Blocks.LIME_CONCRETE),"vanilla control reloads from actual packet");need(signature(quads(mc,stored,snapshot(mc,pos,stored,tile(mc,pos).getModelData()))).equals(loadedGeometry),"reload restores exact UV/material/geometry");ModernClientRuntimeChecks.capture(name("reloaded"));stage=7;return false;}
        if(stage==7){if(!ModernClientRuntimeChecks.captured(name("reloaded")))return false;results.add(Map.of("index",index,"tread",index%2==0?8:4,"upper",index>=4,"points",4096,"forcedReference",true,"oldSnapshotImmutable",true,"storedJoinNegative",true,"ownerBounds",ownerBounds,"neighborBounds",neighborBounds,"controlBounds",controlBounds));if(++index<8){stage=0;return false;}mc.player.setXRot(-80);start(tick);stage=8;return false;}
        if(stage==8){if(tick-started<12||ModernClientRuntimeChecks.renderedFrames()-frames<4)return false;ModernClientRuntimeChecks.capture("modern-shape-chunk-negative-sky.png");stage=9;return false;}
        if(stage==9){if(!ModernClientRuntimeChecks.captured("modern-shape-chunk-negative-sky.png"))return false;try{Files.writeString(mc.gameDirectory.toPath().resolve("screenshots/modern-shape-chunks.json"),new Gson().toJson(results),StandardCharsets.UTF_8);}catch(Exception failure){throw new IllegalStateException("Shape chunk report",failure);}route.clear();route.add(new Vec3(mc.player.getX(),80,-5));route.add(new Vec3(originalPosition.x,80,-5));route.add(originalPosition);stage=10;return false;}
        if(stage==10){if(!route.isEmpty()){if(walk(mc,route.peek(),tick))route.remove();return false;}mc.player.setXRot(0);start(tick);stage=11;return false;}
        if(stage==11){if(tick-started<12)return false;System.out.println("[vandorlabs][modern] shape-chunks-client PASS actual8chunk unload/reload pairs, worker snapshots, canonical/custom geometry, collision/free-volume complement, retained wet NBT, stale-state negative and24world captures");stage=12;}return stage==12;
    }
}
