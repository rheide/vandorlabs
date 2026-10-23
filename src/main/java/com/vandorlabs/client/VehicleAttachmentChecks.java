package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.vehicle.VehicleScan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Ceiling attachment must coexist with slab separation from a hangar floor. */
final class VehicleAttachmentChecks {
    static void run(World world){
        overhang(world);
        BlockPos wall=new BlockPos(60,80,60);int cases=0;
        try{
            for(Block slab:new Block[]{Blocks.STONE_SLAB,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_RAMP_SLAB,Block.getBlockFromName("vandorlabs:programmable_light_slab")})
            for(boolean top:new boolean[]{false,true})for(EnumFacing side:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN})
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true}){
                IBlockState panel=Block.getBlockFromName("vandorlabs:programmable_diagonal_wall").getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
                IBlockState half=slab.getDefaultState();
                if(slab==Blocks.STONE_SLAB)half=half.withProperty(BlockSlab.HALF,top?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);
                else if(slab==ModBlocks.PROGRAMMABLE_SLAB)half=half.withProperty(BlockProgrammableSlab.HALF,top?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);
                else if(slab==ModBlocks.PROGRAMMABLE_RAMP_SLAB)half=half.withProperty(BlockRampControllerSlab.HALF,top?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);
                else half=half.withProperty(BlockProgrammableLightSlab.HALF,top?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM);
                BlockPos at=wall.offset(side);world.setBlockState(wall,panel,2);world.setBlockState(at,half,2);
                boolean connected=side==EnumFacing.UP?!top:top;
                require(VehicleScan.touches(world,wall,panel,side)==connected,"decorative wall to slab");
                require(VehicleScan.touches(world,at,half,side.getOpposite())==connected,"symmetric slab attachment");
                world.setBlockState(wall,Blocks.STONE.getDefaultState(),2);
                require(VehicleScan.touches(world,wall,Blocks.STONE.getDefaultState(),side)==connected,"solid floor separation retained");
                world.setBlockToAir(at);cases++;
            }
        }finally{world.setBlockToAir(wall);world.setBlockToAir(wall.up());world.setBlockToAir(wall.down());}
        System.out.println("[vandorlabs][reprolab] vehicle-slab-attachments PASS cases="+cases+" decorative ceiling capture, symmetric empty-half exclusion and solid floor separation");
    }
    private static void overhang(World world){
        BlockPos base=new BlockPos(76,90,60),wall=base.add(4,2,4),roof=wall.up().south(),decoy=wall.up().east();
        try{
            for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(7,5,7))){world.getChunkFromBlockCoords(p);world.setBlockToAir(p);}
            for(int x=0;x<5;x++)for(int z=0;z<5;z++)world.setBlockState(base.add(x,1,z),Blocks.STONE.getDefaultState(),2);
            BlockTelescopicLandingGear gear=(BlockTelescopicLandingGear)Block.getBlockFromName("vandorlabs:landing_gear");
            for(BlockPos at:new BlockPos[]{base,base.east(2),base.south(2)}){world.setBlockState(at,gear.getDefaultState(),2);((com.vandorlabs.tiles.TileEntityLandingGear)world.getTileEntity(at)).configure(0,0,0,0);}
            BlockPos seat=base.add(1,2,1);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),2);world.setBlockState(seat.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),2);
            world.setBlockState(base.add(2,2,1),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
            world.setBlockState(wall,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH),2);((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)world.getTileEntity(wall)).setDiagonalFullWidth(true);
            world.setBlockState(roof,Block.getBlockFromName("vandorlabs:programmable_light_slab").getDefaultState(),2);
            world.setBlockState(decoy,Blocks.STONE.getDefaultState(),2);
            VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<1000&&!scan.step(128);n++);
            require(scan.result!=null&&scan.error==null,"overhanging wall fixture scan: "+scan.error);
            require(scan.result.at(roof.subtract(scan.origin))!=null,"ceiling on diagonal overhang joins craft outside six-neighbour path");
            require(scan.result.at(decoy.subtract(scan.origin))==null,"corner-only contact does not capture unrelated block");
            System.out.println("[vandorlabs][reprolab] vehicle-overhang-discovery PASS real bounded scan captures attached ceiling and excludes corner contact");
        }finally{for(BlockPos p:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(7,5,7)))world.setBlockToAir(p);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Vehicle attachment: "+message);}
}
