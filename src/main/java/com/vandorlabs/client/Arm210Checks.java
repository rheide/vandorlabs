package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Real installed assemblies exercise independent landing direction and older saves. */
final class Arm210Checks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("arms-210: "+message);}
    static void server(WorldServer world,EntityPlayerMP actor){
        BlockPos root=new BlockPos(520,180,340);int cases=0;
        actor.setGameType(net.minecraft.world.GameType.CREATIVE);
        try{for(EnumFacing mount:EnumFacing.values())for(int type:new int[]{0,1,2}){
            for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-5,-5),root.add(5,5,5))){world.removeTileEntity(at);world.setBlockToAir(at);}
            world.setBlockState(root.offset(mount.getOpposite()),Blocks.STONE.getDefaultState(),2);
            GearBlock arm=type==2?GearRegistry.LARGE_GEAR_SWING_ARM:GearRegistry.GEAR_SWING_ARM,foot=type==2?GearRegistry.LARGE_LANDING_WHEEL:type==1?GearRegistry.LANDING_WHEEL:GearRegistry.LANDING_SKI;
            EnumFacing nativeFacing=type==2&&(mount==EnumFacing.NORTH||mount==EnumFacing.WEST)?EnumFacing.SOUTH:EnumFacing.NORTH;
            System.out.println("[vandorlabs][reprolab] arms-210 landing-direction fixture mount="+mount+" type="+type);
            require(((ItemBlock)Item.getItemFromBlock(arm)).placeBlockAt(new ItemStack(arm),actor,world,root,mount,.5F,.5F,.5F,arm.getDefaultState().withProperty(GearBlock.FACING,nativeFacing)),"ordinary supported arm placement "+mount);
            require(((ItemBlock)Item.getItemFromBlock(foot)).placeBlockAt(new ItemStack(foot),actor,world,root.offset(mount),mount,.5F,.5F,.5F,foot.getDefaultState().withProperty(GearBlock.FACING,nativeFacing)),"foot placement "+mount+" type="+type);
            GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);require(tile.rebuild(),"landing assembly forms "+mount+" "+tile.error());
            EnumFacing fold=EnumFacing.valueOf(tile.direction().name());
            for(EnumFacing facing:new EnumFacing[]{EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.NORTH}){
                NBTTagCompound settings=tile.settings();settings.setInteger("FootFacing",facing.getHorizontalIndex());tile.configure(settings);
                require(tile.footFacing()==facing&&tile.direction().name().equals(fold.name()),"foot direction independent of folding "+mount);
                require(tile.rebuild()&&tile.footFacing()==facing,"rebuild retains explicit foot direction");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());GearTileEntity copy=(GearTileEntity)TileEntity.create(world,saved);
                require(copy.footFacing()==facing,"save/reload retains foot direction");
                NBTTagCompound rotated=saved.copy();for(int turn=0;turn<4;turn++)GearData.rotate(rotated,1,world);if(!rotated.equals(saved)){java.util.Set<String> keys=new java.util.TreeSet<>(saved.getKeySet());keys.addAll(rotated.getKeySet());for(String key:keys)if(!java.util.Objects.equals(saved.getTag(key),rotated.getTag(key)))System.err.println("[vandorlabs] arm-four-yaw-difference mount="+mount+" type="+type+" key="+key+" before="+saved.getTag(key)+" after="+rotated.getTag(key));}require(rotated.equals(saved),"four vehicle rotations retain explicit foot setting");
            }
            NBTTagCompound authored=tile.settings();authored.setInteger("FootFacing",EnumFacing.EAST.getHorizontalIndex());authored.setBoolean("MiddleHinge",true);authored.setInteger("HingeMode",2);authored.setInteger("KinkDirection",(tile.direction().ordinal()+1)%4);tile.configure(authored);
            ItemStack configured=arm.getPickBlock(world.getBlockState(root),null,world,root,actor);require(configured.getSubCompound("ModularGearSettings").getBoolean("MiddleHinge")&&configured.getSubCompound("ModularGearSettings").getInteger("FootFacing")==EnumFacing.EAST.getHorizontalIndex(),"configured pick retains hinge and foot direction");
            BlockPos placed=root.up(4);require(((ItemBlock)configured.getItem()).placeBlockAt(configured,actor,world,placed,mount,.5F,.5F,.5F,arm.getDefaultState()),"configured arm item placement");GearTileEntity configuredTile=(GearTileEntity)world.getTileEntity(placed);require(configuredTile.middleHinge()&&configuredTile.settings().getInteger("FootFacing")==EnumFacing.EAST.getHorizontalIndex(),"configured item restores authored controls");
            NBTTagCompound copied=com.vandorlabs.items.ProgrammableSettings.capture(world,root);NBTTagCompound copiedArm=copied.getCompoundTag(com.vandorlabs.items.ProgrammableSettings.MODULAR_ARM_SETTINGS);require(copiedArm.getBoolean("MiddleHinge")&&copiedArm.getInteger("FootFacing")==EnumFacing.EAST.getHorizontalIndex(),"Duplifier capture retains new arm keys");
            NBTTagCompound reset=configuredTile.settings();reset.setBoolean("MiddleHinge",false);reset.setInteger("HingeMode",0);reset.setInteger("FootFacing",EnumFacing.NORTH.getHorizontalIndex());configuredTile.configure(reset);require(com.vandorlabs.items.ProgrammableSettings.apply(world,placed,copied,actor)&&configuredTile.middleHinge()&&configuredTile.settings().getInteger("FootFacing")==EnumFacing.EAST.getHorizontalIndex(),"Duplifier apply restores hinge and direction");world.removeTileEntity(placed);world.setBlockToAir(placed);
            NBTTagCompound old=tile.writeToNBT(new NBTTagCompound());old.removeTag("ArmFootFacing");old.removeTag("ArmMiddleHinge");old.removeTag("ArmHingeMode");old.removeTag("ArmKinkDirection");old.removeTag("GearSourceFootFacing");old.setInteger("GearFootFacing",nativeFacing.getHorizontalIndex());GearTileEntity legacy=(GearTileEntity)TileEntity.create(world,old);require(legacy.footFacing()==nativeFacing&&!legacy.middleHinge(),"previous-release absent new fields preserve saved foot and Base hinge");
            NBTTagCompound invalid=tile.settings();invalid.setInteger("FootFacing",4);NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());boolean refused=false;try{tile.configure(invalid);}catch(IllegalArgumentException expected){refused=true;}require(refused&&before.equals(tile.writeToNBT(new NBTTagCompound())),"invalid facing is atomic");cases++;
        }}finally{for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-5,-5),root.add(5,5,5))){world.removeTileEntity(at);world.setBlockToAir(at);}}
        System.out.println("[vandorlabs][reprolab] arms-210 landing-direction PASS cases="+cases+" six mounts ski/wheel four directions rebuild reload cardinal rotations previous-save fallback atomic refusal");
    }
    private Arm210Checks(){}
}
