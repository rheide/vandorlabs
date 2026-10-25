package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Independent reference exported from the actual legacy block and baked-model adapters. */
final class LegacyShapeModelGoldens {
    private static final BlockPos POS=new BlockPos(320,70,190);
    private static final Map<String,String> MATERIALS=new HashMap<>();
    private LegacyShapeModelGoldens(){}
    static void run(Minecraft mc,File output)throws IOException {
        LegacyHousingLightingChecks.run();JsonArray rows=new JsonArray();int index=0,rectangle=-1;
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("screen_console_inputs_analysis_framed_gas_giant_static".equals(ScreenHousingTextures.IDS[i]))rectangle=i;
        if(rectangle<0)throw new IllegalStateException("Missing rectangular reference material");
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)MATERIALS.putIfAbsent(ScreenHousingTextures.texture(i),ScreenHousingTextures.IDS[ScreenHousingTextures.canonicalChoice(i)]);
        try {
            for(EnumFacing facing:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST})
                for(boolean upper:new boolean[]{false,true})for(BlockStairs.EnumShape requested:BlockStairs.EnumShape.values())
                    for(int tread:new int[]{1,2,4,8,16})for(int roll=0;roll<3;roll++){
                        int layout=(index/3)%3,profile=(index/15)%3;
                        IBlockState state=ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,upper?BlockStairs.EnumHalf.TOP:BlockStairs.EnumHalf.BOTTOM);
                        clear(mc);mc.world.setBlockState(POS,state,2);
                        if(roll==0&&requested!=BlockStairs.EnumShape.STRAIGHT){boolean outer=requested==BlockStairs.EnumShape.OUTER_LEFT||requested==BlockStairs.EnumShape.OUTER_RIGHT;boolean left=requested==BlockStairs.EnumShape.OUTER_LEFT||requested==BlockStairs.EnumShape.INNER_LEFT;
                            mc.world.setBlockState(POS.offset(outer?facing:facing.getOpposite()),state.withProperty(BlockStairs.FACING,left?facing.rotateYCCW():facing.rotateY()),2);
                        }
                        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);configure(tile,layout,profile,rectangle,tread,roll);
                        IBlockState actual=state.getBlock().getActualState(state,mc.world,POS);
                        if(actual.getValue(BlockStairs.SHAPE)!=(roll==0?requested:BlockStairs.EnumShape.STRAIGHT))throw new IllegalStateException("Reference join fixture "+requested);
                        rows.add(row(mc,"stair/"+facing.getIndex()+"/"+(upper?1:0)+"/"+requested.name()+"/"+tread+"/"+roll,actual,layout,profile));index++;
                    }
            for(EnumFacing facing:EnumFacing.values())for(boolean upper:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(int profile=0;profile<3;profile++){
                clear(mc);IBlockState state=ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing).withProperty(BlockProgrammableSlab.HALF,upper?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);mc.world.setBlockState(POS,state,2);
                configure((TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS),layout,profile,rectangle,8,0);
                rows.add(row(mc,"slab/"+facing.getIndex()+"/"+(upper?1:0)+"/"+layout+"/"+profile,state,layout,profile));
            }
        } finally {clear(mc);}
        JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("rectangularChoice",rectangle);root.add("cases",rows);
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(output,"legacy-shape-model-goldens.json")),StandardCharsets.UTF_8)){new Gson().toJson(root,writer);}
        System.out.println("[vandorlabs][reprolab] shape-model-goldens PASS actual600stair+108slab baked models, joins, collision, cull buckets, shading and content UVs");
    }
    private static void configure(TileEntityAnimatedScreenSelector tile,int layout,int profile,int rectangle,int tread,int roll){
        tile.setHousingTexture(profile==0?rectangle:1);tile.setSideTexture(profile==1?rectangle:-1);tile.setFaceTextures(new FaceTextures(profile==2,new int[]{rectangle,2,3,4,5,6}));tile.setMainTextureLayout(layout);tile.setStairGeometry(tread,roll);
    }
    private static void clear(Minecraft mc){mc.world.setBlockToAir(POS);for(EnumFacing side:EnumFacing.values())mc.world.setBlockToAir(POS.offset(side));}
    private static JsonObject row(Minecraft mc,String key,IBlockState state,int layout,int profile){
        JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("layout",layout);row.addProperty("profile",profile);
        IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);row.addProperty("ao",model.isAmbientOcclusion());IBlockState extended=state.getBlock().getExtendedState(state,mc.world,POS);JsonArray quads=new JsonArray();
        for(EnumFacing bucket:new EnumFacing[]{null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST})for(BakedQuad quad:model.getQuads(extended,bucket,0)){
            JsonObject q=new JsonObject();q.addProperty("bucket",bucket==null?-1:bucket.getIndex());q.addProperty("face",quad.getFace().getIndex());q.addProperty("shade",quad.shouldApplyDiffuseLighting());q.addProperty("sprite",quad.getSprite().getIconName());q.addProperty("material",MATERIALS.get(quad.getSprite().getIconName()));JsonArray vertices=new JsonArray();int[] data=quad.getVertexData();int stride=data.length/4;
            for(int i=0;i<4;i++){JsonArray vertex=new JsonArray();for(int xyz=0;xyz<3;xyz++)vertex.add(Float.intBitsToFloat(data[i*stride+xyz]));vertex.add((Float.intBitsToFloat(data[i*stride+4])-quad.getSprite().getMinU())/(quad.getSprite().getMaxU()-quad.getSprite().getMinU()));vertex.add((Float.intBitsToFloat(data[i*stride+5])-quad.getSprite().getMinV())/(quad.getSprite().getMaxV()-quad.getSprite().getMinV()));vertices.add(vertex);}q.add("vertices",vertices);quads.add(q);
        }
        row.add("quads",quads);List<AxisAlignedBB> bounds=new ArrayList<>();state.getBlock().addCollisionBoxToList(state,mc.world,POS,new AxisAlignedBB(POS),bounds,null,true);JsonArray boxes=new JsonArray();
        for(AxisAlignedBB b:bounds){JsonArray box=new JsonArray();for(double v:new double[]{b.minX-POS.getX(),b.minY-POS.getY(),b.minZ-POS.getZ(),b.maxX-POS.getX(),b.maxY-POS.getY(),b.maxZ-POS.getZ()})box.add(v);boxes.add(box);}row.add("boxes",boxes);return row;
    }
}
