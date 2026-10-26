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
public final class TileEntityTurntableController extends TileEntity implements ITickable,com.vandorlabs.surface.SurfaceController {
    public UUID key=UUID.randomUUID(),captureId,actor;
    public TurntableSurfaceLayout layout;
    public int height=4,scanMode,rotationMode=1,axis;
    public double rpm=1,autoSnapTolerance;
    public String surfaceOn=TurntableTextures.IDS[1],surfaceOff=TurntableTextures.IDS[0];
    private boolean clientRunning,legacySurfaceAppearance;
    public java.util.UUID surfaceKey(){return key;}
    public boolean ownsSurface(BlockPos cell){return layout!=null&&layout.cells.contains(cell);}
    public com.vandorlabs.surface.SquareLayout surfaceLayout(BlockPos cell){return ownsSurface(cell)?layout:null;}
    public boolean surfaceActive(BlockPos cell){return ownsSurface(cell)&&running();}
    public boolean surfaceLocked(){return captureId!=null||TurntableTransactions.busy(this);}
    public boolean hasLegacySurfaceAppearance(){return legacySurfaceAppearance;}
    private void migrateSurfaceAppearance(){
        if(!legacySurfaceAppearance||world==null||world.isRemote||layout==null||!layout.loaded(world))return;
        for(BlockPos cell:layout.cells){TileEntity raw=world.getTileEntity(cell);if(!(raw instanceof TileEntityTurntableSurface))return;((TileEntityTurntableSurface)raw).migrateLegacy(surfaceOn,surfaceOff);}
        legacySurfaceAppearance=false;changed();
    }
    @Override public void onChunkUnload(){com.vandorlabs.tiles.WorldActionAuthorization.unloaded(this);super.onChunkUnload();}
    @Override public void onLoad(){com.vandorlabs.tiles.WorldActionAuthorization.resumed(this);super.onLoad();DeferredTileLoad.schedule(this,this::migrateSurfaceAppearance);}

    public boolean clockwise=true,reverse,enabled=true;
    public ChannelList scanChannels=ChannelList.EMPTY,rotationChannels=ChannelList.EMPTY,directionChannels=ChannelList.EMPTY,restoreChannels=ChannelList.EMPTY;
    public String lastError="";
    public String message="Place a square of Surface blocks in front, then Scan";
    private boolean trigger,rotationTrigger,directionHigh,directionKnown,restoreHigh,restoreKnown;
    private int ticks;
    private double angle,previous;
    public EntityTurntable display(){if(world==null||captureId==null)return null;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(entity instanceof EntityTurntable && captureId.equals(entity.getUniqueID())&&!entity.isDead)return (EntityTurntable)entity;return null;}
    @Override public void invalidate(){com.vandorlabs.tiles.WorldActionAuthorization.revoke(this);if(world!=null&&!world.isRemote&&!(world instanceof com.vandorlabs.vehicle.VehicleWorld)&&!com.vandorlabs.vehicle.VehicleTransfer.isMutating(world)){EntityTurntable entity=display();if(entity!=null)com.vandorlabs.turntable.TurntableEvents.restoreAfterTick(entity);}super.invalidate();}
    private boolean signal(ChannelList channels){return channels.isEmpty()?world.isBlockPowered(pos):RedstoneChannels.allPowered(world,channels);}
    private boolean matches(int mode,ChannelList channels){return mode!=0 && (mode==1)==signal(channels);}
    public boolean legacyCapture(){return layout!=null&&world!=null&&world.isBlockLoaded(layout.root)&&world.getBlockState(layout.root).getBlock() instanceof BlockTurntableSurface&&world.getBlockState(layout.root).getValue(BlockTurntableSurface.FACING)!=layout.normal;}
    public boolean running(){if(world!=null&&world.isRemote)return clientRunning;return enabled&&!legacyCapture()&&layout!=null&&layout.loaded(world)&&matches(Math.max(1,rotationMode),rotationChannels);}
    public double angle(float partial){EntityTurntable entity=display();if(entity!=null)return entity.angle(partial);return TurntableGeometry.wrap(previous+(((angle-previous+540)%360)-180)*partial);}
    public void status(String text){if(!text.equals(message)){message=text;changed();}}
    public void failure(String text){lastError=text==null?"Operation failed; contents retained":text;changed();}
    public void clearError(){if(!lastError.isEmpty()){lastError="";changed();}}
    public TurntableSnapshot prepareCapture(EntityPlayerMP player)throws Exception {
        clearError();
        if(captureId!=null)throw new IllegalArgumentException("Restore the captured contents before scanning again");
        if(!(world instanceof WorldServer))throw new IllegalArgumentException("Capture requires a server world");
        TurntableSurfaceLayout found=TurntableSurfaceLayout.scan(world,pos.offset(world.getBlockState(pos).getValue(BlockVandorDirectional.FACING)));
        axis=0; // Surface normals determine the axis.
        if(reverse)throw new IllegalArgumentException("Restore the old opposite-face capture first; new scans use the textured Surface face");
        for(BlockPos cell:found.cells){TileEntity raw=world.getTileEntity(cell);if(!(raw instanceof TileEntityTurntableSurface))throw new IllegalArgumentException("Missing surface data");TileEntityTurntableSurface surface=(TileEntityTurntableSurface)raw;
            if(!surface.canClaim(pos,key))throw new IllegalArgumentException("Surface is claimed by another controller; remove that controller first");
            com.vandorlabs.vehicle.VehicleService.permission(player,cell);
        }
        TurntableSnapshot snapshot=TurntableSnapshot.capture((WorldServer)world,found,height,player);
        actor=player.getUniqueID();com.vandorlabs.tiles.WorldActionAuthorization.authorize(this,player);release();layout=found;enabled=true;angle=previous=0;
        for(BlockPos cell:layout.cells){TileEntityTurntableSurface surface=(TileEntityTurntableSurface)world.getTileEntity(cell);surface.bind(this);surface.setGroup(layout);}
        migrateSurfaceAppearance();return snapshot;
    }
    public void scan(EntityPlayerMP player)throws Exception {
        TurntableSnapshot snapshot=prepareCapture(player);
        try{TurntableTransfer.capture((WorldServer)world,this,snapshot,player);status("Captured; use Restore to return contents");}catch(Exception failure){if(captureId==null)release();throw failure;}changed();
    }
    private int queuedIntent;
    private EntityPlayerMP queuedActor;
    private String queuedFailurePrefix="";
    public void requestScan(EntityPlayerMP player)throws Exception {requestScan(player,0);}
    private void requestScan(EntityPlayerMP player,double heading)throws Exception {
        if(TurntableTransactions.busy(this))throw new IllegalArgumentException("A turntable transfer is already in progress");
        clearError();TurntableTransactions.submit(this,null,player,true,()->{
            EntityTurntable fresh=display();
            if(fresh!=null&&heading!=0){if(!fresh.snapshot.blocks.cells.isEmpty()||!fresh.snapshot.entities.isEmpty())fresh.install(fresh.snapshot.rotated(360-heading,fresh.view));fresh.setAngle(heading);angle=previous=heading;changed();}
            status("Captured; use Restore to return contents");finishRequest();
        },this::transferFailed);
    }
    public void requestRestore(EntityPlayerMP player)throws Exception {
        requestRestore(player,"");
    }
    private void requestRestore(EntityPlayerMP player,String failurePrefix)throws Exception {
        if(TurntableTransactions.busy(this)){queuedIntent=1;queuedActor=player;queuedFailurePrefix=failurePrefix;return;}
        EntityTurntable entity=display();if(entity==null)throw new IllegalArgumentException("Captured contents are not loaded; load the entire turntable first");
        clearError();TurntableTransactions.submit(this,entity,player,false,this::finishRequest,error->transferFailed(new java.io.IOException(failurePrefix+(error.getMessage()==null?"contents retained":error.getMessage()),error)));
    }
    private void requestRescan(EntityPlayerMP player)throws Exception {
        if(TurntableTransactions.busy(this)){queuedIntent=2;queuedActor=player;return;}
        EntityTurntable old=display();double heading=old==null?0:old.angle();
        if(old==null){if(captureId!=null)throw new IllegalArgumentException("Cannot rescan: captured contents are not loaded");requestScan(player,heading);return;}
        double snapped=TurntableGeometry.wrap(Math.round(heading/90)*90);
        if(Math.abs(((heading-snapped+540)%360)-180)>.001){failure("Rescan needs a 90-degree heading; continuing with the existing capture");return;}
        TurntableTransactions.submit(this,old,player,false,()->{try{requestScan(player,heading);}catch(Exception error){transferFailed(error);}},this::transferFailed);
    }
    private void finishRequest(){int intent=queuedIntent;EntityPlayerMP player=queuedActor;String prefix=queuedFailurePrefix;queuedIntent=0;queuedActor=null;queuedFailurePrefix="";try{if(intent==1&&captureId!=null)requestRestore(player,prefix);else if(intent==2)requestRescan(player);}catch(Exception error){transferFailed(error);}}
    private void transferFailed(Exception error){queuedIntent=0;queuedActor=null;queuedFailurePrefix="";if(captureId==null)release();failure(error.getMessage());}
    public void restore(EntityPlayerMP player)throws Exception{EntityTurntable entity=display();if(entity==null)throw new IllegalArgumentException("Captured contents are not loaded; load the entire turntable first");TurntableTransfer.restore((WorldServer)world,entity,player);}
    public void turnTo(double heading){EntityTurntable entity=display();if(entity==null)throw new IllegalArgumentException("Scan the turntable before setting its angle");entity.turnTo(heading);angle=previous=entity.angle();status("Paused; use Angle or Restore");changed();}
    public void restored(double heading){angle=previous=TurntableGeometry.wrap(heading);changed();}
    public void surfaceChanged(){
        enabled=false;
        // Recovery ownership stays intact; each surviving face renders independently
        // because the original shared renderer's root may have been removed.
        if(layout!=null&&world!=null)for(BlockPos cell:layout.cells)if(world.isBlockLoaded(cell)){
            TileEntity raw=world.getTileEntity(cell);
            if(raw instanceof TileEntityTurntableSurface&&pos.equals(((TileEntityTurntableSurface)raw).owner)&&key.equals(((TileEntityTurntableSurface)raw).ownerKey)){
                net.minecraft.block.state.IBlockState state=world.getBlockState(cell);
                if(!(state.getBlock() instanceof BlockTurntableSurface))continue;
                TileEntityTurntableSurface surface=(TileEntityTurntableSurface)raw;
                surface.setGroup(com.vandorlabs.surface.SquareLayout.single(cell,state.getValue(BlockTurntableSurface.FACING)));
                if(captureId==null)surface.release();
            }
        }
        status("Surface changed; rotation stopped. Restore contents before rebuilding");changed();
    }
    public void release(){if(layout!=null&&world!=null)for(BlockPos cell:layout.cells)if(world.isBlockLoaded(cell)){TileEntity tile=world.getTileEntity(cell);if(tile instanceof TileEntityTurntableSurface && pos.equals(((TileEntityTurntableSurface)tile).owner)&&key.equals(((TileEntityTurntableSurface)tile).ownerKey))((TileEntityTurntableSurface)tile).release();}layout=null;changed();}
    public void settings(NBTTagCompound tag){
        int h=tag.getInteger("Height"),sm=tag.getInteger("ScanMode"),rm=tag.getInteger("RotationMode"),a=tag.getInteger("Axis");double speed=tag.getDouble("RPM");
        ChannelList scan=ChannelList.parse(tag.getString("ScanChannels")),rotate=ChannelList.parse(tag.getString("RotationChannels")),direction=ChannelList.parse(tag.hasKey("DirectionChannels")?tag.getString("DirectionChannels"):"0");
        ChannelList restore=tag.hasKey("RestoreChannels")?ChannelList.parse(tag.getString("RestoreChannels")):restoreChannels;
        double tolerance=tag.hasKey("AutoSnapTolerance")?tag.getDouble("AutoSnapTolerance"):autoSnapTolerance;
        TurntableGeometry.checkSnapTolerance(tolerance);
        TurntableGeometry.degreesPerTick(speed);
        if(h<1||h>16||sm<0||sm>2||rm<1||rm>2||a<0||a>3||scan==null||rotate==null||direction==null||restore==null)throw new IllegalArgumentException("Invalid turntable settings");
        if((captureId!=null||TurntableTransactions.busy(this))&&h!=height)throw new IllegalArgumentException("Finish the transfer and restore before changing the scan height");
        if(!directionChannels.equals(direction)){directionKnown=false;directionChannels=direction;}
        if(!restoreChannels.equals(restore)){restoreKnown=false;restoreChannels=restore;}
        height=h;scanMode=sm;rotationMode=rm;axis=0;rpm=speed;autoSnapTolerance=tolerance;scanChannels=scan;rotationChannels=rotate;clockwise=tag.getBoolean("Clockwise");reverse=false;changed();
    }
    @Override public void update(){
        previous=angle;if(world.isRemote){return;}
        boolean pulse=!directionChannels.isEmpty()&&RedstoneChannels.allPowered(world,directionChannels);
        if(directionKnown&&pulse&&!directionHigh){clockwise=!clockwise;changed();}
        directionHigh=pulse;directionKnown=true;
        boolean restorePower=!restoreChannels.isEmpty()&&RedstoneChannels.allPowered(world,restoreChannels);
        boolean restorePulse=restoreKnown&&restorePower&&!restoreHigh;
        if(!restoreKnown||restoreHigh!=restorePower)markDirty();
        restoreHigh=restorePower;restoreKnown=true;
        ticks++;boolean next=matches(scanMode,scanChannels),scanStarted=false;
        if(next&&!trigger&&captureId==null&&actor!=null)try{requestScan(com.vandorlabs.tiles.WorldActionAuthorization.permissionActor(this));scanStarted=true;}catch(Exception failure){failure(failure.getMessage()==null?"Scan failed":failure.getMessage());}
        trigger=next;boolean rotation=matches(Math.max(1,rotationMode),rotationChannels);
        if(rotation&&!rotationTrigger&&!scanStarted&&actor!=null&&enabled)try{requestRescan(com.vandorlabs.tiles.WorldActionAuthorization.permissionActor(this));}catch(Exception failure){failure(failure.getMessage()==null?"Scan failed":failure.getMessage());}
        if(!rotation&&rotationTrigger&&actor!=null&&enabled){
            EntityTurntable stopped=display();
            if(stopped==null&&TurntableTransactions.busy(this)){queuedIntent=1;queuedFailurePrefix="Automatic restore failed: ";try{queuedActor=com.vandorlabs.tiles.WorldActionAuthorization.permissionActor(this);}catch(IllegalArgumentException denied){failure(denied.getMessage());queuedIntent=0;}}
            if(stopped!=null){
                if(TurntableGeometry.canAutoSnap(stopped.angle(),autoSnapTolerance))try{
                    clearError();requestRestore(com.vandorlabs.tiles.WorldActionAuthorization.permissionActor(this),"Automatic restore failed: ");
                }catch(Exception failure){failure("Automatic restore failed: "+(failure.getMessage()==null?"contents retained":failure.getMessage()));}
            }
        }
        if(restorePulse&&actor!=null&&(captureId!=null||TurntableTransactions.busy(this)))try{
            clearError();requestRestore(com.vandorlabs.tiles.WorldActionAuthorization.permissionActor(this),"Restore pulse failed: ");
        }catch(Exception failure){failure("Restore pulse failed: "+(failure.getMessage()==null?"contents retained":failure.getMessage()));}
        rotationTrigger=rotation;
        if(ticks%20==0&&legacySurfaceAppearance)DeferredTileLoad.schedule(this,this::migrateSurfaceAppearance);
        if(ticks%20==0&&enabled&&layout!=null&&layout.loaded(world))for(BlockPos cell:layout.cells){TileEntity tile=world.getTileEntity(cell);if(!(tile instanceof TileEntityTurntableSurface)){surfaceChanged();break;}TileEntityTurntableSurface surface=(TileEntityTurntableSurface)tile;if(surface.owner==null)surface.bind(this);else if(!pos.equals(surface.owner)||!key.equals(surface.ownerKey)){surfaceChanged();break;}}
        EntityTurntable entity=display();if(entity!=null)angle=entity.angle();
        boolean active=running();if(active!=clientRunning){clientRunning=active;changed();}else if(ticks%10==0&&layout!=null)changed();
    }
    public void changed(){markDirty();if(world!=null){IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.markBlockRangeForRenderUpdate(pos,pos);}}
    @Override public boolean shouldRefresh(World world,BlockPos pos,IBlockState before,IBlockState after){return before.getBlock()!=after.getBlock();}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){super.writeToNBT(tag);tag.setString("SurfaceOn",surfaceOn);tag.setString("SurfaceOff",surfaceOff);tag.setBoolean("SurfaceAppearanceMigrated",!legacySurfaceAppearance);tag.setUniqueId("Key",key);if(captureId!=null)tag.setUniqueId("Capture",captureId);if(actor!=null)tag.setUniqueId("Actor",actor);if(layout!=null)tag.setTag("Surface",layout.write());tag.setInteger("Height",height);tag.setInteger("ScanMode",scanMode);tag.setInteger("RotationMode",rotationMode);tag.setInteger("Axis",axis);tag.setDouble("RPM",rpm);tag.setDouble("AutoSnapTolerance",autoSnapTolerance);tag.setBoolean("Running",world!=null&&running());tag.setBoolean("Clockwise",clockwise);tag.setBoolean("Reverse",reverse);tag.setBoolean("Enabled",enabled);tag.setString("ScanChannels",scanChannels.toString());tag.setString("RotationChannels",rotationChannels.toString());tag.setString("DirectionChannels",directionChannels.toString());tag.setString("RestoreChannels",restoreChannels.toString());tag.setBoolean("RestoreHigh",restoreHigh);tag.setBoolean("RestoreKnown",restoreKnown);tag.setBoolean("DirectionHigh",directionHigh);tag.setBoolean("DirectionKnown",directionKnown);tag.setString("Status",message);tag.setString("Error",lastError);tag.setDouble("Angle",angle);tag.setBoolean("Trigger",trigger);tag.setBoolean("RotationTrigger",rotationTrigger);return tag;}
    @Override public void readFromNBT(NBTTagCompound tag){com.vandorlabs.tiles.WorldActionAuthorization.reading(this);super.readFromNBT(tag);legacySurfaceAppearance=!tag.getBoolean("SurfaceAppearanceMigrated")&&(tag.hasKey("SurfaceOn")||tag.hasKey("SurfaceOff"));surfaceOn=TurntableTextures.normalize(tag.getString("SurfaceOn"),true);surfaceOff=TurntableTextures.normalize(tag.getString("SurfaceOff"),false);key=tag.hasUniqueId("Key")?tag.getUniqueId("Key"):UUID.randomUUID();captureId=tag.hasUniqueId("Capture")?tag.getUniqueId("Capture"):null;actor=tag.hasUniqueId("Actor")?tag.getUniqueId("Actor"):null;layout=tag.hasKey("Surface",10)?TurntableSurfaceLayout.read(tag.getCompoundTag("Surface")):null;height=Math.max(1,Math.min(16,tag.getInteger("Height")));scanMode=Math.max(0,Math.min(2,tag.getInteger("ScanMode")));rotationMode=Math.max(1,Math.min(2,tag.getInteger("RotationMode")));axis=Math.max(0,Math.min(3,tag.getInteger("Axis")));clientRunning=tag.getBoolean("Running");double tolerance=tag.getDouble("AutoSnapTolerance");autoSnapTolerance=Double.isFinite(tolerance)?Math.max(0,Math.min(5,tolerance)):0;double saved=tag.getDouble("RPM");rpm=Double.isFinite(saved)?Math.max(.5,Math.min(10,Math.round(saved*2)/2D)):1;clockwise=tag.getBoolean("Clockwise");reverse=tag.getBoolean("Reverse");enabled=tag.getBoolean("Enabled");ChannelList s=ChannelList.parse(tag.getString("ScanChannels")),r=ChannelList.parse(tag.getString("RotationChannels"));scanChannels=s==null?ChannelList.EMPTY:s;rotationChannels=r==null?ChannelList.EMPTY:r;ChannelList d=ChannelList.parse(tag.getString("DirectionChannels"));directionChannels=d==null?ChannelList.EMPTY:d;directionHigh=tag.getBoolean("DirectionHigh");directionKnown=tag.getBoolean("DirectionKnown");ChannelList restore=ChannelList.parse(tag.getString("RestoreChannels"));restoreChannels=restore==null?ChannelList.EMPTY:restore;restoreHigh=tag.getBoolean("RestoreHigh");restoreKnown=tag.getBoolean("RestoreKnown");message=tag.getString("Status");lastError=tag.getString("Error");double savedAngle=tag.getDouble("Angle");angle=previous=Double.isFinite(savedAngle)?TurntableGeometry.wrap(savedAngle):0;trigger=tag.getBoolean("Trigger");rotationTrigger=tag.getBoolean("RotationTrigger");}
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,1,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager manager,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
