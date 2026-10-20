package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.lang.reflect.Method;

/** Actual Taxi collision/drive and block transfers through both turntable journeys. */
final class TurntableVehicleRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(650,190,320),OWNER=ROOT.west();
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        Vec3d before=player.getPositionVector();EntityGroundVehicle craft=null;TileEntityTurntableController table=null;
        Method advance=EntityGroundVehicle.class.getDeclaredMethod("advance",boolean.class);advance.setAccessible(true);
        try{for(boolean blockMode:new boolean[]{false,true}){
            clear(world);for(int x=-16;x<=20;x++)for(int z=-16;z<=20;z++)world.setBlockState(ROOT.add(x,0,z),Blocks.STONE.getDefaultState());
            for(int x=0;x<9;x++)for(int z=0;z<9;z++)world.setBlockState(ROOT.add(x,0,z),Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState());
            world.setBlockState(OWNER,Block.getBlockFromName("vandorlabs:turntable_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST));
            table=(TileEntityTurntableController)world.getTileEntity(OWNER);table.height=6;table.rpm=3;
            player.setPositionAndUpdate(ROOT.getX()-12,ROOT.getY()+8,ROOT.getZ()-10);TurntableTransactionsChecks.scan(table,player);
            BlockPos seat=build(world,player,ROOT.add(-7,2,3));VehicleScan scan=scan(world,seat);
            craft=VehicleTransfer.assemble(world,scan.result,scan.origin,player);require(!craft.hover,"starts in Taxi mode");checkLight(craft.view);
            drive(advance,craft,0,ROOT.getX()+3);require(centerX(craft)>=ROOT.getX()+4 && centerX(craft)<ROOT.getX()+6,"vehicle drove onto the disc");
            if(blockMode){
                int count=craft.structure.cells.size();VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);craft=null;
                TurntableTransactionsChecks.restore(table,player);TurntableTransactionsChecks.scan(table,player);EntityTurntable display=table.display();require(display.snapshot.blocks.cells.size()==count,"parked vehicle captured completely");checkLight(display.view);
                world.setBlockState(OWNER.down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                for(int tick=0;tick<100;tick++){table.update();TurntableTransactions.drainForChecks(world);display=table.display();require(display!=null,"parked vehicle remains captured while powered");display.onUpdate();}
                require(Math.abs(display.angle()-90)<.001,"parked vehicle rotates ninety degrees");TurntableSnapshot returned=display.snapshot.rotated(display.angle(),display.view);
                world.setBlockToAir(OWNER.down());table.update();TurntableTransactions.drainForChecks(world);require(table.captureId==null&&table.display()==null,"parked vehicle automatically returns to editable blocks at ninety degrees");
                seat=null;for(VehicleStructure.Cell cell:returned.blocks.cells)if(cell.state.getBlock() instanceof BlockPilotSeat && !cell.state.getValue(BlockPilotSeat.UPPER)){seat=returned.origin.add(cell.pos);break;}
                require(seat!=null && world.getBlockState(seat).getValue(BlockPilotSeat.FACING)==EnumFacing.SOUTH,"restored vehicle seat rotates East to South");
                scan=scan(world,seat);craft=VehicleTransfer.assemble(world,scan.result,scan.origin,player);
                checkLight(craft.view);require(!craft.hover&&craft.structure.gearCount==3&&craft.structure.facing==EnumFacing.SOUTH,"rotated blocks reassemble in Taxi with all supports");
            }else{
                double x=centerX(craft),z=centerZ(craft);float yaw=craft.rotationYaw;EntityTurntable display=table.display();
                world.setBlockState(OWNER.down(),Blocks.REDSTONE_BLOCK.getDefaultState());
                for(int tick=0;tick<100;tick++){table.update();TurntableTransactions.drainForChecks(world);display=table.display();require(display!=null&&!craft.isDead&&display.snapshot.entities.isEmpty(),"redstone rescan leaves Taxi craft live");display.onUpdate();}
                require(Math.abs(display.angle()-90)<.001,"redstone rotates exactly ninety degrees");
                double[] expected=TurntableGeometry.rotate(x-display.posX,0,z-display.posZ,TurntableGeometry.Face.UP,90);
                require(Math.abs(centerX(craft)-display.posX-expected[0])<.02 && Math.abs(centerZ(craft)-display.posZ-expected[2])<.02,"Taxi craft rotates about its physical center");
                require(Math.abs(MathHelper.wrapDegrees(craft.rotationYaw-yaw-90))<.01,"Taxi heading follows platform");
                world.setBlockToAir(OWNER.down());for(int tick=0;tick<30;tick++){table.update();TurntableTransactions.drainForChecks(world);require(table.display()==null&&table.captureId==null&&!craft.isDead,"quarter-turn stop releases platform without capturing Taxi craft");TurntableEvents.finishTransfers(world);}
                require(Math.abs(table.angle(1)-90)<.001,"automatic release preserves the rotated Taxi platform heading");
            }
            double start=centerZ(craft);drive(advance,craft,2,ROOT.getZ()+12);
            require(centerZ(craft)-start>6 && centerZ(craft)>ROOT.getZ()+11,"vehicle drives off along rotated South heading");
            require(Math.abs(craft.posY-(ROOT.getY()+1))<.15,"Taxi wheels keep floor height across the platform boundary");
            VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);craft=null;
            if(table.display()!=null)TurntableTransactionsChecks.restore(table,player);table.release();table=null;
        }}finally{
            if(craft!=null&&!craft.isDead)try{VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);}catch(Exception cleanup){craft.setDead();}
            if(table!=null&&table.display()!=null)try{TurntableTransactionsChecks.restore(table,player);}catch(Exception retained){/* disposable fixture retains its recovery record */}
            clear(world);player.setPositionAndUpdate(before.x,before.y,before.z);
        }
        System.out.println("[vandorlabs][reprolab] turntable-vehicles PASS Taxi enter rotate90 auto-release drive-off; park scan rotate90 auto-restore reassemble Taxi drive-off; Pinnacle logo and light textures retained");
    }
    private static double centerX(EntityGroundVehicle craft){return craft.posX+craft.structure.bounds.maxX/2;}
    private static double centerZ(EntityGroundVehicle craft){return craft.posZ+craft.structure.bounds.maxZ/2;}
    private static void drive(Method advance,EntityGroundVehicle craft,int axis,double target)throws Exception{
        craft.autopilotControls((byte)1,(byte)0,false);
        for(int tick=0;tick<100 && (axis==0?craft.posX:centerZ(craft))<target;tick++)advance.invoke(craft,true);
        require((axis==0?craft.posX:centerZ(craft))>=target,"Taxi drive reaches destination");
        craft.autopilotControls((byte)0,(byte)0,true);craft.driveSpeed=0;craft.motionX=craft.motionZ=0;
    }
    private static BlockPos build(WorldServer world,EntityPlayerMP player,BlockPos root){
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)world.setBlockState(root.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState());
        for(BlockPos at:new BlockPos[]{root,root.east(2),root.south(2)}){
            world.setBlockState(at.down(),GearRegistry.LANDING_WHEEL.getDefaultState().withProperty(GearBlock.FACING,EnumFacing.EAST));
            world.setBlockState(at,GearRegistry.GEAR_MOUNT.getDefaultState());GearTileEntity mount=(GearTileEntity)world.getTileEntity(at);require(mount.rebuild(),"fixed wheel formation "+mount.error());
        }
        for(int variant=0;variant<2;variant++){BlockPos lightPos=root.add(2,2,1+variant);world.setBlockState(lightPos,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState());TileEntityProgrammableLight lamp=(TileEntityProgrammableLight)world.getTileEntity(lightPos);lamp.setFaceTexture(lightChoice(variant));lamp.setOffFaceTexture(3);lamp.setHousingTexture(5);}
        BlockPos seat=root.add(0,2,1);BlockPilotSeat block=(BlockPilotSeat)ModBlocks.PILOT_SEAT;
        world.setBlockState(seat,block.getDefaultState().withProperty(BlockPilotSeat.FACING,EnumFacing.EAST));block.onBlockPlacedBy(world,seat,world.getBlockState(seat),player,new ItemStack(block));
        ((TileEntityPilotSeat)world.getTileEntity(seat)).setTaxiMode(true);
        world.setBlockState(root.add(1,2,1),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState());return seat;
    }
    private static int lightChoice(int variant){String id=variant==0?"corp_pinnacle_logo":"corp_pinnacle_light_on";for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.IDS[i].equals(id))return i;throw new IllegalStateException("Missing Pinnacle light fixture "+id);}
    private static void checkLight(VehicleWorld view){java.util.Set<Integer> choices=new java.util.HashSet<>();for(net.minecraft.tileentity.TileEntity raw:view.tiles.values())if(raw instanceof TileEntityProgrammableLight){TileEntityProgrammableLight lamp=(TileEntityProgrammableLight)raw;require((lamp.getFaceTexture()==lightChoice(0)||lamp.getFaceTexture()==lightChoice(1))&&lamp.getOffFaceTexture()==3&&lamp.getHousingTexture()==5,"Pinnacle light selected textures survive Taxi/turntable/rotated block transfers");require(lamp.isOn()&&lamp.activeFaceTexture().equals(ScreenHousingTextures.texture(lamp.getFaceTexture(),true)),"Pinnacle light retains selected powered artwork");choices.add(lamp.getFaceTexture());}require(choices.size()==2,"both Pinnacle light selections retained in captured view");}
    private static VehicleScan scan(WorldServer world,BlockPos seat){VehicleScan scan=new VehicleScan(world,seat);for(int tick=0;tick<200&&!scan.step(128);tick++);require(scan.result!=null&&scan.error==null,"vehicle discovery "+scan.error);return scan;}
    private static void clear(WorldServer world){for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-16,-2,-16),ROOT.add(20,9,20)))if(!world.isAirBlock(at))world.setBlockToAir(at);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("turntable vehicle: "+message);}
}
