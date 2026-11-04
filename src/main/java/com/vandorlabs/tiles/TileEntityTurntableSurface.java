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
    /** Inert carried-view link, excluded from saves and ownership comparisons. */
    public com.vandorlabs.gear.GearTileEntity carriedArm;
    public BlockPos owner;
    public UUID ownerKey;
    public String surfaceOn=TurntableTextures.IDS[1],surfaceOff=TurntableTextures.IDS[0];
    public int textureLayout=2;
    private boolean configured,appearanceReady;
    public boolean appearanceReady(){return world==null||!world.isRemote||appearanceReady;}
    private BlockPos groupOffset=BlockPos.ORIGIN;
    private int groupSize=1,groupHeight=1,groupNormal=1;
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
    public void release(){owner=null;ownerKey=null;setGroup(SquareLayout.single(pos,normal()));changed();scheduleGroup();}
    public boolean appearanceConfigured(){return configured;}
    public void migrateLegacy(String on,String off){if(configured)return;surfaceOn=TurntableTextures.normalize(on,true);surfaceOff=TurntableTextures.normalize(off,false);textureLayout=0;configured=true;changed();}
    public boolean active(){SurfaceController controller=claimedOwner();if(controller!=null)return controller.surfaceActive(pos);if(owner!=null||ownerKey!=null)return false;Boolean arm=com.vandorlabs.gear.ArmSurfaceActivity.activity(this);return Boolean.TRUE.equals(arm);}
    /** Saved on/off artwork defines boundaries independently of controller power. */
    public boolean sameArtwork(TileEntity other){return other instanceof TileEntityTurntableSurface&&surfaceOn.equals(((TileEntityTurntableSurface)other).surfaceOn)&&surfaceOff.equals(((TileEntityTurntableSurface)other).surfaceOff);}
    public String selectedTexture(){return active()?surfaceOn:surfaceOff;}
    private EnumFacing normal(){return world!=null&&world.isBlockLoaded(pos)&&world.getBlockState(pos).getBlock() instanceof BlockTurntableSurface?world.getBlockState(pos).getValue(BlockTurntableSurface.FACING):EnumFacing.UP;}
    public SquareLayout appearanceLayout(){SquareLayout layout=joinedLayout();return textureLayout==2&&layout.width!=layout.height?SquareLayout.single(pos,normal()):layout;}
    private SquareLayout joinedLayout(){
        SurfaceController controller=claimedOwner();if(controller instanceof TileEntityTurntableController&&!((TileEntityTurntableController)controller).enabled)return SquareLayout.single(pos,normal());SquareLayout claimed=controller==null?null:controller.surfaceLayout(pos);if(claimed!=null)return availableRoot(claimed);
        if(cachedGroup==null){
            EnumFacing face=EnumFacing.getFront(groupNormal);if(face!=normal())return SquareLayout.single(pos,normal());
            com.vandorlabs.turntable.TurntableGeometry.Face basis=com.vandorlabs.turntable.TurntableGeometry.Face.valueOf(face.name());BlockPos origin=pos.add(groupOffset);Set<BlockPos> cells=new HashSet<>();
            EnumFacing u=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz),v=EnumFacing.getFacingFromVector(basis.vx,basis.vy,basis.vz);
            for(int y=0;y<groupHeight;y++)for(int x=0;x<groupSize;x++)cells.add(origin.offset(u,x).offset(v,y));
            try{cachedGroup=SquareLayout.rectangle(cells,face);}catch(IllegalArgumentException invalid){cachedGroup=SquareLayout.single(pos,normal());}
        }
        return availableRoot(cachedGroup);
    }
    private SquareLayout checkedSnapshotGroup,snapshotAppearance;private int checkedSnapshotRevision=-1;
    private SquareLayout availableRoot(SquareLayout layout){
        if(world instanceof com.vandorlabs.vehicle.VehicleWorld&&layout.cells.size()>1){
            if(checkedSnapshotGroup!=layout||checkedSnapshotRevision!=((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision){
                checkedSnapshotRevision=((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision;checkedSnapshotGroup=layout;snapshotAppearance=layout;
                for(int u=0;u<layout.width;u++)for(int v=0;v<layout.height;v++){
                    BlockPos at=layout.cell(u,v,0);IBlockState state=world.getBlockState(at);
                    if(!(state.getBlock() instanceof BlockTurntableSurface)||state.getValue(BlockTurntableSurface.FACING)!=layout.normal
                            ||!(world.getTileEntity(at) instanceof TileEntityTurntableSurface))snapshotAppearance=SquareLayout.single(pos,normal());
                }
            }
            return snapshotAppearance;
        }
        // A removal packet may arrive before the replacement group data. Never
        // depend on a missing shared renderer while that update is in flight.
        if(world!=null&&!pos.equals(layout.root)&&world.isBlockLoaded(layout.root)&&!(world.getTileEntity(layout.root) instanceof TileEntityTurntableSurface))return SquareLayout.single(pos,normal());
        return layout;
    }
    private boolean groupPending;
    public void setGroup(SquareLayout layout){
        BlockPos next=layout.cell(0,0,0).subtract(pos);boolean different=!next.equals(groupOffset)||groupSize!=layout.width||groupHeight!=layout.height||groupNormal!=layout.normal.getIndex();
        groupOffset=next;groupSize=layout.width;groupHeight=layout.height;groupNormal=layout.normal.getIndex();cachedGroup=layout;if(different)changed();
    }
    public void scheduleGroup(){if(world==null||world.isRemote||groupPending)return;groupPending=true;DeferredTileLoad.schedule(this,()->{if(groupPending){groupPending=false;refreshGroup();}});}
    public void refreshGroup(){
        if(world==null||world.isRemote||!world.isBlockLoaded(pos)||!(world.getBlockState(pos).getBlock() instanceof BlockTurntableSurface))return;
        SurfaceController controller=claimedOwner();if(controller instanceof TileEntityTurntableController&&!((TileEntityTurntableController)controller).enabled){setGroup(SquareLayout.single(pos,normal()));return;}if(controller!=null){
            if(controller instanceof com.vandorlabs.copier.CopyControllerTile){
                com.vandorlabs.copier.CopyControllerTile copier=(com.vandorlabs.copier.CopyControllerTile)controller;
                for(EnumFacing side:EnumFacing.values())if(side.getAxis()!=normal().getAxis()){
                    BlockPos next=pos.offset(side);if(!world.isBlockLoaded(next)||!copier.sameSurfaceSide(pos,next))continue;
                    IBlockState state=world.getBlockState(next);TileEntity raw=world.getTileEntity(next);
                    if(state.getBlock() instanceof BlockTurntableSurface&&state.getValue(BlockTurntableSurface.FACING)==normal()&&raw instanceof TileEntityTurntableSurface&&sameArtwork(raw)&&((TileEntityTurntableSurface)raw).claimedOwner()==null){copier.surfaceChanged();return;}
                }
            }
            SquareLayout layout=controller.surfaceLayout(pos);if(layout!=null)setGroup(layout);return;
        }
        Set<BlockPos> members;
        try{members=SquareLayout.connected(world,pos,p->{if(!world.isBlockLoaded(p))return true;TileEntity raw=world.getTileEntity(p);return !(raw instanceof TileEntityTurntableSurface)||((TileEntityTurntableSurface)raw).claimedOwner()==null;});}catch(IllegalArgumentException unloaded){return;}
        // Unclaimed/incomplete pads still reach their adjacent Copy Controller.
        // This makes later additions and seed replacements discoverable after a
        // failed scan released the previous claims. Group work stays deferred
        // and is coalesced across the connected component below.
        Set<com.vandorlabs.copier.CopyControllerTile> copiers=new HashSet<>();
        for(BlockPos cell:members)for(EnumFacing side:EnumFacing.values()){
            BlockPos at=cell.offset(side);if(!world.isBlockLoaded(at))continue;TileEntity raw=world.getTileEntity(at);
            if(raw instanceof com.vandorlabs.copier.CopyControllerTile&&((com.vandorlabs.copier.CopyControllerTile)raw).surfaceSeed(cell))copiers.add((com.vandorlabs.copier.CopyControllerTile)raw);
        }
        for(com.vandorlabs.copier.CopyControllerTile copier:copiers)copier.surfaceChanged();
        SquareLayout layout=null;try{layout=SquareLayout.rectangle(members,normal());}catch(IllegalArgumentException irregular){}
        com.vandorlabs.gear.ArmSurfaceActivity.surfaceChanged(world,members);
        for(BlockPos cell:members){TileEntity raw=world.getTileEntity(cell);if(raw instanceof TileEntityTurntableSurface){TileEntityTurntableSurface surface=(TileEntityTurntableSurface)raw;surface.groupPending=false;surface.setGroup(layout==null?SquareLayout.single(cell,normal()):layout);}}
    }
    public void configure(String on,String off,int mode,net.minecraft.entity.player.EntityPlayerMP actor){
        if(world==null||world.isRemote||mode<0||mode>2||!TurntableTextures.valid(on)||!TurntableTextures.valid(off))throw new IllegalArgumentException("Invalid surface appearance");
        SquareLayout layout=joinedLayout();List<TileEntityTurntableSurface> surfaces=new ArrayList<>();
        for(BlockPos cell:layout.cells){if(!world.isBlockLoaded(cell))throw new IllegalArgumentException("Load the complete surface before configuring it");com.vandorlabs.vehicle.VehicleService.permission(actor,cell);TileEntity raw=world.getTileEntity(cell);if(!(raw instanceof TileEntityTurntableSurface))throw new IllegalArgumentException("Surface layout changed");surfaces.add((TileEntityTurntableSurface)raw);}
        for(TileEntityTurntableSurface surface:surfaces){surface.surfaceOn=on;surface.surfaceOff=off;surface.textureLayout=mode;surface.configured=true;surface.setGroup(layout);surface.changed();}
        for(TileEntityTurntableSurface surface:surfaces){surface.scheduleGroup();for(EnumFacing face:EnumFacing.values()){BlockPos next=surface.pos.offset(face);if(world.isBlockLoaded(next)){TileEntity raw=world.getTileEntity(next);if(raw instanceof TileEntityTurntableSurface)((TileEntityTurntableSurface)raw).scheduleGroup();}}}
    }
    private void changed(){markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.markBlockRangeForRenderUpdate(pos,pos);}}
    private void appearanceChanged(){if(com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.surfaceAppearanceChanged(this);}
    @Override public void onLoad(){super.onLoad();appearanceChanged();cachedGroup=null;scheduleGroup();}
    @Override public void update(){if(world!=null&&!world.isRemote&&world.getTotalWorldTime()%20==0&&owner!=null&&world.isBlockLoaded(owner)&&claimedOwner()==null)release();}
    @Override public boolean shouldRefresh(World world,BlockPos pos,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);if(owner!=null&&ownerKey!=null){tag.setLong("Owner",owner.toLong());tag.setUniqueId("OwnerKey",ownerKey);}else{tag.removeTag("Owner");tag.removeTag("OwnerKeyMost");tag.removeTag("OwnerKeyLeast");}tag.setString("SurfaceOn",surfaceOn);tag.setString("SurfaceOff",surfaceOff);tag.setInteger("SurfaceLayout",textureLayout);tag.setBoolean("SurfaceAppearanceConfigured",configured);tag.setLong("SurfaceGroupOffset",groupOffset.toLong());tag.setInteger("SurfaceGroupSize",groupSize);if(groupHeight!=groupSize)tag.setInteger("SurfaceGroupHeight",groupHeight);else tag.removeTag("SurfaceGroupHeight");tag.setInteger("SurfaceGroupNormal",groupNormal);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);appearanceReady=true;owner=tag.hasKey("Owner",4)?BlockPos.fromLong(tag.getLong("Owner")):null;ownerKey=tag.hasUniqueId("OwnerKey")?tag.getUniqueId("OwnerKey"):null;surfaceOn=TurntableTextures.normalize(tag.getString("SurfaceOn"),true);surfaceOff=TurntableTextures.normalize(tag.getString("SurfaceOff"),false);textureLayout=tag.hasKey("SurfaceLayout",3)?Math.max(0,Math.min(2,tag.getInteger("SurfaceLayout"))):2;configured=tag.getBoolean("SurfaceAppearanceConfigured");groupOffset=BlockPos.fromLong(tag.getLong("SurfaceGroupOffset"));groupSize=Math.max(1,Math.min(com.vandorlabs.surface.SquareLayout.MAX_SIZE,tag.getInteger("SurfaceGroupSize")));groupHeight=tag.hasKey("SurfaceGroupHeight",3)?Math.max(1,Math.min(SquareLayout.MAX_SIZE,tag.getInteger("SurfaceGroupHeight"))):groupSize;groupNormal=Math.max(0,Math.min(5,tag.hasKey("SurfaceGroupNormal",3)?tag.getInteger("SurfaceGroupNormal"):1));if(Math.abs(groupOffset.getX())>com.vandorlabs.surface.SquareLayout.MAX_SIZE||Math.abs(groupOffset.getY())>com.vandorlabs.surface.SquareLayout.MAX_SIZE||Math.abs(groupOffset.getZ())>com.vandorlabs.surface.SquareLayout.MAX_SIZE){groupOffset=BlockPos.ORIGIN;groupSize=groupHeight=1;}cachedGroup=null;appearanceChanged();}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,1,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());if(world!=null)world.markBlockRangeForRenderUpdate(pos,pos);}
    @Override public boolean hasFastRenderer(){return world!=null&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld)&&owner==null&&ownerKey==null&&groupSize==1&&groupHeight==1&&groupOffset.equals(BlockPos.ORIGIN);}
    @Override public AxisAlignedBB getRenderBoundingBox(){SquareLayout layout=appearanceLayout();if(!pos.equals(layout.root))return new AxisAlignedBB(pos);double x=Math.abs(layout.basis.ux)*layout.width*.5+Math.abs(layout.basis.vx)*layout.height*.5+.004,y=Math.abs(layout.basis.uy)*layout.width*.5+Math.abs(layout.basis.vy)*layout.height*.5+.004,z=Math.abs(layout.basis.uz)*layout.width*.5+Math.abs(layout.basis.vz)*layout.height*.5+.004;return new AxisAlignedBB(layout.pivot.x-x,layout.pivot.y-y,layout.pivot.z-z,layout.pivot.x+x,layout.pivot.y+y,layout.pivot.z+z);}
    @Override public double getMaxRenderDistanceSquared(){return 65536;}
}
