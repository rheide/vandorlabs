package com.vandorlabs.client;

import com.vandorlabs.cockpit.CockpitData;
import com.vandorlabs.client.cockpits.CockpitModels;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import org.lwjgl.util.vector.Vector3f;
import java.util.*;

/** Fit every complete off-pose in inventory after rotation and depth projection. */
public final class CockpitItemModels {
    @SubscribeEvent(priority=EventPriority.LOWEST) public void bake(ModelBakeEvent event){for(String id:CockpitData.IDS)event.getModelRegistry().putObject(new ModelResourceLocation("vandorlabs:"+id,"inventory"),new Icon(id));}
    private static final class Icon implements IBakedModel {
        final List<BakedQuad> quads=new ArrayList<>();final ItemCameraTransforms transforms;final TextureAtlasSprite particle;
        Icon(String id){
            IBakedModel raw=id.equals("chair_siderests")?CockpitModels.baked(CockpitModels.rig(id).poseResources.get("off"),-1,0):CockpitModels.baked(CockpitModels.rig(id).poseResources.get("off"));particle=raw.getParticleTexture();List<BakedQuad> source=new ArrayList<>();source.addAll(raw.getQuads(null,null,0));for(EnumFacing side:EnumFacing.values())source.addAll(raw.getQuads(null,side,0));
            net.minecraft.util.math.AxisAlignedBB b=CockpitData.get(id,0).bounds;double scale=1/Math.max(b.maxX-b.minX,Math.max(b.maxY-b.minY,b.maxZ-b.minZ));
            for(BakedQuad q:source){int[] data=q.getVertexData().clone();int stride=data.length/4;for(int v=0;v<4;v++){int o=v*stride;data[o]=Float.floatToRawIntBits((float)((Float.intBitsToFloat(data[o])-(b.minX+b.maxX)/2)*scale+.5));data[o+1]=Float.floatToRawIntBits((float)((Float.intBitsToFloat(data[o+1])-(b.minY+b.maxY)/2)*scale+.5));data[o+2]=Float.floatToRawIntBits((float)((Float.intBitsToFloat(data[o+2])-(b.minZ+b.maxZ)/2)*scale+.5));}quads.add(new BakedQuad(data,q.getTintIndex(),q.getFace(),q.getSprite(),q.shouldApplyDiffuseLighting(),q.getFormat()));}
            ItemTransformVec3f gui=new ItemTransformVec3f(new Vector3f(25,225,0),new Vector3f(),new Vector3f(1,1,1));ItemTransformVec3f held=new ItemTransformVec3f(new Vector3f(),new Vector3f(),new Vector3f(.65F,.65F,.65F));
            transforms=ControlItemPadding.fit(this,new ItemCameraTransforms(held,held,held,held,held,gui,held,held));
        }
        public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long rand){return side==null?quads:Collections.emptyList();}
        public boolean isAmbientOcclusion(){return false;}public boolean isGui3d(){return true;}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return particle;}public ItemCameraTransforms getItemCameraTransforms(){return transforms;}public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
    }
}
