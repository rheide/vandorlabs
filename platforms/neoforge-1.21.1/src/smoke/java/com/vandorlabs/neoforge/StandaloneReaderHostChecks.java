package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Actual installed private hosts, ownership, Source item exclusion, and real world restart. */
final class StandaloneReaderHostChecks {
    private static int checks;
    private static ServerLevel observing;
    private static final java.util.List<ItemStack> dropped=new java.util.ArrayList<>();
    static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(event.getLevel()==observing&&event.getEntity() instanceof ItemEntity item)dropped.add(item.getItem().copy());}
    private static java.util.List<ItemStack> remove(ServerLevel level,BlockPos pos){observing=level;dropped.clear();try{level.removeBlock(pos,false);return java.util.List.copyOf(dropped);}finally{observing=null;}}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Standalone reader: "+why);}
    static void run(ServerLevel level,NativeCredentialReaderBlock card,NativeCredentialReaderBlock chip,boolean read){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();
        for(int kind=0;kind<2;kind++){
            NativeCredentialReaderBlock block=kind==0?card:chip;BlockPos pos=new BlockPos(5150+kind*3,90,5150);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.getChunkAt(pos);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);
            if(!read){level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState(),3);}
            need(level.getBlockEntity(pos) instanceof NativeCredentialReaderEntity,"installed actual host "+kind);var owner=(NativeCredentialReaderEntity)level.getBlockEntity(pos);
            if(!read){
                var choices=new CompoundTag();choices.putIntArray("RedstoneChannels",new int[]{27,31});choices.putInt("BaseHeight",1);choices.putInt("BaseTilt",2);choices.putInt("TiltDirection",3);choices.putInt("ControlMountVersion",1);owner.place(choices,2);
                need(owner.configure(player,true,127,0,ControlMountSettings.of(1,2,3)),"authorized unchanged policy permits mount edit");
                ItemStack token=kind==0?NativeCredentialItems.accessCard(2):NativeCredentialItems.dataChip(2);token.setCount(3);var custom=new CompoundTag();custom.putString("PrivateToken","retained");token.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));player.setItemInHand(InteractionHand.MAIN_HAND,token);
                var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false);var result=player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
                need(result.consumesAction()&&player.getMainHandItem().getCount()==2,"real survival credential action owns exactly one token");
            }
            need(owner.outputLevel()==15&&owner.physicalLevel()==15&&owner.visual().contents().occupied(),"reader output and public occupation "+kind);
            need(owner.mountSettings().height==1&&owner.mountSettings().tilt==2&&owner.mountSettings().direction==3&&owner.mountRotation()==2,"mount saved independently of metadata");
            var saved=owner.saveWithoutMetadata(level.registryAccess());need(saved.contains("InsertedToken",Tag.TAG_COMPOUND),"private physical inventory persisted");
            var token=NativeCredentialItems.readStack(saved.getCompound("InsertedToken"),level.registryAccess());need(token.getCount()==1&&token.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getString("PrivateToken").equals("retained"),"complete owned token survives restart");
            var dto=owner.getUpdateTag(level.registryAccess());need(!dto.contains("InsertedToken")&&!dto.contains("TokenReturnTo")&&!dto.contains("ForgeData")&&!dto.toString().contains("PrivateToken"),"public DTO excludes ownership and private token bytes");
            var detached=new NativeCredentialReaderEntity(owner.getType(),pos,owner.getBlockState(),kind==1);detached.loadWithComponents(dto,level.registryAccess());need(detached.visual().contents().occupied()&&detached.visual().contents().output()==15,"initial chunk projection installs without world reads");
            var picked=block.getCloneItemStack(owner.getBlockState(),new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false),level,pos,player).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            need(picked.getAllKeys().equals(java.util.Set.of("RedstoneChannelSettings"))&&!picked.toString().contains("PrivateToken")&&!picked.toString().contains("InsertedToken"),"configured pick excludes physical token/policy");
            NativeRedstoneChannels.flush(level);need(NativeRedstoneChannels.level(level,pos,27)==15&&NativeRedstoneChannels.level(level,pos,31)==15,"reader emits both configured channels read="+read+" channels="+owner.getRedstoneChannels()+" transmitted="+owner.transmittedChannels()+" active="+owner.isChannelMemberActive());
        }
        if(!read){opaque(level,card,player);knownRemoval(level,card,player);}
        player.getInventory().clearContent();System.out.println("[vandorlabs][standalone-reader-host-"+(read?"read":"write")+"] dedicated PASS checks="+checks);
    }
    private static void opaque(ServerLevel level,NativeCredentialReaderBlock block,ServerPlayer player){
        BlockPos pos=new BlockPos(5158,90,5150);level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState(),3);var owner=(NativeCredentialReaderEntity)level.getBlockEntity(pos);
        var original=new CompoundTag();original.putString("FutureReader","keep original");var unknown=new CompoundTag();unknown.putString("id","missing:credential");unknown.putInt("count",1);original.put("InsertedToken",unknown);owner.loadWithComponents(original,level.registryAccess());
        need(owner.inert()&&owner.outputLevel()==0&&!owner.usable(player)&&owner.saveWithoutMetadata(level.registryAccess()).equals(original),"unknown ownership retains exact inert snapshot");
        var items=remove(level,pos);need(items.size()==1,"opaque removal emits one recovery owner");ItemStack recovery=items.get(0);need(recovery.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("NativeReaderRecovery").equals(original),"recovery item retains complete failed reader envelope");
        need(owner.recoveryForRemoval(block.asItem()).isEmpty(),"same removal observer cannot eject twice");
    }
    private static void knownRemoval(ServerLevel level,NativeCredentialReaderBlock block,ServerPlayer player){
        BlockPos pos=new BlockPos(5165,90,5150);level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState(),3);var owner=(NativeCredentialReaderEntity)level.getBlockEntity(pos);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-1);player.setItemInHand(InteractionHand.MAIN_HAND,NativeCredentialItems.accessCard(2));need(player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false)).consumesAction()&&player.getMainHandItem().isEmpty(),"known removal fixture uses actual survival insertion");var items=remove(level,pos);need(level.getBlockState(pos).isAir()&&level.getBlockEntity(pos)==null,"occupied removal leaves no resurrected reader block/entity");need(items.size()==1&&items.get(0).is(NativeCredentialItems.ACCESS_CARD.get())&&items.get(0).getCount()==1,"actual onRemove ejects sole physical token");need(owner.takeForDrop().isEmpty()&&!owner.interact(player,InteractionHand.MAIN_HAND),"stale removed owner cannot eject or interact again");
    }
}
