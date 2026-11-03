package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBridgeChair;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Eight cached rotations of the authored chair; no extra saved block-state properties. */
public final class ChairAngleModel implements IBakedModel {
    private final IBakedModel base;
    private final ChairAngleModel owner;
    private final int fixed;
    private final List<BakedQuad>[] rotations;
    private final IBakedModel[] items;
    private final ItemOverrideList overrides;
    @SuppressWarnings("unchecked") public ChairAngleModel(IBakedModel base){
        this.base=base;owner=this;fixed=-1;rotations=new List[8];items=new IBakedModel[8];items[0]=this;
        overrides=new ItemOverrideList(Collections.emptyList()){
            @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
                net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("BlockEntityTag");int angle=tag==null?0:Math.max(0,Math.min(7,tag.getInteger("ChairAngle")));
                if(items[angle]==null)items[angle]=new ChairAngleModel(owner,angle);return items[angle];
            }
        };
    }
    private ChairAngleModel(ChairAngleModel owner,int angle){this.owner=owner;base=owner.base;fixed=angle;rotations=owner.rotations;items=owner.items;overrides=ItemOverrideList.NONE;}
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
        Integer selected=state instanceof IExtendedBlockState?((IExtendedBlockState)state).getValue(BlockBridgeChair.ANGLE):null;
        int angle=fixed>=0?fixed:selected==null?0:selected;IBlockState clean=state instanceof IExtendedBlockState?((IExtendedBlockState)state).getClean():state;
        if(angle==0)return base.getQuads(clean,side,seed);
        if(side!=null)return Collections.emptyList();
        synchronized(owner){if(rotations[angle]==null){List<BakedQuad> result=new ArrayList<>();for(int face=-1;face<6;face++)for(BakedQuad quad:base.getQuads(clean,face<0?null:EnumFacing.getFront(face),seed))result.add(rotate(quad,angle));rotations[angle]=Collections.unmodifiableList(result);}return rotations[angle];}
    }
    private static BakedQuad rotate(BakedQuad quad,int steps){
        double a=Math.toRadians(steps*45),c=Math.cos(a),s=Math.sin(a);int[] data=quad.getVertexData().clone();int stride=data.length/4,position=0,normal=-1;
        for(int i=0;i<quad.getFormat().getElementCount();i++){VertexFormatElement element=quad.getFormat().getElement(i);if(element.getUsage()==VertexFormatElement.EnumUsage.POSITION)position=quad.getFormat().getOffset(i)/4;if(element.getUsage()==VertexFormatElement.EnumUsage.NORMAL)normal=quad.getFormat().getOffset(i)/4;}
        for(int v=0;v<4;v++){int i=v*stride+position;double x=Float.intBitsToFloat(data[i])-.5,z=Float.intBitsToFloat(data[i+2])-.5;data[i]=Float.floatToRawIntBits((float)(.5+c*x-s*z));data[i+2]=Float.floatToRawIntBits((float)(.5+s*x+c*z));
            if(normal>=0){i=v*stride+normal;int packed=data[i];double nx=(byte)packed,nz=(byte)(packed>>16);int rx=(int)Math.round(c*nx-s*nz),rz=(int)Math.round(s*nx+c*nz);data[i]=(packed&0xff00ff00)|(rx&255)|((rz&255)<<16);}}
        EnumFacing face=quad.getFace();double x=face.getFrontOffsetX(),z=face.getFrontOffsetZ();EnumFacing turned=EnumFacing.getFacingFromVector((float)(c*x-s*z),face.getFrontOffsetY(),(float)(s*x+c*z));
        return new BakedQuad(data,quad.getTintIndex(),turned,quad.getSprite(),quad.shouldApplyDiffuseLighting(),quad.getFormat());
    }
    @Override public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    @Override public boolean isGui3d(){return base.isGui3d();}
    @Override public boolean isBuiltInRenderer(){return false;}
    @Override public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    @Override public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    @Override public ItemOverrideList getOverrides(){return overrides;}
}
