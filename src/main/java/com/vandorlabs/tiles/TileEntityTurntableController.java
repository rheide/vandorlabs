package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.turntable.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import java.util.UUID;

/** Server-owned settings and surface claims. Captured contents remain inert until restored. */
public final class TileEntityTurntableController extends TileEntity implements ITickable {
    public UUID key=UUID.randomUUID(),captureId,actor;
    public TurntableSurfaceLayout layout;
    public int height=4,scanMode,rotationMode=1,axis;
    public double rpm=1;
    public boolean clockwise=true,reverse,enabled=true;
    public ChannelList scanChannels=ChannelList.EMPTY,rotationChannels=ChannelList.EMPTY;
    public String message="Place a square of Surface blocks in front, then Scan";
    private boolean trigger;
    private int ticks;
    private double angle,previous;
    public EntityTurntable display(){if(world==null||captureId==null)return null;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(entity instanceof EntityTurntable && captureId.equals(entity.getUniqueID())&&!entity.isDead)return (EntityTurntable)entity;return null;}
    private boolean signal(ChannelList channels){return channels.isEmpty()?world.isBlockPowered(pos):RedstoneChannels.allPowered(world,channels);}
    private boolean matches(int mode,ChannelList channels){return mode!=0 && (mode==1)==signal(channels);}
    public boolean running(){return enabled&&layout!=null&&layout.loaded(world)&&(rotationMode==0||matches(rotationMode,rotationChannels));}
    public double angle(float partial){EntityTurntable entity=display();if(entity!=null)return entity.angle(partial);return TurntableGeometry.wrap(previous+(((angle-previous+540)%360)-180)*partial);}
    public void status(String text){if(!text.equals(message)){message=text;changed();}}
    public void scan(EntityPlayerMP player)throws Exception {
        if(captureId!=null)throw new IllegalArgumentException("Restore the captured contents before scanning again");
        if(!(world instanceof WorldServer))return;
        TurntableSurfaceLayout found=TurntableSurfaceLayout.scan(world,pos.offset(world.getBlockState(pos).getValue(BlockVandorDirectional.FACING)));
        if(axis!=0 && found.basis.axis()!=axis-1)throw new IllegalArgumentException("Selected axis does not match the Surface face; choose Auto or its perpendicular axis");
        if(reverse)found=found.reversed();
        for(BlockPos cell:found.cells){TileEntity raw=world.getTileEntity(cell);if(!(raw instanceof TileEntityTurntableSurface))throw new IllegalArgumentException("Missing surface data");TileEntityTurntableSurface surface=(TileEntityTurntableSurface)raw;
            if(surface.owner!=null && (!pos.equals(surface.owner)||!key.equals(surface.ownerKey)))throw new IllegalArgumentException("Surface is claimed by another controller; remove that controller first");
            com.vandorlabs.vehicle.VehicleService.permission(player,cell);
        }
        TurntableSnapshot snapshot=TurntableSnapshot.capture((WorldServer)world,found,height,player);
        actor=player.getUniqueID();release();layout=found;angle=previous=0;
        for(BlockPos cell:layout.cells)((TileEntityTurntableSurface)world.getTileEntity(cell)).bind(this);
        try{TurntableTransfer.capture((WorldServer)world,this,snapshot,player);status("Captured; use Restore to return contents");}catch(Exception failure){if(captureId==null)release();throw failure;}changed();
    }
    public void restore(EntityPlayerMP player)throws Exception{EntityTurntable entity=display();if(entity==null)throw new IllegalArgumentException("Captured contents are not loaded; load the entire turntable first");TurntableTransfer.restore((WorldServer)world,entity,player);}
    public void surfaceChanged(){enabled=false;status("Surface changed; rotation stopped. Restore contents before rebuilding");}
    public void release(){if(layout!=null&&world!=null)for(BlockPos cell:layout.cells)if(world.isBlockLoaded(cell)){TileEntity tile=world.getTileEntity(cell);if(tile instanceof TileEntityTurntableSurface && pos.equals(((TileEntityTurntableSurface)tile).owner)&&key.equals(((TileEntityTurntableSurface)tile).ownerKey))((TileEntityTurntableSurface)tile).release();}layout=null;changed();}
    public void settings(NBTTagCompound tag){
        int h=tag.getInteger("Height"),sm=tag.getInteger("ScanMode"),rm=tag.getInteger("RotationMode"),a=tag.getInteger("Axis");double speed=tag.getDouble("RPM");
        ChannelList scan=ChannelList.parse(tag.getString("ScanChannels")),rotate=ChannelList.parse(tag.getString("RotationChannels"));
        TurntableGeometry.degreesPerTick(speed);
        if(h<1||h>16||sm<0||sm>2||rm<0||rm>2||a<0||a>3||scan==null||rotate==null)throw new IllegalArgumentException("Invalid turntable settings");
        if(captureId!=null&&(h!=height||a!=axis||tag.getBoolean("Reverse")!=reverse))throw new IllegalArgumentException("Restore before changing the scan volume or axis");
        height=h;scanMode=sm;rotationMode=rm;axis=a;rpm=speed;scanChannels=scan;rotationChannels=rotate;clockwise=tag.getBoolean("Clockwise");reverse=tag.getBoolean("Reverse");enabled=tag.getBoolean("Enabled");changed();
    }
    @Override public void update(){
        previous=angle;if(world.isRemote){if(display()==null&&running())angle=TurntableGeometry.wrap(angle+(clockwise?1:-1)*TurntableGeometry.degreesPerTick(rpm));return;}
        ticks++;boolean next=matches(scanMode,scanChannels);
        if(next&&!trigger&&captureId==null&&actor!=null)try{scan(net.minecraftforge.common.util.FakePlayerFactory.get((WorldServer)world,new com.mojang.authlib.GameProfile(actor,"Turntable")));}catch(Exception failure){status(failure.getMessage()==null?"Scan failed":failure.getMessage());}
        trigger=next;
        if(ticks%20==0&&layout!=null&&layout.loaded(world))for(BlockPos cell:layout.cells){TileEntity tile=world.getTileEntity(cell);if(!(tile instanceof TileEntityTurntableSurface)){surfaceChanged();break;}TileEntityTurntableSurface surface=(TileEntityTurntableSurface)tile;if(surface.owner==null)surface.bind(this);else if(!pos.equals(surface.owner)||!key.equals(surface.ownerKey)){surfaceChanged();break;}}
        EntityTurntable entity=display();if(entity!=null)angle=entity.angle();else if(captureId==null&&running())angle=TurntableGeometry.wrap(angle+(clockwise?1:-1)*TurntableGeometry.degreesPerTick(rpm));
        if(ticks%10==0&&layout!=null)changed();
    }
    public void changed(){markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.markBlockRangeForRenderUpdate(pos,pos);}}
    @Override public boolean shouldRefresh(World world,BlockPos pos,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setUniqueId("Key",key);if(captureId!=null)tag.setUniqueId("Capture",captureId);if(actor!=null)tag.setUniqueId("Actor",actor);if(layout!=null)tag.setTag("Surface",layout.write());tag.setInteger("Height",height);tag.setInteger("ScanMode",scanMode);tag.setInteger("RotationMode",rotationMode);tag.setInteger("Axis",axis);tag.setDouble("RPM",rpm);tag.setBoolean("Clockwise",clockwise);tag.setBoolean("Reverse",reverse);tag.setBoolean("Enabled",enabled);tag.setString("ScanChannels",scanChannels.toString());tag.setString("RotationChannels",rotationChannels.toString());tag.setString("Status",message);tag.setDouble("Angle",angle);tag.setBoolean("Trigger",trigger);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){super.readFromNBT(tag);key=tag.hasUniqueId("Key")?tag.getUniqueId("Key"):UUID.randomUUID();captureId=tag.hasUniqueId("Capture")?tag.getUniqueId("Capture"):null;actor=tag.hasUniqueId("Actor")?tag.getUniqueId("Actor"):null;layout=tag.hasKey("Surface",10)?TurntableSurfaceLayout.read(tag.getCompoundTag("Surface")):null;height=Math.max(1,Math.min(16,tag.getInteger("Height")));scanMode=Math.max(0,Math.min(2,tag.getInteger("ScanMode")));rotationMode=Math.max(0,Math.min(2,tag.getInteger("RotationMode")));axis=Math.max(0,Math.min(3,tag.getInteger("Axis")));double saved=tag.getDouble("RPM");rpm=Double.isFinite(saved)?Math.max(.1,Math.min(30,saved)):1;clockwise=tag.getBoolean("Clockwise");reverse=tag.getBoolean("Reverse");enabled=tag.getBoolean("Enabled");ChannelList s=ChannelList.parse(tag.getString("ScanChannels")),r=ChannelList.parse(tag.getString("RotationChannels"));scanChannels=s==null?ChannelList.EMPTY:s;rotationChannels=r==null?ChannelList.EMPTY:r;message=tag.getString("Status");double savedAngle=tag.getDouble("Angle");angle=previous=Double.isFinite(savedAngle)?TurntableGeometry.wrap(savedAngle):0;trigger=tag.getBoolean("Trigger");}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,1,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
