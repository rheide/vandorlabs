package com.vandorlabs.copier;

import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.surface.*;
import com.vandorlabs.tiles.TileEntityTurntableSurface;
import com.vandorlabs.redstone.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import java.util.*;

/** Settings stay local; every copy preflights both complete areas before touching materials or blocks. */
public final class CopyControllerTile extends TileEntity implements ITickable,SurfaceController {
    public UUID key=UUID.randomUUID(),actor;
    public int height=4,channelIncrement,triggerMode;
    public ChannelList channels=ChannelList.EMPTY;
    public SquareLayout input,output;
    public String status="Place matching square surfaces on the left and right",error="";
    private boolean known,high,refresh=true,clientBusy;
    private int activeTicks;
    public boolean busy(){return world!=null&&world.isRemote?clientBusy:CopyTransactions.busy(this);}
    public EnumFacing front(){return world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);}
    public BlockPos chest(){return pos.offset(front().getOpposite());}
    public void settings(int height,int increment,int mode,ChannelList channels){if(height<1||height>16||increment < -65534||increment > 65534||mode<0||mode>2||channels==null)throw new IllegalArgumentException("Invalid copy settings");if(busy())throw new IllegalArgumentException("Wait for the current copy to finish");if(triggerMode!=mode||!this.channels.equals(channels))known=false;this.height=height;channelIncrement=increment;triggerMode=mode;this.channels=channels;refresh=true;changed();}
    public void failure(String message){error=message==null?"Copy failed; materials retained":message;changed();}
    public void status(String message){status=message;changed();}
    public void requestCopy(EntityPlayerMP player)throws Exception{if(busy())throw new IllegalArgumentException("A copy is already in progress");error="";com.vandorlabs.tiles.WorldActionAuthorization.authorize(this,player);actor=player.getUniqueID();refreshPads(player);CopyTransactions.submit(this,player);}
    public void refreshPads(EntityPlayerMP player){
        if(busy())throw new IllegalArgumentException("Wait for the current copy to finish");EnumFacing right=front().rotateYCCW();
        java.util.function.Predicate<BlockPos> leftHalf=p->side(p,right)<0,rightHalf=p->side(p,right)>0;
        SquareLayout left=SquareLayout.scan(world,pos.offset(right.getOpposite()),leftHalf),target=SquareLayout.scan(world,pos.offset(right),rightHalf);
        if(left.size!=target.size)throw new IllegalArgumentException("Input and output surfaces must be square and the same size");
        if(left.normal!=target.normal)throw new IllegalArgumentException("Input and output surfaces must face the same direction");
        Set<BlockPos> cells=new HashSet<>(left.cells);for(BlockPos p:target.cells)if(!cells.add(p))throw new IllegalArgumentException("Input and output surfaces must be separate");
        for(BlockPos p:cells){TileEntity raw=world.getTileEntity(p);if(!(raw instanceof TileEntityTurntableSurface)||!((TileEntityTurntableSurface)raw).canClaim(pos,key))throw new IllegalArgumentException("A surface belongs to another controller");if(player!=null)com.vandorlabs.vehicle.VehicleService.permission(player,p);}
        release();input=left;output=target;
        for(SquareLayout area:new SquareLayout[]{input,output})for(BlockPos p:area.cells){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)world.getTileEntity(p);tile.bind(pos,key);tile.setGroup(area);}
        refresh=false;status="Ready: "+left.size+" x "+left.size+", height "+height;changed();
    }
    private int side(BlockPos p,EnumFacing right){return (p.getX()-pos.getX())*right.getFrontOffsetX()+(p.getZ()-pos.getZ())*right.getFrontOffsetZ();}
    public void release(){if(world==null||world.isRemote)return;for(SquareLayout area:new SquareLayout[]{input,output})if(area!=null)for(BlockPos p:area.cells)if(world.isBlockLoaded(p)){TileEntity raw=world.getTileEntity(p);if(raw instanceof TileEntityTurntableSurface){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)raw;if(pos.equals(tile.owner)&&key.equals(tile.ownerKey))tile.release();}}input=output=null;}
    public void completed(){activeTicks=20;status="Copy complete";error="";changed();}
    @Override public UUID surfaceKey(){return key;}
    @Override public boolean ownsSurface(BlockPos p){return input!=null&&input.cells.contains(p)||output!=null&&output.cells.contains(p);}
    @Override public SquareLayout surfaceLayout(BlockPos p){return input!=null&&input.cells.contains(p)?input:output!=null&&output.cells.contains(p)?output:null;}
    @Override public boolean surfaceActive(BlockPos p){return (busy()||activeTicks>0)&&output!=null&&output.cells.contains(p);}
    @Override public boolean surfaceLocked(){return busy();}
    @Override public void surfaceChanged(){refresh=true;error="Surfaces changed; check both complete squares";changed();}
    @Override public void invalidate(){com.vandorlabs.tiles.WorldActionAuthorization.revoke(this);super.invalidate();}
    @Override public void onChunkUnload(){com.vandorlabs.tiles.WorldActionAuthorization.unloaded(this);super.onChunkUnload();}
    @Override public void onLoad(){refresh=true;}
    @Override public void update(){if(world==null||world.isRemote)return;if(activeTicks>0&&--activeTicks==0)changed();if(refresh&&!busy()&&actor!=null){try{EntityPlayerMP player=com.vandorlabs.tiles.WorldActionAuthorization.actor(this);refreshPads(player);}catch(IllegalArgumentException invalid){failure(invalid.getMessage());refresh=false;}}
        if(triggerMode==0||busy())return;boolean signal=channels.isEmpty()?LoadedRedstonePower.isPowered(world,pos):RedstoneChannels.allPowered(world,channels),active=triggerMode==1?signal:!signal;
        boolean edge=known&&!high&&active;known=true;high=active;if(edge&&actor!=null)try{EntityPlayerMP player=com.vandorlabs.tiles.WorldActionAuthorization.actor(this);if(player==null)throw new IllegalArgumentException("The configuring player must be online to authorize a copy");requestCopy(player);}catch(Exception failure){failure(failure.getMessage());}
    }
    public void changed(){markDirty();if(world!=null&&!world.isRemote&&world.isBlockLoaded(pos)){net.minecraft.block.state.IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,2);}}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setUniqueId("CopyKey",key);if(actor!=null)tag.setUniqueId("CopyActor",actor);tag.setBoolean("CopyBusy",busy());tag.setInteger("CopyActiveTicks",activeTicks);tag.setInteger("CopyHeight",height);tag.setInteger("CopyIncrement",channelIncrement);tag.setInteger("CopyMode",triggerMode);tag.setString("CopyChannels",channels.toString());tag.setBoolean("CopyKnown",known);tag.setBoolean("CopyHigh",high);tag.setString("CopyStatus",status);tag.setString("CopyError",error);if(input!=null)tag.setTag("CopyInput",input.write());if(output!=null)tag.setTag("CopyOutput",output.write());return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){com.vandorlabs.tiles.WorldActionAuthorization.revoke(this);super.readFromNBT(tag);clientBusy=tag.getBoolean("CopyBusy");activeTicks=Math.max(0,Math.min(20,tag.getInteger("CopyActiveTicks")));key=tag.hasUniqueId("CopyKey")?tag.getUniqueId("CopyKey"):UUID.randomUUID();actor=tag.hasUniqueId("CopyActor")?tag.getUniqueId("CopyActor"):null;height=Math.max(1,Math.min(16,tag.getInteger("CopyHeight")));channelIncrement=Math.max(-65534,Math.min(65534,tag.getInteger("CopyIncrement")));triggerMode=Math.max(0,Math.min(2,tag.getInteger("CopyMode")));ChannelList parsed=ChannelList.parse(tag.getString("CopyChannels"));channels=parsed==null?ChannelList.EMPTY:parsed;known=tag.getBoolean("CopyKnown");high=tag.getBoolean("CopyHigh");status=tag.getString("CopyStatus");error=tag.getString("CopyError");input=tag.hasKey("CopyInput",10)?SquareLayout.read(tag.getCompoundTag("CopyInput")):null;output=tag.hasKey("CopyOutput",10)?SquareLayout.read(tag.getCompoundTag("CopyOutput")):null;refresh=true;}
    @Override public boolean shouldRefresh(World world,BlockPos pos,net.minecraft.block.state.IBlockState old,net.minecraft.block.state.IBlockState next){return old.getBlock()!=next.getBlock();}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
