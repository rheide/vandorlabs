package com.vandorlabs.neoforge.door;
import com.vandorlabs.render.PanelDepthPolicy;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
/** Large item: clicked bottom center, three supports, all nine edit checks. */
public final class NativeLargeDoorItem extends BlockItem {
 public NativeLargeDoorItem(NativeLargeDoorBlock block,Properties properties){super(block,properties);}
 /**
  * A complete joining door covering one of the four 3x3 squares beside this footprint in its plane,
  * with a different facing; null when none exists or one already shares {@code face}.
  */
 private static NativeLargeDoorEntity orientationPartner(net.minecraft.world.level.Level level,BlockPos anchor,Direction face,boolean horizontal){
  Direction width=face.getCounterClockWise(),row=NativeLargeDoorLifecycle.rows(face,horizontal);
  BlockPos middle=anchor.relative(width).relative(row);NativeLargeDoorEntity partner=null;
  for(Direction step:new Direction[]{width,width.getOpposite(),row,row.getOpposite()}){
   BlockPos at=middle.relative(step,com.vandorlabs.render.LargeDoorJoin.PITCH);
   if(!NativeLargeDoorLifecycle.loaded(level,at)||!(level.getBlockEntity(at) instanceof NativeLargeDoorEntity part))continue;
   BlockPos rootPos=part.anchorPos();
   if(!NativeLargeDoorLifecycle.loaded(level,rootPos)||!(level.getBlockEntity(rootPos) instanceof NativeLargeDoorEntity root)||!root.isAnchor()
           ||!root.joinEnabled()||root.isHorizontal()!=horizontal||!NativeLargeDoorLifecycle.complete(level,root))continue;
   Direction front=root.getBlockState().getValue(NativeLargeDoorBlock.FACING);
   if(!NativeLargeDoorLifecycle.cell(rootPos,front,horizontal,1,1).equals(at))continue;
   if(front==face)return null;
   if(partner==null&&(horizontal||front.getAxis()==face.getAxis()))partner=root;
  }
  return partner;
 }
 @Override public InteractionResult place(BlockPlaceContext context){
  var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var block=(NativeLargeDoorBlock)getBlock();BlockPos center=context.getClickedPos();boolean horizontal=context.getClickedFace().getAxis().isHorizontal()&&!context.replacingClickedOnBlock();Direction face=horizontal?context.getClickedFace():context.getHorizontalDirection().getOpposite(),width=face.getCounterClockWise();BlockPos anchor=center.relative(width,-1);
  // Beside an existing joining door, take its orientation for the same 3x3 footprint so the two
  // join whichever wall was clicked; a neighbour already sharing this facing wins.
  var partner=orientationPartner(level,anchor,face,horizontal);
  if(partner!=null){BlockPos middle=anchor.relative(width).relative(NativeLargeDoorLifecycle.rows(face,horizontal));
   face=partner.getBlockState().getValue(NativeLargeDoorBlock.FACING);width=face.getCounterClockWise();
   anchor=middle.relative(width,-1).relative(NativeLargeDoorLifecycle.rows(face,horizontal),-1);center=anchor.relative(width);}
  if(player==null||stack.isEmpty()||!context.canPlace()||!NativeLargeDoorLifecycle.loaded(level,center)||level.isOutsideBuildHeight(anchor)||level.isOutsideBuildHeight(NativeLargeDoorLifecycle.cell(anchor,face,horizontal,2,2)))return InteractionResult.FAIL;
  for(int x=0;x<3;x++)for(int y=0;y<3;y++){BlockPos cell=NativeLargeDoorLifecycle.cell(anchor,face,horizontal,x,y);if(!NativeLargeDoorLifecycle.loaded(level,cell)||!level.getBlockState(cell).canBeReplaced(context)||level.getBlockState(cell).hasBlockEntity()||level.getBlockEntity(cell)!=null||com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(level,cell)||!level.mayInteract(player,cell)||!player.mayUseItemAt(cell,context.getClickedFace(),stack))return InteractionResult.FAIL;}
  CompoundTag configured=null,recovery=null;var custom=stack.get(DataComponents.CUSTOM_DATA);if(custom!=null){var tag=custom.copyTag();if(tag.contains("SpaceDoorSettings",Tag.TAG_COMPOUND))configured=tag.getCompound("SpaceDoorSettings");if(tag.contains(com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.RECOVERY_KEY)){if(!tag.contains(com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.RECOVERY_KEY,Tag.TAG_COMPOUND))return InteractionResult.FAIL;recovery=tag.getCompound(com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.RECOVERY_KEY);if(!com.vandorlabs.neoforge.credentials.NativeDoorCredentialService.validOpaqueRecovery(recovery,level.registryAccess()))return InteractionResult.FAIL;}}var hit=com.vandorlabs.neoforge.placement.NativePlacementHit.hit(context);int depth=horizontal?PanelDepthPolicy.fromHit((float)hit.y):PanelDepthPolicy.fromHit(face.get3DDataValue(),(float)hit.x,(float)hit.z);NativeDoorSettings settings;try{settings=NativeLargeDoorPlacement.choices(configured,depth);}catch(IllegalArgumentException malformed){return InteractionResult.FAIL;}var support=NativeLargeDoorLifecycle.supportDoor(level,anchor,face,horizontal);
  if(support==null||!support.joinEnabled()){
   var row=NativeLargeDoorLifecycle.rows(face,horizontal);
   for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
    if(Math.abs(dx)+Math.abs(dy)!=1)continue;
    var at=anchor.relative(width,3*dx).relative(row,3*dy);
    if(!NativeLargeDoorLifecycle.loaded(level,at)||!(level.getBlockEntity(at) instanceof NativeLargeDoorEntity candidate))continue;
    if(candidate.isAnchor()&&candidate.joinEnabled()&&candidate.isHorizontal()==horizontal&&candidate.getBlockState().getValue(NativeLargeDoorBlock.FACING)==face&&NativeLargeDoorLifecycle.complete(level,candidate))support=candidate;
   }
  }
  if(configured==null&&support!=null&&support.joinEnabled())settings=support.settings();
  boolean join=configured!=null&&configured.getBoolean(com.vandorlabs.render.LargeDoorJoin.TAG)
          ||support!=null&&support.joinEnabled();
  if(support!=null&&!NativeLargeDoorLifecycle.permitted(level,support,player))return InteractionResult.FAIL;
  if(!NativeLargeDoorLifecycle.placementSupported(level,anchor,face,horizontal,settings,join))return InteractionResult.FAIL;
  var channels=configured==null&&support!=null&&support.joinEnabled()?support.getRedstoneChannels():NativeLargeDoorPlacement.channels(configured);if(recovery!=null&&!recovery.isEmpty())try{for(int x=0;x<3;x++)for(int y=0;y<3;y++)if(!(block.newBlockEntity(NativeLargeDoorLifecycle.cell(anchor,face,horizontal,x,y),block.defaultBlockState().setValue(NativeLargeDoorBlock.FACING,face)) instanceof NativeLargeDoorEntity))return InteractionResult.FAIL;}catch(RuntimeException unavailable){return InteractionResult.FAIL;}if(level.isClientSide)return InteractionResult.SUCCESS;
  boolean[] transferred={false};Runnable transfer=()->{if(!transferred[0]){transferred[0]=true;stack.shrink(1);}};boolean success=false;try{success=NativeLargeDoorPlacement.install(context,block,anchor,face,horizontal,settings,channels,recovery,transfer,join);}finally{boolean owns=level.getBlockState(anchor).is(block)&&level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity root&&root.placementRecovery();if(recovery!=null&&!recovery.isEmpty()&&(!(level.getBlockEntity(anchor) instanceof NativeLargeDoorEntity root)||!root.opaqueCredentialRecovery().equals(recovery)))owns=false;if(success||owns)transfer.run();}if(!success)return InteractionResult.FAIL;var sound=block.defaultBlockState().getSoundType(level,anchor,player);level.playSound(player,center,sound.getPlaceSound(),SoundSource.BLOCKS,1,.8F);return InteractionResult.CONSUME;
 }
}
