package com.vandorlabs.neoforge.trigger;

import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.control.ControlRegistration;
import com.vandorlabs.neoforge.control.MountedControlBlock;
import com.vandorlabs.neoforge.control.MountedControlEntity;
import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Native analogue world input, shared artwork policy, housing storage and deferred lifecycle. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class TriggerRuntimeChecks {
    private static final BlockPos POS=new BlockPos(128,80,8),SOURCE=new BlockPos(132,80,8);
    private static final java.util.Map<ServerLevel,Long> CLIENT_CYCLES=new java.util.IdentityHashMap<>();
    private TriggerRuntimeChecks(){}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Modern trigger: "+message);}
    private static TriggerEntity install(ServerLevel level,BlockPos pos){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);for(Direction side:Direction.values())level.setBlock(pos.relative(side),Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,TriggerRegistration.BLOCK.get().defaultBlockState(),3);return (TriggerEntity)level.getBlockEntity(pos);}
    public static void run(ServerLevel level){
        level.getChunkAt(SOURCE);level.setBlock(SOURCE,Blocks.AIR.defaultBlockState(),3);level.getChunkAt(POS.east(2));level.setBlock(POS.east(2),Blocks.AIR.defaultBlockState(),3);
        TriggerEntity trigger=install(level,POS);require(trigger.settings().layout==0&&!trigger.settings().facesEnabled&&trigger.state().exact==-1,"legacy trigger defaults");
        require(trigger.getBlockState().canOcclude()&&trigger.getBlockState().getCollisionShape(level,POS).bounds().getSize()==1&&trigger.getBlockState().getLightEmission(level,POS)==0&&trigger.getBlockState().getBlock().getExplosionResistance()==6,"solid unlit legacy housing");
        trigger.apply(new ProgrammableBlockDialog.State(1,0,true,new int[]{6,-1,-1,-1,-1,-1},-1));
        BlockPos plate=POS.east();level.setBlock(plate.below(),Blocks.STONE.defaultBlockState(),3);int cases=0;
        for(int signal=0;signal<=15;signal++){
            level.setBlock(plate,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(WeightedPressurePlateBlock.POWER,signal),3);trigger.refreshSignal();
            for(boolean bands:new boolean[]{false,true})for(int exact=-1;exact<=15;exact++){
                require(trigger.configure(1,2,3,4,bands,exact,ChannelList.of(701,702)),"valid native options");
                int expected=exact>=0&&signal!=exact?1:bands?signal==0?1:signal<=5?3:signal<=10?4:2:exact>=0||signal>0?2:1;
                require(trigger.signalLevel()==signal&&trigger.visibleFinish()==expected,"physical analogue input/band/exact "+signal+" "+bands+" "+exact);
                var model=trigger.getModelData().get(ProgrammableBlockEntity.SETTINGS);require(model.mainTexture==expected&&model.layout==0&&!model.facesEnabled&&model.face(0)==-1&&trigger.settings().face(0)==6,"legacy uniform visible artwork with retained face override settings");cases++;
            }
        }
        level.setBlock(plate,Blocks.STONE.defaultBlockState(),3);
        var strong=Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.POWERED,true).setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE,net.minecraft.world.level.block.state.properties.AttachFace.WALL).setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,Direction.EAST);
        level.setBlock(plate.east(),strong,3);require(strong.getDirectSignal(level,plate.east(),Direction.EAST)==15,"native vanilla lever fixture must strongly power its stone support");trigger.refreshSignal();require(trigger.signalLevel()==15,"loaded strong power through solid support");
        level.setBlock(plate.east(),Blocks.AIR.defaultBlockState(),3);level.setBlock(plate,Blocks.AIR.defaultBlockState(),3);trigger.refreshSignal();
        level.getChunkAt(SOURCE);level.setBlock(SOURCE,Blocks.AIR.defaultBlockState(),3);level.setBlock(SOURCE.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(SOURCE,ControlRegistration.ROCKER.get().defaultBlockState().setValue(MountedControlBlock.FACING,Direction.NORTH),3);
        var source=(MountedControlEntity)level.getBlockEntity(SOURCE);source.channelLoaded();source.configure(ChannelList.of(701),0,0,0);ModernRedstoneChannels.latchLevelChanged(source,7);
        trigger.configure(1,2,3,4,true,-1,ChannelList.of(701,702));require(trigger.signalLevel()==7&&trigger.visibleFinish()==4,"actual remote shared channel analogue consumer");
        trigger.onChunkUnloaded();source.setLocalOn(false);source.setLocalOn(true);ModernRedstoneChannels.latchLevelChanged(source,7);trigger.channelLoaded();ModernRedstoneChannels.channelsChanged(trigger);require(trigger.signalLevel()==7,"unload/re-register current channel recovery");
        var saved=trigger.saveWithoutMetadata(level.registryAccess());var restored=new TriggerEntity(POS,trigger.getBlockState());restored.loadWithComponents(saved,level.registryAccess());
        require(restored.state().on==2&&restored.state().low==3&&restored.state().medium==4&&restored.state().bands&&restored.state().exact==-1&&restored.settings().face(0)==6&&restored.settings().layout==0,"full trigger/housing local NBT roundtrip");
        CompoundTag legacy=new CompoundTag();legacy.putInt("TriggerOnTexture",3);restored.loadWithComponents(legacy,level.registryAccess());require(restored.state().low==3&&restored.state().medium==3&&restored.state().exact==-1,"typed missing low/mid legacy migration");
        legacy.putInt("housingTexture",15);restored.loadWithComponents(legacy,level.registryAccess());require(restored.settings().mainTexture==14,"inherited removed-index migration");
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setPos(128.5,80,5);
        var menu=new TriggerMenu(73,player.getInventory(),POS);player.containerMenu=menu;var packet=new TriggerPayload(73,POS,1,2,3,4,true,-1,"701, 702");require(TriggerPayload.apply(player,packet),"authorized trigger settings");
        require(!TriggerPayload.apply(player,new TriggerPayload(74,POS,1,2,3,4,true,-1,"701"))&&!TriggerPayload.apply(player,new TriggerPayload(73,POS,1,2,3,4,true,16,"701")),"invalid window/exact level accepted");
        ItemStack oldHand=player.getMainHandItem();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));player.setGameMode(GameType.SURVIVAL);require(!TriggerPayload.apply(player,packet),"ordinary survival trigger editor accepted");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(com.vandorlabs.neoforge.VandorLabsModern.CONFIGURIZER.get()));require(TriggerPayload.apply(player,packet),"survival configurizer denied");
        player.setPos(200,80,8);require(!TriggerPayload.apply(player,packet),"distant trigger editor accepted");player.setPos(128.5,80,5);player.setGameMode(GameType.CREATIVE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,oldHand);player.containerMenu=player.inventoryMenu;require(!TriggerPayload.apply(player,packet),"missing trigger menu accepted");
        int[] maximum=new int[64];for(int i=0;i<64;i++)maximum[i]=Integer.MAX_VALUE-i;var maximumPacket=new TriggerPayload(73,POS,1,2,3,4,true,0,ChannelList.of(maximum).toString());var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{TriggerPayload.CODEC.encode(bytes,maximumPacket);require(TriggerPayload.CODEC.decode(bytes).equals(maximumPacket),"64 maximum integer channels native codec");}finally{bytes.release();}
        var malformed=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());try{malformed.writeVarInt(73);malformed.writeBlockPos(POS);for(int i=1;i<=4;i++)malformed.writeVarInt(i);malformed.writeBoolean(true);malformed.writeByte(-1);malformed.writeVarInt(ChannelList.MAX_TEXT_LENGTH*3+1);try{TriggerPayload.CODEC.decode(malformed);throw new IllegalStateException("unbounded trigger channel string accepted");}catch(io.netty.handler.codec.DecoderException expected){}}finally{malformed.release();}
        var picked=trigger.getBlockState().getBlock().getCloneItemStack(trigger.getBlockState(),new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS),Direction.NORTH,POS,false),level,POS,player);
        var drops=Block.getDrops(trigger.getBlockState(),level,POS,trigger,player,new ItemStack(Items.STONE_PICKAXE));require(drops.size()==1,"one configured trigger drop");
        for(ItemStack stack:java.util.List.of(picked,drops.get(0))){var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);require(data!=null,"configured trigger item missing data");var tag=data.copyTag();require(tag.getInt("TriggerOnTexture")==2&&tag.getIntArray("RedstoneChannels").length==2&&!tag.contains("ChannelLevel")&&!tag.contains("ChannelSignal")&&!tag.contains("channelSignal")&&tag.getBoolean("FaceTexturesEnabled"),"configured item options/transient exclusion");}
        BlockPos itemPos=new BlockPos(136,80,8);level.getChunkAt(itemPos);level.setBlock(itemPos,Blocks.AIR.defaultBlockState(),3);level.setBlock(itemPos.below(),Blocks.STONE.defaultBlockState(),3);player.setPos(136.5,80,5);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,picked);
        var placedResult=picked.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(itemPos.below()).add(0,.5,0),Direction.UP,itemPos.below(),false)));
        require(placedResult.consumesAction()&&level.getBlockEntity(itemPos) instanceof TriggerEntity,"actual configured trigger item placement");var placed=(TriggerEntity)level.getBlockEntity(itemPos);require(placed.state().on==2&&placed.state().medium==4&&placed.settings().face(0)==6&&placed.settings().layout==0&&placed.getRedstoneChannels().equals(ChannelList.of(701,702)),"configured item roundtrip options");level.setBlock(itemPos,Blocks.AIR.defaultBlockState(),3);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,oldHand);
        for(Direction face:Direction.values()){level.setBlock(POS,trigger.getBlockState().setValue(TriggerBlock.FACING,face),3);require(trigger.getModelData().get(ProgrammableBlockEntity.SETTINGS).mainTexture==4,"six-facing model data");}level.setBlock(POS,trigger.getBlockState().setValue(TriggerBlock.FACING,Direction.NORTH),3);
        System.out.println("[vandorlabs][modern] trigger-write PASS "+cases+" actual analogue/exact/band cases, strong input, remote channels, six faces, default layout, typed migration, housing overrides and configured items");
    }
    public static void prepareRead(ServerLevel level){level.getChunkAt(POS);level.getChunkAt(SOURCE);require(level.getBlockEntity(POS) instanceof TriggerEntity,"saved trigger identity");}
    public static void read(ServerLevel level){var trigger=(TriggerEntity)level.getBlockEntity(POS);require(trigger.settings().layout==0&&trigger.settings().mainTexture==1&&trigger.settings().face(0)==6&&trigger.state().bands&&trigger.state().on==2&&trigger.state().low==3&&trigger.state().medium==4&&trigger.getRedstoneChannels().equals(ChannelList.of(701,702)),"disk saved trigger/housing options");require(trigger.signalLevel()==7&&trigger.visibleFinish()==4,"native deferred channel restore");System.out.println("[vandorlabs][modern] trigger-read PASS exact trigger/housing settings and analogue channel level after native deferred loading");}
    public static void clientFixture(ServerLevel level){level.getChunkAt(SOURCE);level.setBlock(SOURCE,Blocks.AIR.defaultBlockState(),3);for(int x=111;x<=118;x++)for(int z=-5;z<=11;z++){BlockPos p=new BlockPos(x,79,z);if(level.getBlockState(p).isAir())level.setBlock(p,Blocks.SMOOTH_STONE.defaultBlockState(),3);}var trigger=install(level,new BlockPos(112,80,8));trigger.configure(1,2,3,4,false,-1,ChannelList.EMPTY);level.setBlock(new BlockPos(113,79,8),Blocks.STONE.defaultBlockState(),3);level.setBlock(new BlockPos(113,80,8),Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),3);trigger.refreshSignal();CLIENT_CYCLES.put(level,-1L);}
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)||!CLIENT_CYCLES.containsKey(level))return;
        if(!(level.getBlockEntity(new BlockPos(112,80,8)) instanceof TriggerEntity trigger)||!trigger.state().bands||!trigger.getRedstoneChannels().contains(701)||level.players().stream().anyMatch(player->player.containerMenu instanceof TriggerMenu))return;
        long start=CLIENT_CYCLES.get(level);if(start<0){start=level.getGameTime();CLIENT_CYCLES.put(level,start);}int value=new int[]{0,3,7,13}[(int)((level.getGameTime()-start)/40%4)];BlockPos source=new BlockPos(113,80,8);
        var next=Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(WeightedPressurePlateBlock.POWER,value);if(level.getBlockState(source)!=next){level.setBlock(source,next,3);trigger.refreshSignal();}
    }
    @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){CLIENT_CYCLES.remove(event.getLevel());}
}
