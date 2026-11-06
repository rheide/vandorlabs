package com.vandorlabs.neoforge.door;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.TextureLayoutMode;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
/** Stage all nine identities and roles before ownership transfer or neighbor publication. */
public final class NativeLargeDoorPlacement {
 private NativeLargeDoorPlacement(){}
 private record Scope(Level level,BlockPos anchor,Direction width){}private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
 public static boolean mutating(Level level,BlockPos pos){var s=ACTIVE.get();if(s==null||s.level()!=level)return false;int dx=pos.getX()-s.anchor().getX(),dz=pos.getZ()-s.anchor().getZ(),x=dx*s.width().getStepX()+dz*s.width().getStepZ(),y=pos.getY()-s.anchor().getY();return x>=0&&x<3&&y>=0&&y<3&&dx==x*s.width().getStepX()&&dz==x*s.width().getStepZ();}
 public static NativeDoorSettings choices(CompoundTag configured,int depth){var s=configured==null?NativeDoorSettings.fresh(true,false):NativeDoorSettings.read(configured);int layout=configured==null?0:configured.contains("DoorTextureLayout",Tag.TAG_INT)?configured.getInt("DoorTextureLayout"):configured.getBoolean("DoorTileTexture")?1:0;if(!TextureLayoutMode.valid(layout))throw new IllegalArgumentException("Invalid configured Large Door texture layout");var d=s.door();int panel=s.rawPanelMode();if(depth!=0&&panel==2)panel=1;return new NativeDoorSettings(new SpaceDoorData(d.design,d.detail,d.framed,d.direction,depth==0,d.sliding,d.hinges,d.trigger,d.panel),s.faceTexture(),layout,layout==1,depth,panel,s.securityMode(),s.securityAllowed(),s.frames(),s.glass());}
 static ChannelList channels(CompoundTag t){if(t==null)return ChannelList.EMPTY;var fallback=ChannelList.of(Math.max(0,t.getInt("SpaceDoorChannel")));try{return t.contains("RedstoneChannels",Tag.TAG_INT_ARRAY)?ChannelList.of(t.getIntArray("RedstoneChannels")):fallback;}catch(IllegalArgumentException malformed){return fallback;}}
 static boolean install(BlockPlaceContext context,NativeLargeDoorBlock block,BlockPos anchor,Direction facing,NativeDoorSettings settings,ChannelList channels,CompoundTag recovery,Runnable transfer){
  Level level=context.getLevel();Direction width=facing.getCounterClockWise();var state=block.defaultBlockState().setValue(NativeLargeDoorBlock.FACING,facing);BlockPos[] cells=new BlockPos[9];BlockState[] previous=new BlockState[9];NativeLargeDoorEntity[] owners=new NativeLargeDoorEntity[9];for(int x=0;x<3;x++)for(int y=0;y<3;y++){int i=x*3+y;cells[i]=anchor.relative(width,x).above(y);previous[i]=level.getBlockState(cells[i]);}Scope old=ACTIVE.get();ACTIVE.set(new Scope(level,anchor,width));boolean success=false;Throwable original=null;
  try{for(int i=0;i<9;i++){if(!block.stagePart(level,cells[i],state)||level.getBlockState(cells[i])!=state||!(level.getBlockEntity(cells[i]) instanceof NativeLargeDoorEntity part))return false;owners[i]=part;part.assign(anchor);if(i==0)part.installPlacement(settings,channels);}for(int i=0;i<9;i++)if(level.getBlockEntity(cells[i])!=owners[i])return false;if(recovery!=null&&!owners[0].restoreOpaqueCredentialRecovery(recovery,level.registryAccess()))return false;transfer.run();success=true;
  }catch(RuntimeException|Error failure){original=failure;throw failure;}finally{Throwable failed=null;try{if(!success){for(int i=8;i>=0;i--)try{if(level.getBlockState(cells[i]).is(block)&&(owners[i]==null?level.getBlockState(cells[i])==state:level.getBlockEntity(cells[i])==owners[i]))if(!block.restorePart(level,cells[i],previous[i])&&level.getBlockState(cells[i])!=previous[i])throw new IllegalStateException("Large Door placement rollback refused at "+cells[i]);}catch(RuntimeException|Error failure){if(failed==null)failed=failure;else failed.addSuppressed(failure);}if(level.getBlockState(anchor).is(block)&&level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity root&&(owners[0]==null||root==owners[0])){root.assign(anchor);root.installPlacement(settings,channels);if(recovery!=null&&root.opaqueCredentialRecovery().isEmpty())root.restoreOpaqueCredentialRecovery(recovery,level.registryAccess());root.retainPlacementRecovery();}}}finally{if(old==null)ACTIVE.remove();else ACTIVE.set(old);}if(failed!=null){if(original!=null)original.addSuppressed(failed);else throw new IllegalStateException("Large Door rollback failed; surviving root retains ownership",failed);}}
  if(success){for(var owner:owners)owner.refreshFluidGeometry();for(BlockPos cell:cells)block.publishPart(level,cell,state);}return success;
 }
}
