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
    @SubscribeEvent public static void stitch(net.minecraftforge.client.event.TextureStitchEvent.Pre event){event.getMap().registerSprite(new net.minecraft.util.ResourceLocation("vandorlabs:blocks/core_flat"));}
    @SubscribeEvent public static void bake(ModelBakeEvent event){
        TextureAtlasSprite white=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/core_flat");
        for(String id:CoreMesh.IDS){List<BakedQuad> quads=quads(id,white);for(String variant:Arrays.asList("inventory","facing=north,formed=false","facing=south,formed=false","facing=east,formed=false","facing=west,formed=false")){ModelResourceLocation key=new ModelResourceLocation("vandorlabs:"+com.vandorlabs.blocks.FrameMachineDesign.get(id).block,variant);IBakedModel original=event.getModelRegistry().getObject(key);if(original!=null)event.getModelRegistry().putObject(key,new Model(original,quads));}}
    }
    private static List<BakedQuad> quads(String id,TextureAtlasSprite sprite){List<BakedQuad> result=new ArrayList<>();VertexFormat format=DefaultVertexFormats.ITEM;
        for(CoreMesh.Face face:CoreMesh.faces(id)){UnpackedBakedQuad.Builder builder=new UnpackedBakedQuad.Builder(format);builder.setTexture(sprite);builder.setQuadOrientation(EnumFacing.getFacingFromVector((float)face.nx,(float)face.ny,(float)face.nz));builder.setQuadTint(-1);builder.setApplyDiffuseLighting(true);
            for(int vertex=0;vertex<4;vertex++)for(int element=0;element<format.getElementCount();element++){VertexFormatElement field=format.getElement(element);switch(field.getUsage()){
                case POSITION:builder.put(element,(float)face.coordinate(vertex,0),(float)face.coordinate(vertex,1),(float)face.coordinate(vertex,2),1);break;
                case COLOR:builder.put(element,(face.rgb>>16&255)/255F,(face.rgb>>8&255)/255F,(face.rgb&255)/255F,1);break;
                case NORMAL:builder.put(element,(float)face.nx,(float)face.ny,(float)face.nz,0);break;
                case UV:builder.put(element,sprite.getInterpolatedU(8),sprite.getInterpolatedV(8),0,1);break;
                default:builder.put(element);
            }}result.add(builder.build());
        }return Collections.unmodifiableList(result);
    }
    private static final class Model implements IBakedModel {
        private final IBakedModel source;private final List<BakedQuad> quads;private final ItemCameraTransforms transforms;
        Model(IBakedModel source,List<BakedQuad> quads){this.source=source;this.quads=quads;ItemCameraTransforms base=source.getItemCameraTransforms();ItemTransformVec3f gui=new ItemTransformVec3f(base.gui.rotation,base.gui.translation,new org.lwjgl.util.vector.Vector3f(CoreMesh.ITEM_GUI_SCALE,CoreMesh.ITEM_GUI_SCALE,CoreMesh.ITEM_GUI_SCALE));transforms=new ItemCameraTransforms(base.thirdperson_left,base.thirdperson_right,base.firstperson_left,base.firstperson_right,base.head,gui,base.ground,base.fixed);}
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){return side==null?quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}
        public TextureAtlasSprite getParticleTexture(){return source.getParticleTexture();}public ItemCameraTransforms getItemCameraTransforms(){return transforms;}public ItemOverrideList getOverrides(){return source.getOverrides();}
    }
    private CoreModels(){}
}
