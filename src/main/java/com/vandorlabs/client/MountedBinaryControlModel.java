package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.*;

/** Binary controls share the throttle's filled pedestal, tilt and padded configured icons. */
public final class MountedBinaryControlModel implements IBakedModel {
    private final IBakedModel base;private final Block block;private final int itemMount,itemSize;
    // Keep recent mount variants without retaining every possible transformed mesh.
    private final Map<String,List<BakedQuad>> cache=Collections.synchronizedMap(new LinkedHashMap<String,List<BakedQuad>>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<BakedQuad>> entry){return size()>8;}
    });
    public MountedBinaryControlModel(IBakedModel base,Block block){this(base,block,0,block instanceof BlockIndustrialLever?((BlockIndustrialLever)block).defaultSize():0);}
    private MountedBinaryControlModel(IBakedModel base,Block block,int mount,int size){this.base=base;this.block=block;itemMount=mount;itemSize=size;}
    private IBlockState itemPose(){IBlockState state=block.getDefaultState();return block instanceof BlockIndustrialLever?state.withProperty(BlockIndustrialLever.FLOOR,block instanceof BlockTwinPowerLever).withProperty(BlockIndustrialLever.FACING,EnumFacing.NORTH):state.withProperty(BlockVandorSwitch.FACING,EnumFacing.UP);}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
        Integer value=state instanceof IExtendedBlockState?((IExtendedBlockState)state).getValue(MountedControlGeometry.MOUNT):null;int mount=value==null?itemMount:value;
        if(mount%16==0)return base.getQuads(state,side,seed);if(side!=null)return Collections.emptyList();
        IBlockState pose=state==null?itemPose():state;int size=state==null?itemSize:MountedControlGeometry.size(state);
        return cache.computeIfAbsent(pose.toString()+"/"+size+"/"+mount,key->{
            SignalControlMount transform=MountedControlGeometry.transform(pose,size,mount);List<BakedQuad> result=new ArrayList<>();
            for(EnumFacing source:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:base.getQuads(state,source,seed)){
                int[] data=quad.getVertexData();int stride=data.length/4;Vec3d[] points=new Vec3d[4];boolean bottom=true;
                for(int i=0;i<4;i++){int at=i*stride;Vec3d p=new Vec3d(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));bottom &= Math.abs(transform.depth(p))<1e-6;points[i]=transform.transform(p);}
                if(bottom){Vec3d[] projected=new Vec3d[4];for(int i=0;i<4;i++)projected[i]=transform.project(points[i]);SignalControlModel.addQuad(result,quad,projected);
                    for(int i=0;i<4;i++){int next=(i+1)%4;SignalControlModel.addQuad(result,quad,new Vec3d[]{points[i],points[next],projected[next],projected[i]});}
                }else SignalControlModel.addQuad(result,quad,points);
            }
            return Collections.unmodifiableList(result);
        });
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}public boolean isGui3d(){return base.isGui3d();}public boolean isBuiltInRenderer(){return false;}public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){
        ItemCameraTransforms source=base.getItemCameraTransforms();return itemMount%16==0?source:ControlItemPadding.fit(this,source);
    }
    public ItemOverrideList getOverrides(){return new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("RedstoneChannelSettings");if(tag==null)return original;
            int h=tag.getInteger("BaseHeight"),t=SignalControlMount.readTilt(tag),d=tag.getInteger("TiltDirection"),size=tag.hasKey("PowerLeverSize",3)?tag.getInteger("PowerLeverSize"):itemSize;
            if(!SignalControlMount.valid(h,t,d) || size<0 || size>1)return original;
            IBakedModel selected=base.getOverrides().handleItemState(base,stack,world,entity);return new MountedBinaryControlModel(selected,block,h+4*(t+4*d),size);
        }
    };}
}
