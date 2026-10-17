package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.UUID;

/** Ordinary server ticks must park a hollow craft without ejecting its pilot outside. */
final class VehicleParkingExitRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(260,121,240),SEAT=BASE.add(3,2,3),CONTROL=BASE.add(2,2,3);
    private static int stage,ticks;private static UUID id;private static BlockPos parkedSeat;private static AxisAlignedBB interior;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("parking exit: "+message);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    static boolean tick(Minecraft mc)throws Exception{
        if(stage==4)return true;require(++ticks<180,"normal-tick parking timed out stage="+stage);
        if(stage==0){mc.displayGuiScreen(null);mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);EntityPlayerMP p=actor(mc);p.dismountRidingEntity();p.setSneaking(false);p.capabilities.isFlying=false;p.sendPlayerAbilities();
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-3,-1,-3),BASE.add(9,9,9)))w.setBlockToAir(at);
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-3,-1,-3),BASE.add(9,-1,9)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            for(BlockPos at:BlockPos.getAllInBox(BASE.up(),BASE.add(6,6,6))){BlockPos local=at.subtract(BASE);if(local.getY()==1||local.getY()==6||local.getX()==0||local.getX()==6||local.getZ()==0||local.getZ()==6)w.setBlockState(at,Blocks.IRON_BLOCK.getDefaultState(),2);}
            Block gear=null;for(Block b:Block.REGISTRY)if(b instanceof BlockTelescopicLandingGear && !(b instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear)){gear=b;break;}require(gear!=null,"landing gear registered");
            for(BlockPos at:new BlockPos[]{BASE,BASE.east(6),BASE.south(6)}){w.setBlockState(at,gear.getDefaultState(),2);((TileEntityLandingGear)w.getTileEntity(at)).configure(0,0,0,0);}
            w.setBlockState(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,SEAT,w.getBlockState(SEAT),p,new ItemStack(ModBlocks.PILOT_SEAT));((TileEntityPilotSeat)w.getTileEntity(SEAT)).setTaxiMode(true);
            w.setBlockState(CONTROL,Block.getBlockFromName("vandorlabs:vehicle_controller").getDefaultState(),2);w.setBlockState(BASE.add(4,2,3),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),2);
            VehicleScan scan=new VehicleScan(w,CONTROL);for(int step=0;step<100&&!scan.step(256);step++){}require(scan.result!=null,"enclosed craft scan: "+scan.error);
            try{EntityGroundVehicle craft=VehicleTransfer.assemble(w,scan.result,scan.origin,p);id=craft.getUniqueID();require(craft.board(p),"pilot boards enclosed craft");craft.updatePassenger(p);}catch(Exception failure){throw new IllegalStateException(failure);}
        }).get();stage=1;ticks=0;return false;}
        if(stage==1&&ticks>=30){mc.getIntegratedServer().addScheduledTask(()->{
            EntityGroundVehicle craft=(EntityGroundVehicle)world(mc).getEntityFromUuid(id);require(craft!=null&&actor(mc).getRidingEntity()==craft,"normal ticks preserve seated pilot");craft.stopForParking();VehicleRotation.Plan plan=VehicleRotation.plan(craft);parkedSeat=plan.origin.add(plan.structure.seat);interior=new AxisAlignedBB(plan.origin.add(1,1,1),plan.origin.add(6,6,6));
            actor(mc).dismountRidingEntity();craft.taxiReturnPending=false;
            // EntityLivingBase performs a second placement after removePassenger;
            // assert only after the ordinary end-of-player-tick correction runs.
        }).get();stage=2;ticks=0;return false;}
        if(stage==2&&ticks>=5){mc.getIntegratedServer().addScheduledTask(()->{
            EntityGroundVehicle craft=(EntityGroundVehicle)world(mc).getEntityFromUuid(id);EntityPlayerMP p=actor(mc);require(craft!=null&&!craft.isDead,"manual exit keeps craft available for verification");require(interior.contains(p.getPositionVector()),"normal player ticks prefer a supported interior near the seat: "+p.getPositionVector());require(world(mc).getCollisionBoxes(p,p.getEntityBoundingBox()).isEmpty(),"interior dismount has full body clearance");require(craft.board(p),"reboard for ordinary automatic parking");craft.updatePassenger(p);p.dismountRidingEntity();require(craft.taxiReturnPending,"second exit requests real automatic Taxi parking");
        }).get();stage=3;ticks=0;return false;}
        if(stage==3&&ticks>=30){mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);EntityPlayerMP p=actor(mc);net.minecraft.entity.Entity craft=w.getEntityFromUuid(id);require(craft==null||craft.isDead,"ordinary Taxi return parks the craft");require(w.getBlockState(parkedSeat).getBlock()==ModBlocks.PILOT_SEAT,"parked seat restored");require(!p.isRiding()&&interior.contains(p.getPositionVector()),"parking keeps the pilot inside the room");require(p.getDistanceSqToCenter(parkedSeat)<9,"parked pilot stays near their own seat");require(w.getCollisionBoxes(p,p.getEntityBoundingBox()).isEmpty(),"parked pilot has full body clearance");
            // Remove all interior standing room: a safe exterior remains a valid fallback.
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(1,2,1),BASE.add(5,5,5)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            Vec3d near=new Vec3d(parkedSeat).addVector(.5,0,.5);Vec3d blocked=com.vandorlabs.entity.SafeDismount.findNear(w,p,new AxisAlignedBB(parkedSeat).expand(0,1,0),near);require(blocked==null,"enclosed solid room never supplies a false interior candidate");
            Vec3d fallback=com.vandorlabs.entity.SafeDismount.find(w,p,interior.grow(1));require(fallback!=null&&!interior.contains(fallback),"blocked interior still permits a safe exterior fallback");AxisAlignedBB body=new AxisAlignedBB(fallback.x-p.width/2,fallback.y,fallback.z-p.width/2,fallback.x+p.width/2,fallback.y+p.height,fallback.z+p.width/2);require(w.getCollisionBoxes(p,body).isEmpty(),"fallback respects full body collision");
            p.setPositionAndUpdate(BASE.getX()-2,BASE.getY(),BASE.getZ()-2);for(BlockPos at:BlockPos.getAllInBox(BASE.add(-1,0,-1),BASE.add(7,7,7)))w.setBlockToAir(at);
        }).get();stage=4;System.out.println("[vandorlabs][reprolab] vehicle-parking-exit PASS actual-assembly ordinary-ticks Taxi-return interior-seat-preference full-body-clearance blocked-interior exterior-fallback");return true;}
        return false;
    }
}
