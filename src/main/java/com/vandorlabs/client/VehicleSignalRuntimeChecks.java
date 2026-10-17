package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Real server channel callbacks and transformed screen interaction, including parking footprints. */
final class VehicleSignalRuntimeChecks {
    private static final int CHANNEL=19371;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle channels: "+message);}
    private static VehicleStructure.Cell cell(BlockPos pos,IBlockState state,TileEntity tile){tile.setPos(pos);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());if(tile instanceof TileEntityCanopy)((TileEntityCanopy)tile).large.write(tag);return new VehicleStructure.Cell(pos,state,tag);}
    static void run(WorldServer world,EntityPlayerMP player){
        CapturedControlRuntimeChecks.run(world,player);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        BlockPos screenPos=new BlockPos(2,2,2),lightPos=new BlockPos(4,2,2),doorPos=new BlockPos(6,2,2),gearPos=new BlockPos(8,0,2),canopyPos=new BlockPos(10,2,8);
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();screen.redstoneScreen(0).configureRows(Arrays.asList("Door","Level"),Arrays.asList(ChannelList.of(CHANNEL),ChannelList.of(CHANNEL)),0);screen.redstoneScreen(0).rows().get(1).slider=true;
        cells.add(cell(screenPos,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),screen));
        TileEntityProgrammableLight light=new TileEntityProgrammableLight();light.configure(0,15,true,ChannelList.of(CHANNEL),0,1);cells.add(cell(lightPos,ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),light));
        TileEntityProgrammableLight neighbor=new TileEntityProgrammableLight();neighbor.configure(0,15,true,ChannelList.EMPTY,0,1);cells.add(cell(lightPos.east(),ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),neighbor));
        Block propulsion=null;for(Block candidate:Block.REGISTRY)if(candidate instanceof BlockPropulsionLight && !VehicleFlight.lift(candidate)){propulsion=candidate;break;}require(propulsion!=null,"propulsion fixture registered");
        BlockPos propulsionPos=lightPos.up(3);TileEntityRedstoneLight thruster=new TileEntityRedstoneLight();thruster.configureSignalBrightness(true,8);thruster.setRedstoneChannel(CHANNEL);cells.add(cell(propulsionPos,propulsion.getDefaultState(),thruster));
        Block door=Block.getBlockFromName("vandorlabs:programmable_door");require(door!=null,"door fixture registered");
        TileEntitySpaceDoor doorTile=new TileEntitySpaceDoor();doorTile.setRedstoneChannel(CHANNEL);NBTTagCompound doorTag=doorTile.writeToNBT(new NBTTagCompound());doorTag.setInteger("SpaceDoorTrigger",1);doorTile.readFromNBT(doorTag);
        cells.add(cell(doorPos,door.getDefaultState().withProperty(BlockDoor.HALF,BlockDoor.EnumDoorHalf.LOWER),doorTile));
        cells.add(cell(doorPos.up(),door.getDefaultState().withProperty(BlockDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),new TileEntitySpaceDoor()));
        BlockTelescopicLandingGear gear=null;for(Block candidate:Block.REGISTRY)if(candidate instanceof BlockTelescopicLandingGear && !(candidate instanceof com.vandorlabs.blocks.BlockNonWheeledLandingGear)){gear=(BlockTelescopicLandingGear)candidate;break;}require(gear!=null,"gear registered");
        TileEntityLandingGear gearTile=new TileEntityLandingGear();gearTile.setRedstoneChannel(CHANNEL);cells.add(cell(gearPos,gear.getDefaultState(),gearTile));
        BlockCanopy canopy=(BlockCanopy)Block.getBlockFromName("vandorlabs:canopy");
        for(BlockPos pos:canopy.cells(canopyPos,EnumFacing.NORTH,canopy.length)){
            TileEntityCanopy tile=new TileEntityCanopy();tile.setPos(pos);tile.anchor=canopyPos;tile.large.redstone=true;tile.large.channels=ChannelList.of(CHANNEL);
            cells.add(cell(pos,canopy.getDefaultState(),tile));
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));craft.setPosition(400,170,80);craft.rotationYaw=90;
        try{
            craft.view.propulsionLevel=3;craft.signals.tick();screen=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(screenPos);
            for(int index=0;index<2;index++){
                Vec3d from=null,to=null;IBlockState state=craft.view.getBlockState(screenPos);
                for(int y=0;y<=32 && from==null;y++)for(int x=0;x<=32 && from==null;x++){
                    Vec3d a=new Vec3d(x/32D,y/32D,-2),b=a.addVector(0,0,4);
                    if(RedstoneScreenInteractions.hitRow(state,screen,0,a,b,2)==index && RedstoneScreenInteractions.hitRow(state,screen,0,a.addVector(0,.01,0),b.addVector(0,.01,0),2)==index && RedstoneScreenInteractions.hitRow(state,screen,0,a.addVector(0,-.01,0),b.addVector(0,-.01,0),2)==index && RedstoneScreenInteractions.hitColumn(state,screen,0,a,b)>=90 && RedstoneScreenInteractions.hitColumn(state,screen,0,a,b)<100){from=a.add(new Vec3d(screenPos));to=b.add(new Vec3d(screenPos));}
                }
                require(from!=null,"screen row has a clickable display plane");Vec3d eye=craft.toWorld(from),direction=craft.toWorld(to).subtract(eye);
                player.setPositionAndUpdate(eye.x,eye.y-player.getEyeHeight(),eye.z);player.rotationYaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));player.rotationPitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));player.setSneaking(false);
                player.rotationYawHead=player.rotationYaw;
                Vec3d actualEye=craft.toLocal(player.getPositionEyes(1)).subtract(new Vec3d(screenPos)),actualEnd=craft.toLocal(player.getPositionEyes(1).add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(5))).subtract(new Vec3d(screenPos));
                require(RedstoneScreenInteractions.hitRow(state,screen,0,actualEye,actualEnd,2)==index,"aim maps to intended row "+index+" eye="+actualEye+" end="+actualEnd+" wanted="+from);
                require(craft.signals.click(screenPos,player),"rotated onboard row click "+index);
                require(RedstoneChannels.level(world,CHANNEL)>0,"screen publishes to parent channel");craft.signals.tick();
                if(index==1)require(RedstoneChannels.level(world,CHANNEL)==screen.redstoneScreen(0).rows().get(1).segmentValue(2),"slider changes parent channel to its selected numeric level");
                require(((TileEntityAnimatedScreenSelector)craft.view.getTileEntity(lightPos)).getSignalLevel()>0,"programmable light receives level");
                require(((TileEntityProgrammableLight)craft.view.getTileEntity(lightPos.east())).isOn(),"joined light receives assembly signal");
                require(((TileEntityRedstoneLight)craft.view.getTileEntity(propulsionPos)).getBrightness()==RedstoneChannels.level(world,CHANNEL),"configured propulsion light follows channel level");
                require(craft.view.getBlockState(doorPos).getValue(BlockDoor.OPEN),"remote channel opens door");
                require(((TileEntityCanopy)craft.view.getTileEntity(canopyPos)).targetOpen,"channel opens canopy");
                require(craft.view.getBlockState(gearPos).getValue(BlockTelescopicLandingGear.EXTENDED),"channel extends gear");
            }
            RedstoneScreenContents.Row row=((TileEntityAnimatedScreenSelector)craft.view.getTileEntity(screenPos)).redstoneScreen(0).rows().get(0);
            // A world-side latch shares the same bank, even after the craft moves.
            ExternalLatch external=new ExternalLatch(world);RedstoneChannels.register(external);
            try{craft.setPosition(432,170,88);RedstoneChannels.latchChanged(external,false);craft.signals.tick();
                require(!craft.view.getBlockState(doorPos).getValue(BlockDoor.OPEN) && craft.view.getBlockState(gearPos).getValue(BlockTelescopicLandingGear.EXTENDED),"Taxi closes the door but keeps ground supports extended");
                craft.hover=true;craft.view.hover=true;RedstoneChannels.latchChanged(external,true);craft.signals.tick();RedstoneChannels.latchChanged(external,false);craft.signals.tick();
                require(!craft.view.getBlockState(gearPos).getValue(BlockTelescopicLandingGear.EXTENDED),"Hover accepts redstone gear retraction");craft.hover=false;craft.view.hover=false;
                require(!row.active(),"world latch updates onboard row highlight");
                require(!craft.view.getBlockState(propulsionPos).getValue(BlockPropulsionLight.POWERED),"explicit propulsion channel turns off despite movement brightness");
            }finally{RedstoneChannels.unregister(external);}
            VehicleRotation.Plan plan=VehicleRotation.plan(craft);VehicleWorld parked=new VehicleWorld(world,plan.structure);
            for(VehicleStructure.Cell cell:plan.structure.cells)if(cell.state.getBlock()==gear && !cell.state.getValue(BlockTelescopicLandingGear.LOWER)){
                require(cell.state.getValue(BlockTelescopicLandingGear.EXTENDED),"parking forces extended root");
                require(((TileEntityLandingGear)parked.getTileEntity(cell.pos)).progress==1,"parking settles extension");
                require(plan.structure.at(cell.pos.down())!=null,"parking includes previously absent lower cell");
                require(plan.origin.getY()+new VehicleCollision(parked).bounds.minY>=craft.posY+craft.collision.bounds.minY-.5,"parking keeps extended feet above the current support height");
                for(VehicleStructure.Cell target:plan.structure.cells)world.getChunkFromBlockCoords(plan.origin.add(target.pos));
                BlockPos blocked=plan.origin.add(cell.pos.down());world.setBlockState(blocked,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                NBTTagCompound before=craft.structure.write();boolean refused=false;
                try{VehicleTransfer.park(world,craft,plan.origin,null);}catch(Exception expected){refused=true;}
                require(refused && !craft.isDead && before.equals(craft.structure.write()) && !VehicleTransfer.pending(world),"blocked extension refuses conversion without mutating craft");
                world.setBlockToAir(blocked);
            }
        }finally{craft.setDead();}
        require(RedstoneChannels.level(world,CHANNEL)==0,"unloaded vehicle leaves no source behind");
        System.out.println("[vandorlabs][reprolab] vehicle-channels PASS rotated-screen-buttons sliders world-latch door canopy lights gear extended-parking cleanup");
    }
    private static final class ExternalLatch extends TileEntity implements RedstoneChannelLatch{
        public boolean isChannelMemberActive(){return getWorld()!=null&&!isInvalid();}
        boolean on;ExternalLatch(WorldServer world){setWorld(world);}
        public TileEntity channelTile(){return this;}public int getRedstoneChannel(){return CHANNEL;}public void setRedstoneChannel(int ignored){}
        public boolean hasLocalRedstoneSignal(){return on;}public void setChannelSignal(boolean ignored){}public boolean isChannelLatch(){return true;}public boolean latchOn(){return on;}public void applyLinkedLatch(boolean value){on=value;}
    }
}
