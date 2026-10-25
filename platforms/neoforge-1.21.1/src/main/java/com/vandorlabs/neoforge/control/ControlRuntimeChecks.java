package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;

/** Opt-in real installed controls, shared bank semantics and configured item round trips. */
public final class ControlRuntimeChecks {
    private static final BlockPos BUTTON=new BlockPos(100,80,8),A=new BlockPos(104,80,8),B=new BlockPos(108,80,8);
    private ControlRuntimeChecks(){}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Modern mounted control: "+message);}
    private static MountedControlEntity install(ServerLevel level,BlockPos pos,MountedControlBlock block,Direction face){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.relative(face.getOpposite()),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,block.defaultBlockState().setValue(MountedControlBlock.FACING,face),3);var tile=(MountedControlEntity)level.getBlockEntity(pos);tile.channelLoaded();return tile;}
    public static void run(ServerLevel level){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setShiftKeyDown(false);
        var button=install(level,BUTTON,ControlRegistration.BUTTON.get(),Direction.NORTH);var a=install(level,A,ControlRegistration.ROCKER.get(),Direction.NORTH);var b=install(level,B,ControlRegistration.ROCKER.get(),Direction.NORTH);
        require(a.configure(ChannelList.of(401,402),1,2,3)&&b.configure(ChannelList.of(402,403),0,0,0)&&button.configure(ChannelList.of(401,402),0,0,0),"valid settings");
        player.setPos(104.5,80,5);player.setGameMode(GameType.SURVIVAL);var menu=new ControlMenu(81,player.getInventory(),A);player.containerMenu=menu;
        var packet=new ControlPayload(81,A,"401, 402",1,2,3);require(ControlPayload.apply(player,packet),"ordinary survival settings must not require a configurizer");
        require(!ControlPayload.apply(player,new ControlPayload(82,A,"401",0,0,0))&&!ControlPayload.apply(player,new ControlPayload(81,B,"401",0,0,0)),"wrong window/position settings accepted");
        require(!ControlPayload.apply(player,new ControlPayload(81,A,"-1",0,0,0))&&!ControlPayload.apply(player,new ControlPayload(81,A,"401",4,0,0)),"invalid channel/mount payload accepted");
        player.setPos(200,80,8);require(!ControlPayload.apply(player,packet),"distant settings accepted");player.setPos(104.5,80,5);
        player.setGameMode(GameType.ADVENTURE);ItemStack oldHand=player.getMainHandItem();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));require(!ControlPayload.apply(player,packet),"adventure edit without held permission accepted");
        level.setBlock(A.below(),Blocks.STONE.defaultBlockState(),3);var permitted=new ItemStack(Items.STICK);permitted.set(net.minecraft.core.component.DataComponents.CAN_PLACE_ON,new net.minecraft.world.item.AdventureModePredicate(java.util.List.of(net.minecraft.advancements.critereon.BlockPredicate.Builder.block().of(Blocks.STONE).build()),false));player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,permitted);require(ControlPayload.apply(player,packet),"adventure supported held-item permission rejected");
        player.setGameMode(GameType.CREATIVE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,oldHand);player.containerMenu=player.inventoryMenu;
        int[] maximum=new int[64];for(int i=0;i<maximum.length;i++)maximum[i]=Integer.MAX_VALUE-i;var maxPacket=new ControlPayload(81,A,ChannelList.of(maximum).toString(),3,3,3);
        var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{ControlPayload.CODEC.encode(bytes,maxPacket);require(ControlPayload.CODEC.decode(bytes).equals(maxPacket),"64 maximum integer channel codec roundtrip");}finally{bytes.release();}
        var malformed=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{malformed.writeVarInt(81);malformed.writeBlockPos(A);malformed.writeVarInt(ChannelList.MAX_TEXT_LENGTH*3+1);try{ControlPayload.CODEC.decode(malformed);throw new IllegalStateException("Unbounded channel string accepted");}catch(io.netty.handler.codec.DecoderException expected){}}finally{malformed.release();}
        require(!a.configure(ChannelList.of(401),4,0,0)&&!a.configure(ChannelList.of(401),0,4,0)&&!a.configure(ChannelList.of(401),0,0,4),"invalid mount accepted");
        ModernRedstoneChannels.latchLevelChanged(a,7);require(a.localOn()&&a.outputLevel()==7&&a.getBlockState().getSignal(level,A,Direction.NORTH)==7,"linked analogue bank level must retain exact wired power");a.setLocalOn(false);
        a.setLocalOn(true);require(ModernRedstoneChannels.level(level,401)==15&&ModernRedstoneChannels.level(level,402)==15&&!b.localOn(),"partial overlap must not turn whole second bank on");
        b.setLocalOn(true);require(a.localOn()&&b.localOn(),"linked full bank activation");a.setLocalOn(false);
        require(ModernRedstoneChannels.level(level,401)==0&&ModernRedstoneChannels.level(level,402)==0&&ModernRedstoneChannels.level(level,403)==15&&!b.localOn(),"off clears exact overlap but preserves unrelated channel");
        player.setPos(100.5,80,5);ControlRegistration.BUTTON.get().activate(button.getBlockState(),level,BUTTON,player);
        require(level.getBlockTicks().hasScheduledTick(BUTTON,ControlRegistration.BUTTON.get())&&!a.localOn()&&!b.localOn()&&ModernRedstoneChannels.level(level,401)==15&&ModernRedstoneChannels.level(level,402)==15,"actual button schedules release and independently powers channels without changing persistent latches");
        b.setLocalOn(false);require(!a.localOn()&&ModernRedstoneChannels.level(level,401)==15,"button remains independent when linked latches clear");
        ControlRegistration.BUTTON.get().tick(button.getBlockState(),level,BUTTON,level.random);
        require(!button.localOn()&&ModernRedstoneChannels.level(level,401)==0,"scheduled release handler clears timed source");
        b.setLocalOn(true);a.setLocalOn(false);require(ModernRedstoneChannels.level(level,403)==15,"saved partial-bank baseline");
        var saved=a.saveWithoutMetadata(level.registryAccess());var restored=new MountedControlEntity(A,a.getBlockState());restored.loadWithComponents(saved,level.registryAccess());
        require(restored.getRedstoneChannels().equals(a.getRedstoneChannels())&&restored.baseHeight()==1&&restored.baseTilt()==2&&restored.tiltDirection()==3,"local source/mount roundtrip");
        var malformedItem=restored.configuration();malformedItem.putIntArray("RedstoneChannels",new int[]{407});malformedItem.putInt("BaseHeight",4);
        require(restored.applyConfiguration(malformedItem)&&restored.getRedstoneChannels().equals(ChannelList.of(407))&&restored.baseHeight()==1&&restored.baseTilt()==2,"legacy configured item must retain valid channels while ignoring invalid mount");
        player.setPos(104.5,80,5);var picked=a.getBlockState().getBlock().getCloneItemStack(a.getBlockState(),new BlockHitResult(Vec3.atCenterOf(A),Direction.NORTH,A,false),level,A,player);
        var drops=Block.getDrops(a.getBlockState(),level,A,a,player,new ItemStack(Items.STONE_PICKAXE));require(drops.size()==1,"one configured drop");
        for(ItemStack stack:java.util.List.of(picked,drops.get(0))){var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);require(custom!=null,"configured mount/channel item missing settings");var settings=custom.copyTag().getCompound("RedstoneChannelSettings");require(settings.getInt("BaseHeight")==1&&settings.getIntArray("RedstoneChannels").length==2&&!settings.contains("LocalOn")&&!settings.contains("LatchedLevels"),"configured item must retain settings and exclude live latch state");}
        BlockPos placedPos=new BlockPos(112,80,8);level.getChunkAt(placedPos);level.setBlock(placedPos,Blocks.AIR.defaultBlockState(),3);level.setBlock(placedPos.below(),Blocks.STONE.defaultBlockState(),3);
        ItemStack held=player.getMainHandItem();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,picked);player.setPos(112.5,80,5);
        var result=picked.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(placedPos.below()).add(0,.5,0),Direction.UP,placedPos.below(),false)));
        require(result.consumesAction()&&level.getBlockEntity(placedPos) instanceof MountedControlEntity,"actual configured item placement");var placed=(MountedControlEntity)level.getBlockEntity(placedPos);
        require(placed.getRedstoneChannels().equals(a.getRedstoneChannels())&&placed.baseHeight()==1&&placed.baseTilt()==2&&!placed.localOn(),"configured item placement must restore options without live power");level.setBlock(placedPos,Blocks.AIR.defaultBlockState(),3);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
        b.onChunkUnloaded();require(ModernRedstoneChannels.level(level,403)==0,"unload must remove partial saved source immediately");b.channelLoaded();require(ModernRedstoneChannels.level(level,403)==15,"reload must republish preserved partial levels");
        BlockPos geometry=new BlockPos(116,80,8);
        for(Direction face:Direction.values()){
            var tile=install(level,geometry,ControlRegistration.ROCKER.get(),face);require(tile.getBlockState().canSurvive(level,geometry),"six-face sturdy support "+face);
            tile.setLocalOn(true);for(Direction side:Direction.values())require(tile.getBlockState().getSignal(level,geometry,side)==15&&tile.getBlockState().getDirectSignal(level,geometry,side)==15,"weak/strong all-side legacy output "+face+" "+side);
            require(!ControlRegistration.ROCKER.get().canConnectRedstone(tile.getBlockState(),level,geometry,null),"legacy unspecified-side wire rejection");
            require(tile.getBlockState().getLightEmission(level,geometry)==5&&!tile.getBlockState().getCollisionShape(level,geometry).isEmpty(),"solid mounted housing and ON light");
            level.setBlock(geometry.relative(face.getOpposite()),Blocks.AIR.defaultBlockState(),3);require(level.getBlockState(geometry).isAir(),"support removal must pop control "+face);
        }
        install(level,geometry,ControlRegistration.ROCKER.get(),Direction.NORTH);player.setPos(116.5,80,5);player.setGameMode(GameType.SURVIVAL);
        var oldMenu=new ControlMenu(91,player.getInventory(),geometry);player.containerMenu=oldMenu;var oldPacket=new ControlPayload(91,geometry,"601",0,0,0);
        require(ControlPayload.apply(player,oldPacket),"installed ordinary survival session baseline");
        install(level,geometry,ControlRegistration.ROCKER.get(),Direction.NORTH);
        require(!ControlPayload.apply(player,oldPacket),"same-position replacement must reject stale exact-entity session");
        player.containerMenu=player.inventoryMenu;require(!ControlPayload.apply(player,oldPacket),"missing settings session accepted");
        level.setBlock(geometry,Blocks.AIR.defaultBlockState(),3);player.setGameMode(GameType.CREATIVE);
        player.containerMenu=player.inventoryMenu;
        System.out.println("[vandorlabs][modern] controls-write PASS actual button/rocker, exact partial banks, independent momentary release, six-face support/power, configured items, mount/source persistence and unload cleanup");
    }
    public static void prepareRead(ServerLevel level){for(BlockPos pos:new BlockPos[]{BUTTON,A,B}){level.getChunkAt(pos);require(level.getBlockEntity(pos) instanceof MountedControlEntity,"saved control identity missing");}}
    public static void read(ServerLevel level){var a=(MountedControlEntity)level.getBlockEntity(A);var b=(MountedControlEntity)level.getBlockEntity(B);require(a.baseHeight()==1&&a.baseTilt()==2&&a.tiltDirection()==3&&a.getRedstoneChannels().equals(ChannelList.of(401,402)),"saved settings disk identity");require(!a.localOn()&&!b.localOn()&&ModernRedstoneChannels.level(level,403)==15&&ModernRedstoneChannels.level(level,402)==0,"real deferred reload must restore exact partial levels");System.out.println("[vandorlabs][modern] controls-read PASS saved mount/channel settings and partial bank levels after native deferred loading");}
    public static void clientFixture(ServerLevel level){
        for(int x=65;x<=110;x++)for(int z=-5;z<=11;z++){BlockPos floor=new BlockPos(x,79,z);if(level.getBlockState(floor).isAir())level.setBlock(floor,Blocks.SMOOTH_STONE.defaultBlockState(),3);}
        var button=install(level,BUTTON,ControlRegistration.BUTTON.get(),Direction.NORTH);var rocker=install(level,A,ControlRegistration.ROCKER.get(),Direction.NORTH);button.configure(ChannelList.of(501,502),0,0,0);rocker.configure(ChannelList.of(501,502),0,0,0);
    }
}
