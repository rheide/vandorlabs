package com.vandorlabs.gear;

import com.vandorlabs.items.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Real configured columns and the Copifier's capture/mask/apply path. */
@SideOnly(Side.CLIENT)
final class ArmCopifierChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Arm Copifier: "+why);}
    static void run(WorldServer world,EntityPlayerMP player){
        BlockPos origin=new BlockPos(2200,100,0);
        for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM}){
            for(BlockPos p:BlockPos.getAllInBox(origin.add(-3,-2,-3),origin.add(14,4,4))){world.removeTileEntity(p);world.setBlockToAir(p);}
            player.setGameType(GameType.CREATIVE);
            GearTileEntity source=place(world,player,origin,block,0),target=place(world,player,origin.east(10),block,90);
            NBTTagCompound settings=source.settings();settings.setBoolean("JoinArms",true);settings.setInteger("JoinRange",5);settings.setInteger("LoadHeight",7);settings.setBoolean("RotatePayload",true);settings.setDouble("ActiveExtension",2);source.configure(settings);
            net.minecraft.block.state.IBlockState placed=world.getBlockState(target.getPos());source.target(false);double progress=target.progress();
            ItemStack tool=new ItemStack(ModItems.DUPLIFIER);player.setHeldItem(EnumHand.MAIN_HAND,tool);
            require(ItemDuplifier.copyFrom(world,origin.up(),tool)!=null,"capture through the non-controller section");
            require(ItemDuplifier.applyTo(world,origin.east(10).up(),tool,player),"paste through the non-controller section");
            require(target.joinEnabled()&&target.joinRange()==5&&target.settings().getInteger("LoadHeight")==7&&target.settings().getBoolean("RotatePayload")&&target.settings().getDouble("ActiveExtension")==2,"authored platform and movement settings copied "+block);
            require(target.target()&&target.progress()==progress&&world.getBlockState(target.getPos())==placed,"paste preserves current target, progress and placement orientation");
            NBTTagCompound disabled=target.settings();disabled.setBoolean("JoinArms",false);target.configure(disabled);
            NBTTagCompound subset=tool.getSubCompound(ItemDuplifier.SETTINGS_TAG).copy();subset.removeTag(ProgrammableSettings.MODULAR_ARM_SETTINGS);require(ProgrammableSettings.apply(world,target.getPos(),subset,player)&&!target.joinEnabled(),"omitting Arm settings preserves target joining");
        }
        for(BlockPos p:BlockPos.getAllInBox(origin.add(-3,-2,-3),origin.add(14,4,4))){world.removeTileEntity(p);world.setBlockToAir(p);}
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        System.out.println("[vandorlabs] arm-copifier PASS regular/Large Extender/Rotating controllers and member sections authored join range load movement options masks orientation and live progress preserved");
    }
    private static GearTileEntity place(WorldServer world,EntityPlayerMP player,BlockPos at,GearBlock block,float yaw){
        world.setBlockState(at.down(),Blocks.STONE.getDefaultState(),2);player.rotationYaw=yaw;player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block,8));
        for(int n=0;n<2;n++)require(player.getHeldItemMainhand().onItemUse(player,world,at.down().up(n),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"supported column placement");
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(at);require(arm.rebuild(),"two-section assembly");arm.operator(player);return arm;
    }
    private ArmCopifierChecks(){}
}
