package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.credentials.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Real vanilla BlockItem callback path, comparing all type/face/yaw poses with the Source item corpus. */
final class StandaloneReaderItemChecks {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Reader item: "+why);}
    static void run(ServerLevel level,NativeCredentialReaderBlock card,NativeCredentialReaderBlock chip)throws Exception {
        String reference=System.getProperty("vandorlabs.standaloneReaderItemReference");if(reference==null)return;
        JsonObject root;try(var input=Files.newBufferedReader(Path.of(reference))){root=JsonParser.parseReader(input).getAsJsonObject();}need(root.get("golden").getAsString().equals("b1db97a6069763e1147c4ad19b1c40d12c3c135502a5fd71405d644f4cf93a6a"),"Source item callback golden");
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setShiftKeyDown(false);BlockPos target=new BlockPos(5190,94,5150);level.setChunkForced(target.getX()>>4,target.getZ()>>4,true);level.getChunkAt(target);int poses=0;
        for(var entry:root.getAsJsonArray("cases")){var row=entry.getAsJsonObject();if(row.get("mount").getAsInt()!=63)continue;var block=row.get("chip").getAsBoolean()?chip:card;Direction face=Direction.from3DDataValue(row.get("face").getAsInt());BlockPos support=target.relative(face.getOpposite());level.setBlock(target,Blocks.AIR.defaultBlockState(),3);for(Direction side:Direction.values())level.setBlock(target.relative(side),Blocks.AIR.defaultBlockState(),3);level.setBlock(support,Blocks.STONE.defaultBlockState(),3);
            player.setYRot(row.get("yaw").getAsInt()*90);player.setXRot(0);player.setPos(target.getX()+.5+face.getStepX()*3,target.getY()+face.getStepY()*3,target.getZ()+.5+face.getStepZ()*3);CompoundTag choices=TagParser.parseTag(row.get("choices").getAsString());var held=new ItemStack(block,2);var custom=new CompoundTag();custom.put("RedstoneChannelSettings",choices.copy());held.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));var before=held.copy();player.setItemInHand(InteractionHand.MAIN_HAND,held);var hit=new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5),face,support,false);
            need(player.gameMode.useItemOn(player,level,held,InteractionHand.MAIN_HAND,hit).consumesAction(),"actual survival BlockItem use pose "+poses);need(held.getCount()==1&&ItemStack.isSameItemSameComponents(held,before),"placement consumes one unchanged configured frame");need(level.getBlockEntity(target) instanceof NativeCredentialReaderEntity,"actual factory installed reader");var owner=(NativeCredentialReaderEntity)level.getBlockEntity(target);need(owner.chip()==block.chip()&&owner.getBlockState().getValue(NativeMountedSwitchBlock.FACING)==face&&owner.mountRotation()==row.get("rotation").getAsInt(),"Source placement orientation "+poses);need(owner.configuration().equals(choices)&&!owner.visual().contents().occupied()&&owner.outputLevel()==0,"Source configured choices install without inventory/policy");
            ItemStack ctrl=block.getCloneItemStack(owner.getBlockState(),hit,level,target,player);owner.saveToItem(ctrl,level.registryAccess());need(!ctrl.has(DataComponents.BLOCK_ENTITY_DATA)&&ctrl.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("RedstoneChannelSettings").equals(choices),"Ctrl-pick stays choices-only");
            if(poses==0)refusals(level,block,owner,player,target.above(4));level.removeBlock(target,false);poses++;
        }
        need(poses==48,"both item types/all six faces/four player yaws");player.getInventory().clearContent();System.out.println("Native standalone reader actual BlockItem PASS SourcePoses="+poses+" checks="+checks);
    }
    private static void refusals(ServerLevel level,NativeCredentialReaderBlock block,NativeCredentialReaderEntity owner,ServerPlayer player,BlockPos target){
        var snapshot=owner.saveWithoutMetadata(level.registryAccess());snapshot.putString("FutureOwnedReader","keep");for(int kind=0;kind<3;kind++){
            var held=new ItemStack(block,2);if(kind==0)BlockItem.setBlockEntityData(held,owner.getType(),snapshot.copy());else{var custom=new CompoundTag();custom.put(kind==1?"BlockEntityTag":"NativeReaderRecovery",snapshot.copy());held.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));}var before=held.copy();level.setBlock(target.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(target,Blocks.AIR.defaultBlockState(),3);player.setPos(target.getX()+.5,target.getY(),target.getZ()-1);player.setItemInHand(InteractionHand.MAIN_HAND,held);var hit=new BlockHitResult(Vec3.atCenterOf(target.south()).add(0,0,-.5),Direction.NORTH,target.south(),false);need(!player.gameMode.useItemOn(player,level,held,InteractionHand.MAIN_HAND,hit).consumesAction()&&level.isEmptyBlock(target)&&held.getCount()==2&&ItemStack.isSameItemSameComponents(held,before),"unsupported full/recovery envelope refused before world publication "+kind);
        }
    }
}
