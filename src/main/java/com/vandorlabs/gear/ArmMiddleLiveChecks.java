package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Supported player placements, real interaction packets, asynchronous journals and endpoint reload. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmMiddleLiveChecks {
    private static final BlockPos ROOT=new BlockPos(1180,150,40);
    private static final int FIRST=Integer.getInteger("vandorlabs.armDoubleStart",0);
    private static int fixture=FIRST,stage,ticks,started;
    private static boolean clicked,copifier;
    private static final boolean DOUBLE_ONLY=Boolean.getBoolean("vandorlabs.armDoubleOnly");
    private static int variants(){return fixture/(DOUBLE_ONLY?6:2);}
    private static boolean joined(){return variants()/4==1;}
    private static boolean rotates(){return variants()/2%2==1;}
    private static EnumFacing mount(){return DOUBLE_ONLY?EnumFacing.values()[fixture%6]:fixture%2==0?EnumFacing.DOWN:EnumFacing.EAST;}
    private static GearBlock block(){return variants()%2==0?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM;}
    private static BlockPos at(int x,int y,int z){return ROOT.add(ArmOrientation.vector(mount(),new BlockPos(mount()==EnumFacing.WEST?-x:x,y,z)));}
    private static void require(boolean ok,String detail){if(!ok)throw new IllegalStateException("arm-middle fixture="+fixture+" stage="+stage+" tick="+ticks+": "+detail);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(fixture>=(DOUBLE_ONLY?48:16)){System.out.println("[vandorlabs][reprolab] arm-middle PASS cases="+((DOUBLE_ONLY?48:16)-FIRST)+" start="+FIRST+" supported ordinary placement actual-click real-async-journals regular/Large independent/joined upright/sideways rotate/level reload reverse native mining");return true;}
        ticks++;mc.displayGuiScreen(null);
        if(stage==1||stage==3){
            require(ticks-started<300,"client synchronization mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" server="+ArmInteractionSync.status());
            net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(ROOT);
            if(!clicked&&raw instanceof GearTileEntity&&((GearTileEntity)raw).middleHinge()&&((GearTileEntity)raw).mountDirection()==mount()&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(ROOT.getX()+3.5+(fixture&1)*.25,ROOT.getY()+2,ROOT.getZ()-2.5)<.0001&&ArmInteractionSync.acknowledged(mc)){mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(ROOT,mount(),EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;}
            if(!clicked)return false;
        }
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(!copifier){ArmCopifierChecks.run(world,actor);copifier=true;}
            if(stage==0){
                for(BlockPos cell:BlockPos.getAllInBox(ROOT.add(-8,-8,-8),ROOT.add(8,8,8))){world.removeTileEntity(cell);world.setBlockToAir(cell);}
                actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;
                for(int x:new int[]{0,2}){
                    if(x==2&&!joined())continue;world.setBlockState(at(x,1,0),Blocks.STONE.getDefaultState(),2);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));
                    for(int y=1;y>=0;y--)require(actor.getHeldItemMainhand().onItemUse(actor,world,at(x,y,0),EnumHand.MAIN_HAND,mount(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported column placement");
                    GearTileEntity arm=(GearTileEntity)world.getTileEntity(at(x,0,0));arm.operator(actor);require(arm.rebuild(),"column forms "+arm.error());NBTTagCompound settings=arm.settings();settings.setBoolean("MiddleHinge",true);settings.setInteger("HingeMode",2);settings.setInteger("KinkDirection",DOUBLE_ONLY?variants()%4:0);settings.setInteger("Direction",0);settings.setBoolean("RotatePayload",rotates());settings.setBoolean("JoinArms",joined());settings.setInteger("JoinRange",3);arm.configure(settings);
                }
                for(int x=0;x<=(joined()?2:0);x++)world.setBlockState(at(x,-2,0),Blocks.HAY_BLOCK.getDefaultState(),2);
                GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm.rebuild(),"joined topology forms "+arm.error());require(arm.columns().size()==(joined()?2:1),"expected support count");
                actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();actor.connection.setPlayerLocation(ROOT.getX()+3.5+(fixture&1)*.25,ROOT.getY()+2,ROOT.getZ()-2.5,0,0);stage=1;started=ticks;clicked=false;return false;
            }
            GearTileEntity arm=((GearTileEntity)world.getTileEntity(ROOT)).loadedController();require(arm!=null&&arm.middleHinge(),"middle hinge survives loaded owner");
            if(stage==1||stage==3){if(!clicked)return false;stage++;started=ticks;return false;}
            require(ticks-started<450,"motion or journal completes progress="+arm.progress()+" target="+arm.target()+" error="+arm.error());
            if(ArmTransactions.locked(world,ROOT)||ArmTransactions.blocked(arm))return false;
            if(stage==2){if(arm.progress()!=0)return false;require(arm.payload()!=null&&arm.payload().blocks.cells.size()==(joined()?3:1),"exact attachment ownership at active endpoint");NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());arm.readFromNBT(saved);arm.operator(actor);arm.onLoad();require(arm.middleHinge(),"saved hinge survives active reload");stage=3;started=ticks;clicked=false;return false;}
            if(arm.progress()!=1)return false;
            for(int x=0;x<=(joined()?2:0);x++)require(world.getBlockState(at(x,-2,0)).getBlock()==Blocks.HAY_BLOCK,"reversal restores native source payload");
            require(actor.interactionManager.tryHarvestBlock(at(0,-2,0))&&world.isAirBlock(at(0,-2,0)),"restored payload remains normally mineable");
            System.out.println("[vandorlabs][reprolab] arm-middle case PASS fixture="+fixture+" mount="+mount()+" size="+block().sizeClass()+" joined="+joined()+" rotates="+rotates());fixture++;stage=0;started=ticks;clicked=false;return false;
        }).get();
    }
    private ArmMiddleLiveChecks(){}
}
