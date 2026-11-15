package com.vandorlabs.ports;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockBattery;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DeferredTileLoad;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.*;
import net.minecraftforge.items.*;

/** Focused installed-world API checks, including the mod's own Battery and Hex Tank. */
public final class ResourcePortRuntimeChecks {
    private static int checks;
    private static ResourcePortTile idle;
    private static long idleStart,idleLookups,idleRefreshes,idlePackets,idleSerializations;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Resource ports: "+why);}
    private static ResourcePortSettings policy(long rate){return ResourcePortSettings.placed(EnumFacing.NORTH.getIndex()).withFace(EnumFacing.SOUTH.getIndex(),ResourcePortSettings.Face.DISABLED).withFace(EnumFacing.EAST.getIndex(),ResourcePortSettings.Face.OUTPUT).withFace(EnumFacing.WEST.getIndex(),ResourcePortSettings.Face.OUTPUT).withTransfer(rate,false,ResourcePortSettings.OnTexture.TRANSFERRING);}
    static ResourcePortTile put(WorldServer world,BlockPos at,ResourcePortBlock block){world.getChunkFromBlockCoords(at);world.removeTileEntity(at);world.setBlockState(at,block.getDefaultState(),2);ResourcePortTile tile=(ResourcePortTile)world.getTileEntity(at);tile.onLoad();DeferredTileLoad.flush(world);return tile;}
    private static TileEntity receiver(WorldServer world,BlockPos at,ResourcePortBlock.Kind kind,EnumFacing towardPort){
        Block block=kind==ResourcePortBlock.Kind.POWER?Block.getBlockFromName("vandorlabs:battery_block"):kind==ResourcePortBlock.Kind.FLUID?Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank"):Blocks.CHEST;
        IBlockState state=block.getDefaultState();if(kind==ResourcePortBlock.Kind.POWER)state=state.withProperty(BlockBattery.FACING,towardPort);if(kind==ResourcePortBlock.Kind.FLUID)state=state.withProperty(BlockAnimatedScreenSelector.FACING,towardPort);
        world.removeTileEntity(at);world.setBlockState(at,state,2);TileEntity tile=world.getTileEntity(at);if(tile instanceof ResourcePortTile){((ResourcePortTile)tile).place(towardPort);tile.onLoad();DeferredTileLoad.flush(world);}return tile;
    }
    private static int stored(ResourcePortTile tile){return tile instanceof PowerPortTile?((PowerPortTile)tile).stored():tile instanceof FluidPortTile?((FluidPortTile)tile).stored()==null?0:((FluidPortTile)tile).stored().amount:count((MatterPortTile)tile);}
    private static int count(MatterPortTile tile){int count=0;for(int i=0;i<MatterPortTile.SLOTS;i++)count+=tile.stored(i).getCount();return count;}
    static void fill(ResourcePortTile tile,int amount){
        if(tile instanceof PowerPortTile)need(tile.getCapability(CapabilityEnergy.ENERGY,EnumFacing.NORTH).receiveEnergy(amount,false)==amount,"power input receives exact amount");
        else if(tile instanceof FluidPortTile)need(tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.NORTH).fill(new FluidStack(FluidRegistry.WATER,amount),true)==amount,"fluid input receives exact amount");
        else {IItemHandler input=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.NORTH);int left=amount;for(int slot=0;slot<input.getSlots()&&left>0;slot++){ItemStack stack=new ItemStack(Items.DIAMOND,Math.min(64,left));stack.setStackDisplayName("Port cargo");ItemStack remainder=input.insertItem(slot,stack,false);left-=stack.getCount()-remainder.getCount();}need(left==0,"item input receives complete tagged stacks");}
    }
    private static int extract(ResourcePortTile tile,EnumFacing side,int max,boolean simulate){
        if(tile instanceof PowerPortTile)return tile.getCapability(CapabilityEnergy.ENERGY,side).extractEnergy(max,simulate);
        if(tile instanceof FluidPortTile){FluidStack result=tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side).drain(max,!simulate);return result==null?0:result.amount;}
        return tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side).extractItem(0,max,simulate).getCount();
    }
    private static void credit(TileEntity target,ResourcePortBlock.Kind kind,EnumFacing side,int amount){
        if(kind==ResourcePortBlock.Kind.POWER)need(target.getCapability(CapabilityEnergy.ENERGY,side).receiveEnergy(amount,false)==amount,"own Battery accepts extracted RF");
        else if(kind==ResourcePortBlock.Kind.FLUID)need(target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side).fill(new FluidStack(FluidRegistry.WATER,amount),true)==amount,"own Hex Tank accepts extracted fluid");
        else {ItemStack stack=new ItemStack(Items.DIAMOND,amount);stack.setStackDisplayName("Port cargo");need(ItemHandlerHelper.insertItem(target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side),stack,false).isEmpty(),"vanilla Chest accepts tagged cargo");}
    }
    private static int receiverStored(TileEntity tile,ResourcePortBlock.Kind kind,EnumFacing side){if(kind==ResourcePortBlock.Kind.POWER)return tile.getCapability(CapabilityEnergy.ENERGY,side).getEnergyStored();if(kind==ResourcePortBlock.Kind.FLUID){int amount=0;for(IFluidTankProperties tank:tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side).getTankProperties()){FluidStack stack=tank.getContents();if(stack!=null)amount+=stack.amount;}return amount;}int count=0;IItemHandler handler=tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side);for(int i=0;i<handler.getSlots();i++)count+=handler.getStackInSlot(i).getCount();return count;}
    private static void remainingPaths(WorldServer world,EntityPlayerMP actor,BlockPos at,ResourcePortBlock block){
        ResourcePortTile port=put(world,at,block);port.configure(policy(10).withFace(EnumFacing.WEST.getIndex(),ResourcePortSettings.Face.DISABLED),0,ChannelList.EMPTY);fill(port,100);
        BlockPos receiverPos=at.east();world.removeTileEntity(receiverPos);world.setBlockState(receiverPos,Blocks.CHEST.getDefaultState(),2);world.removeTileEntity(receiverPos);PartialReceiver partial=new PartialReceiver(block.kind);world.setTileEntity(receiverPos,partial);port.update();
        need(partial.accepted>0&&partial.accepted<10&&stored(port)+partial.accepted==100,"partial real target rejection retains every unaccepted unit "+block.kind);
        world.removeTileEntity(receiverPos);world.setBlockToAir(receiverPos);
        port=put(world,at,block);TileEntity source=receiver(world,at.north(),block.kind,EnumFacing.NORTH);credit(source,block.kind,EnumFacing.NORTH,37);port.update();need(stored(port)==0&&receiverStored(source,block.kind,EnumFacing.SOUTH)==37,"default pull off does not acquire input resources");
        port.configure(port.settings().withTransfer(0,true,ResourcePortSettings.OnTexture.TRANSFERRING),0,ChannelList.EMPTY);port.update();need(stored(port)==37&&receiverStored(source,block.kind,EnumFacing.SOUTH)==0,"enabled pull moves own Battery/HexTank/Chest resources exactly once");
        world.removeTileEntity(at.north());world.setBlockToAir(at.north());
        port=put(world,at,block);fill(port,37);port.configure(policy(7).withTransfer(7,true,ResourcePortSettings.OnTexture.CONNECTED),2,ChannelList.of(64123));NBTTagCompound expectedConfiguration=port.configuration();actor.setGameType(net.minecraft.world.GameType.SURVIVAL);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));need(actor.interactionManager.tryHarvestBlock(at)&&world.isAirBlock(at),"survival harvest removes port");
        java.util.List<net.minecraft.entity.item.EntityItem> entities=world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(at).grow(2));java.util.List<net.minecraft.entity.item.EntityItem> drops=new java.util.ArrayList<>();for(net.minecraft.entity.item.EntityItem entity:entities)if(!entity.isDead&&entity.getItem().getItem()==Item.getItemFromBlock(block))drops.add(entity);need(drops.size()==1&&drops.get(0).getItem().getCount()==1,"one resource-bearing port item, no loose duplicate inventory");
        ItemStack item=drops.get(0).getItem().copy();drops.get(0).setDead();BlockPos target=at.south(2);world.removeTileEntity(target);world.setBlockToAir(target);world.setBlockState(target.down(),Blocks.STONE.getDefaultState(),2);actor.setHeldItem(EnumHand.MAIN_HAND,item);actor.setPosition(target.getX()+.5,target.getY(),target.getZ()-3);actor.rotationYaw=0;
        need(item.getItem().onItemUse(actor,world,target.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS&&actor.getHeldItemMainhand().isEmpty(),"normal ItemBlock use consumes mined resource owner once");ResourcePortTile placed=(ResourcePortTile)world.getTileEntity(target);need(placed!=null&&stored(placed)==37&&expectedConfiguration.equals(placed.configuration()),"mined item placement restores exact resources and every setting");
    }
    public static boolean idleTick(WorldServer world){
        if(idleStart==Long.MIN_VALUE){idleStart=world.getTotalWorldTime();idleLookups=idle.neighborLookups;idleRefreshes=idle.capabilityRefreshes;idlePackets=idle.visualPackets;idleSerializations=idle.serializationCalls;return false;}
        if(world.getTotalWorldTime()-idleStart<45)return false;need(idle!=null&&!idle.isInvalid()&&world.getTileEntity(idle.getPos())==idle,"idle physical owner stays installed");
        need(idle.visualPackets==idlePackets&&idle.serializationCalls==idleSerializations,"45 actual idle ticks send no appearance packets and serialize no private buffers");need(idle.capabilityRefreshes-idleRefreshes<=6,"idle capability refreshes stay bounded across40tick cache expiry elapsed="+(world.getTotalWorldTime()-idleStart)+" refreshes="+(idle.capabilityRefreshes-idleRefreshes));
        System.out.println("[vandorlabs][reprolab] resource-ports-idle PASS worldTicks="+(world.getTotalWorldTime()-idleStart)+" neighborLookups="+(idle.neighborLookups-idleLookups)+" capabilityRefreshes="+(idle.capabilityRefreshes-idleRefreshes)+" serializationCalls="+(idle.serializationCalls-idleSerializations)+" appearancePackets="+(idle.visualPackets-idlePackets));world.removeTileEntity(idle.getPos());world.setBlockToAir(idle.getPos());idle=null;return true;
    }
    private static void redstoneArtwork(WorldServer world,ResourcePortTile tile){
        ResourcePortSettings art=tile.settings().withTransfer(0,false,ResourcePortSettings.OnTexture.REDSTONE);tile.configure(art,0,ChannelList.EMPTY);for(int i=0;i<5;i++)tile.update();need(tile.appearanceMask()==0,"Redstone artwork stays Off when no Redstone mode is configured");
        world.setBlockState(tile.getPos().down(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.configure(art,1,ChannelList.EMPTY);for(int i=0;i<5;i++)tile.update();need(tile.appearanceMask()!=0&&stored(tile)==0,"configured ON-matched Redstone lights empty active faces without generating resources");
        world.setBlockToAir(tile.getPos().down());tile.configure(art,2,ChannelList.EMPTY);for(int i=0;i<5;i++)tile.update();need(tile.appearanceMask()!=0,"configured OFF-matched Redstone lights active faces");
        tile.configure(art,1,ChannelList.EMPTY);for(int i=0;i<5;i++)tile.update();need(tile.appearanceMask()==0,"unmatched Redstone keeps artwork Off");
        tile.configure(ResourcePortSettings.placed(EnumFacing.NORTH.getIndex()),0,ChannelList.EMPTY);for(int i=0;i<5;i++)tile.update();
    }
    private static void codec(){
        io.netty.buffer.ByteBuf buffer=io.netty.buffer.Unpooled.buffer(),again=io.netty.buffer.Unpooled.buffer();
        try{ResourcePortSettings settings=policy(7).withHousing(1);new ResourcePortMessage(41,settings,2,ChannelList.of(1,2)).toBytes(buffer);need(buffer.readableBytes()==30,"bounded settings body30bytes (previous26)");ResourcePortMessage decoded=new ResourcePortMessage();io.netty.buffer.ByteBuf encoded=buffer.copy();try{decoded.fromBytes(encoded);}finally{encoded.release();}decoded.toBytes(again);need(io.netty.buffer.ByteBufUtil.equals(buffer,again),"housing/controls exact wire roundtrip");again.clear();decoded=new ResourcePortMessage();io.netty.buffer.ByteBuf legacy=buffer.copy(0,buffer.writerIndex()-4);try{decoded.fromBytes(legacy);decoded.toBytes(again);need(again.getInt(again.writerIndex()-4)== -1,"legacy message implies original housing");}finally{legacy.release();}buffer.setByte(17,ChannelList.MAX_CHANNELS+1);decoded=new ResourcePortMessage();decoded.fromBytes(buffer);try{java.lang.reflect.Field accepted=ResourcePortMessage.class.getDeclaredField("valid");accepted.setAccessible(true);need(!accepted.getBoolean(decoded),"oversized channel count rejected before allocation");}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}System.out.println("[vandorlabs][reprolab] resource-port-codec PASS body30(previous26) roundtrip legacy boundedChannels");}finally{buffer.release();again.release();}
    }
    public static void run(WorldServer world,EntityPlayerMP actor){
        codec();BatteryPortRuntimeChecks.run(world,actor);
        BlockPos base=new BlockPos(880,110,32);for(BlockPos p:BlockPos.getAllInBox(base.add(-3,-1,-3),base.add(40,2,3))){world.getChunkFromBlockCoords(p);world.removeTileEntity(p);world.setBlockToAir(p);}
        try{
            int family=0;for(ResourcePortBlock block:ResourcePorts.BLOCKS){
                BlockPos at=base.east(family++*12);ItemBlock item=(ItemBlock)Item.getItemFromBlock(block);
                for(EnumFacing face:EnumFacing.values()){
                    world.removeTileEntity(at);world.setBlockToAir(at);actor.setPosition(at.getX()+.5+face.getFrontOffsetX()*4,at.getY()+face.getFrontOffsetY()*4,at.getZ()+.5+face.getFrontOffsetZ()*4);actor.rotationPitch=face==EnumFacing.UP?90:face==EnumFacing.DOWN?-90:0;if(face.getAxis().isHorizontal())actor.rotationYaw=face.getOpposite().getHorizontalIndex()*90;
                    IBlockState state=block.getStateForPlacement(world,at,EnumFacing.UP,.5F,.5F,.5F,0,actor);need(state.getValue(ResourcePortBlock.FACING)==face,"six-axis placement faces toward player "+block.kind+" "+face);
                    need(item.placeBlockAt(new ItemStack(item),actor,world,at,EnumFacing.UP,.5F,.5F,.5F,state),"actual ItemBlock placement");ResourcePortTile tile=(ResourcePortTile)world.getTileEntity(at);tile.onLoad();DeferredTileLoad.flush(world);
                    need(tile.settings().face(face.getIndex())==ResourcePortSettings.Face.INPUT&&tile.settings().face(face.getOpposite().getIndex())==ResourcePortSettings.Face.OUTPUT,"placed input and opposite output "+face);
                    need(!tile.settings().pullInputs&&tile.settings().throughput==0&&tile.settings().onTexture==ResourcePortSettings.OnTexture.TRANSFERRING,"bare defaults are unlimited, passive and transfer artwork");
                }
                ResourcePortTile tile=put(world,at,block);tile.configure(policy(5),0,ChannelList.EMPTY);TileEntity east=receiver(world,at.east(),block.kind,EnumFacing.WEST),west=receiver(world,at.west(),block.kind,EnumFacing.EAST);fill(tile,100);
                int before=stored(tile),eSim=extract(tile,EnumFacing.EAST,100,true),wSim=extract(tile,EnumFacing.WEST,100,true);need(stored(tile)==before&&eSim+wSim==5&&Math.abs(eSim-wSim)<=1,"simulation is side-effect free and shares rate across passive extractors");
                int e=extract(tile,EnumFacing.EAST,100,false),w=extract(tile,EnumFacing.WEST,100,false);need(e+w==5&&Math.abs(e-w)<=1&&stored(tile)==95,"external output faces share one budget");credit(east,block.kind,EnumFacing.WEST,e);credit(west,block.kind,EnumFacing.EAST,w);
                tile.update();need(stored(tile)==95&&extract(tile,EnumFacing.EAST,100,false)==0,"automatic push and repeated external extraction cannot exceed shared output rate");
                NBTTagCompound publicData=tile.getUpdateTag();need(!publicData.hasKey("PortEnergy")&&!publicData.hasKey("PortFluid")&&!publicData.hasKey("Items")&&!publicData.hasKey("ForgeCaps"),"public client NBT excludes resource ownership and foreign inventories");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());ResourcePortTile restored=(ResourcePortTile)TileEntity.create(world,saved);need(restored!=null&&restored.configuration().equals(tile.configuration())&&stored(restored)==95,"saved resources and all settings round-trip");
                ResourcePortTile target=put(world,at.south(2),block);fill(target,9);tile.configure(policy(7).withTransfer(7,true,ResourcePortSettings.OnTexture.CONNECTED),2,ChannelList.of(64123,64124));NBTTagCompound copied=ProgrammableSettings.capture(world,at);need(copied.getKeySet().size()==1&&copied.hasKey(ProgrammableSettings.RESOURCE_PORT_SETTINGS,10),"Duplifier captures only the settings option");need(ProgrammableSettings.apply(world,target.getPos(),copied,actor)&&target.configuration().equals(tile.configuration())&&stored(target)==9,"Duplifier copies every setting and retains target resources");
                NBTTagCompound invalid=copied.copy();invalid.getCompoundTag(ProgrammableSettings.RESOURCE_PORT_SETTINGS).setLong(ResourcePortData.RATE,-1);NBTTagCompound prior=target.writeToNBT(new NBTTagCompound());need(!ProgrammableSettings.apply(world,target.getPos(),invalid,actor)&&prior.equals(target.writeToNBT(new NBTTagCompound())),"invalid settings refuse atomically without changing contents");
                ResourcePortTile gated=put(world,at.north(2),block);gated.configure(policy(5),1,ChannelList.of(765432));need(extract(gated,EnumFacing.EAST,100,false)==0,"inactive Redstone blocks output");if(gated instanceof PowerPortTile)need(gated.getCapability(CapabilityEnergy.ENERGY,EnumFacing.NORTH).receiveEnergy(100,false)==0,"inactive Redstone blocks incoming RF");else if(gated instanceof FluidPortTile)need(gated.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.NORTH).fill(new FluidStack(FluidRegistry.WATER,100),true)==0,"inactive Redstone blocks incoming fluid");else need(gated.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.NORTH).insertItem(0,new ItemStack(Items.DIAMOND),false).getCount()==1,"inactive Redstone blocks incoming item");
                remainingPaths(world,actor,at.up(),block);
            }
            System.out.println("[vandorlabs][reprolab] resource-ports-runtime PASS checks="+checks+" six-axis actual placement/own Battery/own Hex Tank/Chest/passive extraction+push shared budget/simulation/typed save/opaque client NBT/Duplifier atomicity/Redstone");
        }finally{for(BlockPos p:BlockPos.getAllInBox(base.add(-3,-1,-3),base.add(40,2,3))){world.removeTileEntity(p);world.setBlockToAir(p);}}
        idle=put(world,new BlockPos(946,110,32),ResourcePorts.POWER);redstoneArtwork(world,idle);idleStart=Long.MIN_VALUE;
    }
    /** Only installed during one bounded server callback, then removed before any chunk save. */
    private static final class PartialReceiver extends TileEntity {
        final ResourcePortBlock.Kind kind;int accepted;
        PartialReceiver(ResourcePortBlock.Kind kind){this.kind=kind;}
        private final IEnergyStorage energy=new IEnergyStorage(){public int receiveEnergy(int max,boolean simulate){int amount=Math.min(max,simulate?1000:3);if(!simulate)accepted+=amount;return amount;}public int extractEnergy(int max,boolean simulate){return 0;}public int getEnergyStored(){return accepted;}public int getMaxEnergyStored(){return 1000;}public boolean canExtract(){return false;}public boolean canReceive(){return true;}};
        private final IFluidHandler fluid=new IFluidHandler(){public IFluidTankProperties[] getTankProperties(){return new IFluidTankProperties[]{new FluidTankProperties(null,1000,true,false)};}public int fill(FluidStack stack,boolean commit){int amount=Math.min(stack.amount,commit?3:1000);if(commit)accepted+=amount;return amount;}public FluidStack drain(FluidStack stack,boolean commit){return null;}public FluidStack drain(int amount,boolean commit){return null;}};
        private final IItemHandler items=new IItemHandler(){public int getSlots(){return 1;}public ItemStack getStackInSlot(int slot){return ItemStack.EMPTY;}public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){int amount=simulate?stack.getCount():Math.min(stack.getCount(),3);if(!simulate)accepted+=amount;ItemStack remainder=stack.copy();remainder.shrink(amount);return remainder;}public ItemStack extractItem(int slot,int amount,boolean simulate){return ItemStack.EMPTY;}public int getSlotLimit(int slot){return 64;}};
        @Override public <T>T getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,EnumFacing side){if(kind==ResourcePortBlock.Kind.POWER&&cap==CapabilityEnergy.ENERGY)return CapabilityEnergy.ENERGY.cast(energy);if(kind==ResourcePortBlock.Kind.FLUID&&cap==CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluid);if(kind==ResourcePortBlock.Kind.MATTER&&cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(items);return super.getCapability(cap,side);}
    }
    private ResourcePortRuntimeChecks(){}
}
