package com.vandorlabs.client.segmented;

import com.vandorlabs.segmented.SegmentedDoorGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.*;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import java.util.*;

/** Settled closed segments belong to terrain, not the per-frame tile dispatcher. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SegmentedDoorModels {
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void bake(ModelBakeEvent event){
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean top:new boolean[]{false,true})for(boolean bottom:new boolean[]{false,true}){
            String variant="baked=true,bottom="+bottom+",facing="+facing.getName()+",top="+top;
            ModelResourceLocation key=new ModelResourceLocation("vandorlabs:segmented_door",variant);IBakedModel original=event.getModelRegistry().getObject(key);
            if(original!=null)event.getModelRegistry().putObject(key,new Model(original,facing,top,bottom));
        }
        SegmentedDoorChunks.reload();
    }
    private static final class Variant {
        final int texture;final long phase;
        Variant(int texture,long phase){this.texture=texture;this.phase=phase;}
        public int hashCode(){return texture*31+(int)(phase^(phase>>>32));}
        public boolean equals(Object other){return other instanceof Variant&&texture==((Variant)other).texture&&phase==((Variant)other).phase;}
    }
    private static com.vandorlabs.segmented.SegmentedPanelMesh.Texture texture(final TextureAtlasSprite image){return new com.vandorlabs.segmented.SegmentedPanelMesh.Texture(){public float u(double p){return image.getInterpolatedU(p);}public float v(double p){return image.getInterpolatedV(p);}};}
    private static final class Model implements IBakedModel {
        final IBakedModel original;final List<BakedQuad> quads;final EnumFacing facing;final boolean top,bottom;final Map<Variant,Model> variants=new LinkedHashMap<>(16,.75F,true);final int bytes;int retained;
        Model(IBakedModel original,EnumFacing facing,boolean top,boolean bottom){this(original,facing,top,bottom,-1,0);}
        Model(IBakedModel original,EnumFacing facing,boolean top,boolean bottom,int choice){this(original,facing,top,bottom,choice,0);}
        Model(IBakedModel original,EnumFacing facing,boolean top,boolean bottom,int choice,long phase){
            this.facing=facing;this.top=top;this.bottom=bottom;this.original=original;final List<BakedQuad> result=new ArrayList<>(original.getQuads(null,null,0));
            final TextureAtlasSprite front=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(choice<0?"vandorlabs:blocks/segmented/segmented_panel":com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice)),edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
            final int rotation=((int)(180-facing.getHorizontalAngle())/90)&3;final VertexFormat format=new VertexFormat(DefaultVertexFormats.BLOCK);final float[][] records=new float[4][8];final int[] count={0};
            com.vandorlabs.segmented.SegmentedPanelMesh.Texture image=texture(front);com.vandorlabs.segmented.SegmentedPanelMesh.Surface body=new com.vandorlabs.segmented.SegmentedPanelMesh.Surface(image,image,com.vandorlabs.client.UnifiedTextureSprites.aspect(front),false);
            com.vandorlabs.segmented.SegmentedPanelMesh.nativeCellPhase(top,bottom,phase,body,texture(edge),(x,y,z,u,v,nx,ny,nz)->{
                float[] record=records[count[0]++];record[0]=(float)(x/16);record[1]=(float)(y/16);record[2]=(float)(z/16+.5);record[3]=u;record[4]=v;record[5]=nx;record[6]=ny;record[7]=nz;if(count[0]!=4)return;count[0]=0;
                float rnx=rotation==1?nz:rotation==2?-nx:rotation==3?-nz:nx,rnz=rotation==1?-nx:rotation==2?-nz:rotation==3?nx:nz;TextureAtlasSprite sprite=nz!=0?front:edge;UnpackedBakedQuad.Builder builder=new UnpackedBakedQuad.Builder(format);builder.setTexture(sprite);builder.setQuadTint(-1);builder.setApplyDiffuseLighting(false);builder.setQuadOrientation(EnumFacing.getFacingFromVector(rnx,ny,rnz));
                for(float[] p:records)for(int e=0;e<format.getElementCount();e++){float rx=rotation==1?p[2]:rotation==2?1-p[0]:rotation==3?1-p[2]:p[0],rz=rotation==1?1-p[0]:rotation==2?1-p[2]:rotation==3?p[0]:p[2];VertexFormatElement element=format.getElement(e);switch(element.getUsage()){
                    case POSITION:builder.put(e,rx,p[1],rz,1);break;case COLOR:builder.put(e,1,1,1,1);break;case NORMAL:builder.put(e,rnx,ny,rnz,0);break;case UV:if(element.getIndex()==0)builder.put(e,p[3],p[4],0,1);else builder.put(e,0,0,0,1);break;default:builder.put(e);break;
                }}BakedQuad quad=builder.build();result.add(new BakedQuad(quad.getVertexData(),-1,quad.getFace(),sprite,false,format));
            });
            quads=Collections.unmodifiableList(result);int size=128;for(BakedQuad q:quads)size+=96+q.getVertexData().length*4;bytes=size;
        }
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){
            if(side!=null)return original.getQuads(state,side,random);if(state instanceof net.minecraftforge.common.property.IExtendedBlockState){net.minecraftforge.common.property.IExtendedBlockState extended=(net.minecraftforge.common.property.IExtendedBlockState)state;Integer choice=extended.getValue(com.vandorlabs.blocks.ProgrammableHousingState.FINISH);if(choice!=null&&choice>=0){Integer packed=extended.getValue(com.vandorlabs.segmented.SegmentedDoorBlock.UV_PHASE);int phase=packed==null?0:packed,column=(phase>>>5)&31,row=phase&31;TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice));Variant key=new Variant(choice,com.vandorlabs.segmented.SegmentedPanelMesh.tilePhase(column,row,com.vandorlabs.client.UnifiedTextureSprites.aspect(sprite)));
                synchronized(variants){Model model=variants.get(key);if(model==null){model=new Model(original,facing,top,bottom,choice,key.phase);while(!variants.isEmpty()&&retained+model.bytes>64*1024){Iterator<Model> it=variants.values().iterator();retained-=it.next().bytes;it.remove();}if(model.bytes<=64*1024){variants.put(key,model);retained+=model.bytes;}}return model.quads;}
            }}return quads;
        }
        public boolean isAmbientOcclusion(){return false;}
        public boolean isGui3d(){return true;}
        public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return original.getParticleTexture();}
        public ItemCameraTransforms getItemCameraTransforms(){return original.getItemCameraTransforms();}
        public ItemOverrideList getOverrides(){return original.getOverrides();}
    }
    private SegmentedDoorModels(){}
}
