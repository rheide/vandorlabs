package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.PanelDepth;
import com.vandorlabs.blocks.PanelJoins;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.*;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Immutable frame and pane records are consumed by Minecraft's ordinary chunk builder. */
public final class GlassFrameModel implements IBakedModel {
    private static final Map<TextureAtlasSprite,Boolean> BLENDED=new IdentityHashMap<>();
    static void clear(){BLENDED.clear();}
    private static boolean blended(TextureAtlasSprite sprite){
        Boolean cached=BLENDED.get(sprite);if(cached!=null)return cached;
        boolean blended=sprite.getFrameCount()==0;
        for(int frame=0;frame<sprite.getFrameCount()&&!blended;frame++){
            int[][] levels=sprite.getFrameTextureData(frame);if(levels==null)continue;
            for(int[] pixels:levels){if(pixels==null)continue;for(int pixel:pixels){int alpha=pixel>>>24;if(alpha>0&&alpha<255){blended=true;break;}}if(blended)break;}
        }
        BLENDED.put(sprite,blended);return blended;
    }
    static final class Mesh {
        final List<BakedQuad> quads,cutout,translucent;
        final VertexFormat format;
        Mesh(List<BakedQuad> quads,VertexFormat format,boolean blend){
            this.quads=Collections.unmodifiableList(quads);this.format=format;
            List<BakedQuad> solid=new ArrayList<>(),transparent=new ArrayList<>();
            for(BakedQuad quad:quads)(blend&&blended(quad.getSprite())?transparent:solid).add(quad);
            cutout=Collections.unmodifiableList(solid);translucent=Collections.unmodifiableList(transparent);
        }
        int bytes(){int size=192;for(BakedQuad quad:quads)size+=256+quad.getVertexData().length*4;return size;}
    }
    static Mesh build(TileEntityProgrammableGlass tile,IBlockState state){
        return build(tile,state,DefaultVertexFormats.BLOCK);
    }
    static Mesh build(TileEntityProgrammableGlass tile,IBlockState state,VertexFormat format){
        BlockProgrammableWall.FlatCorner corner=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        if(corner==null){Capture capture=new Capture(state.getValue(BlockGlassWall.ROTATED)?3:0,PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH)),true,format);GlassFrameMesh.emit(tile,capture);return capture.finish();}
        int turns=((int)(180-PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos()).getHorizontalAngle())/90)&3;
        // Released corner/T rails disable blending and use the owner's ordinary light.
        Capture capture=new Capture(turns,0,false,format);
        BlockProgrammableWall.FlatCorner bar=PanelJoins.flatBar(state,tile.getWorld(),tile.getPos());
        if(bar==null)emitCorner(tile,state,corner,capture);
        else{
            emitCorner(tile,state,corner,new Part(capture,0,0,bar.frontArm,true));
            emitCorner(tile,state,null,new Part(capture,3,bar.frontArm-PanelJoins.flatNear(state,tile.getWorld(),tile.getPos()),bar.frontArm,false));
        }
        return capture.finish();
    }
    private static void emitCorner(TileEntityProgrammableGlass tile,IBlockState state,BlockProgrammableWall.FlatCorner corner,BufferBuilder buffer){
        net.minecraft.client.renderer.texture.TextureMap atlas=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks();
        TEProgrammableGlass.meshes(tile,state,corner).pane.emitFlatFrame(buffer,
                atlas.getAtlasSprite(tile.frameAppearance.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal")),
                atlas.getAtlasSprite(com.vandorlabs.tiles.GlassMaterials.texture(tile.getMaterial(),tile.getShade(),tile.getSize(),tile.getTint())),
                atlas.getAtlasSprite(tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side")),
                atlas.getAtlasSprite(tile.frameAppearance.exterior("vandorlabs:blocks/programmable_glass/metal_side")));
    }
    /** Apply the same float-record transforms and clipping as FlatPanelBarRenderer.part. */
    private static final class Part extends BufferBuilder implements GlassFrameMesh.Sprites {
        private static final com.vandorlabs.render.PanelVertexEmitter.RawSink<Capture> EMIT=(out,x,y,z,u,v,nx,ny,nz)->out.pos(x,y,z).tex(u,v).normal(nx,ny,nz).endVertex();
        private final Capture out;private final int turns;private final double depth;private final boolean outside;private final double[] bounds;
        private final float[] points=new float[32];private int vertex;
        Part(Capture out,int turns,double depth,double near,boolean outside){super(16);this.out=out;this.turns=turns;this.depth=depth;this.outside=outside;bounds=new double[]{near-.02,near+4+.02,-1,17,-1,17};}
        public void texture(TextureAtlasSprite sprite){out.texture(sprite);}
        @Override public BufferBuilder pos(double x,double y,double z){int at=vertex*8;points[at]=(float)x;points[at+1]=(float)y;points[at+2]=(float)z;return this;}
        @Override public BufferBuilder tex(double u,double v){int at=vertex*8;points[at+3]=(float)u;points[at+4]=(float)v;return this;}
        @Override public BufferBuilder normal(float x,float y,float z){int at=vertex*8;points[at+5]=x;points[at+6]=y;points[at+7]=z;return this;}
        @Override public void endVertex(){if(++vertex==4){float[] transformed=com.vandorlabs.render.PanelMeshTransform.transform(points,turns,depth,0,1);com.vandorlabs.render.PanelMeshTransform.box(EMIT,out,transformed,0,32,bounds,outside);vertex=0;}}
    }
    private static final class Capture extends BufferBuilder implements GlassFrameMesh.Sprites {
        private final VertexFormat format;
        private final List<BakedQuad> quads=new ArrayList<>();
        private final double[][] points=new double[4][8];
        private final int turns;
        private final boolean blend;
        private final double depth;
        private TextureAtlasSprite sprite;
        private int vertex;
        Capture(int turns,double depth,boolean blend,VertexFormat format){super(16);this.turns=turns;this.depth=depth;this.blend=blend;this.format=new VertexFormat(format);}
        public void texture(TextureAtlasSprite sprite){this.sprite=sprite;}
        @Override public BufferBuilder pos(double x,double y,double z){
            // TESR meshes first retain float positions and UVs before the world transform.
            double a=(float)x/16D,c=((float)z+depth)/16D;
            points[vertex][0]=turns==1?c:turns==2?1-a:turns==3?1-c:a;points[vertex][1]=(float)y/16D;points[vertex][2]=turns==1?1-a:turns==2?1-c:turns==3?a:c;return this;
        }
        @Override public BufferBuilder tex(double u,double v){points[vertex][3]=(float)u;points[vertex][4]=(float)v;return this;}
        @Override public BufferBuilder color(int r,int g,int b,int a){return this;}
        @Override public BufferBuilder lightmap(int sky,int block){return this;}
        @Override public BufferBuilder normal(float x,float y,float z){
            points[vertex][5]=turns==1?z:turns==2?-x:turns==3?-z:x;points[vertex][6]=y;points[vertex][7]=turns==1?-x:turns==2?-z:turns==3?x:z;return this;
        }
        @Override public void endVertex(){if(++vertex==4){add(false);add(true);vertex=0;}}
        private void add(boolean reverse){
            if(sprite==null)throw new IllegalStateException("Glass frame face has no atlas sprite");
            int stride=format.getIntegerSize(),color=format.getColorOffset()/4,uv=format.getUvOffsetById(0)/4;
            final int[] data=new int[4*stride];
            for(int i=0;i<4;i++){
                double[] point=points[reverse?(4-i)&3:i];int at=i*stride;
                for(int axis=0;axis<3;axis++)data[at+axis]=Float.floatToRawIntBits((float)point[axis]);
                data[at+color]=-1;data[at+uv]=Float.floatToRawIntBits((float)point[3]);data[at+uv+1]=Float.floatToRawIntBits((float)point[4]);
                if(format.hasNormal())data[at+format.getNormalOffset()/4]=((byte)(int)(point[5]*127)&255)|(((byte)(int)(point[6]*127)&255)<<8)|(((byte)(int)(point[7]*127)&255)<<16);
            }
            EnumFacing face=EnumFacing.getFacingFromVector((float)points[0][5],(float)points[0][6],(float)points[0][7]);
            // Keep the immutable build format even when OptiFine replaces its global format.
            quads.add(new BakedQuad(data,-1,face,sprite,false,format){@Override public int[] getVertexData(){return data;}});
        }
        Mesh finish(){if(vertex!=0)throw new IllegalStateException("Incomplete glass frame quad");return new Mesh(quads,format,blend);}
    }
    private final IBakedModel delegate;
    public GlassFrameModel(IBakedModel delegate){this.delegate=delegate;}
    private static Mesh currentFrame(Object value){
        if(!(value instanceof Mesh))return null;
        Mesh mesh=(Mesh)value;
        // The raw native writer cannot consume a stale vertex stride. The
        // frame manager refreshes snapshots on the client thread after reload.
        return mesh.format.equals(DefaultVertexFormats.BLOCK)?mesh:null;
    }
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){
        if(state==null)return delegate.getQuads(null,side,random);
        BlockRenderLayer layer=MinecraftForgeClient.getRenderLayer();
        if(layer==null){
            List<BakedQuad> stock=delegate.getQuads(state,side,random);
            if(side!=null||!(state instanceof IExtendedBlockState)||WorldSceneBenchmark.omitGlazing())return stock;
            IExtendedBlockState extended=(IExtendedBlockState)state;
            // The unlayered block-damage model needs the pane too. This read
            // does not acknowledge a native chunk build or change ownership.
            GlassPaneTerrainMesh pane=GlassPaneTerrain.peekMesh(extended.getValue(BlockProgrammableGlass.PANE_MESH));
            if(pane==null)return stock;
            Mesh mesh=currentFrame(extended.getValue(BlockProgrammableGlass.FRAME_MESH));
            List<BakedQuad> frame=mesh==null?Collections.emptyList():mesh.quads;
            List<BakedQuad> damaged=new ArrayList<>(stock.size()+frame.size()+pane.quads.size());
            damaged.addAll(stock);damaged.addAll(frame);damaged.addAll(pane.quads);return damaged;
        }
        if(layer==BlockRenderLayer.SOLID)return delegate.getQuads(state,side,random);
        // OptiFine remaps CUTOUT_MIPPED to CUTOUT when mipmaps are disabled.
        if(layer!=BlockRenderLayer.CUTOUT_MIPPED&&layer!=BlockRenderLayer.CUTOUT&&layer!=BlockRenderLayer.TRANSLUCENT)return Collections.emptyList();
        if(side!=null||!(state instanceof IExtendedBlockState)||WorldSceneBenchmark.omitGlazing())return Collections.emptyList();
        IExtendedBlockState extended=(IExtendedBlockState)state;
        Mesh mesh=currentFrame(extended.getValue(BlockProgrammableGlass.FRAME_MESH));
        // Authored antialiasing and partially transparent rail finishes retain blending.
        if(layer!=BlockRenderLayer.TRANSLUCENT)return mesh==null?Collections.emptyList():mesh.cutout;
        GlassPaneTerrainMesh pane=GlassPaneTerrain.mesh(extended.getValue(BlockProgrammableGlass.PANE_MESH));
        List<BakedQuad> frame=mesh==null?Collections.emptyList():mesh.translucent;
        if(pane==null)return frame;
        if(frame.isEmpty())return pane.quads;
        List<BakedQuad> joined=new ArrayList<>(frame.size()+pane.quads.size());
        joined.addAll(frame);joined.addAll(pane.quads);return joined;
    }
    public boolean isAmbientOcclusion(){return delegate.isAmbientOcclusion();}
    @Override public boolean isAmbientOcclusion(IBlockState state){
        if(state instanceof IExtendedBlockState){
            IExtendedBlockState extended=(IExtendedBlockState)state;
            BlockRenderLayer layer=MinecraftForgeClient.getRenderLayer();
            if(currentFrame(extended.getValue(BlockProgrammableGlass.FRAME_MESH))!=null
                    ||(layer==null||layer==BlockRenderLayer.TRANSLUCENT)
                    &&GlassPaneTerrain.hasMesh(extended.getValue(BlockProgrammableGlass.PANE_MESH)))return false;
        }
        return delegate.isAmbientOcclusion(state);
    }
    public boolean isGui3d(){return delegate.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return delegate.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return delegate.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return delegate.getOverrides();}
}
