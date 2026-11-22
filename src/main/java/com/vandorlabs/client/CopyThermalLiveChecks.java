package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockBridgeChair;
import com.vandorlabs.blocks.BlockTokenSlot;
import com.vandorlabs.blocks.BlockTurntableSurface;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.copier.CopyControllerTile;
import com.vandorlabs.copier.CopyPlan;
import com.vandorlabs.copier.client.GuiCopyController;
import com.vandorlabs.drives.MaxwellEngine;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.tiles.TileEntityBattery;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntityHexTank;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import com.vandorlabs.vehicle.TransferNetworkCompat;
import com.vandorlabs.vehicle.VehicleChip;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.Loader;

/** The connected player's Copy button drives the real material and journal transaction. */
final class CopyThermalLiveChecks {
    private static final BlockPos BASE=new BlockPos(39,91,38);
    private static final BlockPos TANK=new BlockPos(0,0,0), SERVO=new BlockPos(1,0,0),
        RETRIEVER=new BlockPos(2,0,0), SINK=new BlockPos(3,0,0), BATTERY=new BlockPos(0,0,2),
        FLUXDUCT=new BlockPos(1,0,2), CARGO=new BlockPos(2,0,2), PILOT=new BlockPos(3,0,3),
        MAXWELL=new BlockPos(4,0,0), MEK_TANK=new BlockPos(0,0,4), MEK_CUBE=new BlockPos(2,0,4);
    private static final int CHARGE=TileEntityBattery.CAPACITY, FLUID=1450, FLUX_BUFFER=207, DUCT_BUFFER=70;
    private static final int MAXWELL_FUEL=223, MEK_FLUID=917, MEK_ENERGY=2345;
    private static int stage,wait,scenario;
    private static boolean complete;
    private static BlockPos source,target;
    private static NBTTagCompound servo,retriever;
    private static ItemStack chip=ItemStack.EMPTY;

    private CopyThermalLiveChecks() {}
    private static Block block(String name){Block block=Block.getBlockFromName(name);require(block!=null,"installed "+name);return block;}
    private static ItemStack attachment(String name,int tier){Item item=Item.getByNameOrId("thermaldynamics:"+name);require(item!=null,"installed "+name);return new ItemStack(item,1,tier);}
    private static ItemStack cargo(){return new ItemStack(Items.PAPER,3).setStackDisplayName("Exact copy cargo");}
    private static boolean creative(){return scenario==0;}
    private static GameType gameType(){return creative()?GameType.CREATIVE:GameType.SURVIVAL;}
    private static CopyControllerTile owner(WorldServer world){return (CopyControllerTile)world.getTileEntity(BASE);}
    private static void require(boolean condition,String description){if(!condition)throw new IllegalStateException("copy-thermal scenario="+scenario+" stage="+stage+": "+description);}

    static boolean tick(Minecraft mc)throws Exception{
        require(++wait<650,"timeout mode="+mc.playerController.getCurrentGameType()+" hotbar="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition()+" screen="+mc.currentScreen);
        if(stage==0){
            server(mc,()->setup(mc.getIntegratedServer().getWorld(0),actor(mc)));
            stage=1;wait=0;return false;
        }
        if(stage==1){
            if(wait<35||mc.playerController.getCurrentGameType()!=gameType()||mc.player.inventory.currentItem!=0
                ||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getDistanceSq(BASE)>12)return false;
            server(mc,()->prepare(mc.getIntegratedServer().getWorld(0),actor(mc)));
            mc.playerController.processRightClickBlock(mc.player,mc.world,BASE,EnumFacing.NORTH,new Vec3d(BASE).addVector(.5,.6,0),EnumHand.MAIN_HAND);
            stage=2;wait=0;return false;
        }
        if(stage==2){
            if(!(mc.currentScreen instanceof GuiCopyController)||wait<8)return false;
            VehicleTaxiRuntimeChecks.button(mc,1);
            stage=3;wait=0;return false;
        }
        if(stage==3){
            if(wait<10)return false;
            complete=false;
            server(mc,()->check(mc.getIntegratedServer().getWorld(0)));
            if(!complete)return false;
            if(scenario==1){stage=2;wait=0;scenario++;return false;}
            mc.player.closeScreen();
            if(scenario==3){System.out.println("[vandorlabs] copy-thermal PASS connected Copy button Creative surface, Survival material refusal and exact withdrawal, Survival Vehicle Chip, native servo/retriever faces and tiers, Vandor and Mekanism storage, formed Maxwell fuel");return true;}
            scenario++;
            stage=0;wait=0;
        }
        return false;
    }

    private static void setup(WorldServer world,EntityPlayerMP player)throws Exception{
        require(Loader.isModLoaded("thermaldynamics")&&Loader.isModLoaded("mekanism"),"actual Thermal Dynamics and Mekanism are installed");
        Block surface=block("vandorlabs:turntable_surface");
        for(BlockPos at:BlockPos.getAllInBox(BASE.add(-8,-1,-8),BASE.add(8,4,4))){
            world.getChunkFromBlockCoords(at);
            world.setBlockState(at,at.getY()==BASE.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
        for(net.minecraft.entity.item.EntityItem item:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,
            new net.minecraft.util.math.AxisAlignedBB(BASE.add(-8,-1,-8),BASE.add(9,5,2))))item.setDead();
        player.setGameType(gameType());
        player.inventory.currentItem=0;
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        player.connection.sendPacket(new SPacketHeldItemChange(0));
        player.inventoryContainer.detectAndSendChanges();
        player.connection.setPlayerLocation(BASE.getX()+.5,BASE.getY(),BASE.getZ()+2.5,180,10);
        world.setBlockState(BASE,block("vandorlabs:copy_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH),3);
        for(int side:new int[]{-1,1})for(int x=1;x<=7;x++)for(int z=0;z<7;z++)
            world.setBlockState(BASE.add(x*side,0,-z),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,EnumFacing.UP),3);
        CopyControllerTile owner=owner(world);
        owner.settings(3,0,0,ChannelList.EMPTY);
        owner.refreshPads(player);
        source=CopyPlan.volume(owner.input,owner.height)[0];
        target=CopyPlan.volume(owner.output,owner.height)[0];
        place(world,TANK,"vandorlabs:hexagonal_fuel_tank",0);
        place(world,SINK,"vandorlabs:hexagonal_fuel_tank",0);
        ((TileEntityHexTank)world.getTileEntity(source.add(TANK))).tank.setFluid(new FluidStack(FluidRegistry.WATER,FLUID));
        place(world,BATTERY,"vandorlabs:battery_block",0);
        TileEntityBattery battery=(TileEntityBattery)world.getTileEntity(source.add(BATTERY));
        battery.configure(com.vandorlabs.ports.ResourcePortSettings.placed(EnumFacing.EAST.getIndex()),0,ChannelList.EMPTY);
        edit(world,source.add(BATTERY),tag->tag.setInteger("Energy",CHARGE));
        placeDuct(world,player,FLUXDUCT,"thermaldynamics:duct_0",1);
        placeDuct(world,player,SERVO,"thermaldynamics:duct_16",0);
        placeDuct(world,player,RETRIEVER,"thermaldynamics:duct_16",0);
        install(world,player,TANK,EnumFacing.EAST,"servo",4);
        install(world,player,SINK,EnumFacing.WEST,"retriever",2);
        world.setBlockState(source.add(CARGO),Blocks.CHEST.getDefaultState(),3);
        ((TileEntityChest)world.getTileEntity(source.add(CARGO))).setInventorySlotContents(4,cargo());
        Block seat=block("vandorlabs:pilot_seat");
        world.setBlockState(source.add(PILOT),seat.getDefaultState().withProperty(BlockBridgeChair.UPPER,false),2);
        world.setBlockState(source.add(PILOT).up(),seat.getDefaultState().withProperty(BlockBridgeChair.UPPER,true),2);
        world.setBlockState(source.south(6),Blocks.GOLD_BLOCK.getDefaultState(),3);
        TileEntityConstructor engine=MaxwellRuntimeChecks.assemble(world,source.add(MAXWELL),3,EnumFacing.NORTH,0,0);
        require(MaxwellRuntimeChecks.inlet(engine,EnumFacing.NORTH).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),MAXWELL_FUEL),true)==MAXWELL_FUEL,"fill the actual formed Maxwell inlet");
        place(world,MEK_TANK,"mekanism:machineblock2",11);
        place(world,MEK_CUBE,"mekanism:energycube",0);
        edit(world,source.add(MEK_TANK),tag->tag.setInteger("tier",2));
        edit(world,source.add(MEK_CUBE),tag->tag.setInteger("tier",1));
        TileEntity mekTank=world.getTileEntity(source.add(MEK_TANK)), mekCube=world.getTileEntity(source.add(MEK_CUBE));
        FluidTank tank=(FluidTank)mekTank.getClass().getField("fluidTank").get(mekTank);
        require(tank.fill(new FluidStack(FluidRegistry.WATER,MEK_FLUID),true)==MEK_FLUID,"fill the actual Mekanism fluid tank");
        mekCube.getClass().getMethod("setEnergy",double.class).invoke(mekCube,(double)MEK_ENERGY);
        TileEntity filler=world.getTileEntity(source.add(BATTERY));
        filler.markDirty();
        if(!creative()){
            world.setBlockState(owner.chest(),Blocks.CHEST.getDefaultState(),3);
            TileEntityChest chest=(TileEntityChest)world.getTileEntity(owner.chest());
            ItemStack[] materials={new ItemStack(block("vandorlabs:hexagonal_fuel_tank"),2),new ItemStack(block("vandorlabs:battery_block")),
                new ItemStack(block("thermaldynamics:duct_0"),1,1),new ItemStack(block("thermaldynamics:duct_16"),2,0),
                attachment("servo",4),new ItemStack(Blocks.CHEST),cargo(),new ItemStack(seat),attachment("retriever",1)};
            for(int i=0;i<materials.length;i++)chest.setInventorySlotContents(i,materials[i]);
            if(scenario==3)chest.setInventorySlotContents(9,attachment("retriever",2));
            int frame=0;
            CoreLayout shape=new CoreLayout(3,3,MaxwellEngine.depth(3),EnumFacing.NORTH.getIndex(),0,0);
            for(int y=0;y<shape.height;y++)for(int x=0;x<shape.width;x++)for(int z=0;z<shape.depth;z++)if(shape.frame(x,y,z))frame++;
            chest.setInventorySlotContents(10,new ItemStack(block("vandorlabs:longburn_drive_core")));
            chest.setInventorySlotContents(11,new ItemStack(block("vandorlabs:programmable_block"),frame-1));
            chest.setInventorySlotContents(12,new ItemStack(Blocks.GOLD_BLOCK));
            chest.setInventorySlotContents(13,tierItem("mekanism:machineblock2",11,2));
            chest.setInventorySlotContents(14,tierItem("mekanism:energycube",0,1));
            chest.setInventorySlotContents(15,tierItem("mekanism:energycube",0,0));
        }
        DeferredTileLoad.flush(world);
    }

    private static void place(WorldServer world,BlockPos local,String name,int metadata){
        require(world.setBlockState(source.add(local),block(name).getStateFromMeta(metadata),3),"place "+name+" at "+local);
    }
    private static void placeDuct(WorldServer world,EntityPlayerMP player,BlockPos local,String name,int metadata){
        place(world,local,name,metadata);
        BlockPos at=source.add(local);
        Block block=block(name);
        block.onBlockPlacedBy(world,at,world.getBlockState(at),player,new ItemStack(block,1,metadata));
    }
    private static ItemStack tierItem(String name,int metadata,int tier){
        ItemStack item=new ItemStack(block(name),1,metadata);
        NBTTagCompound data=new NBTTagCompound();data.setInteger("tier",tier);item.setTagCompound(data);return item;
    }
    private static void edit(WorldServer world,BlockPos at,java.util.function.Consumer<NBTTagCompound> change){
        TileEntity tile=world.getTileEntity(at);NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());
        change.accept(data);tile.readFromNBT(data);tile.markDirty();
    }
    private static void install(WorldServer world,EntityPlayerMP player,BlockPos local,EnumFacing side,String item,int tier){
        ItemStack stack=attachment(item,tier);player.setHeldItem(EnumHand.MAIN_HAND,stack);
        require(stack.getItem().onItemUse(player,world,source.add(local),EnumHand.MAIN_HAND,side,.5F,.5F,.5F)==EnumActionResult.SUCCESS,
            "native Thermal item installs "+item+" on "+side);
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        player.inventoryContainer.detectAndSendChanges();
    }

    private static void prepare(WorldServer world,EntityPlayerMP owner)throws Exception{
        TileEntity flux=world.getTileEntity(source.add(FLUXDUCT));
        TileEntity duct=world.getTileEntity(source.add(SERVO));
        Object energy=ductUnit(flux,"energy");
        int accepted=(Integer)energy.getClass().getMethod("receiveEnergy",int.class,boolean.class).invoke(energy,FLUX_BUFFER,false);
        require(accepted==FLUX_BUFFER,"seed the live Thermal RF grid through its native buffer: "+accepted);
        Object fluid=ductUnit(duct,"fluid");
        Object grid=fluid.getClass().getMethod("getGrid").invoke(fluid);
        FluidTank fluidBuffer=(FluidTank)grid.getClass().getField("myTank").get(grid);
        require(fluidBuffer.fill(new FluidStack(FluidRegistry.WATER,DUCT_BUFFER),true)==DUCT_BUFFER,
            "seed the live Thermal fluid grid through its native buffer");
        servo=part(world,source.add(SERVO));retriever=part(world,source.add(RETRIEVER));
        require(servo.getString("id").equals("thermaldynamics:servo_fluid")&&servo.getInteger("side")==EnumFacing.WEST.getIndex(),"correct installed fluid Servo");
        require(retriever.getString("id").equals("thermaldynamics:retriever_fluid")&&retriever.getInteger("side")==EnumFacing.EAST.getIndex(),"correct installed fluid Retriever");
        if(scenario==3){
            CopyControllerTile controller=owner(world);
            CopyPlan prepared=CopyPlan.prepare(controller,owner);
            chip=new ItemStack(ModItems.DATA_CHIP);
            VehicleChip.write(chip,new VehicleStructure(prepared.original.cells,PILOT,EnumFacing.NORTH,1),"Thermal Copy fixture");
            BlockPos mount=BASE.up();
            world.setBlockState(mount,block("vandorlabs:data_chip_slot").getDefaultState().withProperty(BlockTokenSlot.FACING,EnumFacing.UP),3);
            ((TileEntityTokenSlot)world.getTileEntity(mount)).setInventorySlotContents(0,chip.copy());
            controller.useChip=true;controller.refreshPads(owner);
        }
    }

    private static Object ductUnit(TileEntity duct,String family)throws Exception{
        for(Object unit:(Iterable<?>)duct.getClass().getMethod("getDuctUnits").invoke(duct))
            if(unit.getClass().getName().startsWith("cofh.thermaldynamics.duct."+family+"."))return unit;
        throw new IllegalStateException("Missing real Thermal "+family+" unit");
    }

    private static NBTTagCompound part(WorldServer world,BlockPos at){
        TileEntity tile=world.getTileEntity(at);
        return TransferNetworkCompat.write(tile).getTagList("Attachments",10).getCompoundTagAt(0);
    }

    private static void check(WorldServer world){
        CopyControllerTile owner=owner(world);
        if(owner.busy())return;
        if(scenario==1){
            if(owner.error.isEmpty())return;
            require(owner.error.contains("Missing material")&&owner.error.toLowerCase(java.util.Locale.ROOT).contains("retriever"),"missing exact Retriever tier "+owner.error);
            TileEntityChest supply=(TileEntityChest)world.getTileEntity(owner.chest());
            require(supply.getStackInSlot(0).getCount()==2&&world.isAirBlock(target.add(TANK)),"failed copy leaves supply and target untouched");
            supply.setInventorySlotContents(9,attachment("retriever",2));
            owner.error="";owner.changed();complete=true;return;
        }
        require(owner.error.isEmpty(),owner.error);
        if(!owner.status.equals("Copy complete"))return;
        verifyResources(world,source,CHARGE,FLUID);
        verifyResources(world,target,creative()?CHARGE:0,creative()?FLUID:0);
        require(servo.equals(part(world,target.add(SERVO)))&&retriever.equals(part(world,target.add(RETRIEVER))),"published Thermal servo and retriever settings round-trip");
        require(world.getBlockState(target.add(FLUXDUCT))==world.getBlockState(source.add(FLUXDUCT)),"native fluxduct tier survives");
        for(BlockPos at:new BlockPos[]{source,target})require(ItemStack.areItemStacksEqual(((TileEntityChest)world.getTileEntity(at.add(CARGO))).getStackInSlot(4),cargo()),"exact cargo remains in "+at);
        if(!creative()){
            TileEntityChest supply=(TileEntityChest)world.getTileEntity(owner.chest());
            for(int i=0;i<16;i++){
                ItemStack expected=i==8?attachment("retriever",1):i==15?tierItem("mekanism:energycube",0,0):ItemStack.EMPTY;
                require(ItemStack.areItemStacksEqual(supply.getStackInSlot(i),expected),"exact supply slot "+i);
            }
        }
        if(scenario==3)require(ItemStack.areItemStacksEqual(((TileEntityTokenSlot)world.getTileEntity(BASE.up())).getStackInSlot(0),chip),"Vehicle Chip retains the full original charge and fluid");
        complete=true;
    }

    private static void verifyResources(WorldServer world,BlockPos origin,int charge,int fluid){
        TileEntity battery=world.getTileEntity(origin.add(BATTERY));
        require(battery!=null&&battery.writeToNBT(new NBTTagCompound()).getInteger("Energy")==charge,"exact battery charge at "+origin);
        TileEntityHexTank tank=(TileEntityHexTank)world.getTileEntity(origin.add(TANK));
        require(tank!=null&&tank.tank.getFluidAmount()==fluid&&(fluid==0||tank.tank.getFluid().getFluid()==FluidRegistry.WATER),"exact Hex Tank fluid at "+origin);
        boolean filled=charge>0;
        TileEntityConstructor engine=(TileEntityConstructor)world.getTileEntity(origin.add(MAXWELL));
        require(engine!=null&&engine.formed()&&engine.maxwellFuel()==(filled?MAXWELL_FUEL:0),"exact formed Maxwell fuel at "+origin);
        NBTTagCompound mekTank=world.getTileEntity(origin.add(MEK_TANK)).writeToNBT(new NBTTagCompound());
        NBTTagCompound mekCube=world.getTileEntity(origin.add(MEK_CUBE)).writeToNBT(new NBTTagCompound());
        require(mekTank.getInteger("tier")==2&&mekTank.getCompoundTag("fluidTank").getInteger("Amount")== (filled?MEK_FLUID:0),"exact separate Mekanism tank fluid and tier");
        require(mekCube.getInteger("tier")==1&&mekCube.getDouble("electricityStored")== (filled?MEK_ENERGY:0),"exact separate Mekanism Cube Joules and tier");
        NBTTagCompound flux=TransferNetworkCompat.write(world.getTileEntity(origin.add(FLUXDUCT))).getCompoundTag("Energy");
        require(flux.getInteger("Energy")== (filled?FLUX_BUFFER:0),"exact Thermal fluxduct buffer at "+origin+" "+flux);
        int buffered=0;
        for(BlockPos local:new BlockPos[]{SERVO,RETRIEVER}){
            NBTTagCompound duct=TransferNetworkCompat.write(world.getTileEntity(origin.add(local))).getCompoundTag("Fluid");
            buffered+=duct.getInteger("Amount");
        }
        require(buffered==(filled?DUCT_BUFFER:0),"exact Thermal fluiduct buffer at "+origin+" "+buffered);
    }

    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private interface Check{void run()throws Exception;}
    private static void server(Minecraft mc,Check check)throws Exception{mc.getIntegratedServer().callFromMainThread(()->{check.run();return null;}).get();}
}
