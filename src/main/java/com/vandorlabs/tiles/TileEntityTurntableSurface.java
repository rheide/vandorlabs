package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockTurntableSurface;
import com.vandorlabs.surface.*;
import com.vandorlabs.turntable.TurntableTextures;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Saved surface appearance belongs to the pad; controller claims never replace its selected artwork. */
public final class TileEntityTurntableSurface extends TileEntity implements net.minecraft.util.ITickable {
    public BlockPos owner;
    public UUID ownerKey;
    public String surfaceOn=TurntableTextures.IDS[1],surfaceOff=TurntableTextures.IDS[0];
    public int textureLayout=1;
    private boolean configured;
    private BlockPos groupOffset=BlockPos.ORIGIN;
    private int groupSize=1,groupNormal=1;
    private SquareLayout cachedGroup;
    public SurfaceController claimedOwner(){
        if(world==null||owner==null||ownerKey==null||!world.isBlockLoaded(owner))return null;
        TileEntity raw=world.getTileEntity(owner);
        if(!(raw instanceof SurfaceController))return null;SurfaceController controller=(SurfaceController)raw;
        return ownerKey.equals(controller.surfaceKey())&&controller.ownsSurface(pos)?controller:null;
    }
    public TileEntityTurntableController controller(){SurfaceController c=claimedOwner();return c instanceof TileEntityTurntableController&&((TileEntityTurntableController)c).enabled?(TileEntityTurntableController)c:null;}
    public boolean canClaim(BlockPos controller,UUID key){
        if(owner==null||owner.equals(controller)&&Objects.equals(ownerKey,key))return true;
        return world!=null&&world.isBlockLoaded(owner)&&claimedOwner()==null;
    }
    public boolean locked(){if(owner!=null&&world!=null&&!world.isBlockLoaded(owner))return true;SurfaceController c=claimedOwner();return c!=null&&c.surfaceLocked();}
    public void bind(BlockPos controller,UUID key){if(!canClaim(controller,key))throw new IllegalArgumentException("Programmable Surface is claimed by another controller");owner=controller.toImmutable();ownerKey=key;changed();}
    public void bind(TileEntityTurntableController controller){bind(controller.getPos(),controller.key);if(controller.hasLegacySurfaceAppearance())migrateLegacy(controller.surfaceOn,controller.surfaceOff);}
    public void release(){owner=null;ownerKey=null;changed();scheduleGroup();}
    public boolean appearanceConfigured(){return configured;}
    public void migrateLegacy(String on,String off){if(configured)return;surfaceOn=TurntableTextures.normalize(on,true);surfaceOff=TurntableTextures.normalize(off,false);textureLayout=0;configured=true;changed();}
    public boolean active(){SurfaceController controller=claimedOwner();return controller!=null&&controller.surfaceActive(pos);}
    public String selectedTexture(){return active()?surfaceOn:surfaceOff;}
    private EnumFacing normal(){return world!=null&&world.isBlockLoaded(pos)&&world.getBlockState(pos).getBlock() instanceof BlockTurntableSurface?world.getBlockState(pos).getValue(BlockTurntableSurface.FACING):EnumFacing.UP;}
    public SquareLayout appearanceLayout(){
        SurfaceController controller=claimedOwner();SquareLayout claimed=controller==null?null:controller.surfaceLayout(pos);if(claimed!=null)return claimed;
        if(cachedGroup==null){
            EnumFacing face=EnumFacing.getFront(groupNormal);if(face!=normal())return SquareLayout.single(pos,normal());
            com.vandorlabs.turntable.TurntableGeometry.Face basis=com.vandorlabs.turntable.TurntableGeometry.Face.valueOf(face.name());BlockPos origin=pos.add(groupOffset);Set<BlockPos> cells=new HashSet<>();
            EnumFacing u=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz),v=EnumFacing.getFacingFromVector(basis.vx,basis.vy,basis.vz);
            for(int y=0;y<groupSize;y++)for(int x=0;x<groupSize;x++)cells.add(origin.offset(u,x).offset(v,y));
            try{cachedGroup=SquareLayout.of(cells,face);}catch(IllegalArgumentException invalid){cachedGroup=SquareLayout.single(pos,normal());}
        }
        return cachedGroup;
    }
    public void setGroup(SquareLayout layout){
        groupOffset=layout.cell(0,0,0).subtract(pos);groupSize=layout.size;groupNormal=layout.normal.getIndex();cachedGroup=layout;changed();
    }
    public void scheduleGroup(){DeferredTileLoad.schedule(this,this::refreshGroup);}
    public void refreshGroup(){
        if(world==null||world.isRemote||!world.isBlockLoaded(pos)||!(world.getBlockState(pos).getBlock() instanceof BlockTurntableSurface))return;
        SurfaceController controller=claimedOwner();if(controller!=null){SquareLayout layout=controller.surfaceLayout(pos);if(layout!=null)setGroup(layout);return;}
        Set<BlockPos> members;
        try{members=SquareLayout.connected(world,pos,p->{if(!world.isBlockLoaded(p))return true;TileEntity raw=world.getTileEntity(p);return !(raw instanceof TileEntityTurntableSurface)||((TileEntityTurntableSurface)raw).claimedOwner()==null;});}catch(IllegalArgumentException unloaded){return;}
        SquareLayout layout=null;try{layout=SquareLayout.of(members,normal());}catch(IllegalArgumentException irregular){}
        for(BlockPos cell:members){TileEntity raw=world.getTileEntity(cell);if(raw instanceof TileEntityTurntableSurface)((TileEntityTurntableSurface)raw).setGroup(layout==null?SquareLayout.single(cell,normal()):layout);}
    }
    public void configure(String on,String off,int mode,net.minecraft.entity.player.EntityPlayerMP actor){
        if(world==null||world.isRemote||mode<0||mode>2||!TurntableTextures.valid(on)||!TurntableTextures.valid(off))throw new IllegalArgumentException("Invalid surface appearance");
        SquareLayout layout=appearanceLayout();List<TileEntityTurntableSurface> surfaces=new ArrayList<>();
        for(BlockPos cell:layout.cells){if(!world.isBlockLoaded(cell))throw new IllegalArgumentException("Load the complete surface before configuring it");com.vandorlabs.vehicle.VehicleService.permission(actor,cell);TileEntity raw=world.getTileEntity(cell);if(!(raw instanceof TileEntityTurntableSurface))throw new IllegalArgumentException("Surface layout changed");surfaces.add((TileEntityTurntableSurface)raw);}
        for(TileEntityTurntableSurface surface:surfaces){surface.surfaceOn=on;surface.surfaceOff=off;surface.textureLayout=mode;surface.configured=true;surface.setGroup(layout);}
    }
    private void changed(){markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.markBlockRangeForRenderUpdate(pos,pos);}}
    @Override public void onLoad(){super.onLoad();cachedGroup=null;scheduleGroup();}
    @Override public void update(){if(world!=null&&!world.isRemote&&world.getTotalWorldTime()%20==0&&owner!=null&&world.isBlockLoaded(owner)&&claimedOwner()==null)release();}
    @Override public boolean shouldRefresh(World world,BlockPos pos,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);if(owner!=null&&ownerKey!=null){tag.setLong("Owner",owner.toLong());tag.setUniqueId("OwnerKey",ownerKey);}else{tag.removeTag("Owner");tag.removeTag("OwnerKeyMost");tag.removeTag("OwnerKeyLeast");}tag.setString("SurfaceOn",surfaceOn);tag.setString("SurfaceOff",surfaceOff);tag.setInteger("SurfaceLayout",textureLayout);tag.setBoolean("SurfaceAppearanceConfigured",configured);tag.setLong("SurfaceGroupOffset",groupOffset.toLong());tag.setInteger("SurfaceGroupSize",groupSize);tag.setInteger("SurfaceGroupNormal",groupNormal);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);owner=tag.hasKey("Owner",4)?BlockPos.fromLong(tag.getLong("Owner")):null;ownerKey=tag.hasUniqueId("OwnerKey")?tag.getUniqueId("OwnerKey"):null;surfaceOn=TurntableTextures.normalize(tag.getString("SurfaceOn"),true);surfaceOff=TurntableTextures.normalize(tag.getString("SurfaceOff"),false);textureLayout=tag.hasKey("SurfaceLayout",3)?Math.max(0,Math.min(2,tag.getInteger("SurfaceLayout"))):1;configured=tag.getBoolean("SurfaceAppearanceConfigured");groupOffset=BlockPos.fromLong(tag.getLong("SurfaceGroupOffset"));groupSize=Math.max(1,Math.min(16,tag.getInteger("SurfaceGroupSize")));groupNormal=Math.max(0,Math.min(5,tag.hasKey("SurfaceGroupNormal",3)?tag.getInteger("SurfaceGroupNormal"):1));if(Math.abs(groupOffset.getX())>16||Math.abs(groupOffset.getY())>16||Math.abs(groupOffset.getZ())>16){groupOffset=BlockPos.ORIGIN;groupSize=1;}cachedGroup=null;}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,1,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);}
    @Override public AxisAlignedBB getRenderBoundingBox(){SquareLayout layout=appearanceLayout();return pos.equals(layout.root)?new AxisAlignedBB(layout.pivot.x-17,layout.pivot.y-17,layout.pivot.z-17,layout.pivot.x+17,layout.pivot.y+17,layout.pivot.z+17):new AxisAlignedBB(pos);}
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
}
