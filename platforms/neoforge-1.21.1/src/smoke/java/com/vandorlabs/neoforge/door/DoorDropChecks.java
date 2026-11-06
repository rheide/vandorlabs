package com.vandorlabs.neoforge.door;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Actual ServerPlayerGameMode mining on either part, separate from placement/render acceptance. */
@EventBusSubscriber(modid="vandorlabs")
public final class DoorDropChecks {
    private static Item expected;private static BlockPos tracked;private static int drops,checks;
    private static void need(boolean pass,String message){checks++;if(!pass)throw new AssertionError("Door mining: "+message);}
    @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(tracked!=null&&!event.isCanceled()&&event.getEntity() instanceof ItemEntity item&&item.getItem().is(expected)&&item.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(tracked))<16)drops+=item.getItem().getCount();}
    public static void run(ServerLevel level,NativeProgrammableDoorEntity prototype){
        var block=(NativeProgrammableDoorBlock)prototype.getBlockState().getBlock();expected=block.asItem();var root=prototype.getBlockPos().east(80);level.getChunkAt(root);level.setBlockAndUpdate(root.below(),Blocks.STONE.defaultBlockState());
        var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("27ee05d1-a50e-4e23-b963-b8f9acd45061"),"DoorMining"));actor.setPos(root.getX()+.5,root.getY(),root.getZ()-2);
        try{for(boolean creative:new boolean[]{false,true})for(int half=0;half<2;half++){
            actor.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);var lower=block.defaultBlockState();level.setBlock(root,lower,Block.UPDATE_CLIENTS);level.setBlock(root.above(),lower.setValue(NativeProgrammableDoorBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS);((NativeProgrammableDoorEntity)level.getBlockEntity(root)).installPlacement(NativeDoorSettings.fresh(false,block.slidingModel()),ChannelList.EMPTY);
            tracked=root;drops=0;need(actor.gameMode.destroyBlock(half==0?root:root.above()),"actual mining succeeds creative="+creative+" half="+half);need(level.getBlockState(root).isAir()&&level.getBlockState(root.above()).isAir(),"both parts removed creative="+creative+" half="+half);need(drops==(creative?0:1),"exactly one survival Door and no creative Door creative="+creative+" half="+half+" drops="+drops);tracked=null;
        }System.out.println("[vandorlabs][door-mining] dedicated PASS checks="+checks+" actual survival/creative lower/upper mining");}
        finally{tracked=null;expected=null;level.removeBlock(root,false);level.removeBlock(root.above(),false);}
    }
}
