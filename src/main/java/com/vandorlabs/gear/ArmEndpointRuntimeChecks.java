package com.vandorlabs.gear;

import com.vandorlabs.gear.core.ArmResource;
import com.vandorlabs.ports.*;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Grid-aligned 3x4 platforms, with real world ticks and durable ownership transfers. */
@SideOnly(Side.CLIENT)
public final class ArmEndpointRuntimeChecks {
    private static int fixture=Integer.getInteger("vandorlabs.armEndpointStart",0),stage,ticks,endpointTick;
    private static final int START=fixture;
    private static String clientState="initializing";
    private static boolean clicked,resourcesChecked;
    private static net.minecraftforge.items.IItemHandler resourceInput;
    private static String clientSections;
    private static final BlockPos BASE=new BlockPos(1807,110,0);
    private static EnumFacing face(){return EnumFacing.values()[fixture%6];}
    private static Vec3d playerPosition(){return new Vec3d(at(-2,0,-2)).addVector(.5,.5,.5);}
    private static GearBlock block(){return fixture<6?GearRegistry.GEAR_EXTENDER:GearRegistry.LARGE_GEAR_EXTENDER;}
    private static BlockPos at(int x,int y,int z){return BASE.add(ArmOrientation.vector(face(),new BlockPos(x,y,z)));}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-endpoint fixture="+fixture+" face="+face()+" stage="+stage+" tick="+ticks+": "+why);}
    private static void place(WorldServer world,EntityPlayerMP player,BlockPos at,net.minecraft.block.Block block){
        for(EnumFacing side:EnumFacing.values())if(!world.isAirBlock(at.offset(side.getOpposite()))){player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block));EnumActionResult result=player.getHeldItemMainhand().onItemUse(player,world,at.offset(side.getOpposite()),EnumHand.MAIN_HAND,side,.5F,.5F,.5F);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);require(result==EnumActionResult.SUCCESS&&world.getBlockState(at).getBlock()==block,"ordinary block placement at retracted endpoint");return;}
        throw new IllegalStateException("No fixture placement neighbour");
    }
    public static boolean tick(Minecraft mc)throws Exception{
        if(fixture==12)return true;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(stage==1&&ticks>=15&&!clicked&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(playerPosition().x,playerPosition().y,playerPosition().z)<1&&mc.world.getBlockState(BASE).getBlock()==block()){
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(BASE,face(),EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;
        }
        clientSections=clientSections(mc);
        clientState="mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition()+" clicked="+clicked;
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static String clientSections(Minecraft mc){
        net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(BASE);
        GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
        if(root==null||!root.isOperational())return "client controller unavailable";
        int visible=0;
        for(int x:new int[]{0,2})for(int z:new int[]{0,3})for(int n=0;n<2;n++){
            BlockPos pos=at(x,-n,z);raw=mc.world.getTileEntity(pos);
            if(!(raw instanceof GearTileEntity))return "missing client section "+pos;
            GearTileEntity tile=(GearTileEntity)raw;
            if(tile.mountDirection()!=face()||tile.loadedController()!=root)return "client section "+pos+" mount="+tile.mountDirection()+" owner="+tile.owner()+" expected mount="+face()+" root="+root.getPos();
            if(com.vandorlabs.gear.client.GearRenderer.visible(tile))visible++;
        }
        return visible==1?null:"client draws "+visible+" arm assembly models";
    }
    private static Block platformBlock(int x,int z){
        if(fixture==1){
            if(x==0&&z==0)return ResourcePorts.POWER;
            if(x==2&&z==0)return ResourcePorts.FLUID;
            if(x==2&&z==3)return ResourcePorts.MATTER;
        }
        if(x==1&&z==1)return Blocks.CHEST;
        if(x==0&&z==1)return Block.getBlockFromName("vandorlabs:turntable_surface");
        return Blocks.STONE;
    }
    private static void enable(GearTileEntity arm,ArmResource mode){
        NBTTagCompound settings=arm.settings();
        settings.setInteger(ArmResource.CHOICE,mode.ordinal());
        arm.configure(settings);
    }
    private static void output(GearTileEntity arm,boolean out){NBTTagCompound options=arm.settings();options.setBoolean(ArmResource.OUT,out);arm.configure(options);}
    private static void checkResources(WorldServer world,GearTileEntity arm,int endpoint,int previous){
        EnumFacing side=face().getOpposite();
        GearTileEntity powerInput=(GearTileEntity)world.getTileEntity(at(0,0,0));
        enable(arm,ArmResource.POWER);
        net.minecraftforge.energy.IEnergyStorage power=powerInput.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,side);
        PowerPortTile battery=(PowerPortTile)world.getTileEntity(at(0,endpoint,0));
        require(power!=null&&power.canReceive()&&powerInput.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,face())==null,"power input only on mounting face");
        require(power.receiveEnergy(17,true)==17&&battery.stored()==17*previous,"RF simulation leaves native owner unchanged");
        require(power.receiveEnergy(17,false)==17&&battery.stored()==17*(previous+1)&&power.extractEnergy(17,false)==0,"only the native recipient owns forwarded RF");
        com.vandorlabs.ports.ResourcePortSettings batterySettings=battery.settings();
        battery.configure(batterySettings.withFace(side.getIndex(),com.vandorlabs.ports.ResourcePortSettings.Face.OUTPUT),0,com.vandorlabs.redstone.ChannelList.EMPTY);
        output(arm,true);require(!power.canReceive()&&power.canExtract()&&power.receiveEnergy(1,false)==0&&power.extractEnergy(5,true)==5&&battery.stored()==17*(previous+1),"output RF simulation/direction");
        require(power.extractEnergy(5,false)==5&&battery.stored()==17*(previous+1)-5,"RF returns through mounting face");
        output(arm,false);battery.configure(batterySettings,0,com.vandorlabs.redstone.ChannelList.EMPTY);require(power.receiveEnergy(5,false)==5,"restore tested RF owner");
        enable(arm,ArmResource.FLUID);
        require(power.receiveEnergy(17,false)==0,"previous RF handle honors changed setting");
        GearTileEntity fluidInput=(GearTileEntity)world.getTileEntity(at(2,0,0));
        net.minecraftforge.fluids.capability.IFluidHandler fluid=fluidInput.getCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,side);
        FluidPortTile tank=(FluidPortTile)world.getTileEntity(at(2,endpoint,0));
        net.minecraftforge.fluids.FluidStack water=new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,27);
        require(fluid!=null&&fluid.fill(water,false)==27&&(tank.stored()==null?0:tank.stored().amount)==27*previous,"joined simulation probes its own tank");
        require(fluid.fill(water,true)==27&&tank.stored().amount==27*(previous+1)&&fluid.drain(27,true)==null,"fluids reach the joined column's own physical endpoint");
        com.vandorlabs.ports.ResourcePortSettings tankSettings=tank.settings();tank.configure(tankSettings.withFace(side.getIndex(),com.vandorlabs.ports.ResourcePortSettings.Face.OUTPUT),0,com.vandorlabs.redstone.ChannelList.EMPTY);
        output(arm,true);require(fluid.fill(water,true)==0&&fluid.drain(3,false).amount==3&&tank.stored().amount==27*(previous+1),"fluid output simulation/direction");
        require(fluid.drain(3,true).amount==3&&tank.stored().amount==27*(previous+1)-3,"fluid returns through mounting face");
        output(arm,false);tank.configure(tankSettings,0,com.vandorlabs.redstone.ChannelList.EMPTY);require(fluid.fill(new net.minecraftforge.fluids.FluidStack(water,3),true)==3,"restore tested fluid owner");
        enable(arm,ArmResource.ITEM);
        GearTileEntity itemInput=(GearTileEntity)world.getTileEntity(at(2,0,3));
        resourceInput=itemInput.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,side);
        MatterPortTile inventory=(MatterPortTile)world.getTileEntity(at(2,endpoint,3));
        ItemStack offer=new ItemStack(net.minecraft.init.Items.GOLD_INGOT,3);
        require(resourceInput!=null&&resourceInput.insertItem(0,offer,true).isEmpty()&&inventory.stored(0).getCount()==3*previous,"item simulation leaves native inventory unchanged");
        require(resourceInput.insertItem(0,offer,false).isEmpty()&&inventory.stored(0).getCount()==3*(previous+1)&&resourceInput.extractItem(0,3,false).isEmpty(),"only the native receiver stores items");
        com.vandorlabs.ports.ResourcePortSettings inventorySettings=inventory.settings();inventory.configure(inventorySettings.withFace(side.getIndex(),com.vandorlabs.ports.ResourcePortSettings.Face.OUTPUT),0,com.vandorlabs.redstone.ChannelList.EMPTY);
        output(arm,true);require(resourceInput.insertItem(0,offer,false)==offer&&resourceInput.extractItem(0,1,true).getCount()==1&&inventory.stored(0).getCount()==3*(previous+1),"item output simulation/direction");
        ItemStack returned=resourceInput.extractItem(0,1,false);require(returned.getCount()==1&&inventory.stored(0).getCount()==3*(previous+1)-1,"items return through mounting face");
        output(arm,false);inventory.configure(inventorySettings,0,com.vandorlabs.redstone.ChannelList.EMPTY);require(resourceInput.insertItem(0,returned,false).isEmpty(),"restore tested item owner");
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        if(++ticks%100==0){net.minecraft.tileentity.TileEntity raw=world.getTileEntity(BASE);GearTileEntity control=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;System.out.println("[vandorlabs] arm-endpoint progress fixture="+fixture+" stage="+stage+" tick="+ticks+" root="+(control==null?"none":control.getPos()+" progress="+control.progress()+" operational="+control.isOperational()+" target="+control.target()+" stored="+control.hasStoredSections()+" payload="+(control.payload()==null?"none":control.payload().materialized())+" locked="+ArmTransactions.locked(world,control.getPos())+" error="+control.error()));}
        require(ticks<600,"endpoint transition timed out; "+clientState);
        if(stage==0){
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;player.connection.setPlayerLocation(playerPosition().x,playerPosition().y,playerPosition().z,90,0);
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-8,-8,-8),BASE.add(8,8,8)))world.setBlockToAir(p);
            for(int x:new int[]{0,2})for(int z:new int[]{0,3}){
                world.setBlockState(at(x,1,z),Blocks.STONE.getDefaultState(),2);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));
                for(int n=0;n<2;n++)require(player.getHeldItemMainhand().onItemUse(player,world,at(x,1-n,z),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"normal supported arm placement");
                GearTileEntity arm=(GearTileEntity)world.getTileEntity(at(x,0,z));require(arm.rebuild(),"column forms");NBTTagCompound settings=arm.settings();settings.setBoolean("JoinArms",true);settings.setInteger("JoinRange",4);arm.configure(settings);
            }
            for(int x=0;x<3;x++)for(int z=0;z<4;z++){
                BlockPos part=at(x,-2,z);world.setBlockState(part,platformBlock(x,z).getDefaultState(),2);
                if(world.getTileEntity(part) instanceof ResourcePortTile){
                    ResourcePortTile port=(ResourcePortTile)world.getTileEntity(part);
                    port.configure(ResourcePortSettings.placed(face().getOpposite().getIndex()),0,com.vandorlabs.redstone.ChannelList.EMPTY);
                }
            }
            world.setBlockState(at(0,-2,1),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState().withProperty(com.vandorlabs.blocks.BlockTurntableSurface.FACING,face().getOpposite()),2);
            world.setBlockState(at(1,-2,1),Blocks.CHEST.getDefaultState(),2);((net.minecraft.inventory.IInventory)world.getTileEntity(at(1,-2,1))).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,37));
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();stage=1;ticks=0;return false;
        }
        GearTileEntity member=(GearTileEntity)world.getTileEntity(BASE);GearTileEntity arm=member==null?null:member.loadedController();
        if(arm==null&&(stage==13||stage==15)&&ticks<30)return false;
        require(arm!=null,"controller remains available");
        boolean locked=ArmTransactions.locked(world,arm.getPos());
        if(fixture==1&&resourceInput!=null&&(locked||arm.progress()>0&&arm.progress()<1)){
            ItemStack offer=new ItemStack(net.minecraft.init.Items.GOLD_INGOT);
            require(resourceInput.insertItem(0,offer,false)==offer,"cached item input refuses through journal hold and motion");
        }
        if(locked)return false;
        if(stage==1){
            if(fixture==1&&!resourcesChecked&&ticks>=6&&arm.progress()==1&&arm.target()){
                GearTileEntity memberInput=(GearTileEntity)world.getTileEntity(at(0,0,0));
                require(memberInput.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,face().getOpposite())==null,"previous saves and ordinary placement default to Off");
                checkResources(world,arm,-2,0);resourcesChecked=true;
            }if(!clicked&&ticks>=5&&arm.progress()==1)require(((com.vandorlabs.tiles.TileEntityTurntableSurface)world.getTileEntity(at(0,-2,1))).active(),"newly attached Surface is active before capture");if(!clicked||arm.target())return false;require(arm.columns().size()==4,"four joined corners");stage=2;ticks=0;return false;}
        if(stage==2){if(arm.progress()!=0)return false;if(endpointTick==0)endpointTick=ticks;if((arm.payload()==null||!arm.payload().materialized())&&ticks-endpointTick<100)return false;
            require(arm.payload()!=null&&arm.payload().materialized(),"aligned retracted platform must become normal world blocks; error="+arm.error()+" stored="+arm.hasStoredSections());
            for(int x=0;x<3;x++)for(int z=0;z<4;z++){require(world.isAirBlock(at(x,-2,z)),"no old platform owner");require(world.getBlockState(at(x,-1,z)).getBlock()==platformBlock(x,z),"normal endpoint cell "+x+","+z);}
            require(!((com.vandorlabs.tiles.TileEntityTurntableSurface)world.getTileEntity(at(0,-1,1))).active(),"retracted Surface is inactive");
            if(fixture==1)checkResources(world,arm,-1,1);
            require(((net.minecraft.inventory.IInventory)world.getTileEntity(at(1,-1,1))).getStackInSlot(0).getCount()==37,"native chest contents");
            NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());NBTTagCompound turned=saved.copy();for(int turn=0;turn<4;turn++){GearData.rotate(turned,1,world);require(ArmStoredSections.read(turned.getCompoundTag("ArmStoredSections")).cells.size()==4,"rotated vehicle data preserves four stored sections");}require(saved.getCompoundTag("ArmStoredSections").equals(turned.getCompoundTag("ArmStoredSections")),"stored section data returns exactly after four vehicle headings");
            BlockPos root=arm.getPos();world.removeTileEntity(root);GearTileEntity reload=new GearTileEntity();reload.readFromNBT(saved);world.setTileEntity(root,reload);reload.operator(player);reload.onLoad();stage=3;ticks=0;return false;
        }
        if(stage==3){if(ticks<25)return false;if(fixture==1)require(arm.resourceTransfer()==ArmResource.ITEM,"transfer mode survives tile reload");require(arm.isOperational()&&arm.progress()==0&&arm.assembly().columnHeight()==2,"reload preserves retracted two-section columns");require(!((com.vandorlabs.tiles.TileEntityTurntableSurface)world.getTileEntity(at(0,-1,1))).active(),"reloaded retracted Surface is inactive");require(!player.interactionManager.tryHarvestBlock(BASE)&&world.getTileEntity(BASE)!=null,"retracted arm sections stay protected while platform cells remain editable");require(player.interactionManager.tryHarvestBlock(at(1,-1,2)),"mine ordinary interior platform cell");stage=4;ticks=0;return false;}
        if(stage==4){if(ticks<5)return false;arm.target(true);stage=5;ticks=0;return false;}
        if(stage==5){if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;if(fixture==1)checkResources(world,arm,-2,2);require(((com.vandorlabs.tiles.TileEntityTurntableSurface)world.getTileEntity(at(0,-2,1))).active(),"extended Surface is active");require(world.isAirBlock(at(1,-2,2)),"mined block is not recreated");for(int x:new int[]{0,2})for(int z:new int[]{0,3})require(world.getBlockState(at(x,-1,z)).getBlock()==block(),"stored arm sections return on extension");require(((net.minecraft.inventory.IInventory)world.getTileEntity(at(1,-2,1))).getStackInSlot(0).getCount()==37,"chest inventory preserved on reverse");stage=18;ticks=0;return false;}
        if(stage==18){if(ticks<20)return false;require(clientSections==null,"restored sections after partial mining: "+clientSections);arm.target(false);stage=6;ticks=0;return false;}
        if(stage==6){if(arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return false;
            place(world,player,at(1,-1,2),Blocks.GOLD_BLOCK);require(player.interactionManager.tryHarvestBlock(at(1,-1,0))&&player.interactionManager.tryHarvestBlock(at(0,-1,1)),"disconnect one corner");stage=9;ticks=0;return false;}
        if(stage==9){if(ticks<5)return false;arm.target(true);stage=10;ticks=0;return false;}
        if(stage==10){if(ticks<20)return false;require(arm.progress()==0&&!arm.target()&&arm.payload()!=null&&arm.payload().materialized(),"disconnected corner refuses at the native endpoint without hiding blocks");require(world.getBlockState(at(1,-1,2)).getBlock()==Blocks.GOLD_BLOCK,"new endpoint block remains native after refusal");place(world,player,at(1,-1,0),Blocks.STONE);place(world,player,at(0,-1,1),Blocks.STONE);arm.target(true);stage=11;ticks=0;return false;}
        if(stage==11){if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;require(world.getBlockState(at(1,-2,2)).getBlock()==Blocks.GOLD_BLOCK,"new block added while retracted travels on the next stroke");arm.target(false);stage=12;ticks=0;return false;}
        if(stage==12){if(arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return false;for(int x=0;x<3;x++)for(int z=0;z<4;z++)if(!world.isAirBlock(at(x,-1,z)))require(player.interactionManager.tryHarvestBlock(at(x,-1,z)),"mine all retracted platform blocks");stage=7;ticks=0;return false;}
        if(stage==7){if(ticks<10)return false;require(arm.payload()==null,"removed payload is no longer retained");arm.target(true);stage=8;ticks=0;return false;}
        if(stage==13){if(ticks<25)return false;require(arm.columns().size()==3,"shortened corner arm is ignored by the matching group");for(int x=0;x<3;x++)for(int z=0;z<4;z++)world.setBlockState(at(x,-2,z),Blocks.STONE.getDefaultState(),2);arm.target(false);stage=14;ticks=0;return false;}
        if(stage==14){if(ticks<20)return false;require(arm.progress()==1&&arm.target(),"missing matching corner refuses platform move at construction endpoint");player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block()));require(player.getHeldItemMainhand().onItemUse(player,world,at(2,0,3),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"rebuild shortened arm with normal placement");player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);stage=15;ticks=0;return false;}
        if(stage==15){if(ticks<25)return false;require(arm.columns().size()==4,"rebuilt matching corner rejoins automatically");arm.target(false);stage=16;ticks=0;return false;}
        if(stage==16){if(arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return false;arm.target(true);stage=17;ticks=0;return false;}
        if(stage==17){if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;return next();}
        if(arm.progress()!=1)return false;if(stage==8){stage=19;ticks=0;return false;}if(ticks<20)return false;require(clientSections==null,"restored sections after complete mining: "+clientSections);require(arm.payload()==null&&arm.assembly().columnHeight()==2,"empty arms extend at original length");for(int x=0;x<3;x++)for(int z=0;z<4;z++)require(world.isAirBlock(at(x,-2,z)),"empty extension creates no payload");
        if(fixture%6==0){require(player.interactionManager.tryHarvestBlock(at(2,-1,3)),"remove an extended corner section");stage=13;ticks=0;return false;}return next();
    }
    private static boolean next(){
        System.out.println("[vandorlabs] arm-endpoint case PASS fixture="+fixture+" face="+face());fixture++;stage=0;ticks=0;endpointTick=0;clicked=false;resourcesChecked=false;resourceInput=null;if(fixture==12)System.out.println("[vandorlabs] joined-arm PASS arm-endpoint cases="+(fixture-START)+" 3x4 regular/Large six axes native blocks chest reload mining reverse empty extension corner arm removal rebuild rejoining");return fixture==12;
    }
    private ArmEndpointRuntimeChecks(){}
}
