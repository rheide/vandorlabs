package com.vandorlabs.neoforge.copier;

import com.vandorlabs.copier.core.CopySettings;
import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.redstone.*;
import com.vandorlabs.neoforge.surface.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Paired pads and event-driven trigger inputs; only the transfer service can consume construction materials. */
public final class NativeCopyControllerEntity extends BlockEntity implements NativeSurfaceController,NativeChannelMember {
    private UUID key=UUID.randomUUID(),actor;
    private CopySettings settings=new CopySettings(4,0,0,ChannelList.EMPTY,false);
    private NativeSurfaceLayout input,output;
    private boolean inert,known,high,refresh=true,clientBusy;
    private long activeUntil;private int loadedActiveTicks;
    private String status="Place matching rectangular surfaces on the left and right",error="";
    private CompoundTag retained=new CompoundTag();
    public long topologyScans,packets;
    public NativeCopyControllerEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public boolean inert(){return inert;}
    public CopySettings settings(){return settings;}
    public UUID key(){return key;}
    public UUID actorId(){return actor;}
    public String status(){return status;}
    public String error(){return error;}
    public Direction front(){return getBlockState().getValue(NativeCopyControllerBlock.FACING);}
    public BlockPos chest(){return worldPosition.relative(front().getOpposite());}
    public NativeSurfaceLayout input(){return input;}
    public NativeSurfaceLayout output(){return output;}
    public boolean busy(){return level!=null&&level.isClientSide?clientBusy:NativeCopyService.busy(this);}
    boolean installed(){if(!(level instanceof ServerLevel world)||!world.getServer().isSameThread()||isRemoved()||inert)return false;var chunk=world.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(worldPosition)==this;}
    public void authorize(ServerPlayer player){if(!installed()||player.serverLevel()!=level||!player.isAlive()||player.isSpectator())throw new IllegalArgumentException("Copy actor unavailable");actor=player.getUUID();setChanged();NativeCopyUpdates.queue(this);}
    public ServerPlayer actor(){if(!(level instanceof ServerLevel world)||actor==null)return null;var player=world.getServer().getPlayerList().getPlayer(actor);return player!=null&&player.serverLevel()==world&&player.isAlive()&&!player.isSpectator()?player:null;}
    public boolean configure(ServerPlayer player,CopySettings choices){if(!installed()||busy()||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,this))return false;boolean edge=choices.changesEdgeBank(settings);settings=Objects.requireNonNull(choices);if(edge)known=false;authorize(player);refresh=true;NativeRedstoneChannels.channelsChanged(this);changed();return true;}
    public void request(ServerPlayer player){if(!installed()||busy())throw new IllegalArgumentException("A copy is already in progress");authorize(player);refreshPads(player);error="";NativeCopyService.submit(this,player);}
    public void checkAreas(ServerPlayer player){authorize(player);refreshPads(player);}
    private static String bounded(String value){return value==null?"Copy refused":value.length()>512?value.substring(0,512):value;}
    public void status(String value){String next=bounded(value);if(!status.equals(next)){status=next;changed();}}
    public void failure(String value){String next=bounded(value);if(!error.equals(next)){error=next;changed();}}
    public void completed(){activeUntil=level.getGameTime()+20;status="Copy complete";error="";changed();activity();if(level instanceof ServerLevel world)world.scheduleTick(worldPosition,getBlockState().getBlock(),20);}
    public void deferred(){if(!installed())return;if(refresh&&!busy()){var player=actor();if(player==null){failure("The configuring player must be online to check changed pads");return;}try{refreshPads(player);}catch(IllegalArgumentException refused){refresh=false;failure(refused.getMessage());}}signal();}
    public void scheduled(){if(!installed())return;if(activeUntil>0&&level.getGameTime()>=activeUntil){activeUntil=0;changed();activity();}deferred();}
    private void signal(){if(!installed()||busy()||settings.activation==0)return;var world=(ServerLevel)level;boolean signal;if(settings.channels.isEmpty())signal=NativeLoadedRedstonePower.level(world,worldPosition)>0;else{signal=true;for(int i=0;i<settings.channels.size();i++)if(NativeRedstoneChannels.level(world,worldPosition,settings.channels.get(i))<=0){signal=false;break;}}boolean active=settings.active(signal),edge=known&&!high&&active;known=true;high=active;setChanged();if(edge){var player=actor();if(player==null)failure("The configuring player must be online to authorize a copy");else try{request(player);}catch(IllegalArgumentException refused){failure(refused.getMessage());}}}
    public void refreshPads(ServerPlayer player){if(!installed()||busy())throw new IllegalArgumentException("Wait for the current copy to finish");try{scan(player);}catch(IllegalArgumentException refused){release();throw refused;}}
    private NativeSurfaceLayout scanPad(BlockPos seed,java.util.function.Predicate<BlockPos> side,String label){try{return new NativeSurfaceLayout(NativeSurfaceLayout.connected(level,seed,side,false),level.getBlockState(seed).getValue(NativeSurfaceBlock.FACING),true);}catch(IllegalArgumentException refused){throw new IllegalArgumentException(label+" pad: "+refused.getMessage());}}
    private void scan(ServerPlayer player){topologyScans++;var right=front().getCounterClockWise();var left=settings.chip?null:scanPad(worldPosition.relative(right.getOpposite()),p->side(p,right)<0,"Input (left)");var next=scanPad(worldPosition.relative(right),p->side(p,right)>0,"Output (right)");if(left!=null&&(left.geometry.width!=next.geometry.width||left.geometry.height!=next.geometry.height||left.normal!=next.normal))throw new IllegalArgumentException("Input and output rectangles must match dimensions and facing");var cells=new HashSet<BlockPos>(left==null?List.of():left.cells);for(var pos:next.cells)if(!cells.add(pos))throw new IllegalArgumentException("Input and output pads overlap");
        for(var pos:cells){if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile)||tile.inert()||!tile.canClaim(worldPosition,key)||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,player.getMainHandItem())||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,pos))throw new IllegalArgumentException("Surface claim/permission changed");}
        var previousInput=input;var previousOutput=output;input=left;output=next;releaseCells(previousInput,previousOutput,cells);for(var pos:cells){var tile=(NativeSurfaceEntity)level.getBlockEntity(pos);if(!tile.claimedBy(worldPosition,key)){tile.bind(worldPosition,key);tile.schedule();}}refresh=false;error="";status="Ready: "+next.geometry.width+" x "+next.geometry.height+", height "+settings.height;changed();}
    private int side(BlockPos pos,Direction right){return (pos.getX()-worldPosition.getX())*right.getStepX()+(pos.getZ()-worldPosition.getZ())*right.getStepZ();}
    public void release(){if(level==null||level.isClientSide)return;var oldInput=input;var oldOutput=output;input=output=null;releaseCells(oldInput,oldOutput,Set.of());}
    private void releaseCells(NativeSurfaceLayout first,NativeSurfaceLayout second,Set<BlockPos> keep){for(var area:new NativeSurfaceLayout[]{first,second})if(area!=null)for(var pos:area.cells)if(!keep.contains(pos)&&level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile&&tile.claimedBy(worldPosition,key))tile.release();}
    public boolean sameSurfaceSide(BlockPos first,BlockPos next){var right=front().getCounterClockWise();return Integer.signum(side(first,right))==Integer.signum(side(next,right));}
    @Override public UUID surfaceKey(){return key;}
    @Override public boolean ownsSurface(BlockPos pos){return input!=null&&input.cells.contains(pos)||output!=null&&output.cells.contains(pos);}
    @Override public NativeSurfaceLayout surfaceLayout(BlockPos pos){return input!=null&&input.cells.contains(pos)?input:output!=null&&output.cells.contains(pos)?output:null;}
    @Override public boolean surfaceActive(BlockPos pos){return (busy()||level!=null&&activeUntil>level.getGameTime())&&output!=null&&output.cells.contains(pos);}
    @Override public boolean surfaceLocked(){return busy();}
    @Override public boolean joinsArtwork(){return false;}
    @Override public boolean joinsArtwork(BlockPos first,BlockPos next){return sameSurfaceSide(first,next);}
    @Override public boolean surfaceSeed(BlockPos pos){var right=front().getCounterClockWise();return pos.equals(worldPosition.relative(right))||!settings.chip&&pos.equals(worldPosition.relative(right.getOpposite()));}
    @Override public boolean acceptsExtension(BlockPos existing,BlockPos added){return sameSurfaceSide(existing,added);}
    @Override public void surfaceChanged(){if(!installed())return;if(!busy())release();refresh=true;failure("Surfaces changed; check both complete rectangles");NativeCopyUpdates.queue(this);}
    private void activity(){for(var area:new NativeSurfaceLayout[]{input,output})if(area!=null)for(var pos:area.cells)if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile)tile.activityChanged();}
    public void changed(){setChanged();if(level!=null&&!level.isClientSide){packets++;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}activity();}
    @Override public void onLoad(){super.onLoad();if(loadedActiveTicks>0&&level!=null){activeUntil=level.getGameTime()+loadedActiveTicks;loadedActiveTicks=0;}refresh=true;NativeCopyUpdates.queue(this);if(level instanceof ServerLevel)NativeRedstoneChannels.queueRegistration(this);}
    @Override public void setRemoved(){NativeCopyUpdates.remove(this);if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeCopyUpdates.remove(this);if(level instanceof ServerLevel)NativeRedstoneChannels.unregister(this);super.onChunkUnloaded();}
    @Override public BlockEntity channelBlockEntity(){return this;}
    @Override public ChannelList getRedstoneChannels(){return inert?ChannelList.EMPTY:settings.channels;}
    @Override public boolean isChannelMemberActive(){return !inert&&NativeChannelMember.super.isChannelMemberActive();}
    @Override public void beforeChannelRegistration(){NativeCopyUpdates.queue(this);}
    @Override public void setChannelLevel(int ignored){NativeCopyUpdates.queue(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();writeChoices(tag);tag.putBoolean("CopyPublic",true);tag.putBoolean("NativeCopyInert",inert);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    private void writeChoices(CompoundTag tag){tag.putUUID("CopyKey",key);tag.putBoolean("CopyBusy",busy());tag.putInt("CopyActiveTicks",level==null?0:(int)Math.max(0,Math.min(20,activeUntil-level.getGameTime())));tag.putBoolean("CopyUseChip",settings.chip);tag.putInt("CopyHeight",settings.height);tag.putInt("CopyIncrement",settings.increment);tag.putInt("CopyMode",settings.activation);tag.putString("CopyChannels",settings.channels.toString());tag.putString("CopyStatus",status);tag.putString("CopyError",error);if(input!=null)tag.put("CopyInput",NativeCopyLayouts.write(input));if(output!=null)tag.put("CopyOutput",NativeCopyLayouts.write(output));}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);writeChoices(tag);tag.putInt("NativeCopySchema",1);if(actor!=null)tag.putUUID("CopyActor",actor);tag.putBoolean("CopyKnown",known);tag.putBoolean("CopyHigh",high);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){var original=tag.copy();try{boolean publicView=level!=null&&level.isClientSide&&tag.getBoolean("CopyPublic");if(tag.getBoolean("CopyPublic")&&!publicView)throw new IllegalArgumentException("Public copy choices cannot be physical contents");if(tag.contains("NativeCopySchema")&&(!tag.contains("NativeCopySchema",Tag.TAG_INT)||tag.getInt("NativeCopySchema")!=1))throw new IllegalArgumentException("Future Copy Controller schema");for(String field:List.of("CopyHeight","CopyIncrement","CopyMode","CopyActiveTicks"))if(tag.contains(field)&&!tag.contains(field,Tag.TAG_INT))throw new IllegalArgumentException("Copy setting field type");for(String field:List.of("CopyUseChip","CopyBusy","CopyKnown","CopyHigh","CopyPublic"))if(tag.contains(field)&&(!tag.contains(field,Tag.TAG_BYTE)||tag.getByte(field)<0||tag.getByte(field)>1))throw new IllegalArgumentException("Copy boolean field type");for(String field:List.of("CopyStatus","CopyError"))if(tag.contains(field)&&!tag.contains(field,Tag.TAG_STRING))throw new IllegalArgumentException("Copy message field type");if(tag.contains("CopyChannels")&&!tag.contains("CopyChannels",Tag.TAG_STRING))throw new IllegalArgumentException("Copy channels type");var bank=ChannelList.parse(tag.getString("CopyChannels"));var next=new CopySettings(tag.contains("CopyHeight")?tag.getInt("CopyHeight"):1,tag.getInt("CopyIncrement"),tag.getInt("CopyMode"),bank,tag.getBoolean("CopyUseChip"));var nextKey=NativeCopyLayouts.uuid(tag,"CopyKey",false);var nextActor=NativeCopyLayouts.uuid(tag,"CopyActor",false);var left=tag.contains("CopyInput")?NativeCopyLayouts.read(tag.getCompound("CopyInput"),worldPosition,true):null;var right=tag.contains("CopyOutput")?NativeCopyLayouts.read(tag.getCompound("CopyOutput"),worldPosition,true):null;if(!publicView){NativePlatformComponents.requireSupported(original,registries);try(var guard=NativePlatformComponents.guardLoad(this)){super.loadAdditional(original,registries);guard.requireSuccessful();}retained=original.copy();for(String field:List.of("NativeCopySchema","CopyKey","CopyKeyMost","CopyKeyLeast","CopyActor","CopyActorMost","CopyActorLeast","CopyKnown","CopyHigh","CopyHeight","CopyIncrement","CopyMode","CopyChannels","CopyUseChip","CopyInput","CopyOutput","CopyStatus","CopyError","CopyBusy","CopyActiveTicks"))retained.remove(field);NativePlatformComponents.forgetLoadedFields(retained);}settings=next;key=nextKey==null?UUID.randomUUID():nextKey;actor=nextActor;input=left;output=right;known=tag.getBoolean("CopyKnown");high=tag.getBoolean("CopyHigh");clientBusy=tag.getBoolean("CopyBusy");loadedActiveTicks=Math.max(0,Math.min(20,tag.getInt("CopyActiveTicks")));activeUntil=level==null?0:level.getGameTime()+loadedActiveTicks;status=bounded(tag.getString("CopyStatus"));error=bounded(tag.getString("CopyError"));inert=tag.getBoolean("NativeCopyInert");refresh=true;}catch(RuntimeException failure){retained=original;inert=true;input=output=null;clientBusy=false;}}
}
