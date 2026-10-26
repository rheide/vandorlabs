package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Real placement and world ticks: sizes x mounts x payload modes x joined/independent. */
@SideOnly(Side.CLIENT)
public final class ArmRotatingEndpointRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(1903,110,0);
    private static int fixture=Integer.getInteger("vandorlabs.rotatingEndpointStart",0),stage,ticks;
    private static final int START=fixture;
    private static boolean clicked;
    private static BlockPos edited;
    private static EnumFacing face(){return EnumFacing.values()[fixture%6];}
    private static boolean rotates(){return fixture/6%2==1;}
    private static boolean joined(){return fixture>=24;}
    private static GearBlock block(){return fixture/12%2==0?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM;}
    private static BlockPos at(int x,int y,int z){return ROOT.add(ArmOrientation.vector(face(),new BlockPos(x,y,z)));}
    private static Vec3d camera(){return new Vec3d(at(-3,-2,-4)).addVector(.5,.5,.5);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("rotating-endpoint fixture="+fixture+" stage="+stage+" tick="+ticks+" face="+face()+": "+why);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(fixture>=48)return true;
        mc.displayGuiScreen(null);
        if(stage==1&&ticks>20&&!clicked&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()
                &&mc.player.getDistanceSq(camera().x,camera().y,camera().z)<1&&mc.world.getBlockState(ROOT).getBlock()==block()){
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,face(),EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;
        }
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        require(++ticks<400,"bounded motion");
        if(stage==0){
            for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-8,-8,-8),ROOT.add(8,8,8))){world.removeTileEntity(p);world.setBlockToAir(p);}
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;
            for(int x:new int[]{0,2}){
                if(!joined()&&x==2)continue;
                world.setBlockState(at(x,1,0),Blocks.STONE.getDefaultState(),2);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));
                for(int n=0;n<2;n++)require(player.getHeldItemMainhand().onItemUse(player,world,at(x,1-n,0),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported placement");
                GearTileEntity arm=(GearTileEntity)world.getTileEntity(at(x,0,0));require(arm.rebuild(),"column forms");
                NBTTagCompound settings=arm.settings();settings.setBoolean("JoinArms",joined());settings.setInteger("JoinRange",3);settings.setInteger("Direction",2);settings.setBoolean("RotatePayload",rotates());arm.configure(settings);
            }
            for(int x=0;x<=(joined()?2:0);x++)world.setBlockState(at(x,-2,0),Blocks.HAY_BLOCK.getDefaultState(),2);
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();player.connection.setPlayerLocation(camera().x,camera().y,camera().z,0,0);
            stage=1;ticks=0;return false;
        }
        GearTileEntity arm=((GearTileEntity)world.getTileEntity(ROOT)).loadedController();require(arm!=null,"controller retained");
        if(stage==1&&ticks<=20)return false;
        if(ArmTransactions.locked(world,arm.getPos()))return false;
        require(arm.columns().size()==(joined()?2:1),"expected joined topology");
        require(ticks<150||arm.error().isEmpty(),"motion error: "+arm.error());
        if(stage==1){
            if(!clicked||arm.progress()!=0||arm.payload()==null)return false;
            require(rotates()==arm.payload().materialized(),"rotated grid endpoint is native, level fractional endpoint stays captured");
            if(rotates()){
                ArmPayload.Endpoint endpoint=ArmPayload.endpoint(arm,0);require(endpoint!=null,"native endpoint maps to construction");
                edited=endpoint.placed(at(joined()?2:0,-2,0));
                require(player.interactionManager.tryHarvestBlock(edited),"mine actual endpoint block");stage=2;ticks=0;return false;
            }
            arm.target(true);stage=4;ticks=0;return false;
        }
        if(stage==2){
            if(ticks<8)return false;
            require(world.isAirBlock(edited),"native payload removed");
            world.setBlockState(edited,Blocks.GOLD_BLOCK.getDefaultState(),2);arm.target(true);stage=3;ticks=0;return false;
        }
        if(stage==3){
            if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;
            require(arm.payload().blocks.cells.size()==(joined()?3:1),"new endpoint block included");
            for(int x=0;x<=(joined()?2:0);x++)require(world.getBlockState(at(x,-2,0)).getBlock()==(x==(joined()?2:0)?Blocks.GOLD_BLOCK:Blocks.HAY_BLOCK),"all edited platform cells restored");
            arm.target(false);stage=5;ticks=0;return false;
        }
        if(stage==4){
            if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;
            edited=at(joined()?2:0,-2,0);require(player.interactionManager.tryHarvestBlock(edited),"mine level payload at its native endpoint");stage=6;ticks=0;return false;
        }
        if(stage==6){
            if(ticks<8)return false;world.setBlockState(edited,Blocks.CHEST.getDefaultState(),2);
            ((net.minecraft.inventory.IInventory)world.getTileEntity(edited)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,37));arm.target(false);stage=7;ticks=0;return false;
        }
        if(stage==7){if(arm.progress()!=0||arm.payload()==null)return false;require(!arm.payload().materialized(),"level chest stays captured");arm.target(true);stage=8;ticks=0;return false;}
        if(stage==8){
            if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;
            require(((net.minecraft.inventory.IInventory)world.getTileEntity(edited)).getStackInSlot(0).getCount()==37,"native endpoint replacement preserves chest contents");
            for(BlockPos p:arm.payload().placedCells(arm))require(player.interactionManager.tryHarvestBlock(p),"mine entire native platform");stage=9;ticks=0;return false;
        }
        if(stage==5){
            if(arm.progress()!=0||arm.payload()==null||!arm.payload().materialized())return false;
            require(world.getBlockState(edited).getBlock()==Blocks.GOLD_BLOCK,"replacement returns to the same rotated native cell");
            for(BlockPos p:arm.payload().placedCells(arm))require(player.interactionManager.tryHarvestBlock(p),"mine entire rotated platform");stage=9;ticks=0;return false;
        }
        if(stage==9){if(ticks<8)return false;require(arm.payload()==null,"no retained removed payload");arm.target(rotates());stage=10;ticks=0;return false;}
        if(stage==10){
            if(arm.progress()!=(rotates()?1:0))return false;require(arm.payload()==null,"empty move creates no payload");
            if(!rotates()){arm.target(true);stage=11;ticks=0;return false;}
        }else if(stage==11&&arm.progress()!=1)return false;
        require(arm.payload()==null&&arm.progress()==1,"empty arm returns at original length");
        System.out.println("[vandorlabs] rotating-endpoint case PASS fixture="+fixture+" face="+face()+" joined="+joined()+" rotates="+rotates()+" size="+block().sizeClass());
        fixture++;stage=ticks=0;clicked=false;
        if(fixture>=48)System.out.println("[vandorlabs] arm-wall PASS saved independent joined and "+(48-START)+" selected live endpoint combinations edits inventory reverse empty motion");
        return fixture>=48;
    }
    private ArmRotatingEndpointRuntimeChecks(){}
}
