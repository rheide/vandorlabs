package com.vandorlabs.neoforge.segmented;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.copifier.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;
/** Actual public factories and native DDA, followed by the canonical suite's separate-process restart. */
final class SegmentedSuppliedArtworkHostChecks {
 private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Supplied Segmented host: "+why);}
 private static int choice(String id){for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(id.equals(ModernFinishCatalog.ENTRIES.get(i).id()))return i;throw new AssertionError("Missing supplied artwork "+id);}
 private static NativeSegmentedDoorEntity tile(ServerLevel world,BlockPos pos){return (NativeSegmentedDoorEntity)world.getBlockEntity(pos);}
 static void write(ServerLevel world,ServerPlayer actor,BlockPos base){
  var root=tile(world,base.above(2));var cell=tile(world,base.east().above());var latent=new NativeSegmentedDoorSettings(0,false,1,0,true,2,true,NativeSegmentedDoorSettings.DEFAULT.glass,0);int profiles=0;
  for(int kind=0;kind<3;kind++)for(boolean gate:new boolean[]{false,true}){
   int selected=choice(gate?SegmentedArtworkPolicy.gateId(kind):SegmentedArtworkPolicy.tileId(kind));actor.setPos(cell.getBlockPos().getX()+.5,cell.getBlockPos().getY()+.5,cell.getBlockPos().getZ()-2);root.configureAll(actor,ChannelList.EMPTY,10,false,true,selected,latent);cell.configureCellTexture(actor,selected,false);
   need(cell.effectiveTexture()==selected&&cell.settings().window&&cell.settings().shape==2&&cell.settings().join,"local selection preserves latent settings");var visual=cell.root().publication().visual();need(visual.texture(1)==selected,"coherent selected-column per-row publication");
   var picked=NativeSegmentedItem.pick(NativeSegmentedRegistrations.ITEM.get(),cell);var config=picked.get(DataComponents.CUSTOM_DATA).copyTag().getCompound(NativeSegmentedItem.CONFIGURATION);need(config.getInt(SegmentedCellArtwork.FIELD)==selected,"configured pick retains supplied local choice");
   var copied=NativeCopifierSegmentedEdit.capture(cell.copiedState());var converted=NativeCopifierCraftingItems.apply(new ItemStack(NativeSegmentedRegistrations.ITEM.get()),copied,world.registryAccess());need(!converted.isEmpty()&&converted.get(DataComponents.CUSTOM_DATA).copyTag().getCompound(NativeSegmentedItem.CONFIGURATION).getInt(SegmentedCellArtwork.FIELD)==selected,"copied recipe retains supplied local choice");need(tile(world,base.above()).configureCopied(copied,actor),"actual copied settings accept supplied local choice");
   if(gate){BlockPos lever=base.east().above().south();world.setBlock(lever.south(),Blocks.STONE.defaultBlockState(),2);world.setBlock(lever,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACING,Direction.NORTH).setValue(LeverBlock.FACE,AttachFace.WALL),3);boolean gap=false,bar=false;
    for(int x=2;x<15;x++)for(int y=3;y<14;y++){var start=new Vec3(base.getX()+1+x/16D,base.getY()+1+y/16D,base.getZ()-1);var end=start.add(0,0,4);var hit=world.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,actor));bar|=hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(cell.getBlockPos());if(!gap&&hit.getBlockPos().equals(lever)){gap=true;actor.setPos(start.x,start.y-actor.getEyeHeight(),start.z);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);need(actor.gameMode.useItemOn(actor,world,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit).consumesAction()&&world.getBlockState(lever).getValue(LeverBlock.POWERED),"actual vanilla lever use through authored gap");}}
    need(gap&&bar,"native Level.clip distinguishes real bars and open spaces kind="+kind);world.removeBlock(lever,false);world.removeBlock(lever.south(),false);
   }
   profiles++;
  }
  // Preserve one actual configured item placement across the process boundary.
  int selected=choice(SegmentedArtworkPolicy.gateId(2));root.configureAll(actor,ChannelList.EMPTY,10,false,true,selected,latent);cell.configureCellTexture(actor,selected,false);var stack=NativeSegmentedItem.pick(NativeSegmentedRegistrations.ITEM.get(),cell);stack.setCount(2);BlockPos placed=base.south(32);world.getChunkAt(placed);world.setChunkForced(placed.getX()>>4,placed.getZ()>>4,true);world.setBlock(placed.below(),Blocks.STONE.defaultBlockState(),2);actor.setPos(placed.getX()+.5,placed.getY()+.5,placed.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);var hit=new BlockHitResult(new Vec3(placed.getX()+.5,placed.getY(),placed.getZ()+.5),Direction.UP,placed.below(),false);need(actor.gameMode.useItemOn(actor,world,stack,InteractionHand.MAIN_HAND,hit).consumesAction()&&stack.getCount()==1&&tile(world,placed).effectiveTexture()==selected,"real supplied configured-item placement consumes once");
  actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);root.configureAll(actor,ChannelList.EMPTY,10,false,true,-1,NativeSegmentedDoorSettings.DEFAULT);root.configureTexture(actor,-1);System.out.println("[vandorlabs][segmented-supplied] write PASS profiles="+profiles+" canonical/pick/copy/recipe/latentWindow/LevelClip/bars/gaps/vanillaLever/placement");
 }
 static void read(ServerLevel world,BlockPos base){BlockPos placed=base.south(32);world.getChunkAt(placed);var saved=tile(world,placed);need(saved!=null&&!saved.inert()&&saved.effectiveTexture()==choice(SegmentedArtworkPolicy.gateId(2))&&saved.cellArtwork().overridden&&saved.settings().window&&saved.settings().shape==2&&saved.settings().join,"saved local gate and latent settings survive restart");System.out.println("[vandorlabs][segmented-supplied] read PASS configured local model/latent settings");}
}
