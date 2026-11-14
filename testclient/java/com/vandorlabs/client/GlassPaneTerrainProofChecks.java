package com.vandorlabs.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.chunk.CompiledChunk;
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
import net.minecraftforge.client.model.pipeline.ForgeBlockModelRenderer;

/** Native buffer contents, including mixed builds and optional public UV adapters. */
public final class GlassPaneTerrainProofChecks {
    private static int checks;
    private static final BufferBuilder HOLDER=new BufferBuilder(256);
    private static final class Chunk extends CompiledChunk {
        Chunk(BufferBuilder.State state,boolean used){setState(state);if(used)setLayerUsed(BlockRenderLayer.TRANSLUCENT);}
    }
    public static final class Sprite extends TextureAtlasSprite {
        Sprite(String name,int x){super("vandorlabs:checks/proof-"+name);setIconWidth(16);setIconHeight(16);initSprite(512,512,x,96,false);}
        public float toSingleU(float u){return (u-getMinU())/(getMaxU()-getMinU());}
        public float toSingleV(float v){return (v-getMinV())/(getMaxV()-getMinV());}
    }
    public static final class SpriteState extends BufferBuilder.State {
        private final TextureAtlasSprite[] sprites;
        SpriteState(int[] raw,VertexFormat format,TextureAtlasSprite[] sprites){HOLDER.super(raw,format);this.sprites=sprites;}
        public TextureAtlasSprite[] getQuadSprites(){return sprites;}
    }
    private static final class Model implements IBakedModel {
        final GlassPaneTerrainMesh mesh;Model(GlassPaneTerrainMesh mesh){this.mesh=mesh;}
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return side==null?mesh.quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return false;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return mesh.sprite;}
        public ItemCameraTransforms getItemCameraTransforms(){return ItemCameraTransforms.DEFAULT;}
        public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
    }
    private static final class Access implements IBlockAccess {
        public TileEntity getTileEntity(BlockPos pos){return null;}
        public int getCombinedLight(BlockPos pos,int emission){return 0x00a00070;}
        public IBlockState getBlockState(BlockPos pos){return Blocks.AIR.getDefaultState();}
        public boolean isAirBlock(BlockPos pos){return true;}public Biome getBiome(BlockPos pos){return Biomes.PLAINS;}
        public int getStrongPower(BlockPos pos,EnumFacing face){return 0;}public WorldType getWorldType(){return WorldType.DEFAULT;}
        public boolean isSideSolid(BlockPos pos,EnumFacing face,boolean fallback){return false;}
    }
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static VertexFormat extended(){
        return new VertexFormat().addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.COLOR_4UB)
                .addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.TEX_2S)
                .addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B)
                .addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.PADDING,6));
    }
    private static Map<BlockPos,List<GlassPaneTerrainMesh>> candidates(Map<BlockPos,GlassPaneTerrainMesh> wanted){
        Map<BlockPos,List<GlassPaneTerrainMesh>> result=new HashMap<>();
        for(Map.Entry<BlockPos,GlassPaneTerrainMesh> entry:wanted.entrySet())result.put(entry.getKey(),Collections.singletonList(entry.getValue()));return result;
    }
    private static CompiledChunk nativeChunk(VertexFormat format,BlockPos origin,Map<BlockPos,GlassPaneTerrainMesh> panes,boolean forge){
        BufferBuilder buffer=new BufferBuilder(32768);buffer.begin(7,format);buffer.setTranslation(-origin.getX(),-origin.getY(),-origin.getZ());
        BlockModelRenderer renderer=forge?new ForgeBlockModelRenderer(new BlockColors()):new BlockModelRenderer(new BlockColors());
        for(Map.Entry<BlockPos,GlassPaneTerrainMesh> entry:panes.entrySet())
            need(renderer.renderModelFlat(new Access(),new Model(entry.getValue()),Blocks.GLASS.getDefaultState(),entry.getKey(),buffer,false,0),"native emitter rejected pane");
        buffer.sortVertexData(origin.getX()+8,origin.getY()+8,origin.getZ()+24);BufferBuilder.State state=buffer.getVertexState();buffer.finishDrawing();
        return new Chunk(state,!panes.isEmpty());
    }
    private static GlassPaneTerrainProof.Result inspect(CompiledChunk chunk,BlockPos origin,Map<BlockPos,GlassPaneTerrainMesh> wanted){
        return GlassPaneTerrainProof.inspect(chunk,origin,candidates(wanted),wanted);
    }
    private static CompiledChunk state(int[] raw,VertexFormat format){return new Chunk(HOLDER.new State(raw,format),true);}
    private static void nativeLayouts(Sprite gold){
        for(VertexFormat format:new VertexFormat[]{new VertexFormat(DefaultVertexFormats.BLOCK),extended()})
            for(BlockPos origin:new BlockPos[]{new BlockPos(0,64,0),new BlockPos(-32,80,-16),new BlockPos(29999984,240,-30000000)})
                for(boolean rotated:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(boolean single:new boolean[]{false,true})
                    for(boolean forge:new boolean[]{false,true}){
                        GlassPaneTerrainMesh mesh=GlassPaneTerrainMesh.build(rotated,depth,gold,single,format);
                        BlockPos pos=origin.add(5,3,7);Map<BlockPos,GlassPaneTerrainMesh> wanted=Collections.singletonMap(pos,mesh);
                        CompiledChunk chunk=nativeChunk(format,origin,wanted,forge);GlassPaneTerrainProof.Result result=inspect(chunk,origin,wanted);
                        need(result.verified&&result.present.equals(wanted.keySet())&&result.wantedPresent.equals(wanted.keySet()),"actual native material records missing");
                        need(result.physicalPresent.equals(wanted.keySet())&&result.wantedPhysicalPresent.equals(wanted.keySet())&&result.matched.get(pos)==mesh,"actual native ownership missing");
                        int[] shifted=chunk.getState().getRawBuffer().clone();int stride=format.getIntegerSize();
                        for(int at=0;at<shifted.length;at+=stride){
                            shifted[at]=Float.floatToRawIntBits(Float.intBitsToFloat(shifted[at])+256);
                            shifted[at+1]=Float.floatToRawIntBits(Float.intBitsToFloat(shifted[at+1])-128);
                            shifted[at+2]=Float.floatToRawIntBits(Float.intBitsToFloat(shifted[at+2])+512);
                        }
                        need(inspect(state(shifted,format),origin,wanted).wantedPresent.equals(wanted.keySet()),"region-relative positions changed proof");
                    }
    }
    private static void mixedAndHoles(Sprite gold,Sprite blue){
        VertexFormat format=DefaultVertexFormats.BLOCK;BlockPos origin=new BlockPos(32,64,-32);
        GlassPaneTerrainMesh goldMesh=GlassPaneTerrainMesh.build(false,0,gold,true,format),blueMesh=GlassPaneTerrainMesh.build(false,0,blue,true,format);
        Map<BlockPos,GlassPaneTerrainMesh> wanted=new HashMap<>(),drawn=new HashMap<>();Map<BlockPos,List<GlassPaneTerrainMesh>> known=new HashMap<>();
        for(int x=0;x<4;x++)for(int y=0;y<4;y++){
            BlockPos pos=origin.add(x,y,7);wanted.put(pos,goldMesh);known.put(pos,Arrays.asList(goldMesh,blueMesh));
            if(x==1&&y==1)continue;drawn.put(pos,x<2?blueMesh:goldMesh);
        }
        CompiledChunk chunk=nativeChunk(format,origin,drawn,true);
        GlassPaneTerrainProof.Result result=GlassPaneTerrainProof.inspect(chunk,origin,known,wanted);
        need(result.verified&&result.present.equals(drawn.keySet())&&result.physicalPresent.equals(drawn.keySet()),"mixed upload used all-or-nothing ownership");
        need(result.wantedPresent.size()==8&&result.wantedPhysicalPresent.equals(drawn.keySet()),"old tint was credited as current material");
        for(BlockPos pos:drawn.keySet())need(result.matched.get(pos)==drawn.get(pos),"matching old/native material not retained");
        BlockPos old=origin.add(0,0,7);Map<BlockPos,GlassPaneTerrainMesh> one=Collections.singletonMap(old,goldMesh);
        result=inspect(nativeChunk(format,origin,Collections.singletonMap(old,blueMesh),false),origin,one);
        need(result.present.isEmpty()&&result.wantedPresent.isEmpty()&&result.physicalPresent.contains(old),"UV/tint negative control accepted wrong material or lost physical ownership");
        GlassPaneTerrainMesh displaced=GlassPaneTerrainMesh.build(false,2,gold,true,format);
        result=inspect(nativeChunk(format,origin,Collections.singletonMap(old,displaced),false),origin,one);
        need(result.present.isEmpty()&&result.physicalPresent.isEmpty(),"wrong pane depth was credited");
    }
    private static void recordOrder(Sprite gold){
        VertexFormat format=extended();BlockPos origin=new BlockPos(-32,64,16),pos=origin.add(15,15,15);
        GlassPaneTerrainMesh mesh=GlassPaneTerrainMesh.build(true,1,gold,false,format);Map<BlockPos,GlassPaneTerrainMesh> wanted=Collections.singletonMap(pos,mesh);
        int[] raw=nativeChunk(format,origin,wanted,true).getState().getRawBuffer(),cycled=raw.clone();int stride=format.getIntegerSize(),quad=stride*4;
        for(int q=0;q<raw.length/quad;q++)for(int v=0;v<4;v++)
            System.arraycopy(raw,q*quad+v*stride,cycled,(raw.length/quad-1-q)*quad+((v+1)%4)*stride,stride);
        for(int i=0;i<cycled.length;i+=stride){cycled[i+format.getColorOffset()/4]=0xff99aabb;cycled[i+format.getUvOffsetById(1)/4]=0x00300020;cycled[i+stride-1]=0x12345678;}
        need(inspect(state(cycled,format),origin,wanted).wantedPresent.contains(pos),"quad sort/cyclic rotation or lighting/shader payload changed ownership");
        need(inspect(state(Arrays.copyOf(raw,raw.length-quad),format),origin,wanted).physicalPresent.isEmpty(),"incomplete physical sheet was accepted");
        int[] duplicate=raw.clone();System.arraycopy(raw,0,duplicate,quad,quad);
        need(inspect(state(duplicate,format),origin,wanted).physicalPresent.isEmpty(),"one winding duplicated to fake its opposite");
        int[] wrongUv=raw.clone();wrongUv[format.getUvOffsetById(0)/4]=Float.floatToRawIntBits(.8125F);
        GlassPaneTerrainProof.Result result=inspect(state(wrongUv,format),origin,wanted);
        need(result.wantedPresent.isEmpty()&&result.physicalPresent.contains(pos),"changed UV was credited or physical ownership lost");
    }
    private static void localUv(Sprite gold,Sprite blue){
        VertexFormat format=DefaultVertexFormats.BLOCK;BlockPos origin=new BlockPos(0,64,0),pos=origin.add(3,5,6);
        GlassPaneTerrainMesh mesh=GlassPaneTerrainMesh.build(false,0,gold,true,format);Map<BlockPos,GlassPaneTerrainMesh> wanted=Collections.singletonMap(pos,mesh);
        int[] raw=nativeChunk(format,origin,wanted,true).getState().getRawBuffer().clone();int stride=format.getIntegerSize(),uv=format.getUvOffsetById(0)/4;
        for(int at=0;at<raw.length;at+=stride){raw[at+uv]=Float.floatToRawIntBits(gold.toSingleU(Float.intBitsToFloat(raw[at+uv])));raw[at+uv+1]=Float.floatToRawIntBits(gold.toSingleV(Float.intBitsToFloat(raw[at+uv+1])));}
        TextureAtlasSprite[] sprites=new TextureAtlasSprite[raw.length/(stride*4)];Arrays.fill(sprites,gold);
        GlassPaneTerrainProof.Result result=inspect(new Chunk(new SpriteState(raw,format,sprites),true),origin,wanted);
        need(result.wantedPresent.contains(pos),"public sprite-local UV adapter did not prove tint");
        result=inspect(state(raw,format),origin,wanted);
        need(result.wantedPresent.isEmpty()&&result.wantedPhysicalPresent.contains(pos),"missing local-UV metadata invented material proof or lost physical ownership");
        Arrays.fill(sprites,blue);result=inspect(new Chunk(new SpriteState(raw,format,sprites),true),origin,wanted);
        need(result.wantedPresent.isEmpty()&&result.physicalPresent.contains(pos),"local UVs confused different tints");
    }
    private static void emptyAndUnknown(Sprite gold){
        VertexFormat format=DefaultVertexFormats.BLOCK;BlockPos origin=BlockPos.ORIGIN;
        GlassPaneTerrainMesh mesh=GlassPaneTerrainMesh.build(false,0,gold,true,format);Map<BlockPos,GlassPaneTerrainMesh> wanted=Collections.singletonMap(origin,mesh);
        need(inspect(new Chunk(null,false),origin,wanted).verified,"completed empty chunk did not remove old ownership");
        need(!inspect(new Chunk(null,true),origin,wanted).verified,"nonempty layer without records invented an empty result");
        need(!inspect(state(new int[3],format),origin,wanted).verified,"partial vertex record accepted");
        VertexFormat unsupported=new VertexFormat().addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.SHORT,VertexFormatElement.EnumUsage.POSITION,4))
                .addElement(DefaultVertexFormats.COLOR_4UB).addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.TEX_2S);
        need(!GlassPaneTerrainProof.supports(unsupported),"foreign layout allowed before publication");
        need(!inspect(state(new int[unsupported.getIntegerSize()*8],unsupported),origin,wanted).verified,"unknown layout invented ownership");
    }
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();Sprite gold=new Sprite("gold",64),blue=new Sprite("blue",128);
        nativeLayouts(gold);mixedAndHoles(gold,blue);recordOrder(gold);localUv(gold,blue);emptyAndUnknown(gold);
        System.out.println("Glass native buffer proof PASS checks="+checks+" actual Forge/vanilla emission, 28/56-byte records, large-world and region-relative coordinates, mixed owners/materials, holes, winding/sorting and UV negative controls");
    }
    private GlassPaneTerrainProofChecks(){}
}
