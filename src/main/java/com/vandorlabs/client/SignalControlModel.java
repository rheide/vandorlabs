package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Retains supplied detent artwork; extends the mounting plate before tilting every quad together. */
public final class SignalControlModel implements IBakedModel {
    private final IBakedModel base;
    private final BlockSignalControl block;
    private final int itemMount;
    private final Map<String,List<BakedQuad>> cache=new java.util.concurrent.ConcurrentHashMap<>();
    public SignalControlModel(IBakedModel base,BlockSignalControl block){this(base,block,0);}
    private SignalControlModel(IBakedModel base,BlockSignalControl block,int mount){this.base=base;this.block=block;itemMount=mount;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
        Integer value=state instanceof IExtendedBlockState?((IExtendedBlockState)state).getValue(BlockSignalControl.MOUNT):null;
        int mount=value==null?itemMount:value;
        if(mount%12==0)return base.getQuads(state,side,seed);
        IBlockState pose=state==null?block.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP):state;
        // Tilted faces cannot be culled against the original support or adjacent blocks.
        if(side!=null)return Collections.emptyList();
        return cache.computeIfAbsent(pose.getValue(BlockVandorSwitch.FACING)+"/"+pose.getValue(BlockVandorSwitch.ROTATION)+"/"+mount,k->{
            SignalControlMount transform=new SignalControlMount(pose.getValue(BlockVandorSwitch.FACING),pose.getValue(BlockVandorSwitch.ROTATION),mount%3,(mount/3)%4,mount/12,block.supportBounds(pose));
            List<BakedQuad> result=new ArrayList<>();
            for(EnumFacing source:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:base.getQuads(state,source,seed)){
                int[] data=quad.getVertexData().clone();int stride=data.length/4;Vec3d[] points=new Vec3d[4];
                for(int i=0;i<4;i++){int at=i*stride;points[i]=transform.transform(new Vec3d(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2])));data[at]=Float.floatToRawIntBits((float)points[i].x);data[at+1]=Float.floatToRawIntBits((float)points[i].y);data[at+2]=Float.floatToRawIntBits((float)points[i].z);}
                Vec3d normal=points[1].subtract(points[0]).crossProduct(points[2].subtract(points[0])).normalize();
                int packed=((int)Math.round(normal.x*127)&255)|(((int)Math.round(normal.y*127)&255)<<8)|(((int)Math.round(normal.z*127)&255)<<16);
                int normalOffset=quad.getFormat().getNormalOffset()/4;if(quad.getFormat().hasNormal())for(int i=0;i<4;i++)data[i*stride+normalOffset]=packed;
                result.add(new BakedQuad(data,quad.getTintIndex(),EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z),quad.getSprite(),quad.shouldApplyDiffuseLighting(),quad.getFormat()));
            }
            return Collections.unmodifiableList(result);
        });
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){
        ItemCameraTransforms source=base.getItemCameraTransforms();if(itemMount%12==0)return source;
        ItemTransformVec3f gui=source.gui;
        ItemTransformVec3f padded=new ItemTransformVec3f(gui.rotation,gui.translation,new org.lwjgl.util.vector.Vector3f(gui.scale.x*.65F,gui.scale.y*.65F,gui.scale.z*.65F));
        return new ItemCameraTransforms(source.thirdperson_left,source.thirdperson_right,source.firstperson_left,source.firstperson_right,source.head,padded,source.ground,source.fixed);
    }
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("RedstoneChannelSettings");if(tag==null)return original;
            int h=tag.getInteger("BaseHeight"),t=tag.getInteger("BaseTilt"),d=tag.getInteger("TiltDirection");
            return h==0&&t==0?original:SignalControlMount.valid(h,t,d)?new SignalControlModel(base,block,h+3*(t+4*d)):original;
        }
    };}
}
