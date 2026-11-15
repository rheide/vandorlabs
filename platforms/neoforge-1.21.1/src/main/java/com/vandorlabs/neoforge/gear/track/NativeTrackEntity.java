package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.gear.track.core.*;
import com.vandorlabs.neoforge.gear.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;

/** Cached track artwork belongs to each cell; arm ownership remains in the shared arm host. */
public final class NativeTrackEntity extends NativeGearEntity {
    public record Visual(int style,int facing,int key,boolean visible){}
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    private int style;
    private String trackError="";
    @Override public String error(){var root=controllerEntity();return root!=null&&root!=this&&!root.error().isEmpty()?root.error():trackError;}
    private int key=16;
    private boolean pending=true,complete;
    private long nextCheck;
    private BlockPos leader;
    private List<BlockPos> trackMembers=List.of();
    public NativeTrackEntity(BlockEntityType<NativeGearEntity> type,BlockPos pos,BlockState state){super(type,pos,state);style=((NativeTrackBlock)state.getBlock()).defaultStyle();}
    public TrackTopology.Style style(){return TrackTopology.Style.values()[style];}
    public TrackVisualState appearance(){return TrackVisualState.strict(key);}
    public List<BlockPos> trackMembers(){return trackMembers;}
    public BlockPos trackLeader(){return leader;}
    public boolean complete(){return complete;}
    public boolean trackPending(){return pending;}
    public long nextCheck(){return nextCheck;}
    public void trackStyle(int value){if(value<0||value>2)throw new IllegalArgumentException("Unknown Track style");if(level==null||level.isClientSide)throw new IllegalStateException("Track style authority");if(style!=value){style=value;requestRebuild();setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}}
    @Override public void requestRebuild(){super.requestRebuild();pending=true;nextCheck=level==null?0:level.getGameTime()+1;}
    public void installTrack(int appearance,BlockPos leader,List<BlockPos> members,boolean complete,String error){TrackVisualState.strict(appearance);boolean changed=key!=appearance||!Objects.equals(this.leader,leader)||this.complete!=complete||!trackError.equals(error)||!trackMembers.equals(members);key=appearance;this.leader=leader;trackMembers=List.copyOf(members);this.complete=complete;trackError=error;pending=false;nextCheck=level.getGameTime()+40;if(changed){setChanged();requestModelDataUpdate();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}}
    @Override public ModelData getModelData(){return ModelData.builder().with(VISUAL,new Visual(style,getBlockState().getValue(NativeGearBlock.FACING).get2DDataValue(),key,controllerEntity()==null)).build();}
    private static long packed(BlockPos pos){return com.vandorlabs.persistence.LegacyBlockPosition.pack(pos.getX(),pos.getY(),pos.getZ());}
    private static BlockPos unpack(long value){return new BlockPos(com.vandorlabs.persistence.LegacyBlockPosition.x(value),com.vandorlabs.persistence.LegacyBlockPosition.y(value),com.vandorlabs.persistence.LegacyBlockPosition.z(value));}
    private void writeTrack(CompoundTag tag){tag.putInt("TrackStyle",style);var appearance=new CompoundTag();appearance.putInt("Key",key);tag.put("Appearance",appearance);tag.putBoolean("TrackComplete",complete);tag.putString("TrackError",trackError);if(leader!=null)tag.putLong("TrackLeaderOffset",packed(leader.subtract(worldPosition)));else tag.remove("TrackLeaderOffset");var members=new ListTag();for(var pos:trackMembers)members.add(LongTag.valueOf(packed(pos.subtract(worldPosition))));tag.put("TrackMembers",members);}
    @Override protected void validateFamily(CompoundTag tag){
        if(tag.contains("TrackComplete")&&(!tag.contains("TrackComplete",Tag.TAG_BYTE)||tag.getByte("TrackComplete")<0||tag.getByte("TrackComplete")>1))throw new IllegalArgumentException("Malformed Track complete flag");
        if(tag.contains("TrackError")&&(!tag.contains("TrackError",Tag.TAG_STRING)||tag.getString("TrackError").length()>256))throw new IllegalArgumentException("Malformed Track error");
        if(tag.contains("TrackStyle")&&(!tag.contains("TrackStyle",Tag.TAG_INT)||tag.getInt("TrackStyle")<0||tag.getInt("TrackStyle")>2))throw new IllegalArgumentException("Unknown Track style");
        if(tag.contains("Appearance")&&!tag.contains("Appearance",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed Track appearance");
        if(tag.contains("Appearance")){var appearance=tag.getCompound("Appearance");if(!appearance.contains("Key",Tag.TAG_INT))throw new IllegalArgumentException("Malformed Track key");if(tag.contains("NativeGearSchema"))TrackVisualState.strict(appearance.getInt("Key"));}
        if(tag.contains("TrackLeaderOffset")&&!tag.contains("TrackLeaderOffset",Tag.TAG_LONG))throw new IllegalArgumentException("Malformed Track leader");
        if(tag.contains("TrackMembers")&&!tag.contains("TrackMembers",Tag.TAG_LIST))throw new IllegalArgumentException("Malformed Track members");
        if(tag.contains("TrackMembers")){var list=(ListTag)tag.get("TrackMembers");if(list.size()>64||!list.isEmpty()&&list.getElementType()!=Tag.TAG_LONG)throw new IllegalArgumentException("Track member bound/type");}
    }
    @Override public void placed(Direction face,CompoundTag choices){if(choices!=null)validateFamily(choices);super.placed(face,choices);if(choices!=null&&choices.contains("TrackStyle"))trackStyle(choices.getInt("TrackStyle"));}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);if(!inert())writeTrack(tag);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);if(inert())return;style=tag.contains("TrackStyle",Tag.TAG_INT)?tag.getInt("TrackStyle"):((NativeTrackBlock)getBlockState().getBlock()).defaultStyle();if(style<0||style>2)throw new IllegalArgumentException("Unknown Track style");key=tag.contains("Appearance",Tag.TAG_COMPOUND)?TrackVisualState.legacy(tag.getCompound("Appearance").getInt("Key")).key():16;leader=tag.contains("TrackLeaderOffset",Tag.TAG_LONG)?worldPosition.offset(unpack(tag.getLong("TrackLeaderOffset"))):null;trackMembers=List.of();complete=false;pending=true;nextCheck=0;if(level!=null&&level.isClientSide){requestModelDataUpdate();if(level.getBlockEntity(worldPosition)==this)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);writeTrack(tag);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
