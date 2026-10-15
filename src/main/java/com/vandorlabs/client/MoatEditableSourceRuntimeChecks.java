package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Inactive Moat sources are editable blocks; activation captures their current data. */
final class MoatEditableSourceRuntimeChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Moat editable sources: "+message);}
    static void run(World world,EntityPlayerMP actor){
        BlockPos root=new BlockPos(120,40,112),seed=root.south();double x=actor.posX,y=actor.posY,z=actor.posZ;
        try{
            for(BlockPos at:BlockPos.getAllInBox(root.add(-3,-3,-3),root.add(3,7,6)))world.setBlockToAir(at);
            actor.setPosition(root.getX()+.5,root.getY()+2,root.getZ()-2);
            world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
            world.setBlockState(seed,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);
            TileEntityAnimatedScreenSelector source=(TileEntityAnimatedScreenSelector)world.getTileEntity(seed);source.setHousingTexture(3);
            NBTTagCompound first=source.writeToNBT(new NBTTagCompound());
            TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(root);
            require(ramp.configureHalfOffsets(actor,0,-2,8,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"configure inactive tower");
            require(!ramp.attached()&&world.getBlockState(seed).getBlock()==ModBlocks.PROGRAMMABLE_BLOCK,"inactive zero-Start source remains an ordinary editable block");
            require(first.equals(world.getTileEntity(seed).writeToNBT(new NBTTagCompound())),"inactive configuration preserves complete source tile data");
            // Edit both the selected extent and the material finish before first activation.
            source.setHousingTexture(6);world.setBlockState(seed.up(),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(seed.up())).setHousingTexture(6);
            NBTTagCompound changed=source.writeToNBT(new NBTTagCompound());
            ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            require(ramp.attached()&&ramp.isOpen()&&ramp.vehicleSources().contains(seed.up()),"activation captures the edited vertical extent");
            TileEntityControlledRamp part=null;for(BlockPos at:ramp.vehicleCells())if(world.getTileEntity(at) instanceof TileEntityControlledRamp&&((TileEntityControlledRamp)world.getTileEntity(at)).sourceTileTags.containsKey(seed)){part=(TileEntityControlledRamp)world.getTileEntity(at);break;}
            require(part!=null&&part.sourceTileTags.get(seed).equals(changed),"activation journals the edited finish and genuine source NBT");
            ramp.setChannelSignal(false);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            require(!ramp.attached()&&world.getBlockState(seed.up()).getBlock()==ModBlocks.PROGRAMMABLE_BLOCK,"inactive retraction releases original blocks");
            require(world.getTileEntity(seed).writeToNBT(new NBTTagCompound()).equals(changed),"retraction restores exact edited source NBT");
            world.setBlockToAir(seed.up());
            ramp.setChannelSignal(true);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            require(ramp.vehicleSources().size()==1,"next activation rescans shortened source tower");
            ramp.setChannelSignal(false);ControllerRuntimeChecks.elapsed(ramp,ramp.durationTicks()+1);
            require(!ramp.attached()&&world.isAirBlock(seed.up()),"removed source does not reappear from a stale journal");
            require(ramp.configureHalfOffsets(actor,2,-2,8,true,false,false,EnumFacing.SOUTH,0,false,1,true,false,0,0,true),"configure nonzero stored skew");
            require(ramp.attached()&&!ramp.isOpen(),"nonzero Start retains controlled geometry while inactive");
            require(ramp.recover(false),"nonzero skew recovers safely");
            require(world.getTileEntity(seed).writeToNBT(new NBTTagCompound()).equals(changed),"nonzero Start recovery also preserves source data");
            System.out.println("[vandorlabs][reprolab] moat-editable-off PASS zero-start-originals editable-extent finish-NBT recapture shortened-selection nonzero-start");
        }finally{
            if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(false);
            for(BlockPos at:BlockPos.getAllInBox(root.add(-3,-3,-3),root.add(3,7,6)))world.setBlockToAir(at);
            actor.setPosition(x,y,z);
        }
    }
}
