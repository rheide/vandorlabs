package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Client-side oracle may call vanilla's stripped combined-light method. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
final class VehicleCombinedLightChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Vehicle combined light: "+why);}
    static void run(WorldServer parent){
        EntityGroundVehicle craft=new EntityGroundVehicle(parent);
        VehicleStructure source=VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null),new VehicleStructure.Cell(new BlockPos(4,0,4),Blocks.STONE.getDefaultState(),null)));
        craft.install(source);craft.setPosition(1712,190,1712);BlockPos sample=BlockPos.ORIGIN;
        try{for(int yaw:new int[]{0,45,90}){
            craft.rotationYaw=yaw;BlockPos physical=new BlockPos(craft.toWorld(new Vec3d(sample).addVector(.5,.5,.5)));
            Map<BlockPos,IBlockState> states=new LinkedHashMap<>();Map<BlockPos,int[]> lights=new LinkedHashMap<>();
            List<BlockPos> touched=new ArrayList<>();touched.add(physical);for(EnumFacing face:EnumFacing.VALUES)touched.add(physical.offset(face));
            for(BlockPos pos:touched){parent.getChunkFromBlockCoords(pos);states.put(pos,parent.getBlockState(pos));lights.put(pos,new int[]{parent.getLightFor(EnumSkyBlock.SKY,pos),parent.getLightFor(EnumSkyBlock.BLOCK,pos)});}
            try{
                for(BlockPos pos:touched)parent.setBlockState(pos,Blocks.AIR.getDefaultState(),2);
                parent.setBlockState(physical,Blocks.STONE_SLAB.getDefaultState(),2);
                require(parent.getBlockState(physical).useNeighborBrightness(),"slab fixture uses neighbor brightness");
                parent.setLightFor(EnumSkyBlock.SKY,physical,1);parent.setLightFor(EnumSkyBlock.BLOCK,physical,2);
                for(EnumFacing face:EnumFacing.VALUES){BlockPos neighbor=physical.offset(face);parent.setLightFor(EnumSkyBlock.SKY,neighbor,face==EnumFacing.DOWN?15:face==EnumFacing.NORTH?11:3);parent.setLightFor(EnumSkyBlock.BLOCK,neighbor,face==EnumFacing.DOWN?15:face==EnumFacing.EAST?7:4);}
                for(int minimum:new int[]{0,9,15}){
                    int expected=((parent.provider.hasSkyLight()?11:0)<<20)|(Math.max(7,minimum)<<4);
                    require(parent.getCombinedLight(physical,minimum)==expected,"independent slab levels yaw="+yaw);
                    require(craft.view.getCombinedLight(sample,minimum)==expected,"physical neighbor parity yaw="+yaw+" minimum="+minimum);
                }
            }finally{
                for(Map.Entry<BlockPos,IBlockState> old:states.entrySet())parent.setBlockState(old.getKey(),old.getValue(),2);
                for(Map.Entry<BlockPos,int[]> old:lights.entrySet()){parent.setLightFor(EnumSkyBlock.SKY,old.getKey(),old.getValue()[0]);parent.setLightFor(EnumSkyBlock.BLOCK,old.getKey(),old.getValue()[1]);}
            }
        }}finally{craft.signals.close();craft.setDead();}
        System.out.println("[vandorlabs][reprolab] vehicle-combined-light PASS loaded-slab world-cardinal-neighbors yaw0-45-90 sky-block-minimum downward-exclusion vanilla-client-oracle");
    }
}
