package com.vandorlabs.neoforge.reservoir;

import com.vandorlabs.neoforge.ports.*;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.ports.ResourcePortSettings;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Real installed capabilities and ticks, per-cell ownership, actual mining/placement and cold save. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeBufferRuntimeChecks {
    public static final BlockPos BASE=new BlockPos(18512,120,18512);
    private static final BlockPos POWER=BASE, BATTERY=BASE.above(), FLUID=BASE.east(8), TANK=FLUID.above(), PULL=BASE.east(16), PASSIVE=PULL.above(), GROUP=BASE.east(32), MOVED=BASE.east(40), LARGE=BASE.east(48), BOUND=BASE.east(64);
    private static ServerLevel level;
    private static int tick,start,stage,checks;
    private static String phase;
    private static BlockPos trackedDrop;private static final List<ItemEntity> drops=new ArrayList<>();
    @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event){if(trackedDrop!=null&&!event.isCanceled()&&event.getEntity() instanceof ItemEntity item&&item.position().distanceToSqr(Vec3.atCenterOf(trackedDrop))<4)drops.add(item);}
    private static final class Actor extends net.neoforged.neoforge.common.util.FakePlayer {
        Actor(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.fromString("9f8d8656-95e6-43bf-9329-353d822a9302"),"OwnStorage"));setGameMode(GameType.SURVIVAL);}
    }
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Own storage phase="+phase+" stage="+stage+" tick="+tick+": "+why);}
    private static void load(BlockPos pos){level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);level.getChunkAt(pos);}
    private static NativeBufferEntity put(BlockPos pos,boolean battery,Direction facing){load(pos);level.setBlock(pos,(battery?NativeBufferBindings.BATTERY:NativeBufferBindings.TANK).defaultBlockState().setValue(NativeBufferBlock.FACING,facing),3);return (NativeBufferEntity)level.getBlockEntity(pos);}
    private static NativePowerPortEntity power(){return (NativePowerPortEntity)level.getBlockEntity(POWER);}
    private static NativeBatteryEntity battery(){return (NativeBatteryEntity)level.getBlockEntity(BATTERY);}
    private static NativeFluidPortEntity port(BlockPos pos){return (NativeFluidPortEntity)level.getBlockEntity(pos);}
    private static NativeHexTankEntity tank(BlockPos pos){return (NativeHexTankEntity)level.getBlockEntity(pos);}
    private static NativePortData.Configuration settings(Direction face,ResourcePortSettings.Face mode,boolean pull){var faces=new ResourcePortSettings.Face[6];Arrays.fill(faces,ResourcePortSettings.Face.DISABLED);faces[face.get3DDataValue()]=mode;faces[Direction.WEST.get3DDataValue()]=ResourcePortSettings.Face.INPUT;return new NativePortData.Configuration(new ResourcePortSettings(faces,37,pull,ResourcePortSettings.OnTexture.TRANSFERRING),0,ChannelList.EMPTY);}
    private static void config(NativeResourcePortEntity tile,Direction face,ResourcePortSettings.Face mode,boolean pull){need(tile.configure(settings(face,mode,pull)),"installed port configuration "+tile.getBlockPos());}
    private static void codec(){
        for(boolean battery:new boolean[]{true,false}){
            var state=(battery?NativeBufferBindings.BATTERY:NativeBufferBindings.TANK).defaultBlockState();var block=(NativeBufferBlock)state.getBlock();need(state.getDestroySpeed(level,BASE)==5&&block.getExplosionResistance()==6&&state.requiresCorrectToolForDrops(),"Source metal hardness/resistance/tool requirement");need(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)&&!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),"Source stone pickaxe minimum");var owner=(NativeBufferEntity)block.newBlockEntity(BlockPos.ZERO,state);var tag=new CompoundTag();tag.putString("id",battery?"vandorlabs:battery":"vandorlabs:hex_tank");tag.putString("FutureOwnField","retained");if(battery){tag.putInt("Energy",37);tag.putInt("HousingTexture",1);}else{var fluid=new CompoundTag();fluid.putString("FluidName","water");fluid.putInt("Amount",37);tag.put("Tank",fluid);}var before=tag.copy();owner.loadWithComponents(tag,level.registryAccess());need(!owner.inert()&&owner.amount()==37,"Source known contents migrate "+battery);need(tag.equals(before),"Source preimage unchanged");var saved=owner.saveWithId(level.registryAccess());need(saved.getString("id").equals(tag.getString("id"))&&saved.getString("FutureOwnField").equals("retained"),"canonical identity and unknown own fields retained");var copy=(NativeBufferEntity)block.newBlockEntity(BlockPos.ZERO,state);copy.loadWithComponents(saved,level.registryAccess());need(!copy.inert()&&copy.amount()==37,"canonical resource reload");if(battery)need(owner.casing()==1&&copy.casing()==1&&saved.getInt(com.vandorlabs.persistence.HousingSettings.MAIN)==1&&!saved.contains("HousingTexture"),"uppercase Source battery casing migrates to canonical native key");need(!copy.getUpdateTag(level.registryAccess()).contains("FutureOwnField"),"private future fields excluded from public DTO");
            var caps=new CompoundTag();caps.putString("future:opaque","owned");tag.put("ForgeCaps",caps);owner.loadWithComponents(tag,level.registryAccess());need(owner.inert()&&owner.amount()==0&&owner.saveWithoutMetadata(level.registryAccess()).equals(tag),"unknown capabilities retained inert byte-exact");
            tag.remove("ForgeCaps");tag.putInt("NativeBufferSchema",99);owner.loadWithComponents(tag,level.registryAccess());need(owner.inert()&&owner.saveWithoutMetadata(level.registryAccess()).equals(tag),"future schema retained inert byte-exact");
        }
    }
    private static void sides(){
        int n=0;for(var facing:Direction.values())for(boolean battery:new boolean[]{true,false}){var pos=BASE.north(12).east(n++*3);var tile=put(pos,battery,facing);need(tile.installed(),"real installed owner");for(var side:Direction.values())if(battery){var cap=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,side);need((cap!=null)==(side.getAxis()==facing.getAxis()),"battery axis capability "+facing+"/"+side);if(cap!=null)need(cap.canReceive()==(side==facing)&&cap.canExtract()==(side==facing.getOpposite()),"battery front input/rear output");}else need((level.getCapability(Capabilities.FluidHandler.BLOCK,pos,side)!=null)==(side.getAxis()==facing.getAxis()),"tank axial fluid capability");if(tile instanceof NativeBatteryEntity cell){need(cell.energy(null).receiveEnergy(5000,true)==1000&&cell.amount()==0,"1000 RF simulation unchanged");need(cell.energy(facing).receiveEnergy(5000,false)==1000&&cell.amount()==1000,"1000 RF receive limit");need(cell.energy(facing.getOpposite()).extractEnergy(300,true)==300&&cell.amount()==1000,"battery drain simulation unchanged");}level.removeBlock(pos,false);}
    }
    private static void topology(){
        for(var center:List.of(GROUP,LARGE))for(int x=-3;x<=5;x++)for(int z=-3;z<=5;z++)load(center.offset(x,0,z));
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)put(GROUP.offset(x,0,z),false,Direction.UP);
        var root=tank(GROUP);need(root.members().size()==4&&NativeTankGroups.part(root).size==2,"complete two-cell cross section");need(root.fill(new FluidStack(Fluids.WATER,50000),FluidAction.SIMULATE)==50000&&root.members().stream().allMatch(t->t.amount()==0),"group fill simulation unchanged");need(root.fill(new FluidStack(Fluids.WATER,50000),FluidAction.EXECUTE)==50000,"aggregate fill");root.members();long beforeBuild=NativeTankGroups.membershipBuildCount();need(root.drain(1,FluidAction.EXECUTE).getAmount()==1&&root.fill(new FluidStack(Fluids.WATER,1),FluidAction.EXECUTE)==1,"same-kind positive amount edits");need(root.getTanks()==4&&NativeTankGroups.membershipBuildCount()==beforeBuild,"positive amount changes preserve membership cache");var before=new LinkedHashMap<BlockPos,Integer>();for(var cell:root.members())before.put(cell.getBlockPos(),cell.amount());need(before.values().stream().mapToInt(Integer::intValue).sum()==50000&&before.values().stream().filter(v->v==16000).count()==3,"per-cell 16k ownership");need(root.fill(new FluidStack(Fluids.LAVA,1),FluidAction.EXECUTE)==0,"foreign fluid refused");
        var actor=new Actor(level);actor.setPos(GROUP.getX()+.5,GROUP.getY(),GROUP.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);need(root.configure(actor,root.casing(),2,true)&&root.members().size()==1,"shape change splits grouping");for(var entry:before.entrySet())need(tank(entry.getKey()).amount()==entry.getValue(),"shape split retains exact local fluid");need(root.configure(actor,root.casing(),1,false)&&root.members().size()==4,"shape restored merges without redistribution");
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)put(LARGE.offset(x,0,z),false,Direction.UP);need(tank(LARGE).members().size()==9&&NativeTankGroups.part(tank(LARGE)).size==3,"Source three-cell cross section");
        for(int y=0;y<258;y++)put(BOUND.above(y-60),false,Direction.UP);long builds=NativeTankGroups.membershipBuildCount();var bounded=tank(BOUND);need(bounded.getTanks()==256,"Source 256-cell traversal bound");for(int i=0;i<bounded.getTanks();i++){need(bounded.getFluidInTank(i).isEmpty()&&bounded.getTankCapacity(i)==16000&&bounded.isFluidValid(i,new FluidStack(Fluids.WATER,1)),"indexed real handler enumeration "+i);}need(NativeTankGroups.membershipBuildCount()-builds==1,"256-cell enumeration builds ordered membership once");need(NativeTankGroups.retainedCells(level)<=NativeTankGroups.maxRetainedCells(),"bounded retained weak cell references");for(int y=0;y<258;y++)level.removeBlock(BOUND.above(y-60),false);
        mineAndPlace(actor,before);
    }
    private static void mineAndPlace(Actor actor,Map<BlockPos,Integer> before){
        var pos=GROUP.east();var original=tank(pos);int owned=original.amount();need(owned>0,"mined cell owns nonzero fluid");actor.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));trackedDrop=pos;drops.clear();need(actor.gameMode.destroyBlock(pos)&&level.getBlockState(pos).isAir()&&level.getBlockEntity(pos)==null,"actual Survival mining removes exact cell");trackedDrop=null;need(drops.size()==1&&drops.get(0).getItem().is(NativeBufferBindings.TANK.asItem())&&drops.get(0).getItem().getCount()==1,"one configured resource-bearing item actualJoinEvents="+drops.stream().map(ItemEntity::getItem).toList());var drop=drops.get(0);drop.setNoPickUpDelay();actor.getInventory().clearContent();drop.playerTouch(actor);need(drop.isRemoved(),"actual inventory pickup retires item entity");ItemStack stack=ItemStack.EMPTY;for(int i=0;i<actor.getInventory().getContainerSize();i++)if(actor.getInventory().getItem(i).is(NativeBufferBindings.TANK.asItem())){stack=actor.getInventory().getItem(i);break;}need(!stack.isEmpty(),"resource item in inventory");actor.setItemInHand(InteractionHand.MAIN_HAND,stack);load(MOVED);level.setBlock(MOVED.below(),Blocks.STONE.defaultBlockState(),3);level.removeBlock(MOVED,false);actor.setPos(MOVED.getX()+.5,MOVED.getY(),MOVED.getZ()-2);var hit=new BlockHitResult(Vec3.atCenterOf(MOVED.below()).add(0,.501,0),Direction.UP,MOVED.below(),false);need(stack.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,hit)).consumesAction()&&stack.isEmpty(),"actual configured placement consumes exactly one item");need(tank(MOVED).amount()==owned&&tank(MOVED).settings().layout==1&&!tank(MOVED).blockShape(),"placement installs complete fluid and choices");int total=tank(MOVED).amount();for(var entry:before.entrySet())if(!entry.getKey().equals(pos)){need(tank(entry.getKey()).amount()==entry.getValue(),"mining split keeps surviving cell fluid");total+=tank(entry.getKey()).amount();}need(total==50000,"mining and placement conserve group total");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){phase=System.getProperty("vandorlabs.bufferPhase","");if(phase.isEmpty())return;level=event.getServer().overworld();try{
        for(var pos:List.of(POWER,BATTERY,FLUID,TANK,PULL,PASSIVE,GROUP,MOVED,LARGE))load(pos);
        if(phase.equals("read"))return;
        codec();sides();topology();
        put(BATTERY,true,Direction.DOWN);put(TANK,false,Direction.UP);put(PASSIVE,false,Direction.UP);
        for(var pos:List.of(POWER,FLUID,PULL)){var kind=pos.equals(POWER)?NativePortKind.POWER:NativePortKind.FLUID;level.setBlock(pos,NativePortBindings.BLOCKS.get(kind).defaultBlockState(),3);}
    }catch(Throwable failure){fail(failure);}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(level==null)return;try{++tick;need(tick<200,"installed lifecycle readiness timeout");if(phase.equals("read")){if(tick<3)return;need(battery().amount()==0&&power().stored()==370,"battery rear push saved ownership");need(tank(TANK).amount()==370&&port(FLUID).stored().isEmpty(),"port output to tank persisted");need(tank(PASSIVE).amount()==0&&port(PULL).stored().getAmount()==370,"optional pull ownership persisted");int total=tank(MOVED).amount();for(int x=0;x<2;x++)for(int z=0;z<2;z++){var at=GROUP.offset(x,0,z);if(!at.equals(GROUP.east()))total+=tank(at).amount();}need(total==50000&&level.getBlockState(GROUP.east()).isAir(),"cold restart exact split local ownership");finish();return;}
        if(start==0){if(tick<4)return;config(power(),Direction.UP,ResourcePortSettings.Face.OUTPUT,false);config(port(FLUID),Direction.UP,ResourcePortSettings.Face.OUTPUT,false);config(port(PULL),Direction.UP,ResourcePortSettings.Face.INPUT,false);need(power().energy(Direction.WEST).receiveEnergy(370,false)==370,"seed power port");need(port(FLUID).fluid(Direction.WEST).fill(new FluidStack(Fluids.WATER,370),FluidAction.EXECUTE)==370,"seed fluid port");need(tank(PASSIVE).fill(new FluidStack(Fluids.WATER,370),FluidAction.EXECUTE)==370,"seed passive own tank");for(int i=0;i<10;i++){need(tank(PASSIVE).drain(1,FluidAction.EXECUTE).getAmount()==1&&tank(PASSIVE).fill(new FluidStack(Fluids.WATER,1),FluidAction.EXECUTE)==1,"same-cell burst retains fluid");}need(tank(PASSIVE).resourcePublicationCount()==0,"resource mutation does not immediately publish");start=tick;return;}
        if(stage==0&&tick==start+10){need(battery().amount()==370&&power().stored()==0,"real budget37 port pushes into battery front");need(tank(TANK).amount()==370&&port(FLUID).stored().isEmpty(),"real budget37 port pushes into tank");need(tank(PASSIVE).amount()==370&&port(PULL).stored().isEmpty(),"optional pull off leaves passive tank untouched");need(tank(PASSIVE).resourcePublicationCount()==1,"21 same-cell resource mutations coalesce to one five-tick update");System.out.println("[vandorlabs][own-storage] coalescing PASS sameCellMutations21 resourceBlockUpdates1 previousImmediate21");config(port(PULL),Direction.UP,ResourcePortSettings.Face.INPUT,true);level.setBlock(BATTERY,battery().getBlockState().setValue(NativeBufferBlock.FACING,Direction.UP),3);config(power(),Direction.UP,ResourcePortSettings.Face.INPUT,false);stage=1;return;}
        if(stage==1&&tick==start+20){need(battery().amount()==0&&power().stored()==370,"battery rear output pushes back into input port");need(tank(PASSIVE).amount()==0&&port(PULL).stored().getAmount()==370,"optional input pull drains passive tank");finish();}
    }catch(Throwable failure){fail(failure);}}
    private static void finish(){NativeRedstoneChannels.flush(level);level.getServer().saveEverything(true,true,true);System.out.println("[vandorlabs][own-storage] "+phase+" PASS checks="+checks+" realPorts sidedCapabilities perCellSplit miningPlacement coldPersistence");var server=level.getServer();level=null;server.halt(false);}
    private static void fail(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][own-storage] FAIL "+failure);var server=level.getServer();level=null;server.halt(false);}
    private NativeBufferRuntimeChecks(){}
}
