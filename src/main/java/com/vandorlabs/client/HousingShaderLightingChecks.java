package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.FaceTextures;
import com.vandorlabs.tiles.ScreenHousingTextures;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import org.lwjgl.util.vector.Vector3f;

/** Actual loaded OptiFine AO output; a vertex-light contract, not a scene screenshot. */
final class HousingShaderLightingChecks {
    private static final BlockPos POS=new BlockPos(8,100,8);
    private static int generation;

    static void run(Minecraft mc)throws Exception {
        String version=(String)Class.forName("Config").getMethod("getVersion").invoke(null);
        require(DefaultVertexFormats.BLOCK.getNextOffset()>32,"live shader vertex format is required");
        Method layer=BufferBuilder.class.getMethod("setBlockLayer",BlockRenderLayer.class);
        int previousAO=mc.gameSettings.ambientOcclusion;
        BlockRenderLayer previousLayer=MinecraftForgeClient.getRenderLayer();
        BlockModelRenderer renderer=mc.getBlockRendererDispatcher().getBlockModelRenderer();
        int faces=0,gradients=0,rejected=0;
        try {
            mc.gameSettings.ambientOcclusion=2;
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);
            for(int family=0;family<4;family++)for(EnumFacing facing:EnumFacing.HORIZONTALS) {
                Block block=family==0?ModBlocks.PROGRAMMABLE_BLOCK:family==3?ModBlocks.PROGRAMMABLE_STORAGE:ModBlocks.PROGRAMMABLE_SLAB;
                IBlockState listed=block.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);
                if(family==1||family==2)listed=listed.withProperty(BlockProgrammableSlab.HALF,
                        family==2?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);
                IExtendedBlockState extended=((IExtendedBlockState)listed)
                        .withProperty(ProgrammableHousingState.FINISH,family==3?ScreenHousingTextures.DEFAULT_STORAGE:0)
                        .withProperty(ProgrammableHousingState.SIDE_FINISH,-1)
                        .withProperty(ProgrammableHousingState.FACES,FaceTextures.DEFAULT)
                        .withProperty(ProgrammableHousingState.TILE_SIDES,1)
                        .withProperty(ProgrammableHousingState.VISIBLE,63);
                IBakedModel housing=mc.getBlockRendererDispatcher().getModelForState(listed);
                require(housing instanceof ProgrammableHousingModel,"registered housing model missing");
                require(housing.isAmbientOcclusion(),"registered housing model disables AO");
                IBlockAccess access=access(listed);
                List<BakedQuad> quads=housing.getQuads(extended,null,0);
                require(quads.size()==6,"housing face coverage changed");
                for(BakedQuad quad:quads) {
                    int[] expected=light(renderer,access,extended,housing,reference(quad),layer);
                    int[] actual=light(renderer,access,extended,housing,quad,layer);
                    require(Arrays.equals(expected,actual),"FaceBakery AO mismatch family="+family+" facing="+facing+" face="+quad.getFace());
                    for(int vertex=1;vertex<4;vertex++)if(expected[vertex*5+4]!=expected[4]){gradients++;break;}
                    if(quad.getFace().getAxis()==EnumFacing.Axis.Y) {
                        int[] data=quad.getVertexData(),shifted=data.clone();int stride=data.length/4;
                        System.arraycopy(data,stride,shifted,0,3*stride);
                        System.arraycopy(data,0,shifted,3*stride,stride);
                        BakedQuad wrong=new BakedQuad(shifted,-1,quad.getFace(),quad.getSprite(),false,quad.getFormat());
                        if(!Arrays.equals(expected,light(renderer,access,extended,housing,wrong,layer)))rejected++;
                    }
                    faces++;
                }
            }
        } finally {
            mc.gameSettings.ambientOcclusion=previousAO;
            ForgeHooksClient.setRenderLayer(previousLayer);
        }
        require(faces==96&&gradients>0&&rejected>0,"fixture cannot reject displaced floor lighting");
        System.out.println("[vandorlabs][reprolab] housing-shader-lighting PASS version="+version+" generation="+(generation++)
                +" vertex-bytes="+DefaultVertexFormats.BLOCK.getNextOffset()+" faces="+faces+" gradients="+gradients
                +" rejected-displaced-floors="+rejected+" cubes/both-slab-halves/storage/all-facings actual-OptiFine-vertex-light-contract; scene appearance separate");
    }

    private static IBlockAccess access(IBlockState center) {
        return new IBlockAccess() {
            public IBlockState getBlockState(BlockPos p) {
                return p.equals(POS)?center:p.equals(POS.up().west().north())?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState();
            }
            public int getCombinedLight(BlockPos p,int minimum) {
                int x=p.getX()-POS.getX(),y=p.getY()-POS.getY(),z=p.getZ()-POS.getZ();
                int block=2+Math.floorMod(3*x+5*y+7*z,13),sky=1+Math.floorMod(7*x+2*y+3*z,15);
                return (Math.max(block,minimum)<<4)|(sky<<20);
            }
            public TileEntity getTileEntity(BlockPos p){return null;}
            public boolean isAirBlock(BlockPos p){return getBlockState(p).getBlock()==Blocks.AIR;}
            public Biome getBiome(BlockPos p){return Biomes.PLAINS;}
            public int getStrongPower(BlockPos p,EnumFacing side){return 0;}
            public WorldType getWorldType(){return WorldType.DEFAULT;}
            public boolean isSideSolid(BlockPos p,EnumFacing side,boolean fallback){return getBlockState(p).isSideSolid(this,p,side);}
        };
    }

    /** The engine's own corner order is independent of the custom housing emitter. */
    static BakedQuad reference(BakedQuad quad) {
        float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        int[] data=quad.getVertexData();int stride=data.length/4;
        for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<3;axis++) {
            float value=Float.intBitsToFloat(data[vertex*stride+axis])*16;
            low[axis]=Math.min(low[axis],value);high[axis]=Math.max(high[axis],value);
        }
        BlockPartFace face=new BlockPartFace(null,-1,"#all",new BlockFaceUV(new float[]{0,0,16,16},0));
        return new FaceBakery().makeBakedQuad(new Vector3f(low[0],low[1],low[2]),new Vector3f(high[0],high[1],high[2]),
                face,quad.getSprite(),quad.getFace(),ModelRotation.X0_Y0,null,false,false);
    }

    private static int[] light(BlockModelRenderer renderer,IBlockAccess access,IBlockState state,IBakedModel housing,BakedQuad quad,Method layer)throws Exception {
        IBakedModel one=new IBakedModel() {
            public List<BakedQuad> getQuads(IBlockState s,EnumFacing side,long seed){return side==null?Collections.singletonList(quad):Collections.emptyList();}
            public boolean isAmbientOcclusion(){return housing.isAmbientOcclusion();}
            public boolean isGui3d(){return housing.isGui3d();}
            public boolean isBuiltInRenderer(){return false;}
            public TextureAtlasSprite getParticleTexture(){return quad.getSprite();}
            public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
            public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
        };
        BufferBuilder buffer=new BufferBuilder(512);layer.invoke(buffer,BlockRenderLayer.SOLID);
        buffer.begin(7,DefaultVertexFormats.BLOCK);
        require(renderer.renderModel(access,one,state,POS,buffer,false,0),"empty actual shader AO output");
        buffer.finishDrawing();
        return physicalLight(buffer);
    }

    /** Compare colors and light at physical corners, leaving artwork UVs untouched. */
    static int[] physicalLight(BufferBuilder buffer) {
        require(buffer.getVertexCount()==4,"single face changed vertex count");
        VertexFormat format=buffer.getVertexFormat();int stride=format.getIntegerSize();
        int[] raw=buffer.getVertexState().getRawBuffer();List<int[]> rows=new ArrayList<>();
        for(int vertex=0;vertex<4;vertex++) {
            int at=vertex*stride;
            rows.add(new int[]{raw[at],raw[at+1],raw[at+2],raw[at+format.getColorOffset()/4],raw[at+format.getUvOffsetById(1)/4]});
        }
        rows.sort((a,b)->{for(int axis=0;axis<3;axis++){int c=Float.compare(Float.intBitsToFloat(a[axis]),Float.intBitsToFloat(b[axis]));if(c!=0)return c;}return 0;});
        int[] result=new int[20];for(int i=0;i<4;i++)System.arraycopy(rows.get(i),0,result,i*5,5);
        return result;
    }

    private static void require(boolean value,String message){if(!value)throw new AssertionError("Housing shader lighting: "+message);}
    private HousingShaderLightingChecks(){}
}
