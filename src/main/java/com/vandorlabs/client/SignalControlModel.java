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
    // World poses remain separate from the shared, prepared inventory variants.
    private final BoundedRenderCache<MountKey,List<BakedQuad>> cache=new BoundedRenderCache<>(8,512L*1024);
    private long cacheGeneration=-1;
    private final ItemOverrideList overrides=new ItemOverrideList(Collections.emptyList()){
        @Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){
            net.minecraft.nbt.NBTTagCompound tag=stack.getSubCompound("RedstoneChannelSettings");if(tag==null)return original;
            int h=tag.getInteger("BaseHeight"),t=SignalControlMount.readTilt(tag),d=tag.getInteger("TiltDirection");
            if(h==0&&t==0||!SignalControlMount.valid(h,t,d))return original;
            int mount=h+4*(t+4*d);
            ItemVariantModels.Key key=new ItemVariantModels.Key(SignalControlModel.this,null,null,0,0,mount);
            IBakedModel found=ItemVariantModels.find(key);if(found!=null)return found;
            return ItemVariantModels.retain(key,new SignalControlModel(base,block,mount),base.getItemCameraTransforms(),true);
        }
    };
    public SignalControlModel(IBakedModel base,BlockSignalControl block){this(base,block,0);}
    private SignalControlModel(IBakedModel base,BlockSignalControl block,int mount){this.base=base;this.block=block;itemMount=mount;}
    IBakedModel itemBase(){return base;}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){
        IExtendedBlockState extended=state instanceof IExtendedBlockState?(IExtendedBlockState)state:null;
        Integer value=extended!=null&&extended.getUnlistedNames().contains(BlockSignalControl.MOUNT)?extended.getValue(BlockSignalControl.MOUNT):null;
        int mount=value==null?itemMount:value;
        if(mount%16==0)return base.getQuads(state,side,seed);
        IBlockState pose=state==null?block.getDefaultState().withProperty(BlockVandorSwitch.FACING,block.hasSelectableType()?EnumFacing.UP:EnumFacing.NORTH):state;
        // Tilted faces cannot be culled against the original support or adjacent blocks.
        if(side!=null)return Collections.emptyList();
        boolean knownState=extended==null||extended.getUnlistedNames().size()==1&&extended.getUnlistedNames().contains(BlockSignalControl.MOUNT);
        MountKey key=state!=null&&knownState&&ItemVariantModels.nativeModel(base)?new MountKey(state,mount,seed):null;
        long generation=ItemVariantModels.currentGeneration();
        if(key!=null)synchronized(cache){
            if(cacheGeneration!=generation){cache.clear();cacheGeneration=generation;}
            List<BakedQuad> found=cache.get(key);if(found!=null)return found;
        }
        boolean stable=true;
            SignalControlMount transform=new SignalControlMount(pose.getValue(BlockVandorSwitch.FACING),pose.getValue(BlockVandorSwitch.ROTATION),mount%4,(mount/4)%4,mount/16,block.supportBounds(pose));
            List<BakedQuad> result=new ArrayList<>();
            for(EnumFacing source:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:base.getQuads(state,source,seed)){
                stable&=ItemVariantModels.staticQuad(quad);
                int[] data=quad.getVertexData();int stride=data.length/4;Vec3d[] points=new Vec3d[4];boolean bottom=true;
                for(int i=0;i<4;i++){
                    int at=i*stride;Vec3d original=new Vec3d(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));
                    bottom &= Math.abs(transform.depth(original))<1e-7;points[i]=transform.transform(original);
                }
                if(bottom && mount%16>0){
                    Vec3d[] projected=new Vec3d[4];for(int i=0;i<4;i++)projected[i]=transform.project(points[i]);
                    addQuad(result,quad,projected);
                    for(int i=0;i<4;i++){int next=(i+1)%4;addQuad(result,quad,new Vec3d[]{points[i],points[next],projected[next],projected[i]});}
                }else addQuad(result,quad,points);
            }
        List<BakedQuad> immutable=Collections.unmodifiableList(result);
        if(key!=null&&stable&&generation==ItemVariantModels.currentGeneration())synchronized(cache){
            if(cacheGeneration==generation)cache.put(key,immutable,128+ItemVariantModels.quadBytes(immutable));
        }
        return immutable;
    }
    static void addQuad(List<BakedQuad> result,BakedQuad source,Vec3d[] points){
        Vec3d normal=Vec3d.ZERO;
        for(int i=1;i<3 && normal.lengthSquared()<1e-14;i++)normal=points[i].subtract(points[0]).crossProduct(points[i+1].subtract(points[0]));
        if(normal.lengthSquared()<1e-14)return;normal=normal.normalize();
        int[] data=source.getVertexData().clone();int stride=data.length/4;
        int packed=((int)Math.round(normal.x*127)&255)|(((int)Math.round(normal.y*127)&255)<<8)|(((int)Math.round(normal.z*127)&255)<<16);
        for(int i=0;i<4;i++){
            int at=i*stride;data[at]=Float.floatToRawIntBits((float)points[i].x);data[at+1]=Float.floatToRawIntBits((float)points[i].y);data[at+2]=Float.floatToRawIntBits((float)points[i].z);
            if(source.getFormat().hasNormal()){
                int offset=source.getFormat().getNormalOffset();
                // Three normal bytes can share an int with shader/padding data.
                for(int axis=0;axis<3;axis++){
                    int target=at+(offset+axis)/4,shift=((offset+axis)%4)*8;
                    data[target]=(data[target]&~(255<<shift))|(((packed>>(axis*8))&255)<<shift);
                }
            }
        }
        result.add(new BakedQuad(data,source.getTintIndex(),EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z),source.getSprite(),source.shouldApplyDiffuseLighting(),source.getFormat()));
    }
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return base.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){
        ItemCameraTransforms source=base.getItemCameraTransforms();return itemMount%16==0?source:ControlItemPadding.fit(this,source);
    }
    public ItemOverrideList getOverrides(){return overrides;}
    private static final class MountKey {
        final net.minecraft.block.Block block;
        final Map<net.minecraft.block.properties.IProperty<?>,Comparable<?>> properties;
        final int mount;final long seed;
        MountKey(IBlockState state,int mount,long seed){
            // Forge 1.12 can retain the earlier getClean() state when a listed
            // property changes after an unlisted value. Its current listed map
            // is immutable and contains the actual pose supplied to getQuads.
            block=state.getBlock();properties=state.getProperties();this.mount=mount;this.seed=seed;
        }
        @Override public int hashCode(){return 31*(31*(31*System.identityHashCode(block)+properties.hashCode())+mount)+(int)(seed^(seed>>>32));}
        @Override public boolean equals(Object other){if(!(other instanceof MountKey))return false;MountKey k=(MountKey)other;return block==k.block&&mount==k.mount&&seed==k.seed&&properties.equals(k.properties);}
    }
}
