package com.vandorlabs.neoforge.builders;
import net.minecraft.core.*;import net.minecraft.nbt.*;import net.minecraft.world.level.block.entity.*;import net.minecraft.world.level.block.state.BlockState;
/** A bounded relative member reference; malformed saves remain inert with their original data. */
public final class NativeBuilderPartEntity extends BlockEntity {
 private BlockPos offset=BlockPos.ZERO;private java.util.UUID key;private Boolean legacyMatter;private long savedOffset;private boolean inert=true;private CompoundTag retained=new CompoundTag();
 public NativeBuilderPartEntity(BlockPos pos,BlockState state){super(NativeBuilderBindings.PART_ENTITY,pos,state);}
 public boolean inert(){return inert;}public BlockPos owner(){return worldPosition.offset(offset);}public void owner(BlockPos root){offset=root.subtract(worldPosition);savedOffset=offset.asLong();legacyMatter=null;if(Math.abs(offset.getX())>16||Math.abs(offset.getY())>16||Math.abs(offset.getZ())>16||offset.equals(BlockPos.ZERO))throw new IllegalArgumentException("Builder part offset");if(level!=null&&NativeBuilderFormation.loaded(level,root)&&level.getBlockEntity(root) instanceof NativeBuilderEntity entity)key=entity.key();inert=false;setChanged();}
 public NativeBuilderEntity root(){if(inert||isRemoved()||level==null||!NativeBuilderFormation.loaded(level,worldPosition)||level.getBlockEntity(worldPosition)!=this||!NativeBuilderFormation.loaded(level,owner())||!(level.getBlockEntity(owner()) instanceof NativeBuilderEntity root)||root.inert()||!root.formed()||key!=null&&!key.equals(root.key())||worldPosition.equals(owner()))return null;var d=root.design();for(int x=0;x<d.width();x++)for(int y=0;y<d.height();y++)for(int z=0;z<d.depth();z++)if(root.cell(x,y,z).equals(worldPosition))return legacyMatter==null||NativeLegacyBuilderData.supports(d)&&root.layout().frame(x,y,z)==legacyMatter?root:null;return null;}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){tag.merge(retained.copy());if(inert){com.vandorlabs.neoforge.NativePlatformComponents.clearForInertSave(this);return;}super.saveAdditional(tag,registries);tag.putLong("ConstructorOwner",savedOffset);if(legacyMatter!=null)tag.putBoolean("ConstructorMatter",legacyMatter);if(key!=null)tag.putUUID("BuilderOwner",key);}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){var original=tag.copy();try{com.vandorlabs.neoforge.NativePlatformComponents.requireSupported(original,registries);if(tag.contains("ForgeCaps")&&(!tag.contains("ForgeCaps",Tag.TAG_COMPOUND)||!tag.getCompound("ForgeCaps").isEmpty()))throw new IllegalArgumentException("Unsupported legacy part capabilities");com.vandorlabs.neoforge.transfer.NativeConstructorFrame.validatePart(tag,true);var next=readOffset(tag);try(var guard=com.vandorlabs.neoforge.NativePlatformComponents.guardLoad(this)){super.loadAdditional(original,registries);guard.requireSuccessful();}offset=next;savedOffset=tag.getLong("ConstructorOwner");legacyMatter=tag.contains("ConstructorMatter")?tag.getBoolean("ConstructorMatter"):null;key=tag.hasUUID("BuilderOwner")?tag.getUUID("BuilderOwner"):null;retained=original;for(var k:java.util.List.of("id","x","y","z","ConstructorOwner","BuilderOwner","ConstructorMatter","ForgeCaps"))retained.remove(k);com.vandorlabs.neoforge.NativePlatformComponents.forgetLoadedFields(retained);inert=false;}catch(RuntimeException invalid){offset=BlockPos.ZERO;savedOffset=0;legacyMatter=null;key=null;inert=true;retained=original;}}
 static BlockPos readOffset(CompoundTag tag){
  if(!tag.contains("ConstructorOwner",Tag.TAG_LONG)||tag.contains("BuilderOwner")&&!tag.hasUUID("BuilderOwner"))
   throw new IllegalArgumentException("Builder part reference schema");
  long raw=tag.getLong("ConstructorOwner");
  BlockPos result=BlockPos.of(raw);
  if(tag.contains("ConstructorMatter")){
   if(tag.contains("BuilderOwner")||!tag.contains("ConstructorMatter",Tag.TAG_BYTE)
       ||tag.getByte("ConstructorMatter")<0||tag.getByte("ConstructorMatter")>1)
    throw new IllegalArgumentException("Legacy builder part reference schema");
   // Java Edition 1.12 packs x/y/z in that order, with signed 26/12/26-bit coordinates.
   result=new BlockPos((int)(raw>>38),(int)(raw<<26>>52),(int)(raw<<38>>38));
  }
  if(result.equals(BlockPos.ZERO)||Math.abs(result.getX())>16||Math.abs(result.getY())>16||Math.abs(result.getZ())>16)
   throw new IllegalArgumentException("Builder part reference bounds");
  return result;
 }
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){var tag=new CompoundTag();if(!inert){tag.putLong("ConstructorOwner",savedOffset);if(legacyMatter!=null)tag.putBoolean("ConstructorMatter",legacyMatter);if(key!=null)tag.putUUID("BuilderOwner",key);}return tag;}
 @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
}
