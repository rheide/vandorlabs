package com.vandorlabs.neoforge.control;

import com.vandorlabs.neoforge.redstone.ModernRedstoneChannels;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in installed world, configured item and restart contracts for the eight additional controls. */
public final class ExpandedControlRuntimeChecks {
    private ExpandedControlRuntimeChecks(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Expanded mounted controls: "+message);}
    public static BlockPos pos(int i){return new BlockPos(144+4*i,80,8);}
    public static MountedControlBlock block(int i){return ControlRegistration.ALL.get(i+2).get();}
    private static MountedControlEntity install(ServerLevel level,BlockPos pos,BlockState state){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);Direction face=((MountedControlBlock)state.getBlock()).outward(state);level.setBlock(pos.relative(face.getOpposite()),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,state,3);var tile=(MountedControlEntity)level.getBlockEntity(pos);need(tile!=null,"installed entity");tile.channelLoaded();return tile;}
    public static void run(ServerLevel level){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setShiftKeyDown(false);player.containerMenu=player.inventoryMenu;
        int geometries=0;
        for(int i=0;i<8;i++){
            MountedControlBlock block=block(i);BlockPos pos=pos(i);var tile=install(level,pos,block.defaultBlockState());
            need(!tile.localOn()&&tile.outputLevel()==0&&tile.baseHeight()==0&&tile.baseTilt()==0&&tile.tiltDirection()==0,"fresh defaults "+i);
            need(tile.profile().signal==tile.signalControl()&&tile.profile().power==tile.powerLever(),"shared dialog profile "+i);
            need(tile.powerLever()?tile.powerLeverSize()==((MountedPowerLeverBlock)block).defaultSize:tile.lowLimit()==5&&tile.highLimit()==15&&tile.step()==0,"typed settings defaults "+i);
            need(block.defaultBlockState().getLightEmission(level,pos)==0,"non-emissive control "+i);
            need(block.defaultBlockState().getDestroySpeed(level,pos)==(tile.powerLever()?.5F:5F),"hardness "+i);
            need(block.defaultBlockState().requiresCorrectToolForDrops()!=tile.powerLever(),"tool requirements "+i);
            need(tile.configureOptions(ChannelList.EMPTY,1,2,3,5,15,tile.controlType(),tile.powerLeverSize()),"mount/options "+i);
            if(tile.signalControl()){
                for(int step=0;step<4;step++){tile.setStep(step);need(tile.step()==step&&tile.outputLevel()==new int[]{0,5,10,15}[step]&&tile.getBlockState().getValue(MountedSignalControlBlock.LEVEL)==step,"unlinked four-detent output "+i+"/"+step);for(Direction side:Direction.values())need(tile.getBlockState().getSignal(level,pos,side)==tile.outputLevel()&&tile.getBlockState().getDirectSignal(level,pos,side)==tile.outputLevel(),"analogue weak/strong "+i);}
                tile.setStep(0);
            }else{tile.setLocalOn(true);need(tile.outputLevel()==15&&((MountedPowerLeverBlock)block).active(tile.getBlockState()),"power lever activation "+i);tile.setLocalOn(false);}
            player.setPos(pos.getX()+.5,80,5);var menu=new ControlMenu(150+i,player.getInventory(),pos);player.containerMenu=menu;
            var packet=new ControlPayload(150+i,pos,"801, 802",1,2,3,4,14,tile.signalControl()?tile.controlType():0,tile.powerLeverSize());
            need(ControlPayload.apply(player,packet),"actual exact menu settings "+i);
            need(!ControlPayload.apply(player,new ControlPayload(151+i,pos,"801",0,0,0,4,14,0,0)),"wrong menu "+i);
            if(tile.signalControl())need(!tile.configureOptions(ChannelList.EMPTY,0,0,0,10,11,0,0),"invalid detent bounds "+i);
            need(!ControlPayload.apply(player,new ControlPayload(150+i,pos,"801",0,0,0,4,14,3,0)),"invalid type "+i);
            need(!ControlPayload.apply(player,new ControlPayload(150+i,pos,"801",0,0,0,4,14,0,2)),"invalid size "+i);
            // Type selection must retain both exact native menu identity and channel membership.
            if(tile.signalControl()&&((MountedSignalControlBlock)block).selectableType()){
                for(int type=0;type<3;type++){need(tile.configureOptions(ChannelList.of(801,802),1,2,3,4,14,type,0),"type selection");need(level.getBlockEntity(pos)==tile&&menu.stillValid(player)&&tile.controlType()==type,"type swap preserves installed session and entity");tile.setStep(2);need(tile.outputLevel()==9&&ModernRedstoneChannels.level(level,801)==9,"type swap retains actual bank");}
                tile.configureOptions(ChannelList.of(801,802),1,2,3,4,14,((MountedSignalControlBlock)block).controlType,0);
            }
            tile.setLocalOn(false);player.containerMenu=player.inventoryMenu;
            var saved=tile.saveWithoutMetadata(level.registryAccess());var copy=new MountedControlEntity(pos,tile.getBlockState());copy.loadWithComponents(saved,level.registryAccess());need(copy.getRedstoneChannels().equals(tile.getRedstoneChannels())&&copy.baseHeight()==1&&copy.baseTilt()==2&&copy.tiltDirection()==3,"native persistence "+i);
            need(!tile.signalControl()||copy.lowLimit()==4&&copy.highLimit()==14,"saved typed limits "+i);
            ItemStack picked=block.getCloneItemStack(tile.getBlockState(),new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false),level,pos,player);
            var drops=Block.getDrops(tile.getBlockState(),level,pos,tile,player,new ItemStack(Items.STONE_PICKAXE));need(drops.size()==1&&picked.is(block.itemBlock().asItem())&&drops.get(0).is(block.itemBlock().asItem()),"canonical merged item/drop "+i);
            for(var stack:java.util.List.of(picked,drops.get(0))){var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);need(data!=null,"configured item settings "+i);var tag=data.copyTag().getCompound("RedstoneChannelSettings");need(!tag.contains("LocalOn")&&!tag.contains("ControlStep")&&!tag.contains("LatchedLevels")&&tag.getIntArray("RedstoneChannels").length==2,"item options exclude live state "+i);}
            BlockPos target=new BlockPos(212,80,8);level.getChunkAt(target);level.setBlock(target,Blocks.AIR.defaultBlockState(),3);level.setBlock(target.below(),Blocks.STONE.defaultBlockState(),3);player.setPos(212.5,80,5);player.setItemInHand(InteractionHand.MAIN_HAND,picked);
            var placed=picked.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(target.below()).add(0,.5,0),Direction.UP,target.below(),false)));
            need(placed.consumesAction()&&level.getBlockEntity(target) instanceof MountedControlEntity,"actual configured item placement "+i);var restored=(MountedControlEntity)level.getBlockEntity(target);need(restored.baseHeight()==1&&restored.baseTilt()==2&&restored.getRedstoneChannels().equals(tile.getRedstoneChannels())&&!restored.localOn(),"placed settings/no live state "+i);need(!tile.powerLever()||restored.powerLeverSize()==tile.powerLeverSize(),"placed size "+i);need(!tile.signalControl()||restored.controlType()==tile.controlType()&&restored.lowLimit()==4&&restored.highLimit()==14,"placed type/limits "+i);level.setBlock(target,Blocks.AIR.defaultBlockState(),3);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            for(BlockState state:block.getStateDefinition().getPossibleStates())for(int mount:new int[]{0,1,12,63}){int size=tile.powerLeverSize();var shape=ControlGeometry.shape(state,mount,size,true);var selection=ControlGeometry.shape(state,mount,size,false);need(!shape.isEmpty()&&!selection.isEmpty()&&selection.bounds().contains(shape.bounds().getCenter()),"authored shape "+i+"/"+state+"/"+mount);geometries++;}
            // Different channels isolate each persisted source while retaining real partial bank levels.
            need(tile.configureOptions(ChannelList.of(900+i),1,2,3,4,14,tile.controlType(),tile.powerLeverSize()),"saved unique channel");if(tile.signalControl())tile.setStep(2);else ModernRedstoneChannels.latchLevelChanged(tile,7);
            need(tile.outputLevel()==(tile.signalControl()?9:7),"persisted analogue level "+i);
        }
        player.containerMenu=player.inventoryMenu;System.out.println("[vandorlabs][modern] expanded-controls-shapes "+ControlGeometry.buildStats());System.out.println("[vandorlabs][modern] expanded-controls-write PASS eight actual adapters, "+geometries+" authored state/mount shapes, exact menu/type identity, analogue bank levels, configured merged items and real placement");
    }
    public static void prepareRead(ServerLevel level){for(int i=0;i<8;i++){level.getChunkAt(pos(i));need(level.getBlockEntity(pos(i)) instanceof MountedControlEntity,"saved adapter "+i);}}
    public static void read(ServerLevel level){for(int i=0;i<8;i++){var tile=(MountedControlEntity)level.getBlockEntity(pos(i));need(tile.baseHeight()==1&&tile.baseTilt()==2&&tile.tiltDirection()==3&&tile.getRedstoneChannels().equals(ChannelList.of(900+i)),"disk settings "+i);need(tile.outputLevel()==(tile.signalControl()?9:7)&&ModernRedstoneChannels.level(level,900+i)==tile.outputLevel(),"deferred restored analogue bank "+i);}System.out.println("[vandorlabs][modern] expanded-controls-read PASS eight adapters preserve settings and exact per-channel levels across process restart");}
    public static void clientFixture(ServerLevel level){
        for(int x=112;x<=180;x++)for(int z=-5;z<=11;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        for(int i=0;i<8;i++)install(level,pos(i),block(i).defaultBlockState());
    }
}
