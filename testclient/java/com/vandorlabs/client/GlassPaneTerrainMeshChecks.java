package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
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
import net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.CoreModManager;

/** Native pane records, winding, real block-writer consumption and model-layer separation. */
public final class GlassPaneTerrainMeshChecks {
    private static int checks;
    private static final int LIGHT=0x00a00070;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void equal(float actual,float expected,String why){need(Math.abs(actual-expected)<.000001F,why+": "+actual+" != "+expected);}
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(){super("vandorlabs:checks/native-pane");setIconWidth(16);setIconHeight(16);initSprite(512,256,73,41,false);}
    }
    private static final class Model implements IBakedModel {
        final TextureAtlasSprite sprite;final List<BakedQuad> quads;final boolean sided;
        Model(TextureAtlasSprite sprite,List<BakedQuad> quads,boolean sided){this.sprite=sprite;this.quads=quads;this.sided=sided;}
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null||sided?quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return true;}public boolean isGui3d(){return false;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return sprite;}
        public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
        public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
    }
    private static final class Access implements IBlockAccess {
        TileEntity tile;
        public TileEntity getTileEntity(BlockPos pos){return tile;}
        public int getCombinedLight(BlockPos pos,int emission){return (LIGHT&0xffff0000)|Math.max(LIGHT&65535,emission*16);}
        public IBlockState getBlockState(BlockPos pos){return Blocks.AIR.getDefaultState();}
        public boolean isAirBlock(BlockPos pos){return true;}public Biome getBiome(BlockPos pos){return Biomes.PLAINS;}
        public int getStrongPower(BlockPos pos,EnumFacing face){return 0;}public WorldType getWorldType(){return WorldType.DEFAULT;}
        public boolean isSideSolid(BlockPos pos,EnumFacing face,boolean fallback){return false;}
    }
    private static VertexFormat normal(){
        return new VertexFormat(DefaultVertexFormats.BLOCK).addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B);
    }
    private static VertexFormat expanded(){
        return normal().addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.PADDING,6));
    }
    private static float at(int[] data,int stride,int vertex,int component){return Float.intBitsToFloat(data[vertex*stride+component]);}
    private static void geometry(Sprite sprite){
        for(VertexFormat format:new VertexFormat[]{new VertexFormat(DefaultVertexFormats.BLOCK),normal(),expanded()})
            for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(boolean single:new boolean[]{false,true}){
                need(GlassPaneTerrainMesh.supports(format),"valid native layout refused");
                GlassPaneTerrainMesh mesh=GlassPaneTerrainMesh.build(rotated,depth,sprite,single,format);
                int count=single?2:4,stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4;
                need(mesh.quads.size()==count,"sheet/face count");need(mesh.format.equals(format)&&mesh.format!=format,"format must be an immutable copy");
                for(int q=0;q<count;q++){
                    BakedQuad quad=mesh.quads.get(q);int[] data=quad.getVertexData();
                    need(data.length==stride*4,"raw record stride");need(quad.getSprite()==sprite&&!quad.hasTintIndex(),"authored sprite or vertex tint changed");
                    need(!quad.shouldApplyDiffuseLighting(),"flat authored pane acquired face shading");
                    double plane=(single?.5:q/2==0?7D/16:9D/16)+PanelDepth.offset(depth)/16D;
                    for(int vertex=0;vertex<4;vertex++){
                        float x=at(data,stride,vertex,0),y=at(data,stride,vertex,1),z=at(data,stride,vertex,2);
                        equal(rotated?x:z,(float)(rotated?1-plane:plane),"pane depth");
                        float u=rotated?z:x;
                        need((u==0||u==1)&&(y==0||y==1),"pane escaped its own cell");
                        equal(at(data,stride,vertex,uv),sprite.getInterpolatedU(u*16),"authored U");
                        equal(at(data,stride,vertex,uv+1),sprite.getInterpolatedV((1-y)*16),"authored V");
                        need(data[vertex*stride+format.getColorOffset()/4]==-1,"pane lost white unmodified material color");
                        need(data[vertex*stride+format.getUvOffsetById(1)/4]==0,"live light baked into shared template");
                    }
                    float ax=at(data,stride,1,0)-at(data,stride,0,0),ay=at(data,stride,1,1)-at(data,stride,0,1),az=at(data,stride,1,2)-at(data,stride,0,2);
                    float bx=at(data,stride,2,0)-at(data,stride,0,0),by=at(data,stride,2,1)-at(data,stride,0,1),bz=at(data,stride,2,2)-at(data,stride,0,2);
                    float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                    need(nx*quad.getFace().getFrontOffsetX()+ny*quad.getFace().getFrontOffsetY()+nz*quad.getFace().getFrontOffsetZ()>0,"winding disagrees with culling normal");
                    if((q&1)==1)need(mesh.quads.get(q-1).getFace().getOpposite()==quad.getFace(),"physical sheet lacks opposite winding");
                }
                try{mesh.quads.add(mesh.quads.get(0));throw new AssertionError("mutable published quad list");}catch(UnsupportedOperationException expected){checks++;}
                need(mesh.bytes()>=count*stride*16,"cache undercounts vertex storage");
                nativeWriter(mesh,false);nativeWriter(mesh,true);
            }
    }
    private static void nativeWriter(GlassPaneTerrainMesh mesh,boolean forge){
        BlockModelRenderer renderer=forge?new ForgeBlockModelRenderer(new BlockColors()):new BlockModelRenderer(new BlockColors());
        BufferBuilder buffer=new BufferBuilder(4096);BlockPos origin=new BlockPos(-32,64,29999984),pos=origin.add(5,3,7);
        buffer.begin(7,mesh.format);buffer.setTranslation(-origin.getX(),-origin.getY(),-origin.getZ());
        need(renderer.renderModelFlat(new Access(),new Model(mesh.sprite,mesh.quads,false),Blocks.GLASS.getDefaultState(),pos,buffer,false,0),"native block writer rejected panes");
        need(buffer.getVertexCount()==mesh.quads.size()*4,"native block writer changed face count");
        int[] raw=buffer.getVertexState().getRawBuffer();int stride=mesh.format.getIntegerSize(),light=mesh.format.getUvOffsetById(1)/4,uv=mesh.format.getUvOffsetById(0)/4;
        for(int q=0;q<mesh.quads.size();q++){
            int[] local=mesh.quads.get(q).getVertexData();
            for(int vertex=0;vertex<4;vertex++){
                int source=vertex*stride,target=(q*4+vertex)*stride;
                equal(Float.intBitsToFloat(raw[target]),Float.intBitsToFloat(local[source])+5,"native translated X");
                equal(Float.intBitsToFloat(raw[target+1]),Float.intBitsToFloat(local[source+1])+3,"native translated Y");
                equal(Float.intBitsToFloat(raw[target+2]),Float.intBitsToFloat(local[source+2])+7,"native translated Z");
                need(raw[target+uv]==local[source+uv]&&raw[target+uv+1]==local[source+uv+1],"native writer changed glimmer coordinates");
                need(raw[target+light]==LIGHT,"native writer lost current lightmap");
            }
        }
        buffer.finishDrawing();
    }
    private static void unsupportedFormats(Sprite sprite){
        VertexFormatElement floatColor=new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.COLOR,4);
        VertexFormatElement floatNormal=new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.NORMAL,3);
        VertexFormat[] unsupported={
            new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.TEX_2S),
            new VertexFormat().addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.TEX_2S),
            new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(floatColor).addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.TEX_2S),
            new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.TEX_2F),
            new VertexFormat(DefaultVertexFormats.BLOCK).addElement(floatNormal),
            new VertexFormat(DefaultVertexFormats.BLOCK).addElement(DefaultVertexFormats.PADDING_1B)
        };
        need(!GlassPaneTerrainMesh.supports(null),"null layout accepted");
        for(VertexFormat format:unsupported){
            need(!GlassPaneTerrainMesh.supports(format),"foreign layout accepted");
            try{GlassPaneTerrainMesh.build(false,0,sprite,true,format);throw new AssertionError("foreign layout reached native emitter");}
            catch(IllegalArgumentException expected){checks++;}
        }
    }
    private static void modelLayers(Sprite sprite){
        BlockProgrammableGlass block=new BlockProgrammableGlass("native_pane_checks");
        GlassPaneTerrainMesh pane=GlassPaneTerrainMesh.build(false,0,sprite,true,DefaultVertexFormats.BLOCK);
        BakedQuad stock=pane.quads.get(0);List<BakedQuad> stockList=Collections.singletonList(stock);
        GlassFrameModel model=new GlassFrameModel(new Model(sprite,stockList,true));
        IExtendedBlockState empty=(IExtendedBlockState)block.getDefaultState(),state=empty.withProperty(BlockProgrammableGlass.PANE_MESH,pane);
        BlockRenderLayer previous=MinecraftForgeClient.getRenderLayer();
        try{
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.SOLID);
            need(model.getQuads(state,null,0)==stockList,"stock solid frame changed");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT_MIPPED);
            need(model.getQuads(state,null,0).isEmpty(),"pane leaked into frame cutout layer");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.TRANSLUCENT);
            need(model.getQuads(state,null,0)==pane.quads,"frameless pane not independently published");
            need(model.getQuads(state,EnumFacing.NORTH,0).isEmpty(),"sided lookup duplicated pane");
            need(!model.isAmbientOcclusion(state),"flat pane enabled face ambient shading");
            GlassFrameModel.Mesh frame=new GlassFrameModel.Mesh(stockList,new VertexFormat(DefaultVertexFormats.BLOCK),false);
            IExtendedBlockState combined=state.withProperty(BlockProgrammableGlass.FRAME_MESH,frame);
            need(model.getQuads(combined,null,0)==pane.quads,"cutout frame entered native translucent layer");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT);
            need(model.getQuads(combined,null,0)==frame.cutout,"mipmap-disabled selected frame lost cutout layer");
            GlassFrameModel.Mesh blended=new GlassFrameModel.Mesh(stockList,new VertexFormat(DefaultVertexFormats.BLOCK),true);
            IExtendedBlockState alphaFrame=state.withProperty(BlockProgrammableGlass.FRAME_MESH,blended);
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.TRANSLUCENT);
            need(model.getQuads(alphaFrame,null,0).size()==3,"translucent selected frame/pane combination");
            GlassFrameModel.Mesh stale=new GlassFrameModel.Mesh(stockList,normal(),false);
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT_MIPPED);
            need(model.getQuads(state.withProperty(BlockProgrammableGlass.FRAME_MESH,stale),null,0).isEmpty(),"stale frame stride reached native buffer");
            ForgeHooksClient.setRenderLayer(null);
            need(model.getQuads(combined,null,0).size()==4,"damage model omitted pane or selected frame");
            need(model.getQuads(combined,EnumFacing.UP,0)==stockList,"damage sided delegate changed");
            need(model.getQuads(null,null,0)==stockList,"item delegate changed");
            Access world=new Access();TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();world.tile=tile;
            tile.paneTerrainMesh=pane;tile.frameChunkMesh=frame;tile.frameChunkLight=15;
            IExtendedBlockState published=(IExtendedBlockState)block.getExtendedState(block.getDefaultState(),world,BlockPos.ORIGIN);
            need(published.getValue(BlockProgrammableGlass.PANE_MESH)==pane&&published.getValue(BlockProgrammableGlass.FRAME_MESH)==frame,"independent pane/frame extended properties");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.TRANSLUCENT);
            need(block.getPackedLightmapCoords(published,world,BlockPos.ORIGIN)==LIGHT,"fullbright frame leaked into pane lighting");
            ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT_MIPPED);
            need((block.getPackedLightmapCoords(published,world,BlockPos.ORIGIN)&65535)==240,"frame lost its separate light");
            tile.frameChunkMesh=null;tile.frameChunkPublication=new Object();
            published=(IExtendedBlockState)block.getExtendedState(block.getDefaultState(),world,BlockPos.ORIGIN);
            need(published.getValue(BlockProgrammableGlass.PANE_MESH)==pane,"no frame caused early return before pane publication");
        }finally{ForgeHooksClient.setRenderLayer(previous);}
    }
    public static void main(String[] args)throws Exception{
        Field environment=CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);boolean previous=environment.getBoolean(null);
        environment.setBoolean(null,true);
        try{
            net.minecraft.init.Bootstrap.register();Sprite sprite=new Sprite();
            geometry(sprite);unsupportedFormats(sprite);modelLayers(sprite);
            System.out.println("GlassPaneTerrainMesh PASS checks="+checks+" native28/normal32/extended56 geometry, opposing culling windings, authored UVs, native Forge/vanilla emission, live lightmap, frame separation and damage/item dispatch");
        }finally{environment.setBoolean(null,previous);}
    }
    private GlassPaneTerrainMeshChecks(){}
}
