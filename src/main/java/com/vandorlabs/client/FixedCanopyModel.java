package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockFixedCanopyGlass;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Immutable atlas quads are published on the client thread, then read by chunk workers. */
final class FixedCanopyModel implements IBakedModel {
    static final class Mesh {
        final List<CanopyMesh.Face> source;
        final String settings;
        final EnumFacing facing;
        final boolean centered,visible;
        final List<BakedQuad> solid,glass;
        Mesh(TileEntityCanopy tile){
            source=tile.baseFaces();settings=tile.large.key();facing=tile.facing();centered=tile.centeredPlacement;visible=tile.draws();
            List<BakedQuad> a=new ArrayList<>(),b=new ArrayList<>();
            for(CanopyMesh.Face face:visible?new CanopyRenderGeometry(source).posed(tile.block().kind,1,tile.large,0,tile.centeredPlacement):Collections.<CanopyMesh.Face>emptyList()){
                List<BakedQuad> output=CanopyFinishes.translucent(face,tile.large.material,tile.large.tint)?b:a;
                float alpha=CanopyFinishes.opacity(face,tile.large.material,tile.large.tint);
                if(face.vertices.length==4){add(output,face,new int[]{0,1,2,3},facing,alpha);if(face.doubleSided)add(output,face,new int[]{3,2,1,0},facing,alpha);}
                else for(int i=1;i<face.vertices.length-1;i++){add(output,face,new int[]{0,i,i+1,i+1},facing,alpha);if(face.doubleSided)add(output,face,new int[]{i+1,i+1,i,0},facing,alpha);}
            }
            solid=Collections.unmodifiableList(a);glass=Collections.unmodifiableList(b);
        }
        boolean matches(TileEntityCanopy tile){return visible==tile.draws()&&centered==tile.centeredPlacement&&source==tile.baseFaces()&&facing==tile.facing()&&settings.equals(tile.large.key());}
        int bytes(){int n=128;for(BakedQuad q:solid)n+=q.getVertexData().length*4+112;for(BakedQuad q:glass)n+=q.getVertexData().length*4+112;for(CanopyMesh.Face f:source)n+=128+f.vertices.length*96;return n;}
    }
    private static void add(List<BakedQuad> out,CanopyMesh.Face face,int[] indices,EnumFacing facing,float alpha){
        VertexFormat format=new VertexFormat(DefaultVertexFormats.BLOCK);
        String name=face.material.contains(":")?face.material:"vandorlabs:blocks/canopy/"+face.material;
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
        Vec3d[] p=new Vec3d[4];for(int i=0;i<4;i++)p[i]=CanopyMesh.rotate(face.vertices[indices[i]],facing);
        Vec3d normal=p[1].subtract(p[0]).crossProduct(p[2].subtract(p[0])).normalize();
        if(normal.lengthSquared()<1e-16)normal=p[2].subtract(p[0]).crossProduct(p[3].subtract(p[0])).normalize();
        UnpackedBakedQuad.Builder builder=new UnpackedBakedQuad.Builder(format);builder.setTexture(sprite);builder.setQuadTint(-1);builder.setApplyDiffuseLighting(false);
        builder.setQuadOrientation(EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z));
        for(int v=0;v<4;v++)for(int e=0;e<format.getElementCount();e++){
            VertexFormatElement element=format.getElement(e);
            switch(element.getUsage()){
                case POSITION:builder.put(e,(float)p[v].x,(float)p[v].y,(float)p[v].z,1);break;
                case COLOR:builder.put(e,1,1,1,alpha);break;
                case NORMAL:builder.put(e,(float)normal.x,(float)normal.y,(float)normal.z,0);break;
                case UV:if(element.getIndex()==0)builder.put(e,sprite.getInterpolatedU(face.uv[indices[v]][0]*16),sprite.getInterpolatedV((1-face.uv[indices[v]][1])*16),0,1);else builder.put(e,0,0,0,1);break;
                default:builder.put(e);break;
            }
        }
        BakedQuad packed=builder.build();
        // Do not retain the builder's much larger unpacked float arrays.
        out.add(new BakedQuad(packed.getVertexData(),-1,packed.getFace(),sprite,false,format));
    }
    private final IBakedModel delegate;
    FixedCanopyModel(IBakedModel delegate){this.delegate=delegate;}
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){
        if(side!=null||!(state instanceof IExtendedBlockState))return Collections.emptyList();
        Object value=((IExtendedBlockState)state).getValue(BlockFixedCanopyGlass.MESH);
        if(!(value instanceof Mesh))return Collections.emptyList();Mesh mesh=(Mesh)value;
        return net.minecraftforge.client.MinecraftForgeClient.getRenderLayer()==BlockRenderLayer.TRANSLUCENT?mesh.glass:mesh.solid;
    }
    public boolean isAmbientOcclusion(){return false;}
    public boolean isGui3d(){return true;}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return delegate.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return delegate.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return delegate.getOverrides();}
}
