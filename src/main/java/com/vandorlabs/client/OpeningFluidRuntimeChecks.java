package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

/** Reversible aperture fill must never become a second stored bucket. */
final class OpeningFluidRuntimeChecks {
    static final String[] IDS={"programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor"};
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("opening fluids: "+message);}
    static void run(World world){
        BlockPos pos=new BlockPos(148,230,148);
        try{for(String id:IDS)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++){
            for(EnumFacing side:EnumFacing.values())world.setBlockToAir(pos.offset(side));world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            Block block=Block.getBlockFromName("vandorlabs:"+id);boolean door=block instanceof BlockConfigurableSpaceDoor;
            IBlockState state=block.getDefaultState().withProperty(door?BlockVandorDoor.FACING:BlockProgrammableTrapdoor.FACING,facing);world.setBlockState(pos,state,2);
            TileEntity tile=world.getTileEntity(pos);
            if(door){world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);((TileEntitySpaceDoor)tile).setPlacementDepth(depth);require(WaterloggedParts.data(world.getTileEntity(pos.up()))==WaterloggedParts.data(tile),"door halves share stored sides");}
            else ((TileEntityProgrammableTrapdoor)tile).configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,depth,false,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED,0);
            ContainedFluids data=WaterloggedParts.data(tile);data.set(0,new FluidStack(FluidRegistry.WATER,1000));require(WaterloggedParts.fluid(world,pos,1)==null,"closed dry side");double[] plane=OpeningFluids.plane(world,pos);if(tile instanceof TileEntityProgrammableDiagonalTrapdoor)require(Math.abs(plane[1])>.1&&Math.hypot(plane[0],plane[2])>.1,"diagonal uses its actual sloped leaf plane");
            NBTTagCompound stored=tile.writeToNBT(new NBTTagCompound());
            world.setBlockState(pos,world.getBlockState(pos).withProperty(door?BlockVandorDoor.OPEN:BlockProgrammableTrapdoor.OPEN,true),2);
            require(WaterloggedParts.fluid(world,pos,1)!=null,"opening fills opposite side");require(!data.occupied(1),"temporary fill is not stored");if(door){require(WaterloggedParts.fluid(world,pos.up(),1)!=null,"upper opening follows lower open state");require(java.util.Arrays.equals(plane,OpeningFluids.plane(world,pos.up())),"upper fluid sides follow lower facing and depth");}require(java.util.Arrays.equals(plane,OpeningFluids.plane(world,pos)),"fluid sides stay fixed as leaf opens");
            require(Boolean.TRUE.equals(block.isAABBInsideMaterial(world,pos,new AxisAlignedBB(pos),net.minecraft.block.material.Material.WATER)),"opening water physics");
            TileEntity saved=TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));require(WaterloggedParts.data(saved).occupied(0)&&!WaterloggedParts.data(saved).occupied(1),"save keeps original sides only");
            world.setBlockState(pos,world.getBlockState(pos).withProperty(door?BlockVandorDoor.OPEN:BlockProgrammableTrapdoor.OPEN,false),2);require(WaterloggedParts.fluid(world,pos,1)==null,"closing removes opposite fill");require(data.occupied(0),"closing preserves source side");
            data.set(1,new FluidStack(FluidRegistry.LAVA,1000));world.setBlockState(pos,world.getBlockState(pos).withProperty(door?BlockVandorDoor.OPEN:BlockProgrammableTrapdoor.OPEN,true),2);require(WaterloggedParts.fluid(world,pos,1).getFluid()==FluidRegistry.LAVA,"distinct saved fluid is not replaced");
            data.set(0,null);data.set(1,null);double[] p=OpeningFluids.plane(world,pos);EnumFacing waterFace=null;for(EnumFacing face:EnumFacing.values())if(-(p[0]*face.getFrontOffsetX()+p[1]*face.getFrontOffsetY()+p[2]*face.getFrontOffsetZ())>.1){waterFace=face;break;}require(waterFace!=null,"water-facing boundary");world.setBlockState(pos.offset(waterFace),net.minecraft.init.Blocks.WATER.getDefaultState(),2);require(WaterloggedParts.fluid(world,pos,0)!=null&&WaterloggedParts.fluid(world,pos,1)!=null,"adjacent water fills open aperture");world.setBlockState(pos,world.getBlockState(pos).withProperty(door?BlockVandorDoor.OPEN:BlockProgrammableTrapdoor.OPEN,false),2);require(WaterloggedParts.fluid(world,pos,0)!=null&&WaterloggedParts.fluid(world,pos,1)==null,"closing restores external water side");
            world.setBlockToAir(pos.offset(waterFace));world.setBlockToAir(pos.up());world.setBlockToAir(pos);
        }}finally{world.setBlockToAir(pos);for(EnumFacing side:EnumFacing.values())world.setBlockToAir(pos.offset(side));}
        System.out.println("[vandorlabs][reprolab] opening-fluids PASS door trapdoor diagonal-trapdoor four-facings three-depths reversible-fill water-physics saved-sides external-water distinct-fluids");
    }
}
