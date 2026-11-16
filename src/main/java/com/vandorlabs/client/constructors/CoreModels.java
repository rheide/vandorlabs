package com.vandorlabs.client.constructors;

import com.vandorlabs.geometry.CoreMesh;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Real hollow Core geometry for world blocks and their padded inventory models. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class CoreModels {
    private static final class Sprites {final TextureAtlasSprite flat,legacy;Sprites(TextureAtlasSprite flat,TextureAtlasSprite legacy){this.flat=flat;this.legacy=legacy;}}
    private static Map<String,Sprites> sprites=Collections.emptyMap();
    @SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){event.getMap().registerSprite(new net.minecraft.util.ResourceLocation("vandorlabs:blocks/core_flat"));for(String id:CoreMesh.IDS)event.getMap().registerSprite(new net.minecraft.util.ResourceLocation(CoreMesh.legacyTexture(id)));}
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        TextureAtlasSprite white=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/core_flat");
        Map<String,Sprites> next=new LinkedHashMap<>();
        for(String id:CoreMesh.IDS){TextureAtlasSprite legacy=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(CoreMesh.legacyTexture(id));next.put(id,new Sprites(white,legacy));List<BakedQuad> quads=quads(id,white,legacy);for(String variant:Arrays.asList("inventory","facing=north,formed=false","facing=south,formed=false","facing=east,formed=false","facing=west,formed=false")){ModelResourceLocation key=new ModelResourceLocation("vandorlabs:"+com.vandorlabs.blocks.FrameMachineDesign.get(id).block,variant);IBakedModel original=event.getModelRegistry().getObject(key);if(original!=null)event.getModelRegistry().putObject(key,new Model(original,quads));}}
        sprites=Collections.unmodifiableMap(next);
    }
    private static List<BakedQuad> quads(String id,TextureAtlasSprite flat,TextureAtlasSprite legacy){List<BakedQuad> result=new ArrayList<>();VertexFormat format=DefaultVertexFormats.ITEM;CoreMesh.Palette palette=CoreMesh.palette(id);
        for(CoreMesh.Face face:CoreMesh.faces(id)){int rgb=face.rgb(palette);TextureAtlasSprite sprite=face.textured?legacy:flat;UnpackedBakedQuad.Builder builder=new UnpackedBakedQuad.Builder(format);builder.setTexture(sprite);builder.setQuadOrientation(EnumFacing.getFacingFromVector((float)face.nx,(float)face.ny,(float)face.nz));builder.setQuadTint(-1);builder.setApplyDiffuseLighting(face.textured);
            for(int vertex=0;vertex<4;vertex++)for(int element=0;element<format.getElementCount();element++){VertexFormatElement field=format.getElement(element);switch(field.getUsage()){
                case POSITION:builder.put(element,(float)face.coordinate(vertex,0),(float)face.coordinate(vertex,1),(float)face.coordinate(vertex,2),1);break;
                case COLOR:builder.put(element,(rgb>>16&255)/255F,(rgb>>8&255)/255F,(rgb&255)/255F,1);break;
                case NORMAL:builder.put(element,(float)face.nx,(float)face.ny,(float)face.nz,0);break;
                case UV:builder.put(element,sprite.getInterpolatedU(face.u(vertex)*16),sprite.getInterpolatedV(face.v(vertex)*16),0,1);break;
                default:builder.put(element);
            }}result.add(builder.build());
        }return Collections.unmodifiableList(result);
    }
    /** Shared voxel-layer surface, fullbright while fabricating; no global cut-plane artwork. */
    public static void emit(net.minecraft.client.renderer.BufferBuilder buffer,String id,double progress,int light){Sprites atlas=sprites.get(id);if(atlas==null)return;CoreMesh.Palette palette=CoreMesh.palette(id);for(CoreMesh.Face face:CoreMesh.faces(id,progress)){TextureAtlasSprite sprite=face.textured?atlas.legacy:atlas.flat;int rgb=face.fabricationRGB(palette);for(int vertex=0;vertex<4;vertex++)buffer.pos(face.coordinate(vertex,0),face.coordinate(vertex,1),face.coordinate(vertex,2)).color(rgb>>16&255,rgb>>8&255,rgb&255,255).tex(sprite.getInterpolatedU(face.u(vertex)*16),sprite.getInterpolatedV(face.v(vertex)*16)).lightmap(light&65535,light>>>16).endVertex();}}
    private static final class Model implements IBakedModel {
        private final IBakedModel source;private final List<BakedQuad> quads;private final ItemCameraTransforms transforms;
        Model(IBakedModel source,List<BakedQuad> quads){this.source=source;this.quads=quads;ItemCameraTransforms base=source.getItemCameraTransforms();ItemTransformVec3f gui=new ItemTransformVec3f(base.gui.rotation,base.gui.translation,new org.lwjgl.util.vector.Vector3f(CoreMesh.ITEM_GUI_SCALE,CoreMesh.ITEM_GUI_SCALE,CoreMesh.ITEM_GUI_SCALE));transforms=new ItemCameraTransforms(base.thirdperson_left,base.thirdperson_right,base.firstperson_left,base.firstperson_right,base.head,gui,base.ground,base.fixed);}
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){return side==null?quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return source.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return transforms;}public ItemOverrideList getOverrides(){return source.getOverrides();}
    }
    private CoreModels(){}
}
