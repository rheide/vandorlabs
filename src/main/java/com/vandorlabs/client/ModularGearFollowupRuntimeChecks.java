package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Ground-dependent Taxi permissions and the covers surrounding modular support shafts. */
final class ModularGearFollowupRuntimeChecks {
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        covers(world,player);skis(world,player);pushes(world,player);LegacyGearMigrationRuntimeChecks.run(world,player);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("modular gear follow-up: "+message);}
    private static void pushes(WorldServer world,EntityPlayerMP player){
        BlockPos at=new BlockPos(570,180,200);Vec3d previous=player.getPositionVector();boolean flying=player.capabilities.isFlying;
        try{for(boolean large:new boolean[]{false,true})for(boolean swing:new boolean[]{false,true}){
            clear(world,at);GearTileEntity gear=build(world,player,at,large,swing,false);
            java.util.List<AxisAlignedBB> boxes=gear.collisionBoxes();AxisAlignedBB foot=boxes.get(boxes.size()-(large?4:1));
            player.capabilities.isFlying=false;player.setPositionAndUpdate((foot.minX+foot.maxX)/2,foot.maxY,(foot.minZ+foot.maxZ)/2);Vec3d start=player.getPositionVector();
            gear.target(false);for(int tick=0;tick<40;tick++)gear.update();
            require(gear.progress()==0&&player.getPositionVector().distanceTo(start)>.1,"player carried through complete retraction "+large+" "+swing);
            player.setPositionAndUpdate(previous.x,previous.y,previous.z);
            net.minecraft.entity.item.EntityArmorStand entity=new net.minecraft.entity.item.EntityArmorStand(world,(foot.minX+foot.maxX)/2,foot.minY,(foot.minZ+foot.maxZ)/2);entity.setNoGravity(true);world.spawnEntity(entity);Vec3d original=entity.getPositionVector();
            gear.target(true);for(int tick=0;tick<40;tick++)gear.update();require(gear.fullyDeployed()&&entity.getPositionVector().distanceTo(original)>.01,"entity displaced without stopping deployment");entity.setDead();
        }}finally{clear(world,at);player.capabilities.isFlying=flying;player.setPositionAndUpdate(previous.x,previous.y,previous.z);}
        System.out.println("[vandorlabs][reprolab] modular-gear-entities PASS both sizes arms player riding retraction and entity pushing deployment");
    }
    private static void clear(WorldServer world,BlockPos root){for(BlockPos at:BlockPos.getAllInBox(root.add(-4,-6,-4),root.add(12,5,12)))if(!world.isAirBlock(at))world.setBlockToAir(at);}
    private static GearTileEntity build(WorldServer world,EntityPlayerMP player,BlockPos at,boolean large,boolean swing,boolean ski){
        if(!ski)return ModularWheelRuntimeChecks.build(world,player,at,large,swing,EnumFacing.NORTH,1);
        if(!large)return ModularGearRuntimeChecks.build(world,at,true,swing,EnumFacing.NORTH,1,1,1);
        GearBlock arm=swing?GearRegistry.LARGE_GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_EXTENDER;
        ItemBlock item=(ItemBlock)Item.getItemFromBlock(arm);
        require(item.placeBlockAt(new ItemStack(arm),player,world,at,EnumFacing.DOWN,.5F,.5F,.5F,arm.getDefaultState()),"large ski mount placement");
        for(BlockPos cell:arm.componentCells(world,at)){world.setBlockState(cell.down(),GearRegistry.LANDING_SKI.getDefaultState());world.setBlockState(cell.up(),Blocks.IRON_BLOCK.getDefaultState());}
        GearTileEntity root=(GearTileEntity)world.getTileEntity(at);require(root.rebuild(),"large ski formation "+root.error());return root;
    }
    private static void covers(WorldServer world,EntityPlayerMP player){
        BlockPos at=new BlockPos(540,180,200);
        try{for(boolean large:new boolean[]{false,true})for(boolean swing:new boolean[]{false,true})for(boolean sliding:new boolean[]{false,true}){
            clear(world,at);GearTileEntity gear=build(world,player,at,large,swing,false);BlockPos mount=large?at.down().east(2):at.down().west();
            BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)Block.getBlockFromName("vandorlabs:programmable_trapdoor");
            world.setBlockState(mount,block.getDefaultState().withProperty(BlockProgrammableTrapdoor.FACING,large?EnumFacing.WEST:EnumFacing.EAST));
            TileEntityProgrammableTrapdoor cover=(TileEntityProgrammableTrapdoor)world.getTileEntity(mount);cover.configure(0,0,sliding,1,0);cover.setCover(true);
            require(world.getBlockState(mount).getValue(BlockProgrammableTrapdoor.OPEN),"deployed modular wheel opens its cover");
            gear.target(false);
            for(int tick=0;tick<80&&gear.progress()>0;tick++){gear.update();if(gear.progress()>0){cover.requestOpen(false);require(world.getBlockState(mount).getValue(BlockProgrammableTrapdoor.OPEN),"cover cannot close through retracting wheel");}}
            require(gear.progress()==0&&!world.getBlockState(mount).getValue(BlockProgrammableTrapdoor.OPEN),"cover returns to unpowered setting after stow "+large+" "+swing+" "+sliding+" "+gear.error());
            gear.target(true);require(world.getBlockState(mount).getValue(BlockProgrammableTrapdoor.OPEN),"deployment opens cover before movement");
        }}finally{clear(world,at);}
        System.out.println("[vandorlabs][reprolab] modular-gear-covers PASS both sizes arms and rotating/sliding covers stay open until stowed");
    }
    private static void skis(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos at=new BlockPos(540,150,240);EntityGroundVehicle craft=null;Vec3d old=player.getPositionVector();
        java.lang.reflect.Method advance=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);advance.setAccessible(true);
        try{for(boolean large:new boolean[]{false,true})for(boolean swing:new boolean[]{false,true}){
            clear(world,at);ground(world,at,Blocks.ICE);
            for(int x=0;x<8;x++)for(int z=0;z<8;z++)world.setBlockState(at.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState());
            for(BlockPos root:new BlockPos[]{at,at.east(4),at.south(4)})build(world,player,root,large,swing,true);
            BlockPos seat=at.add(2,2,2);world.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState());ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,seat,world.getBlockState(seat),player,new ItemStack(ModBlocks.PILOT_SEAT));
            world.setBlockState(at.add(3,2,2),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState());
            world.setBlockState(at.add(4,2,2),Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState());
            VehicleScan scan=new VehicleScan(world,seat);for(int tick=0;tick<200&&!scan.step(128);tick++);
            require(scan.result!=null&&scan.error==null,"ski vehicle scan "+scan.error);require(scan.result.hasSkisOnly&&scan.result.gearCount==3,"all supports identified as skis");
            for(Block ice:new Block[]{Blocks.ICE,Blocks.PACKED_ICE,Blocks.FROSTED_ICE}){ground(world,at,ice);require(VehicleSkiTraction.canStart(world,scan.result,scan.origin),"ski Taxi on "+ice.getRegistryName());}
            world.setBlockState(at.down(2),Blocks.STONE.getDefaultState());require(!VehicleSkiTraction.canStart(world,scan.result,scan.origin),"one runner on ordinary ground rejects Taxi");ground(world,at,Blocks.ICE);
            require(!VehicleFlight.canHover(scan.result),"fixture has no lift blocks");player.setPositionAndUpdate(at.getX()-3,at.getY()+3,at.getZ()-3);
            craft=VehicleTransfer.assemble(world,scan.result,scan.origin,player);require(!craft.hover&&VehicleSkiTraction.allowed(craft),"ski craft enters Taxi on ice without Hover hardware");
            double start=craft.posZ;craft.autopilotControls((byte)1,(byte)0,false);for(int tick=0;tick<15;tick++)advance.invoke(craft,true);require(Math.abs(craft.posZ-start)>1,"skis actually drive on ice");
            ground(world,at,Blocks.STONE);double stoppedX=craft.posX,stoppedZ=craft.posZ;advance.invoke(craft,true);require(craft.posX==stoppedX&&craft.posZ==stoppedZ&&craft.driveSpeed==0,"ski Taxi stops before leaving ice");
            VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);craft=null;
        }}finally{if(craft!=null&&!craft.isDead)craft.setDead();clear(world,at);for(BlockPos p:BlockPos.getAllInBox(at.add(-12,-2,-12),at.add(18,-2,18)))world.setBlockToAir(p);player.setPositionAndUpdate(old.x,old.y,old.z);}
        System.out.println("[vandorlabs][reprolab] ski-taxi PASS both arm types/sizes three ice types no lift requirement ordinary-ground rejection and real drive stop");
    }
    private static void ground(WorldServer world,BlockPos at,Block block){for(BlockPos p:BlockPos.getAllInBox(at.add(-12,-2,-12),at.add(18,-2,18)))world.setBlockState(p,block.getDefaultState(),2);}
}
