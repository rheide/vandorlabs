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
    private static VehicleStructure lightingStructure(){
        com.vandorlabs.tiles.TileEntityPilotSeat seat=new com.vandorlabs.tiles.TileEntityPilotSeat();seat.setPos(BlockPos.ORIGIN);
        return new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new net.minecraft.nbt.NBTTagCompound())),new VehicleStructure.Cell(new BlockPos(4,0,4),Blocks.STONE.getDefaultState(),null)),BlockPos.ORIGIN,EnumFacing.NORTH,1);
    }
    static void runClient(net.minecraft.client.Minecraft mc)throws Exception{
        EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);VehicleStructure source=lightingStructure();
        craft.install(source);craft.setPosition(Math.floor(mc.player.posX),Math.floor(mc.player.posY)+2,Math.floor(mc.player.posZ));
        VehicleDynamicLights.Probe probe=null;java.lang.reflect.Method update=null;boolean dynamic=false,contribution=false;
        try{
            try{Class<?> config=Class.forName("Config");dynamic=(Boolean)config.getMethod("isDynamicLights").invoke(null);if(dynamic)update=Class.forName("net.optifine.DynamicLights").getMethod("update",net.minecraft.client.renderer.RenderGlobal.class);}catch(ClassNotFoundException absent){dynamic=false;}
            for(int yaw:new int[]{0,45,90}){
                craft.rotationYaw=yaw;BlockPos physical=new BlockPos(craft.toWorld(new Vec3d(.5,.5,.5)));require(mc.world.isBlockLoaded(physical),"client light comparison needs a loaded parent");
                for(int minimum:new int[]{0,9,15})require(craft.view.getCombinedLight(BlockPos.ORIGIN,minimum)==mc.world.getCombinedLight(physical,minimum),"client combined-light bridge yaw="+yaw+" minimum="+minimum);
                if(dynamic){
                    int ordinary=com.vandorlabs.CommonProxy.sharedVehicleCombinedLight(mc.world,physical,0);
                    probe=new VehicleDynamicLights.Probe(mc.world,15);probe.setPosition(physical.getX()+.5,physical.getY()+.5,physical.getZ()+.5);mc.world.addEntityToWorld(probe.getEntityId(),probe);
                    Thread.sleep(60);update.invoke(null,mc.renderGlobal);
                    int golden=mc.world.getCombinedLight(physical,0);require(craft.view.getCombinedLight(BlockPos.ORIGIN,0)==golden,"OptiFine dynamic source survives carried light bridge yaw="+yaw);
                    if((ordinary&65535)<224){require((golden&65535)>(ordinary&65535),"enabled OptiFine probe did not contribute light yaw="+yaw);contribution=true;}
                    mc.world.removeEntityFromWorld(probe.getEntityId());probe=null;Thread.sleep(60);update.invoke(null,mc.renderGlobal);
                }
            }
        }finally{if(probe!=null){mc.world.removeEntityFromWorld(probe.getEntityId());if(update!=null){Thread.sleep(60);update.invoke(null,mc.renderGlobal);}}craft.signals.close();craft.setDead();}
        System.out.println("[vandorlabs][reprolab] vehicle-combined-light-client PASS yaw0-45-90 remote-parent-golden OptiFine-enabled="+dynamic+" dynamic-contribution="+contribution);
    }
    static void run(WorldServer parent){
        EntityGroundVehicle craft=new EntityGroundVehicle(parent);
        VehicleStructure source=lightingStructure();
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
