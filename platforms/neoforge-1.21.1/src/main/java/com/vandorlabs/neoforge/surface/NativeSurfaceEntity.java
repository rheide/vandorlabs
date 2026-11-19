package com.vandorlabs.neoforge.surface;

import com.vandorlabs.surface.core.*;
import com.vandorlabs.neoforge.NativePlatformComponents;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;
import java.util.*;

/** Saved artwork and claims; complete per-cell terrain faces never depend on one drawing root. */
public final class NativeSurfaceEntity extends BlockEntity {
    public record Visual(String texture,int mode,int width,int height,int u,int v,Direction normal,boolean inert){}
    public static final ModelProperty<Visual> VISUAL=new ModelProperty<>();
    private String on=SurfaceArtwork.IDS[1],off=SurfaceArtwork.IDS[0];
    private int mode=2;
    private boolean configured,inert,active;
    private BlockPos owner,groupOffset=BlockPos.ZERO;
    private UUID ownerKey;
    private int groupWidth=1,groupHeight=1,groupNormal=1;
    private NativeSurfaceLayout cached;
    private CompoundTag retained=new CompoundTag();
    private ModelData model;
    public long discoveryScans,visualPackets,serialized;
    public NativeSurfaceEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);publish();}
    public boolean inert(){return inert;}
    public String activeArtwork(){return on;}
    public String inactiveArtwork(){return off;}
    public int layoutMode(){return mode;}
    public Direction normal(){return getBlockState().getValue(NativeSurfaceBlock.FACING);}
    public boolean sameArtwork(NativeSurfaceEntity other){return other!=null&&on.equals(other.on)&&off.equals(other.off);}
    public NativeSurfaceController claimedOwner(){if(level==null||owner==null||ownerKey==null||!level.hasChunkAt(owner))return null;return level.getBlockEntity(owner) instanceof NativeSurfaceController controller&&ownerKey.equals(controller.surfaceKey())&&controller.ownsSurface(worldPosition)?controller:null;}
    public boolean claimedBy(BlockPos controller,UUID identity){return Objects.equals(owner,controller)&&Objects.equals(ownerKey,identity);}
    /** Explicit saved claim identity, even when the claimant is unloaded or its tile is absent. */
    public boolean claimedByOther(BlockPos controller,UUID identity){return owner!=null&&!claimedBy(controller,identity);}
    public boolean canClaim(BlockPos controller,UUID key){return owner==null||owner.equals(controller)&&Objects.equals(ownerKey,key)||level!=null&&level.hasChunkAt(owner)&&claimedOwner()==null;}
    public boolean locked(){if(inert||owner!=null&&level!=null&&!level.hasChunkAt(owner))return true;var controller=claimedOwner();return controller!=null&&controller.surfaceLocked();}
    public void bind(BlockPos controller,UUID key){if(!canClaim(controller,key))throw new IllegalArgumentException("Programmable Surface is claimed by another controller");owner=controller.immutable();ownerKey=key;changed();schedule();}
    public void release(){owner=null;ownerKey=null;setGroup(NativeSurfaceLayout.single(worldPosition,normal()));changed();schedule();}
    public NativeSurfaceLayout layout(){
        if(cached==null){var face=Direction.from3DDataValue(groupNormal);if(face!=normal())return NativeSurfaceLayout.single(worldPosition,normal());var basis=NativeSurfaceLayout.face(face);var origin=worldPosition.offset(groupOffset);var cells=new ArrayList<BlockPos>();for(int v=0;v<groupHeight;v++)for(int u=0;u<groupWidth;u++)cells.add(origin.offset(basis.ux*u+basis.vx*v,basis.uy*u+basis.vy*v,basis.uz*u+basis.vz*v));try{cached=new NativeSurfaceLayout(cells,face,true);}catch(IllegalArgumentException invalid){cached=NativeSurfaceLayout.single(worldPosition,normal());}}
        return cached;
    }
    public NativeSurfaceLayout appearanceLayout(){var layout=layout();return layout.geometry.independentArtwork(mode)?NativeSurfaceLayout.single(worldPosition,normal()):layout;}
    private void publish(){var layout=appearanceLayout();var geometry=layout.geometry;int u=geometry.face.u(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ())-geometry.minU,v=geometry.face.v(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ())-geometry.minV;model=ModelData.builder().with(VISUAL,new Visual(active?on:off,mode,geometry.width,geometry.height,u,v,normal(),inert)).build();if(level!=null&&level.isClientSide&&level.getBlockEntity(worldPosition)==this)requestModelDataUpdate();}
    @Override public ModelData getModelData(){return model;}
    private void changed(){setChanged();publish();if(level!=null&&!level.isClientSide){visualPackets++;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}}
    public void activityChanged(){boolean next=claimedOwner()!=null&&claimedOwner().surfaceActive(worldPosition);if(active!=next){active=next;changed();}}
    public void setGroup(NativeSurfaceLayout layout){var offset=layout.cell(0,0,0).subtract(worldPosition);boolean different=!offset.equals(groupOffset)||groupWidth!=layout.geometry.width||groupHeight!=layout.geometry.height||groupNormal!=layout.normal.get3DDataValue();groupOffset=offset;groupWidth=layout.geometry.width;groupHeight=layout.geometry.height;groupNormal=layout.normal.get3DDataValue();cached=layout;if(different)changed();}
    public void schedule(){NativeSurfaceUpdates.queue(this);}
    public void refreshGroup(){
        if(!(level instanceof ServerLevel)||inert||isRemoved()||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,worldPosition))return;discoveryScans++;var controller=claimedOwner();
        if(controller==null&&owner==null){for(Direction side:Direction.values()){var at=worldPosition.relative(side);if(!level.hasChunkAt(at)||!(level.getBlockEntity(at) instanceof NativeSurfaceEntity neighbor))continue;var adjacent=neighbor.claimedOwner();if(adjacent!=null&&neighbor.normal()==normal()&&adjacent.acceptsExtension(at,worldPosition)){adjacent.surfaceChanged();break;}}}
        if(owner!=null&&controller==null){if(!level.hasChunkAt(owner))return;owner=null;ownerKey=null;changed();}
        if(controller!=null&&controller.joinsArtwork()){var layout=controller.surfaceLayout(worldPosition);if(layout!=null){setGroup(layout);activityChanged();return;}}
        Set<BlockPos> members;
        try{members=NativeSurfaceLayout.connected(level,worldPosition,p->{if(!level.hasChunkAt(p))return true;if(!(level.getBlockEntity(p) instanceof NativeSurfaceEntity tile))return true;return controller==null?tile.claimedOwner()==null:controller.ownsSurface(p)&&controller.joinsArtwork(worldPosition,p)&&tile.mode==mode;},true);}catch(IllegalArgumentException unavailable){return;}
        for(var pos:members)if(com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,pos))return;
        NativeSurfaceLayout group=null;try{group=new NativeSurfaceLayout(members,normal(),true);}catch(IllegalArgumentException irregular){}
        for(var pos:members)if(level.getBlockEntity(pos) instanceof NativeSurfaceEntity tile){NativeSurfaceUpdates.settled(tile);tile.setGroup(group==null?NativeSurfaceLayout.single(pos,tile.normal()):group);tile.activityChanged();}
    }
    public void placedAppearance(String on,String off,int mode){if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||server.getBlockEntity(worldPosition)!=this||inert||!NativeSurfaceArtwork.valid(on)||!NativeSurfaceArtwork.valid(off))throw new IllegalArgumentException("Surface placement settings");SurfaceArtwork.checkMode(mode);this.on=on;this.off=off;this.mode=mode;configured=true;changed();schedule();}
    public boolean configure(ServerPlayer player,String on,String off,int mode){
        if(!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||inert||!NativeSurfaceArtwork.valid(on)||!NativeSurfaceArtwork.valid(off)||mode<0||mode>2||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(player,this))return false;
        var layout=layout();var tiles=new ArrayList<NativeSurfaceEntity>();
        for(var pos:layout.cells){if(!server.hasChunkAt(pos)||!server.mayInteract(player,pos)||!player.mayUseItemAt(pos,normal(),player.getMainHandItem())||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(server,pos)||!(server.getBlockEntity(pos) instanceof NativeSurfaceEntity tile)||tile.inert)return false;tiles.add(tile);}
        for(var tile:tiles){tile.on=on;tile.off=off;tile.mode=mode;tile.configured=true;tile.changed();tile.schedule();for(var side:Direction.values()){var next=tile.worldPosition.relative(side);if(server.hasChunkAt(next)&&server.getBlockEntity(next) instanceof NativeSurfaceEntity other)other.schedule();}}return true;
    }
    public void removed(){var controller=claimedOwner();if(controller!=null)controller.surfaceChanged();NativeSurfaceUpdates.settled(this);if(level!=null&&!level.isClientSide)for(var side:Direction.values()){var next=worldPosition.relative(side);if(level.hasChunkAt(next)&&level.getBlockEntity(next) instanceof NativeSurfaceEntity tile)tile.schedule();}}
    @Override public void onLoad(){super.onLoad();schedule();}
    @Override public void setRemoved(){NativeSurfaceUpdates.settled(this);super.setRemoved();}
    @Override public void onChunkUnloaded(){NativeSurfaceUpdates.settled(this);super.onChunkUnloaded();}
    private void appearance(CompoundTag tag){tag.putString("SurfaceOn",on);tag.putString("SurfaceOff",off);tag.putInt("SurfaceLayout",mode);tag.putBoolean("SurfaceAppearanceConfigured",configured);tag.putLong("SurfaceGroupOffset",groupOffset.asLong());tag.putInt("SurfaceGroupSize",groupWidth);if(groupWidth!=groupHeight)tag.putInt("SurfaceGroupHeight",groupHeight);else tag.remove("SurfaceGroupHeight");tag.putInt("SurfaceGroupNormal",groupNormal);if(owner!=null&&ownerKey!=null){tag.putLong("Owner",owner.asLong());tag.putUUID("OwnerKey",ownerKey);}else{tag.remove("Owner");tag.remove("OwnerKey");}}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){serialized++;tag.merge(retained.copy());if(inert){NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);tag.putInt("NativeSurfaceSchema",1);appearance(tag);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        var original=tag.copy();try{
            if(tag.contains("NativeSurfaceSchema")&&(!tag.contains("NativeSurfaceSchema",Tag.TAG_INT)||tag.getInt("NativeSurfaceSchema")!=1))throw new IllegalArgumentException("Future Surface schema");
            for(String key:List.of("SurfaceAppearanceConfigured","SurfacePublic","SurfaceActive","NativeSurfaceInert"))if(tag.contains(key)&&(!tag.contains(key,Tag.TAG_BYTE)||tag.getByte(key)<0||tag.getByte(key)>1))throw new IllegalArgumentException("Surface boolean field");
            boolean hasOwner=tag.contains("Owner"),hasOwnerKey=tag.contains("OwnerKey")||tag.contains("OwnerKeyMost")||tag.contains("OwnerKeyLeast");if(hasOwner!=hasOwnerKey||tag.contains("OwnerKey")&&!tag.hasUUID("OwnerKey"))throw new IllegalArgumentException("Surface owner identity");
            for(String key:List.of("SurfaceOn","SurfaceOff"))if(tag.contains(key)&&(!tag.contains(key,Tag.TAG_STRING)||!NativeSurfaceArtwork.valid(tag.getString(key))))throw new IllegalArgumentException("Surface artwork");
            int nextMode=tag.contains("SurfaceLayout")?tag.getInt("SurfaceLayout"):2;SurfaceArtwork.checkMode(nextMode);
            for(String key:List.of("SurfaceLayout","SurfaceGroupSize","SurfaceGroupHeight","SurfaceGroupNormal"))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Surface integer field");
            for(String key:List.of("Owner","SurfaceGroupOffset"))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_LONG))throw new IllegalArgumentException("Surface position field");
            int width=tag.contains("SurfaceGroupSize")?tag.getInt("SurfaceGroupSize"):1,height=tag.contains("SurfaceGroupHeight")?tag.getInt("SurfaceGroupHeight"):width,normal=tag.contains("SurfaceGroupNormal")?tag.getInt("SurfaceGroupNormal"):1;boolean nativePositions=tag.contains("NativeSurfaceSchema")||tag.getBoolean("SurfacePublic");var offset=position(tag.getLong("SurfaceGroupOffset"),nativePositions);
            if(width<1||width>32||height<1||height>32||normal<0||normal>5||Math.abs((long)offset.getX())>32||Math.abs((long)offset.getY())>32||Math.abs((long)offset.getZ())>32)throw new IllegalArgumentException("Surface group bounds");
            boolean publicView=level!=null&&level.isClientSide&&tag.getBoolean("SurfacePublic");if(tag.getBoolean("SurfacePublic")&&!publicView)throw new IllegalArgumentException("Public Surface data is not a physical owner");
            if(!publicView){NativePlatformComponents.requireSupported(original,registries);try(var guard=NativePlatformComponents.guardLoad(this)){super.loadAdditional(original,registries);guard.requireSuccessful();}retained=original.copy();NativePlatformComponents.forgetLoadedFields(retained);for(String key:List.of("NativeSurfaceSchema","SurfaceOn","SurfaceOff","SurfaceLayout","SurfaceAppearanceConfigured","SurfaceGroupOffset","SurfaceGroupSize","SurfaceGroupHeight","SurfaceGroupNormal","Owner","OwnerKey","OwnerKeyMost","OwnerKeyLeast"))retained.remove(key);}
            on=NativeSurfaceArtwork.normalize(tag.getString("SurfaceOn"),true);off=NativeSurfaceArtwork.normalize(tag.getString("SurfaceOff"),false);mode=nextMode;configured=tag.getBoolean("SurfaceAppearanceConfigured");owner=tag.contains("Owner")?position(tag.getLong("Owner"),nativePositions):null;ownerKey=tag.hasUUID("OwnerKey")?tag.getUUID("OwnerKey"):null;if(ownerKey==null&&(tag.contains("OwnerKeyMost")||tag.contains("OwnerKeyLeast"))){if(!tag.contains("OwnerKeyMost",Tag.TAG_LONG)||!tag.contains("OwnerKeyLeast",Tag.TAG_LONG))throw new IllegalArgumentException("Malformed Surface owner");ownerKey=new UUID(tag.getLong("OwnerKeyMost"),tag.getLong("OwnerKeyLeast"));}groupOffset=offset;groupWidth=width;groupHeight=height;groupNormal=normal;active=tag.getBoolean("SurfaceActive");inert=tag.getBoolean("NativeSurfaceInert");cached=null;publish();if(level!=null&&level.isClientSide&&level.getBlockEntity(worldPosition)==this)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
        }catch(RuntimeException invalid){retained=original;inert=true;owner=null;ownerKey=null;groupWidth=groupHeight=1;groupOffset=BlockPos.ZERO;cached=null;publish();}
    }
    private static BlockPos position(long packed,boolean nativePositions){return nativePositions?BlockPos.of(packed):new BlockPos(com.vandorlabs.persistence.LegacyBlockPosition.x(packed),com.vandorlabs.persistence.LegacyBlockPosition.y(packed),com.vandorlabs.persistence.LegacyBlockPosition.z(packed));}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=new CompoundTag();appearance(tag);tag.putBoolean("SurfacePublic",true);tag.putBoolean("SurfaceActive",active);tag.putBoolean("NativeSurfaceInert",inert);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
