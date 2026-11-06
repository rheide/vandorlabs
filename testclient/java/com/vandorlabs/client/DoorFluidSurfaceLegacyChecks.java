package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.segmented.*;
import net.minecraft.block.BlockDoor;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Actual final opening surfaces for both Door motions, nine-cell Large Door and stationary Segmented Door. */
public final class DoorFluidSurfaceLegacyChecks {
    private static BlockConfigurableSpaceDoor[] doors;
    private static SegmentedDoorBlock segmented;
    private static void stored(TileEntity tile,int pair){NBTTagCompound root=new NBTTagCompound(),data=new NBTTagCompound();for(int side=0;side<2;side++){int kind=side==0?(pair==1?0:pair==5?2:1):(pair==0?0:pair>=4?2:1);if(kind==0)continue;NBTTagCompound slot=new NBTTagCompound(),extra=new NBTTagCompound();slot.setString("FluidName",kind==1?"water":"lava");slot.setInteger("Amount",kind==1?777:333);extra.setInteger("FixtureKind",pair==3&&side==1?99:kind);slot.setTag("Tag",extra);data.setTag("Side"+side,slot);}root.setTag("ContainedFluids",data);if(tile instanceof TileEntitySpaceDoor)((TileEntitySpaceDoor)tile).fluids.read(root);else if(tile instanceof SegmentedDoorTile)((SegmentedDoorTile)tile).fluids.read(root);else ((TileEntityProgrammableTrapdoor)tile).fluids.read(root);}
    private static TileEntity setup(NonRenderingChecks.MemoryWorld world,BlockPos pos,int family,EnumFacing face,int depth,boolean open){
        if(family==3){world.setBlockState(pos,segmented.getDefaultState().withProperty(SegmentedDoorBlock.FACING,face),2);SegmentedDoorTile tile=(SegmentedDoorTile)world.getTileEntity(pos);tile.settings=new SegmentedDoorSettings(0,false,1,0,false,0,false,SegmentedDoorSettings.DEFAULT.glass,depth);tile.height=3;tile.topOffset=0;tile.column=1;tile.columns=3;tile.formed=true;tile.targetOpen=open;tile.from=open?1:0;tile.started=100;tile.duration=20;return tile;}
        BlockConfigurableSpaceDoor block=doors[family];BlockPos root=family==2?pos.offset(face.rotateYCCW(),-1).down():pos;
        for(int column=0;column<(family==2?3:1);column++)for(int row=0;row<(family==2?3:2);row++){BlockPos at=root.offset(face.rotateYCCW(),column).up(row);world.setBlockState(at,block.getDefaultState().withProperty(BlockVandorDoor.FACING,face).withProperty(BlockVandorDoor.OPEN,open).withProperty(BlockVandorDoor.HALF,row==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER),2);TileEntitySpaceDoor tile=(TileEntitySpaceDoor)world.getTileEntity(at);if(family==2)((TileEntityLargeProgrammableDoor)tile).assign(root);tile.setPlacementDepth(depth);}
        return world.getTileEntity(pos);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();TrapdoorFluidLegacyChecks.blocks();BlockDetailedDoor template=new BlockDetailedDoor("surface_door_template",BlockVandorDoor.DoorMotion.HINGED,false,true,false,1,15,11.24F,90,-90,-15,15);doors=new BlockConfigurableSpaceDoor[]{new BlockConfigurableSpaceDoor("surface_sliding",true,template),new BlockConfigurableSpaceDoor("surface_hinged",false,template),new BlockLargeProgrammableDoor("surface_large",template)};segmented=new SegmentedDoorBlock();Path file=Paths.get(args[0]);Files.createDirectories(file.toAbsolutePath().getParent());MessageDigest hash=MessageDigest.getInstance("SHA-256");int cases=0,faces=0;
        try(java.io.PrintWriter out=new java.io.PrintWriter(Files.newBufferedWriter(file))){for(int family=0;family<4;family++)for(EnumFacing face:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(boolean open:new boolean[]{false,true})for(int pair=0;pair<6;pair++)for(int neighbor=0;neighbor<8;neighbor++)for(BlockPos pos:new BlockPos[]{new BlockPos(8,80,8),new BlockPos(29999967,200,-29999967)}){NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);TileEntity tile=setup(world,pos,family,face,depth,open);stored(tile,pair);
            if(neighbor==1)world.setBlockState(pos.north(),net.minecraft.init.Blocks.WATER.getDefaultState(),2);if(neighbor==2)world.setBlockState(pos.west(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);if(neighbor==6)world.setBlockState(pos.up(),net.minecraft.init.Blocks.LAVA.getDefaultState(),2);if(neighbor==7)world.setBlockState(pos.north(),net.minecraft.init.Blocks.FLOWING_WATER.getDefaultState().withProperty(net.minecraft.block.BlockLiquid.LEVEL,3),2);
            if(neighbor>=3&&neighbor<=5){BlockPos at=neighbor==3?pos.up():neighbor==4?pos.west():pos.east();TileEntityProgrammableTrapdoor other=TrapdoorFluidLegacyChecks.setup(world,at,false,1,EnumFacing.NORTH,0,neighbor==5);stored(other,neighbor==4?3:2);}
            faces+=TrapdoorFluidSurfaceLegacyChecks.emit(world,pos,tile,family,pair,neighbor,out,hash);cases++;
        }}StringBuilder golden=new StringBuilder();for(byte value:hash.digest())golden.append(String.format(Locale.ROOT,"%02x",value&255));if(cases!=9216||faces!=73988||!golden.toString().equals("c4f9104a38efb53e2f7f39438e1d4a268d22aa315170bb8bcea9633d39d05cc6"))throw new AssertionError("Door-family surface corpus changed "+cases+"/"+faces+"/"+golden);System.out.println("Actual Door-family final fluid surfaces PASS cases="+cases+" faces="+faces+" golden="+golden);
    }
}
