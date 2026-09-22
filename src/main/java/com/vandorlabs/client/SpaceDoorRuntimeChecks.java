package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockConfigurableSpaceDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.render.SpaceDoorMotion;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Real item placement and pick-block tests; invoked by the live ReproLab. */
final class SpaceDoorRuntimeChecks {
    private static void check(boolean ok,String message) {
        if (!ok) throw new IllegalStateException("Space door runtime: "+message);
    }
    private static TileEntitySpaceDoor tile(World world,BlockPos pos) {
        return (TileEntitySpaceDoor)world.getTileEntity(pos);
    }
    private static void clear(World world,BlockPos pos) {
        world.setBlockToAir(pos.up()); world.setBlockToAir(pos);
    }
    private static IBlockState state(BlockConfigurableSpaceDoor block) {
        return block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH)
                .withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER)
                .withProperty(BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.RIGHT);
    }
    static void run(World world,EntityPlayer player,BlockPos source) {
        BlockConfigurableSpaceDoor block=(BlockConfigurableSpaceDoor)Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs","space_door"));
        ItemStack blank=new ItemStack(block);
        ItemBlock item=(ItemBlock)blank.getItem();
        BlockPos target=source.east(4), neighbor=target.east();
        for (BlockPos pos:new BlockPos[]{source,target,neighbor}) {
            clear(world,pos); world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);
        }
        check(item.placeBlockAt(blank.copy(),player,world,source,EnumFacing.UP,.5F,.5F,.5F,state(block)),"default placement");
        check(tile(world,source).isSliding() && tile(world,source).isMiddle()
                && tile(world,source).getSlideDirection()==0,"new door must default to sideways / middle");
        // Existing settings survive the default change and NBT reload.
        tile(world,source).configure(4,2,false,0,false,false);
        NBTTagCompound saved=tile(world,source).writeToNBT(new NBTTagCompound());
        tile(world,source).readFromNBT(saved);
        check(!tile(world,source).isSliding() && !tile(world,source).isMiddle(),"saved rotating/edge changed");
        // A different adjacent configuration must not overwrite a picked item.
        IBlockState other=state(block).withProperty(BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.LEFT);
        check(item.placeBlockAt(blank.copy(),player,world,neighbor,EnumFacing.UP,.5F,.5F,.5F,other),"neighbor placement");
        tile(world,neighbor).configure(0,0,true,2,false,true);
        NBTTagCompound neighborSettings=tile(world,neighbor).itemSettings();
        int cases=0;
        for (int design=0;design<TileEntitySpaceDoor.DESIGNS.length;design++) for (int detail=0;detail<3;detail++)
            for (SpaceDoorMotion motion:SpaceDoorMotion.values()) for (boolean framed:new boolean[]{false,true})
                for (boolean middle:new boolean[]{false,true}) {
                    TileEntitySpaceDoor original=tile(world,source);
                    original.configure(design,detail,framed,motion.direction,middle,motion.sliding);
                    original.setRedstoneChannel(7341);
                    NBTTagCompound expected=original.itemSettings();
                    for (BlockPos half:new BlockPos[]{source,source.up()}) {
                        ItemStack picked=block.getPickBlock(world.getBlockState(half),null,world,half,player);
                        check(picked.getItem()==blank.getItem(),"pick must return unified item");
                        NBTTagCompound settings=picked.getSubCompound("SpaceDoorSettings");
                        check(expected.equals(settings),"pick lost settings from "+half);
                        check(!settings.hasKey("x") && !settings.hasKey("id") && !settings.hasKey("ChannelSignal"),
                                "pick copied transient tile data");
                        clear(world,target);
                        check(item.placeBlockAt(picked,player,world,target,EnumFacing.UP,.5F,.5F,.5F,state(block)),"picked placement");
                        check(tile(world,target).itemSettings().equals(expected),"placement changed copied properties");
                        check(!world.getBlockState(target).getValue(BlockVandorDoor.OPEN),"pick copied open state");
                        check(tile(world,neighbor).itemSettings().equals(neighborSettings),"pick changed neighbor settings");
                        cases++;
                    }
                }
        for (BlockPos pos:new BlockPos[]{source,target,neighbor}) clear(world,pos);
        System.out.println("[vandorlabs][reprolab] space-door-settings PASS: "+cases
                +" upper/lower pick-and-place cases, defaults, saved settings and neighbor precedence");
    }
}
