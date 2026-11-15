package com.vandorlabs.gear;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.core.*;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.tiles.TileEntityConstructor;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Separate clearance construction: original test4 floor and supports are never edited. */
final class ArmCoreGapRaisedFixture {
    static final BlockPos ROOT=ArmCoreFixture.ROOT;
    static final Map<BlockPos,IBlockState> SUPPORTS=new LinkedHashMap<>();
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Raised Oculus construction: "+why);}
    private static void place(WorldServer world,EntityPlayerMP actor,Block block,BlockPos at){need(world.isAirBlock(at),"clear construction cell "+at);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block));need(actor.getHeldItemMainhand().onItemUse(actor,world,at,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS&&world.getBlockState(at).getBlock()==block,"ordinary item placement "+block.getRegistryName()+" "+at);}
    static GearTileEntity build(WorldServer world,EntityPlayerMP actor){
        ArmCoreFixture.enableCaps();ArmCoreFixture.emptyHands(actor,GameType.CREATIVE);actor.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+4.5,180,0);
        int floor=ROOT.getY()-7;
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-7,-9),ROOT.add(6,9,5)))if(!world.isAirBlock(at))world.setBlockToAir(at);
        for(int x=-4;x<=6;x++)for(int z=-9;z<=5;z++){BlockPos at=new BlockPos(ROOT.getX()+x,floor,ROOT.getZ()+z);place(world,actor,Blocks.STONE,at);SUPPORTS.put(at,world.getBlockState(at));}
        for(int x:new int[]{0,2})for(int y=floor+1;y<ROOT.getY();y++){BlockPos at=new BlockPos(ROOT.getX()+x,y,ROOT.getZ());place(world,actor,Blocks.STONE,at);SUPPORTS.put(at,world.getBlockState(at));}
        for(int x:new int[]{0,2}){actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_SWING_ARM,5));for(int y=0;y<5;y++){BlockPos support=ROOT.add(x,y-1,0);need(actor.getHeldItemMainhand().onItemUse(actor,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported arm section");}GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT.add(x,0,0));need(arm.rebuild()&&arm.assembly().columnHeight()==5,"five real sections");}
        for(int x:new int[]{0,2}){GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT.add(x,0,0));NBTTagCompound settings=arm.settings();settings.setBoolean("RotatePayload",true);settings.setInteger("Direction",GearMotion.Direction.SOUTH.ordinal());settings.setBoolean("MiddleHinge",true);settings.setInteger("HingeMode",ArmHinge.Mode.DOUBLE.ordinal());settings.setInteger("KinkDirection",GearMotion.Direction.SOUTH.ordinal());settings.setBoolean("JoinArms",true);settings.setInteger("JoinRange",8);settings.setDouble("StartAngle",0);settings.setDouble("ActiveAngle",90);arm.configure(settings);}
        BlockConstructorCore core=(BlockConstructorCore)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:oculus_drive_core"));BlockPos owner=ROOT.up(5);actor.rotationYaw=0;place(world,actor,core,owner);
        IBlockState wanted=world.getBlockState(owner);need(wanted.getValue(BlockVandorDirectional.FACING)==EnumFacing.NORTH,"ordinary placed Core facing");
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));CoreLayout layout=new CoreLayout(3,3,3,EnumFacing.NORTH.ordinal(),0,2);
        for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++){BlockPos at=owner.add(layout.x(x,z),y,layout.z(x,z));if(!at.equals(owner)&&layout.frame(x,y,z))place(world,actor,matter,at);}
        actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);TileEntityConstructor machine=(TileEntityConstructor)world.getTileEntity(owner);need(core.onBlockActivated(world,owner,wanted,actor,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)&&machine.formed(),"ordinary frame activation");need(machine.layout().cornerX==0&&machine.layout().cornerZ==2,"same saved Core corner and facing");
        GearTileEntity root=(GearTileEntity)world.getTileEntity(ROOT);need(root.rebuild()&&root.columns().size()==2&&root.assembly().columnHeight()==5,"two matching joined real columns");need(root.mode()==GearMotion.Mode.ROTATE&&root.hingeMode()==ArmHinge.Mode.DOUBLE&&root.direction()==GearMotion.Direction.SOUTH&&root.kinkDirection()==GearMotion.Direction.SOUTH,"same ROTATE/Double/South mechanism");ArmCoreFixture.emptyHands(actor,GameType.SURVIVAL);return root;
    }
    static void intact(WorldServer world){for(Map.Entry<BlockPos,IBlockState> entry:SUPPORTS.entrySet())need(world.getBlockState(entry.getKey())==entry.getValue(),"clearance floor and actual mounting pillars retained");}
    private ArmCoreGapRaisedFixture(){}
}
