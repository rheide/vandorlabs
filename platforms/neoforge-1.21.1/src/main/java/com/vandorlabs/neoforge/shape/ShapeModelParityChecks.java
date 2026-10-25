package com.vandorlabs.neoforge.shape;

import com.google.gson.*;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.SlabStairDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.util.RandomSource;

/** Actual native model/collision export compared with separately executed legacy adapters. */
public final class ShapeModelParityChecks {
    private static boolean done;
    private static final BlockPos POS=BlockPos.ZERO;
    private ShapeModelParityChecks(){}
    public static void run(Minecraft mc){
        if(done)return;done=true;com.vandorlabs.neoforge.client.NativeHousingLightingChecks.run(mc);JsonArray rows=new JsonArray();int index=0,rectangle=-1;
        for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if("screen_console_inputs_analysis_framed_gas_giant_static".equals(ModernFinishCatalog.entry(i).id()))rectangle=i;
        if(rectangle<0)throw new IllegalStateException("Missing rectangular reference material");
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST})for(boolean upper:new boolean[]{false,true})for(StairsShape requested:StairsShape.values())for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
            int layout=(index/3)%3,profile=(index/15)%3;BlockState state=ShapeRegistration.STAIRS.get().defaultBlockState().setValue(StairBlock.FACING,facing).setValue(StairBlock.HALF,upper?Half.TOP:Half.BOTTOM);
            Map<BlockPos,BlockState> blocks=new HashMap<>();blocks.put(POS,state);
            if(roll==0&&requested!=StairsShape.STRAIGHT){boolean outer=requested==StairsShape.OUTER_LEFT||requested==StairsShape.OUTER_RIGHT,left=requested==StairsShape.OUTER_LEFT||requested==StairsShape.INNER_LEFT;blocks.put(POS.relative(outer?facing:facing.getOpposite()),state.setValue(StairBlock.FACING,left?facing.getCounterClockWise():facing.getClockWise()));}
            var settings=settings(layout,profile,rectangle,tread,roll);var tile=new ProgrammableShapeEntity(POS,state);need(tile.applyShape(settings),"fixture settings");View view=new View(blocks,tile);BlockState actual=ShapeRegistration.STAIRS.get().connected(state,view,POS);need(actual.getValue(StairBlock.SHAPE)==(roll==0?requested:StairsShape.STRAIGHT),"actual join fixture "+requested);tile.setBlockState(actual);blocks.put(POS,actual);
            rows.add(row(mc,"stair/"+facing.get3DDataValue()+"/"+(upper?1:0)+"/"+requested.name()+"/"+tread+"/"+roll,actual,view,tile,layout,profile));index++;
        }
        for(Direction facing:Direction.values())for(boolean upper:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(int profile=0;profile<3;profile++){
            BlockState state=ShapeRegistration.SLAB.get().defaultBlockState().setValue(ProgrammableSlabBlock.FACING,facing).setValue(ProgrammableSlabBlock.HALF,upper?Half.TOP:Half.BOTTOM);var tile=new ProgrammableShapeEntity(POS,state);need(tile.applyShape(settings(layout,profile,rectangle,8,0)),"slab settings");View view=new View(Map.of(POS,state),tile);
            rows.add(row(mc,"slab/"+facing.get3DDataValue()+"/"+(upper?1:0)+"/"+layout+"/"+profile,state,view,tile,layout,profile));
        }
        JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("rectangularChoice",rectangle);root.add("cases",rows);
        try{var file=mc.gameDirectory.toPath().resolve("screenshots/modern-shape-model-parity.json");Files.createDirectories(file.getParent());try(Writer writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)){new Gson().toJson(root,writer);}}catch(IOException e){throw new IllegalStateException("Shape parity export",e);}
        System.out.println("[vandorlabs][modern] shape-model-parity PASS actual600stair+108slab native baked models and collision exports, all actual join states, content UVs/cull/shading");
    }
    private static void need(boolean b,String message){if(!b)throw new IllegalStateException("Shape parity: "+message);}
    private static SlabStairDialog.State settings(int layout,int profile,int rectangle,int tread,int roll){return new SlabStairDialog.State(profile==0?rectangle:1,profile==1?rectangle:-1,layout,profile==2,new int[]{rectangle,2,3,4,5,6},-1,new StairState(tread,roll));}
    private static JsonObject row(Minecraft mc,String key,BlockState state,View view,ProgrammableShapeEntity tile,int layout,int profile){
        JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("layout",layout);row.addProperty("profile",profile);var model=mc.getBlockRenderer().getBlockModel(state);row.addProperty("ao",model.useAmbientOcclusion());row.addProperty("rendererAo",model.useAmbientOcclusion(state,tile.getModelData(),net.minecraft.client.renderer.RenderType.solid()).name());JsonArray quads=new JsonArray();
        for(Direction bucket:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(var quad:model.getQuads(state,bucket,RandomSource.create(0),tile.getModelData(),net.minecraft.client.renderer.RenderType.solid())){
            JsonObject q=new JsonObject();q.addProperty("bucket",bucket==null?-1:bucket.get3DDataValue());q.addProperty("face",quad.getDirection().get3DDataValue());q.addProperty("shade",quad.isShade());q.addProperty("quadAo",quad.hasAmbientOcclusion());String sprite=quad.getSprite().contents().name().toString();q.addProperty("sprite",sprite);float usedU=1,usedV=1;
            for(var entry:ModernFinishCatalog.ENTRIES)if(entry.texture().equals(sprite)){usedU=entry.u();usedV=entry.v();q.addProperty("material",ModernFinishCatalog.entry(entry.canonical()).id());break;}
            JsonArray vertices=new JsonArray();int[] data=quad.getVertices();int stride=data.length/4;
            for(int i=0;i<4;i++){JsonArray vertex=new JsonArray();for(int xyz=0;xyz<3;xyz++)vertex.add(Float.intBitsToFloat(data[i*stride+xyz]));vertex.add((Float.intBitsToFloat(data[i*stride+4])-quad.getSprite().getU0())/(quad.getSprite().getU1()-quad.getSprite().getU0())/usedU);vertex.add((Float.intBitsToFloat(data[i*stride+5])-quad.getSprite().getV0())/(quad.getSprite().getV1()-quad.getSprite().getV0())/usedV);vertices.add(vertex);}q.add("vertices",vertices);quads.add(q);
        }
        row.add("quads",quads);JsonArray boxes=new JsonArray();for(var b:state.getCollisionShape(view,POS,CollisionContext.empty()).toAabbs()){JsonArray box=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})box.add(v);boxes.add(box);}row.add("boxes",boxes);return row;
    }
    private record View(Map<BlockPos,BlockState> states,BlockEntity tile) implements BlockGetter {
        @Override public BlockEntity getBlockEntity(BlockPos pos){return pos.equals(POS)?tile:null;}
        @Override public BlockState getBlockState(BlockPos pos){return states.getOrDefault(pos,Blocks.AIR.defaultBlockState());}
        @Override public FluidState getFluidState(BlockPos pos){return Fluids.EMPTY.defaultFluidState();}
        @Override public int getHeight(){return 384;}
        @Override public int getMinBuildHeight(){return -64;}
    }
}
