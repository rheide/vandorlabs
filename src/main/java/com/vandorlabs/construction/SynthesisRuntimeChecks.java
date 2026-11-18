package com.vandorlabs.construction;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.machines.SynthesisRuntime;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.*;

/** Exact assembled machine exercises power admission, inventory ownership and saved work. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SynthesisRuntimeChecks {
    private static void batteryInsertion(TileEntityConstructor tile,net.minecraft.entity.player.EntityPlayerMP player){
        double x=player.posX,y=player.posY,z=player.posZ;
        float yaw=player.rotationYaw,pitch=player.rotationPitch;
        boolean creative=player.capabilities.isCreativeMode;
        ItemStack held=player.getHeldItemMainhand().copy(),installed=tile.batteryStack(0).copy();
        com.vandorlabs.machines.SynthesisPresentation.Spec rack=com.vandorlabs.machines.SynthesisPresentation.forMachine(tile.builderMachine());
        net.minecraft.util.math.Vec3d target=MachineMountSurface.rotate(new net.minecraft.util.math.Vec3d(rack.rackX+.5,rack.rackY+com.vandorlabs.rack.ModuleRackGeometry.slotCenterY(0)/16,rack.rackFront()),tile.facing(),tile.core().width,tile.core().depth).add(new net.minecraft.util.math.Vec3d(tile.getPos().subtract(tile.pivot())));
        net.minecraft.util.math.Vec3d eye=target.add(new net.minecraft.util.math.Vec3d(tile.facing().getDirectionVec()).scale(2));
        try{
            player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);
            player.rotationYaw=tile.facing().getOpposite().getHorizontalAngle();player.rotationPitch=0;
            for(boolean mode:new boolean[]{false,true}){
                player.capabilities.isCreativeMode=mode;
                tile.synthesisInventory().setStackInSlot(2,ItemStack.EMPTY);
                player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE));
                require(tile.synthesisModuleInteraction(player,EnumHand.MAIN_HAND)&&tile.batteryStack(0).getCount()==1,"physical battery mount accepts one item creative="+mode);
                require(mode?player.getHeldItemMainhand().getCount()==1:player.getHeldItemMainhand().isEmpty(),"battery insertion consumes Survival inventory and preserves Creative inventory creative="+mode);
            }
        }finally{
            tile.synthesisInventory().setStackInSlot(2,installed);player.setHeldItem(EnumHand.MAIN_HAND,held);
            player.capabilities.isCreativeMode=creative;player.rotationYaw=yaw;player.rotationPitch=pitch;player.setPosition(x,y,z);
        }
    }
    private static void legacyGantry(World world,BlockPos root,Block matter){
        BlockConstructorCore core=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:enclosed_gantry_core");
        Block part=Block.getBlockFromName("vandorlabs:constructor_part");
        com.vandorlabs.machines.CoreLayout old=new com.vandorlabs.machines.CoreLayout(5,3,3,EnumFacing.NORTH.getIndex(),0,0);
        world.setBlockState(root,core.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH).withProperty(BlockConstructorCore.FORMED,true),2);
        for(BlockPos at:BlockPos.getAllInBox(root,root.add(4,2,2))){
            if(at.equals(root))continue;
            BlockPos local=at.subtract(root);
            world.setBlockState(at,part.getDefaultState(),2);
            ((TileEntityConstructorPart)world.getTileEntity(at)).owner(root,old.frame(local.getX(),local.getY(),local.getZ()));
        }
        ((TileEntityConstructor)world.getTileEntity(root)).dismantle();
        for(BlockPos at:BlockPos.getAllInBox(root,root.add(4,2,2))){
            BlockPos local=at.subtract(root);
            if(!at.equals(root))require(world.getBlockState(at).getBlock()==(old.frame(local.getX(),local.getY(),local.getZ())?matter:net.minecraft.init.Blocks.AIR),"2.10 Gantry frame recovery at "+local);
            world.setBlockToAir(at);
        }
    }
    public static void run(World world,net.minecraft.entity.player.EntityPlayerMP player){
        long time=world.getTotalWorldTime();BlockPos root=new BlockPos(180,220,180);
        BlockConstructorCore block=(BlockConstructorCore)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:synthesis_tower_core"));
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
        legacyGantry(world,root.east(16),matter);
        try{
            world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH));
            TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
            for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++)for(int z=0;z<block.depth;z++)if(!root.equals(tile.cell(x,y,z)))world.setBlockState(tile.cell(x,y,z),tile.frame(x,y,z)?matter.getDefaultState():net.minecraft.init.Blocks.AIR.getDefaultState());
            require(tile.assemble(),"frame assembles");
            tile.configureSynthesisRedstone(1,com.vandorlabs.redstone.ChannelList.EMPTY);require(!tile.synthesisEnabled(),"ON activation awaits signal");
            world.setBlockState(root.west(),net.minecraft.init.Blocks.REDSTONE_BLOCK.getDefaultState());tile.sampleInput(root);require(tile.synthesisEnabled(),"physical redstone enables ON");
            tile.configureSynthesisRedstone(2,com.vandorlabs.redstone.ChannelList.EMPTY);require(!tile.synthesisEnabled(),"physical redstone disables OFF");
            world.setBlockToAir(root.west());tile.sampleInput(root);require(tile.synthesisEnabled(),"OFF enables without signal");
            tile.configureSynthesisRedstone(1,com.vandorlabs.redstone.ChannelList.of(19991));tile.setChannelSignal(true);require(tile.synthesisEnabled(),"channel aggregate enables ON");tile.setChannelSignal(false);require(!tile.synthesisEnabled(),"channel OFF pauses");tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);
            tile=firstLoadSignals(world,tile,matter);
            batteryInsertion(tile,player);
            double px=player.posX,py=player.posY,pz=player.posZ;net.minecraft.inventory.Container old=player.openContainer;
            player.setPosition(root.getX()+1,root.getY()+1,root.getZ()-2);
            com.vandorlabs.container.ContainerSynthesisTower container=new com.vandorlabs.container.ContainerSynthesisTower(player.inventory,tile);container.windowId=37;player.openContainer=container;
            com.vandorlabs.network.MessageSynthesisTower message=new com.vandorlabs.network.MessageSynthesisTower(37,root,2);io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();message.toBytes(bytes);require(bytes.readableBytes()==13,"fixed packet bytes");com.vandorlabs.network.MessageSynthesisTower decoded=new com.vandorlabs.network.MessageSynthesisTower();decoded.fromBytes(bytes);require(com.vandorlabs.network.MessageSynthesisTower.apply(player,decoded)&&tile.selectedDesign()==2,"packet roundtrip authorized selection");bytes.release();
            com.vandorlabs.network.MessageSynthesisRedstone redstone=new com.vandorlabs.network.MessageSynthesisRedstone(37,root,2,com.vandorlabs.redstone.ChannelList.of(71,72));io.netty.buffer.ByteBuf redBytes=io.netty.buffer.Unpooled.buffer();redstone.toBytes(redBytes);require(redBytes.readableBytes()==22,"redstone packet bounded bytes");com.vandorlabs.network.MessageSynthesisRedstone redDecoded=new com.vandorlabs.network.MessageSynthesisRedstone();redDecoded.fromBytes(redBytes);require(com.vandorlabs.network.MessageSynthesisRedstone.apply(player,redDecoded)&&tile.synthesisActivation()==2&&tile.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(71,72)),"redstone packet channels roundtrip");redBytes.release();
            require(!com.vandorlabs.network.MessageSynthesisRedstone.apply(player,new com.vandorlabs.network.MessageSynthesisRedstone(36,root,0,com.vandorlabs.redstone.ChannelList.EMPTY)),"redstone wrong window refused");
            for(int kind=0;kind<4;kind++){io.netty.buffer.ByteBuf bad=io.netty.buffer.Unpooled.buffer();bad.writeInt(37).writeLong(root.toLong()).writeByte(kind==0?3:0).writeByte(kind==1?255:kind==2?1:0);if(kind==3)bad.writeByte(1);boolean rejected=false;try{new com.vandorlabs.network.MessageSynthesisRedstone().fromBytes(bad);}catch(IllegalArgumentException expected){rejected=true;}finally{bad.release();}require(rejected,"redstone malformed mode/count/length "+kind);}
            tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);
            require(!com.vandorlabs.network.MessageSynthesisTower.apply(player,new com.vandorlabs.network.MessageSynthesisTower(36,root,0)),"wrong window refused");
            for(int length:new int[]{0,12,14,8192}){io.netty.buffer.ByteBuf bad=io.netty.buffer.Unpooled.buffer(length).writeZero(length);boolean rejected=false;try{new com.vandorlabs.network.MessageSynthesisTower().fromBytes(bad);}catch(IllegalArgumentException expected){rejected=true;}finally{bad.release();}require(rejected,"malformed packet length "+length);}
            net.minecraft.item.ItemStack original=player.inventory.getStackInSlot(9).copy();
            player.inventory.setInventorySlotContents(9,new ItemStack(matter));require(!container.transferStackInSlot(player,7).isEmpty()&&tile.synthesisInventory().getStackInSlot(0).getCount()==1,"shift input routes matter");
            require(!container.transferStackInSlot(player,0).isEmpty()&&tile.synthesisInventory().getStackInSlot(0).isEmpty(),"shift input returns one owned stack");
            for(int slot=0;slot<player.inventory.getSizeInventory();slot++)if(player.inventory.getStackInSlot(slot).getItem()==net.minecraft.item.Item.getItemFromBlock(matter))player.inventory.setInventorySlotContents(slot,ItemStack.EMPTY);
            player.inventory.setInventorySlotContents(9,original);player.openContainer=old;player.setPosition(px,py,pz);
            TileEntityConstructor legacy=new TileEntityConstructor();NBTTagCompound previous=tile.writeToNBT(new NBTTagCompound());previous.removeTag("Synthesis");previous.removeTag("SynthesisActivation");legacy.synthesisInventory().setStackInSlot(0,new ItemStack(matter));legacy.readFromNBT(previous);require(legacy.synthesisInventory().getSlots()==6&&legacy.synthesisInventory().getStackInSlot(0).isEmpty()&&!legacy.processing()&&legacy.synthesisActivation()==0,"previous-release absent runtime clears stale inventory and defaults always");
            for(int design=0;design<3;design++){
                tile.selectDesign(design);tile.synthesisInventory().setStackInSlot(0,new ItemStack(matter));
                for(int tick=0;tick<com.vandorlabs.machines.SynthesisRecipePolicy.CORE_TICKS;tick++){
                    world.setTotalWorldTime(++time);
                    net.minecraft.tileentity.TileEntity port=world.getTileEntity(tile.synthesisPowerPort());
                    IEnergyStorage energy=port.getCapability(CapabilityEnergy.ENERGY,EnumFacing.SOUTH);
                    require(energy!=null,"back power port");require(!port.hasCapability(CapabilityEnergy.ENERGY,EnumFacing.NORTH),"power rejects interior face");
                    require(energy.receiveEnergy(1000,true)==20,"simulation admits bounded work");
                    require(energy.receiveEnergy(1000,false)==20,"tick rate bounded");require(energy.receiveEnergy(1000,false)==0,"repeated transfer same tick rejected");
                    tile.update();if(tick<com.vandorlabs.machines.SynthesisRecipePolicy.CORE_TICKS-1)require(tile.working(),"actual powered work enables lasers");
                    if(tick==90){float progress=tile.buildProgress();tile.configureSynthesisRedstone(1,com.vandorlabs.redstone.ChannelList.EMPTY);world.setTotalWorldTime(++time);require(energy.receiveEnergy(20,false)==0,"paused no battery has no RF sink");tile.update();require(tile.buildProgress()==progress&&!tile.working(),"paused exact progress and lasers off");NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());
                        if(design==0){
                            NBTTagCompound oldJob=saved.copy();
                            oldJob.getCompoundTag("Synthesis").removeTag("WorkCost");
                            TileEntityConstructor oldJobTile=new TileEntityConstructor();
                            oldJobTile.readFromNBT(oldJob);
                            require(oldJobTile.processing()&&oldJobTile.buildProgress()==progress*2,"pre-upgrade job retains original 8000 RF target");
                        }
                        TileEntityConstructor reload=new TileEntityConstructor();reload.setWorld(world);reload.setPos(root);reload.readFromNBT(saved);world.setTileEntity(root,reload);tile=reload;require(tile.processing()&&tile.synthesisInventory().getStackInSlot(0).isEmpty(),"saved work retains consumed input");require(tile.synthesisActivation()==1&&!tile.synthesisEnabled()&&tile.buildProgress()==progress,"paused reload exact progress/mode");tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);}
                }
                require(!tile.processing()&&ItemStack.areItemsEqual(tile.synthesisInventory().getStackInSlot(1),SynthesisRuntime.result(design)),"selected core completed");
                require(tile.batteryEnergy()==0,"no built-in battery");tile.synthesisInventory().extractItem(1,1,false);
            }
            tile.synthesisInventory().setStackInSlot(2,new ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE));tile.configureSynthesisRedstone(1,com.vandorlabs.redstone.ChannelList.EMPTY);world.setTotalWorldTime(++time);
            IEnergyStorage pausedPower=world.getTileEntity(tile.synthesisPowerPort()).getCapability(CapabilityEnergy.ENERGY,EnumFacing.SOUTH);
            require(pausedPower.receiveEnergy(20,false)==20&&tile.batteryEnergy()==20,"disabled tower charges battery");
            com.vandorlabs.rack.ModuleBatteryData.receive(tile.synthesisInventory().getStackInSlot(2),9980,false);tile.selectDesign(0);tile.synthesisInventory().setStackInSlot(0,new ItemStack(matter));tile.update();require(!tile.processing(),"disabled tower preserves queued matter");tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);
            ItemStack secondBank=new ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE);
            com.vandorlabs.rack.ModuleBatteryData.receive(secondBank,10000,false);
            tile.synthesisInventory().setStackInSlot(3,secondBank);
            for(int tick=0;tick<com.vandorlabs.machines.SynthesisRecipePolicy.CORE_TICKS;tick++){world.setTotalWorldTime(++time);tile.update();}
            require(!tile.processing()&&tile.batteryEnergy()==4000&&ItemStack.areItemsEqual(tile.synthesisInventory().getStackInSlot(1),SynthesisRuntime.result(0)),"two charged banks spend exactly 16000 RF");tile.synthesisInventory().extractItem(1,1,false);tile.synthesisInventory().extractItem(2,1,false);tile.synthesisInventory().extractItem(3,1,false);
            for(boolean sourceFirst:new boolean[]{false,true}){
                tile.synthesisInventory().setStackInSlot(2,new ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE));com.vandorlabs.rack.ModuleBatteryData.receive(tile.synthesisInventory().getStackInSlot(2),4000,false);tile.synthesisInventory().setStackInSlot(0,new ItemStack(matter));
                IEnergyStorage input=world.getTileEntity(tile.synthesisPowerPort()).getCapability(CapabilityEnergy.ENERGY,EnumFacing.SOUTH);
                for(int tick=0;tick<com.vandorlabs.machines.SynthesisRecipePolicy.CORE_TICKS;tick++){world.setTotalWorldTime(++time);if(sourceFirst)require(input.receiveEnergy(20,false)==20,"source-first feeds work");tile.update();if(!sourceFirst)require(input.receiveEnergy(20,false)==20,"source-after replenishes battery");if(tick<com.vandorlabs.machines.SynthesisRecipePolicy.CORE_TICKS-1)require(tile.processing(),"one work budget bounds duration");}
                require(!tile.processing()&&tile.batteryEnergy()==4000,"external power preserves battery balance independent of tick order "+sourceFirst);tile.synthesisInventory().extractItem(1,1,false);tile.synthesisInventory().extractItem(2,1,false);
            }
            BlockPos chestPos=tile.cell(0,0,2).offset(tile.facing().rotateYCCW());world.setBlockState(chestPos,net.minecraft.init.Blocks.CHEST.getDefaultState());
            net.minecraft.tileentity.TileEntityChest chest=(net.minecraft.tileentity.TileEntityChest)world.getTileEntity(chestPos);
            for(int slot=0;slot<chest.getSizeInventory();slot++)chest.setInventorySlotContents(slot,new ItemStack(net.minecraft.init.Blocks.COBBLESTONE,64));
            tile.selectDesign(0);tile.synthesisInventory().setStackInSlot(1,SynthesisRuntime.result(0));tile.synthesisInventory().setStackInSlot(0,new ItemStack(matter,2));
            for(int tick=0;tick<9;tick++){world.setTotalWorldTime(++time);tile.update();}require(!tile.processing()&&tile.synthesisInventory().getStackInSlot(0).getCount()==2&&!tile.synthesisInventory().getStackInSlot(1).isEmpty(),"full chest preserves queued input and output");
            NBTTagCompound queued=tile.writeToNBT(new NBTTagCompound());TileEntityConstructor restored=new TileEntityConstructor();restored.setWorld(world);restored.setPos(root);restored.readFromNBT(queued);world.setTileEntity(root,restored);tile=restored;tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);chest.setInventorySlotContents(0,ItemStack.EMPTY);
            world.setTotalWorldTime(++time);tile.update();require(chest.getStackInSlot(0).getCount()==1&&ItemStack.areItemsEqual(chest.getStackInSlot(0),SynthesisRuntime.result(0))&&tile.synthesisInventory().getStackInSlot(1).isEmpty()&&tile.processing()&&tile.synthesisInventory().getStackInSlot(0).getCount()==1,"reloaded queued output exports once and starts next input");
            tile.configureSynthesisRedstone(1,com.vandorlabs.redstone.ChannelList.EMPTY);NBTTagCompound cleared=tile.writeToNBT(new NBTTagCompound());cleared.getCompoundTag("Synthesis").setBoolean("Active",false);cleared.getCompoundTag("Synthesis").setInteger("Work",0);tile.readFromNBT(cleared);tile.synthesisInventory().setStackInSlot(0,ItemStack.EMPTY);tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);world.setBlockToAir(chestPos);
            for(boolean active:new boolean[]{false,true}){
                TileEntityConstructor detached=new TileEntityConstructor();detached.setWorld(world);detached.setPos(root);detached.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                detached.synthesisInventory().setStackInSlot(0,active?ItemStack.EMPTY:new ItemStack(matter));detached.synthesisInventory().setStackInSlot(2,new ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE));com.vandorlabs.rack.ModuleBatteryData.receive(detached.synthesisInventory().getStackInSlot(2),1000,false);
                NBTTagCompound state=detached.writeToNBT(new NBTTagCompound());state.getCompoundTag("Synthesis").setBoolean("Active",active);state.getCompoundTag("Synthesis").setInteger("Work",active?200:0);detached.readFromNBT(state);NBTTagCompound before=detached.writeToNBT(new NBTTagCompound());
                world.setTotalWorldTime(++time);detached.update();require(before.equals(detached.writeToNBT(new NBTTagCompound())),"detached stale tile cannot consume input/battery or advance work active="+active);
            }
            SynthesisDigitizeChecks.run(world,tile);
            System.out.println("[vandorlabs][reprolab] synthesis PASS cases=3 powered production rate simulation save/reload exact input/output ownership");
        }finally{net.minecraft.tileentity.TileEntity raw=world.getTileEntity(root);if(raw instanceof TileEntityConstructor)((TileEntityConstructor)raw).dismantle();for(int x=0;x<block.width;x++)for(int y=0;y<block.height;y++)for(int z=0;z<block.depth;z++)world.setBlockToAir(root.add(x,y,z));}
    }
    private static TileEntityConstructor firstLoadSignals(World world,TileEntityConstructor tile,Block matter){
        final BlockPos root=tile.getPos(),sourcePos=root.west(4);world.setBlockState(sourcePos,net.minecraft.init.Blocks.CHEST.getDefaultState());final net.minecraft.tileentity.TileEntity sourceTile=world.getTileEntity(sourcePos);final int channel=19993;
        com.vandorlabs.redstone.RedstoneChannelMember source=new com.vandorlabs.redstone.RedstoneChannelMember(){public net.minecraft.tileentity.TileEntity channelTile(){return sourceTile;}public int getRedstoneChannel(){return 0;}public void setRedstoneChannel(int ignored){}public com.vandorlabs.redstone.ChannelList getRedstoneChannels(){return com.vandorlabs.redstone.ChannelList.EMPTY;}public com.vandorlabs.redstone.ChannelList transmittedChannels(){return com.vandorlabs.redstone.ChannelList.of(channel);}public int transmissionRange(){return 0;}public boolean hasLocalRedstoneSignal(){return true;}public void setChannelSignal(boolean ignored){}};
        com.vandorlabs.redstone.RedstoneChannels.register(source);
        for(boolean wireless:new boolean[]{false,true}){
            if(!wireless)world.setBlockState(root.west(),net.minecraft.init.Blocks.REDSTONE_BLOCK.getDefaultState());
            tile.configureSynthesisRedstone(2,wireless?com.vandorlabs.redstone.ChannelList.of(channel):com.vandorlabs.redstone.ChannelList.EMPTY);tile.synthesisInventory().setStackInSlot(0,new net.minecraft.item.ItemStack(matter));net.minecraft.item.ItemStack battery=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.BATTERY_MODULE);com.vandorlabs.rack.ModuleBatteryData.receive(battery,1000,false);tile.synthesisInventory().setStackInSlot(2,battery);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());
            TileEntityConstructor loaded=new TileEntityConstructor();loaded.setWorld(world);loaded.setPos(root);loaded.readFromNBT(saved);world.setTileEntity(root,loaded);loaded.onLoad();loaded.update();require(loaded.synthesisInventory().getStackInSlot(0).getCount()==1&&loaded.batteryEnergy()==1000&&!loaded.processing()&&loaded.buildProgress()==0,"first tick before deferred input readiness cannot spend Matter/RF OFF-under-ON wireless="+wireless);
            com.vandorlabs.tiles.DeferredTileLoad.flush(world);require(!loaded.synthesisEnabled(),"deferred registration settles actual ON source before enabling OFF mode wireless="+wireless);loaded.update();require(loaded.synthesisInventory().getStackInSlot(0).getCount()==1&&loaded.batteryEnergy()==1000&&!loaded.processing(),"settled disabled OFF mode remains unchanged wireless="+wireless);
            loaded.synthesisInventory().extractItem(0,1,false);loaded.synthesisInventory().extractItem(2,1,false);if(!wireless)world.setBlockToAir(root.west());tile=loaded;
        }
        com.vandorlabs.redstone.RedstoneChannels.unregister(source);world.setBlockToAir(sourcePos);tile.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);return tile;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Synthesis: "+message);}
}
