package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.credentials.NativeTokenReader;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.screen.ScreenChipMount;
import com.vandorlabs.screen.ScreenFamily;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One private embedded owner and its separately projected journal page. No discovery during decode. */
final class NativeScreenChipService {
 static final String VIEW="NativeScreenChipView",PAGE="ChipDisplayPage";
 record Prepared(NativeEmbeddedChipReader reader,int page,NativeScreenChipView client){}
 private final NativeScreenEntity owner;
 private NativeEmbeddedChipReader reader;
 private int page;
 private NativeScreenChipView client,cached,externalSource,externalDisplay;
 private NativeTokenReader.Visual cachedVisual;
 private NativeTokenReader.Motion cachedMotion;
 private int cachedPage=-1;
 private final NativeTokenReader.Host host=new NativeTokenReader.Host(){
  public BlockEntity owner(){return owner;}
  public boolean usable(ServerPlayer player){return NativeScreenChipService.this.usable(player);}
  public void changed(){page=0;cached=null;owner.chipChanged();}
  public void schedule(int ticks){throw new IllegalStateException("Passive Screen reader cannot schedule return ownership");}
 };
 NativeScreenChipService(NativeScreenEntity owner){this.owner=owner;}
 Prepared prepare(CompoundTag tag,HolderLookup.Provider registries){
  NativeEmbeddedChipReader next=null;if(tag.contains("ScreenChipReader",Tag.TAG_COMPOUND)){next=new NativeEmbeddedChipReader(host);next.read(tag.getCompound("ScreenChipReader"),registries);}
  NativeScreenChipView projected=null;if(owner.getLevel()!=null&&owner.getLevel().isClientSide&&tag.contains(VIEW)){if(!tag.contains(VIEW,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Screen chip projection envelope");projected=NativeScreenChipView.read(tag.getCompound(VIEW));}
  // Released saved pages clamp to 256 before the journal is lazily materialized.
  boolean clientLevel=owner.getLevel()!=null&&owner.getLevel().isClientSide;int restored=projected!=null?projected.page:Math.max(0,Math.min(clientLevel?415:256,tag.getInt(PAGE)));return new Prepared(next,restored,projected);
 }
 void install(Prepared prepared){externalSource=null;externalDisplay=null;reader=prepared.reader;page=prepared.page;client=prepared.client;cached=null;cachedVisual=null;cachedMotion=null;cachedPage=-1;}
 void clear(){externalSource=null;externalDisplay=null;reader=null;client=null;cached=null;cachedVisual=null;cachedMotion=null;page=0;cachedPage=-1;}
 boolean selected(int slot){return owner.family().supportsSlot(slot)&&(slot==0?owner.screenSettings().primarySurface():owner.screenSettings().secondarySurface())==-4&&!owner.controls(slot).enabled();}
 boolean selected(){return selected(0)||selected(1);}
 int page(){return page;}
 NativeScreenChipView view(){
  if(owner.inert()||!selected())return null;if(owner.getLevel()!=null&&owner.getLevel().isClientSide)return client==null?NativeScreenChipView.empty():client;
  if(!ScreenChipMount.integrated(owner.family())||reader==null)return NativeScreenChipView.empty();long now=owner.getLevel()==null?0:owner.getLevel().getGameTime();var visual=reader.visual(now);var motion=reader.motion(now);int actual=reader.journal()==null?0:Math.max(0,Math.min(reader.journal().pages.size()-1,page));if(cached==null||cachedPage!=actual||!visual.equals(cachedVisual)||!motion.equals(cachedMotion)){cached=NativeScreenChipView.of(reader,now,actual);cachedVisual=visual;cachedMotion=motion;cachedPage=actual;}return cached;
 }
 NativeScreenChipView display(NativeScreenChipView source){if(source==null)source=NativeScreenChipView.empty();int actual=source.journal==null?0:Math.max(0,Math.min(source.journal.pages.size()-1,page));if(source!=externalSource||externalDisplay==null||externalDisplay.page!=actual){externalSource=source;externalDisplay=source.withPage(actual);}return externalDisplay;}
 void write(CompoundTag tag,boolean publicProjection,HolderLookup.Provider registries){
  tag.remove(VIEW);if(publicProjection){tag.remove("ScreenChipReader");var view=view();if(view!=null&&ScreenChipMount.integrated(owner.family()))tag.put(VIEW,view.write());}
  else if(reader!=null){if(reader.occupied())tag.put("ScreenChipReader",reader.write(registries));else if(tag.getCompound("ScreenChipReader").contains("InsertedToken"))tag.remove("ScreenChipReader");}
  if(owner.screenSettings().primarySurface()==-4||owner.screenSettings().secondarySurface()==-4)tag.putInt(PAGE,page);else tag.remove(PAGE);
 }
 private boolean installed(ServerPlayer player){return owner.getLevel() instanceof ServerLevel level&&level.getServer().isSameThread()&&NativeRedstoneChannels.open(level)&&!owner.inert()&&!owner.isRemoved()&&player.serverLevel()==level&&level.hasChunkAt(owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner;}
 private boolean usable(ServerPlayer player){return owner.getLevel() instanceof ServerLevel level&&level.getServer().isSameThread()&&NativeRedstoneChannels.open(level)&&!owner.inert()&&!owner.isRemoved()&&player.serverLevel()==level&&level.hasChunkAt(owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner&&NativeConfigurationAccess.canEditDialog(player,owner);}
 boolean interact(ServerPlayer player,InteractionHand hand){if(!usable(player)||!ScreenChipMount.integrated(owner.family()))return false;if(reader==null){reader=new NativeEmbeddedChipReader(host);reader.read(new CompoundTag(),owner.getLevel().registryAccess());}return reader.interact(player,hand);}
 boolean navigate(ServerPlayer player,NativeScreenEntity authorizedClicked,NativeScreenChipView contents,int delta){if(!installed(player)||authorizedClicked==null||authorizedClicked.getLevel()!=owner.getLevel()||authorizedClicked.inert()||authorizedClicked.isRemoved()||!owner.getLevel().hasChunkAt(authorizedClicked.getBlockPos())||owner.getLevel().getBlockEntity(authorizedClicked.getBlockPos())!=authorizedClicked||!NativeConfigurationAccess.canEditDialog(player,authorizedClicked)||contents==null||contents.journal==null||(delta!=-1&&delta!=1))return false;int current=Math.max(0,Math.min(contents.journal.pages.size()-1,page));page=Math.max(0,Math.min(contents.journal.pages.size()-1,current+delta));cached=null;owner.chipChanged();return true;}
}
