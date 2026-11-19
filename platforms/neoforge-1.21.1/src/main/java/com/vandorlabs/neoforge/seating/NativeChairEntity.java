package com.vandorlabs.neoforge.seating;
import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.persistence.*;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.*;
/** One lower-cell owner. Pilot vehicle fields remain opaque and dormant in this stationary adapter. */
public final class NativeChairEntity extends BlockEntity {
    public static final String ITEM_SETTINGS="ChairSettings";public static final ModelProperty<View> VIEW=new ModelProperty<>();
    public record View(NativeSeatKind kind,int style,int height,int angle,boolean join,int pilotHeight,int part,int arms){}
    private ChairSettings settings=new ChairSettings(0,1,0,true);private int pilotHeight,part,arms;private boolean inert,opaqueProxy;
    private CompoundTag retained=new CompoundTag();private boolean retainedItem;private volatile View view;private ItemStack removalDrop;
    public NativeChairEntity(BlockPos pos,BlockState state){super(NativeSeatBindings.type(state),pos,state);publish();}
    public NativeSeatKind kind(){return ((NativeSeatBlock)getBlockState().getBlock()).kind;}
    public void prepareRemoval(ItemStack stack){removalDrop=stack.copy();}
    public ItemStack removalDrop(){return removalDrop==null?null:removalDrop.copy();}
    public void placed(){refreshAppearance();changed();}
    public void refreshAppearance(){if(level==null||NativeSeatBlock.proxy(getBlockState())||isRemoved()||!NativeSeatBlock.loaded(level,worldPosition)||level.getBlockEntity(worldPosition)!=this)return;var face=getBlockState().getValue(NativeSeatBlock.FACING);int nextPart=0,nextArms=0;
        if(kind().connected())nextPart=ChairSettings.part(settings.join,row(worldPosition.relative(face.getCounterClockWise())),row(worldPosition.relative(face.getClockWise())));
        if(kind()==NativeSeatKind.PILOT)nextArms=(solid(worldPosition.relative(face.getCounterClockWise()))?1:0)|(solid(worldPosition.relative(face.getClockWise()))?2:0);
        if(part!=nextPart||arms!=nextArms){part=nextPart;arms=nextArms;setChanged();publish();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    }
    private boolean row(BlockPos at){if(!NativeSeatBlock.loaded(level,at))return false;var other=level.getBlockState(at);if(!(other.getBlock() instanceof NativeSeatBlock)||!other.is(getBlockState().getBlock())||other.getValue(NativeSeatBlock.UPPER))return false;var neighbor=NativeSeatBlock.owner(level,at);return neighbor!=null&&ChairSettings.connects(true,true,true,other.getValue(NativeSeatBlock.FACING)==getBlockState().getValue(NativeSeatBlock.FACING),settings.height,neighbor.settings.height,neighbor.settings.join);}
    private boolean solid(BlockPos at){if(!NativeSeatBlock.loaded(level,at))return false;var state=level.getBlockState(at);return !(state.getBlock() instanceof NativeSeatBlock)&&net.minecraft.world.level.block.Block.isShapeFullBlock(state.getCollisionShape(level,at));}
    @Override public void onLoad(){super.onLoad();NativeSeatUpdates.queue(this);}

    void repairUpper(){if(inert||kind()!=NativeSeatKind.PILOT||NativeSeatBlock.proxy(getBlockState())||!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||isRemoved()||!NativeSeatBlock.loaded(server,worldPosition)||server.getBlockEntity(worldPosition)!=this)return;var upper=worldPosition.above();if(!NativeSeatBlock.loaded(server,upper)||server.isOutsideBuildHeight(upper)||!server.getBlockState(upper).isAir()||server.getBlockEntity(upper)!=null||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(server,upper))return;server.setBlock(upper,getBlockState().setValue(NativeSeatBlock.UPPER,true),3);}
    public boolean proxyShareable(){return NativeSeatBlock.proxy(getBlockState())&&!inert&&!opaqueProxy&&!hasAttachments()&&components().isEmpty()&&getPersistentData().isEmpty();}
    public boolean taxiMode(){return !retained.contains("TaxiMode",Tag.TAG_BYTE)||retained.getBoolean("TaxiMode");}
    public ChairSettings settings(){return settings;}public int pilotHeight(){return pilotHeight;}public boolean inert(){return inert;}public View view(){return view;}
    private void publish(){view=inert?new View(kind(),0,1,0,true,0,0,0):new View(kind(),settings.style,settings.height,settings.angle,settings.join,pilotHeight,part,arms);requestModelDataUpdate();}
    public net.minecraft.world.phys.Vec3 cushionOffset(){
        return kind()==NativeSeatKind.ADVANCED?NativeAdvancedChairGeometry.cushion(getBlockState().getValue(NativeSeatBlock.FACING),pilotHeight)
            :new net.minecraft.world.phys.Vec3(.5,cushion(),.5);
    }
    public double cushion(){if(kind()==NativeSeatKind.ADVANCED)return NativeAdvancedChairGeometry.cushion(getBlockState().getValue(NativeSeatBlock.FACING),pilotHeight).y;return kind()==NativeSeatKind.CHAIR?ChairSettings.cushion(settings.style,settings.height):kind().connected()?ChairSettings.connectedCushion(kind()==NativeSeatKind.LUXURY,settings.height):ChairSettings.pilotCushion(pilotHeight);}
    public double yaw(){return SeatPose.yaw(getBlockState().getValue(NativeSeatBlock.FACING).toYRot(),kind()==NativeSeatKind.CHAIR?settings.angle:0);}
    public boolean configure(Player actor,int style,int height,int angle,boolean join){
        if(NativeSeatBlock.proxy(getBlockState())||inert||!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||!NativeConfigurationAccess.canEditDialog(actor,this))return false;
        if(kind().adjustablePilotHeight()){if(height<0||height>3)return false;pilotHeight=height;}else{if(!ChairSettings.valid(style,height,angle))return false;settings=new ChairSettings(style,height,angle,join);}
        changed();return true;
    }
    public boolean configureCopied(ChairSettings next,int nextPilotHeight,Boolean taxi){
        if(inert||NativeSeatBlock.proxy(getBlockState())||!(level instanceof ServerLevel server)||!server.getServer().isSameThread()||isRemoved()||!server.hasChunkAt(worldPosition)||server.getBlockEntity(worldPosition)!=this||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(server,worldPosition)||nextPilotHeight<0||nextPilotHeight>3)return false;
        settings=next;pilotHeight=nextPilotHeight;if(kind()==NativeSeatKind.PILOT&&taxi!=null)retained.putBoolean("TaxiMode",taxi);changed();return true;
    }
    private void changed(){refreshAppearance();setChanged();publish();if(level==null)return;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);var upper=worldPosition.above();if(level.hasChunkAt(upper)){var state=level.getBlockState(upper);level.sendBlockUpdated(upper,state,state,3);}level.updateNeighborsAt(worldPosition,getBlockState().getBlock());
        if(!level.isClientSide)for(var seat:level.getEntitiesOfClass(NativeChairMount.class,new net.minecraft.world.phys.AABB(worldPosition).inflate(2)))if(worldPosition.equals(seat.chairPos()))seat.updatePose(this);
    }
    public CompoundTag configuration(){var tag=new CompoundTag();if(NativeSeatBlock.proxy(getBlockState()))return tag;if(kind().adjustablePilotHeight()){tag.putInt(ChairSettings.PILOT_HEIGHT,pilotHeight);}else{tag.putInt(ChairSettings.STYLE,settings.style);tag.putInt(ChairSettings.HEIGHT,settings.height);if(settings.angle!=0)tag.putInt(ChairSettings.ANGLE,settings.angle);if(kind().connected())tag.putBoolean(ChairSettings.JOIN,settings.join);}return tag;}
    private CompoundTag itemData(){var root=new CompoundTag();if(inert)root.put("NativeChairRetained",retained.copy());else {root.put(ITEM_SETTINGS,configuration());if(!retained.isEmpty())root.put("NativeChairPreserved",retained.copy());}return root;}
    public void writeItem(ItemStack stack){stack.applyComponents(collectComponents());}
    private boolean readSettings(CompoundTag tag){
        if(tag.contains("TaxiMode")&&!tag.contains("TaxiMode",Tag.TAG_BYTE))return false;
        for(String key:List.of(ChairSettings.STYLE,ChairSettings.HEIGHT,ChairSettings.ANGLE,ChairSettings.PILOT_HEIGHT))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_INT))return false;
        if(tag.contains(ChairSettings.JOIN)&&!tag.contains(ChairSettings.JOIN,Tag.TAG_BYTE))return false;
        settings=new ChairSettings(ChairSettings.style(tag.getInt(ChairSettings.STYLE)),ChairSettings.height(tag.contains(ChairSettings.HEIGHT,Tag.TAG_INT),tag.getInt(ChairSettings.HEIGHT)),ChairSettings.angle(tag.getInt(ChairSettings.ANGLE)),!tag.contains(ChairSettings.JOIN)||tag.getBoolean(ChairSettings.JOIN));
        if(tag.contains("SeatRenderPart")&&!tag.contains("SeatRenderPart",Tag.TAG_INT)||tag.contains("PilotArms")&&!tag.contains("PilotArms",Tag.TAG_INT))return false;part=tag.getInt("SeatRenderPart");arms=tag.getInt("PilotArms");if(part<0||part>3||arms<0||arms>3)return false;
        pilotHeight=ChairSettings.pilotHeight(tag.getInt(ChairSettings.PILOT_HEIGHT));return true;
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);retainedItem=false;retained=tag.copy();for(String key:List.of("id","x","y","z"))retained.remove(key);if(retained.contains("keepPacked",Tag.TAG_BYTE))retained.remove("keepPacked");opaqueProxy=retained.getAllKeys().stream().anyMatch(key->!key.equals("NativeChairSchema")&&!key.equals("NativeChairProxy")&&!(List.of("components","NeoForgeData","neoforge:attachments","ForgeCaps").contains(key)&&retained.contains(key,Tag.TAG_COMPOUND)&&retained.getCompound(key).isEmpty()));try{com.vandorlabs.neoforge.NativePlatformComponents.requireSupported(tag,registries);}catch(IllegalArgumentException unsupported){inert=true;publish();return;}if(tag.contains("NativeChairRetained")){inert=true;if(tag.contains("NativeChairRetained",Tag.TAG_COMPOUND)){retained=tag.getCompound("NativeChairRetained").copy();retainedItem=true;}publish();return;}inert=tag.contains("NativeChairSchema")&&(!tag.contains("NativeChairSchema",Tag.TAG_INT)||tag.getInt("NativeChairSchema")!=1)||!readSettings(tag);if(!inert)com.vandorlabs.neoforge.NativePlatformComponents.forgetLoadedFields(retained);publish();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){if(inert){if(retainedItem){super.saveAdditional(tag,registries);tag.put("NativeChairRetained",retained.copy());}else{com.vandorlabs.neoforge.NativePlatformComponents.clearForInertSave(this);tag.merge(retained);}return;}super.saveAdditional(tag,registries);tag.merge(retained);tag.putInt("NativeChairSchema",1);if(NativeSeatBlock.proxy(getBlockState())){tag.putBoolean("NativeChairProxy",true);return;}tag.merge(configuration());tag.putInt("SeatRenderPart",part);tag.putInt("PilotArms",arms);if(settings.angle==0)tag.remove(ChairSettings.ANGLE);}
    private void retainItem(CompoundTag raw){retained=raw.copy();retainedItem=true;inert=true;publish();}
    @Override protected void applyImplicitComponents(DataComponentInput input){super.applyImplicitComponents(input);var custom=input.get(DataComponents.CUSTOM_DATA);if(custom==null)return;var root=custom.copyTag();if(root.contains("NativeChairRetained")){retainItem(root.contains("NativeChairRetained",Tag.TAG_COMPOUND)?root.getCompound("NativeChairRetained"):root);return;}if(root.contains("NativeChairPreserved")){if(!root.contains("NativeChairPreserved",Tag.TAG_COMPOUND)){retainItem(root);return;}retained=root.getCompound("NativeChairPreserved").copy();if(retained.contains("components")||retained.contains("neoforge:attachments")||retained.contains("NeoForgeData")){retainItem(root);return;}}
        if(kind()==NativeSeatKind.ADVANCED&&!root.contains(ITEM_SETTINGS)&&root.contains("BlockEntityTag")){
            if(!root.contains("BlockEntityTag",Tag.TAG_COMPOUND)){retainItem(root);return;}
            retained=root.getCompound("BlockEntityTag").copy();
            for(String key:List.of("id","x","y","z"))retained.remove(key);
            if(!readSettings(retained)){retainItem(root);return;}
        }
        if(root.contains(ITEM_SETTINGS)&&(!root.contains(ITEM_SETTINGS,Tag.TAG_COMPOUND)||!readSettings(root.getCompound(ITEM_SETTINGS)))){retainItem(root);return;}publish();}
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder){super.collectImplicitComponents(builder);builder.set(DataComponents.CUSTOM_DATA,CustomData.of(itemData()));}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=configuration();tag.putInt("SeatRenderPart",part);tag.putInt("PilotArms",arms);tag.putBoolean("NativeChairInert",inert);return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){inert=!readSettings(tag)||tag.getBoolean("NativeChairInert");publish();if(level!=null){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);var upper=worldPosition.above();if(level.hasChunkAt(upper))level.sendBlockUpdated(upper,level.getBlockState(upper),level.getBlockState(upper),3);}}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public ModelData getModelData(){return ModelData.builder().with(VIEW,view).build();}
}
