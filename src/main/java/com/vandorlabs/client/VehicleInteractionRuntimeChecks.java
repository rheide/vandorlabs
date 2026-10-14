package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Actual vanilla client clicks and custom network dispatch on distant captured cells. */
public final class VehicleInteractionRuntimeChecks {
    private static UUID id;
    private static int ticks,phase,doorCase,totalTicks;
    private static final BlockPos SEAT=new BlockPos(40,20,1),DOOR=new BlockPos(36,20,1),PAIR=new BlockPos(24,20,1),LARGE=new BlockPos(32,20,1);
    private static final BlockPos ORIGIN=new BlockPos(160,101,40);
    private static final BlockPos SCREEN=new BlockPos(28,22,4);
    private static final List<BlockPos> doorCells=new ArrayList<>(),trapRoots=new ArrayList<>();
    private static boolean finished;
    private static NBTTagCompound originalStructure;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("vehicle clicks: "+message);}
    private static EntityGroundVehicle server(Minecraft mc) {
        return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);
    }
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle client(Minecraft mc) {
        for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;
        throw new IllegalStateException("vehicle click snapshot unavailable");
    }
    private static void setup(Minecraft mc) {
        WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=player(mc);
        player.dismountRidingEntity();player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        player.capabilities.isFlying=true;player.capabilities.allowFlying=true;player.sendPlayerAbilities();
        // The origin support rotates around the full structure pivot too.
        for(BlockPos at:BlockPos.getAllInBox(ORIGIN.add(-8,-1,-32),ORIGIN.add(52,-1,35)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));
        for(int x=22;x<=42;x++)for(int z=0;z<=4;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,19,z),Blocks.STONE.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(SEAT.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null));
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();screen.setPos(SCREEN);
        screen.redstoneScreen(0).configureRows(Arrays.asList("Door","Level"),Arrays.asList(com.vandorlabs.redstone.ChannelList.of(19372),com.vandorlabs.redstone.ChannelList.of(19372)),14);screen.redstoneScreen(0).rows().get(1).slider=true;
        cells.add(new VehicleStructure.Cell(SCREEN,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),screen.writeToNBT(new NBTTagCompound())));
        Block normal=Block.getBlockFromName("vandorlabs:programmable_door"),large=Block.getBlockFromName("vandorlabs:large_programmable_door");
        require(normal instanceof BlockConfigurableSpaceDoor && large instanceof BlockLargeProgrammableDoor,"registered programmable doors");
        for(BlockPos lower:new BlockPos[]{DOOR,PAIR,PAIR.east()})for(int row=0;row<2;row++) {
            BlockPos pos=lower.up(row);TileEntitySpaceDoor tile=new TileEntitySpaceDoor();tile.setPos(pos);tile.setFaceTexture(14);
            cells.add(new VehicleStructure.Cell(pos,normal.getDefaultState().withProperty(BlockDoor.FACING,EnumFacing.NORTH)
                    .withProperty(BlockDoor.HINGE,lower.equals(PAIR.east())?BlockDoor.EnumHingePosition.RIGHT:BlockDoor.EnumHingePosition.LEFT)
                    .withProperty(BlockDoor.HALF,row==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER),tile.writeToNBT(new NBTTagCompound())));
            doorCells.add(pos);
        }
        EnumFacing width=EnumFacing.NORTH.rotateYCCW();
        for(int column=0;column<3;column++)for(int row=0;row<3;row++) {
            BlockPos pos=LARGE.offset(width,column).up(row);TileEntityLargeProgrammableDoor tile=new TileEntityLargeProgrammableDoor();
            tile.setPos(pos);tile.assign(LARGE);tile.setFaceTexture(14);
            cells.add(new VehicleStructure.Cell(pos,large.getDefaultState().withProperty(BlockDoor.FACING,EnumFacing.NORTH),tile.writeToNBT(new NBTTagCompound())));
            doorCells.add(pos);
        }
        for(int kind=0;kind<2;kind++)for(int mode=0;mode<3;mode++)for(int motion=0;motion<2;motion++) {
            BlockPos root=new BlockPos(2+kind*12+mode*4+motion*2,22,8);trapRoots.add(root);
            Block trap=kind==0?ModBlocks.PROGRAMMABLE_TRAPDOOR:ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR;
            for(int part=0;part<2;part++) {
                BlockPos pos=root.east(part);TileEntityProgrammableTrapdoor tile=kind==0?new TileEntityProgrammableTrapdoor():new TileEntityProgrammableDiagonalTrapdoor();
                tile.setPos(pos);tile.setTileTexture(motion==1);tile.configure(14,mode,motion==1,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED,0);
                NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());data.setLong("TrapdoorPartner",root.east(1-part).toLong());
                cells.add(new VehicleStructure.Cell(pos,trap.getDefaultState().withProperty(net.minecraft.block.BlockTrapDoor.FACING,EnumFacing.NORTH),data));doorCells.add(pos);
            }
        }
        for(Block trap:new Block[]{Blocks.TRAPDOOR,Blocks.IRON_TRAPDOOR}) {
            BlockPos root=new BlockPos(2+trapRoots.size()*2,22,12);trapRoots.add(root);doorCells.add(root);
            net.minecraft.tileentity.TileEntity tile=trap.createTileEntity(world,trap.getDefaultState());
            if(tile!=null){tile.setWorld(world);tile.setPos(root);}
            cells.add(new VehicleStructure.Cell(root,trap.getDefaultState(),tile==null?null:tile.writeToNBT(new NBTTagCompound())));
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(VehicleTaxiMode.withSetting(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,1),false));
        originalStructure=craft.structure.write();craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());require(world.spawnEntity(craft),"fixture spawn");id=craft.getUniqueID();
        player.setPositionAndUpdate(200.5,120,38.5);
    }
    private static void aim(Minecraft mc,Vec3d eye,Vec3d at) {
        Vec3d delta=at.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        mc.player.setPositionAndRotation(eye.x,eye.y-mc.player.getEyeHeight(),eye.z,yaw,pitch);
        mc.player.prevRotationYaw=mc.player.rotationYawHead=mc.player.prevRotationYawHead=yaw;mc.player.prevRotationPitch=pitch;
    }
    private static void click(Minecraft mc) throws Exception {
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,mc.player.rotationYaw,mc.player.rotationPitch,false));
        // The vanilla origin-indexed picker misses this craft; exercise its ordinary
        // empty-hand click fallback, rather than calling boarding or toggling directly.
        mc.objectMouseOver=new RayTraceResult(RayTraceResult.Type.MISS,mc.player.getPositionEyes(1),EnumFacing.UP,BlockPos.ORIGIN);
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
    }
    private static void seatClick(Minecraft mc,boolean upper) throws Exception {
        EntityGroundVehicle craft=client(mc);double y=SEAT.getY()+(upper?1.4:.5);
        aim(mc,craft.toWorld(new Vec3d(40.5,y,-1)),craft.toWorld(new Vec3d(40.5,y,1.5)));
        VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
        require(target!=null && target.craft==craft && target.hit.action==VehicleSelection.Action.BOARD,"far seat selected");
        require(target.hit.cell.pos.equals(upper?SEAT.up():SEAT),"requested seat half selected");
        click(mc);
    }
    private static void doorClick(Minecraft mc,BlockPos root) throws Exception {
        EntityGroundVehicle craft=client(mc);Block expected=craft.view.getBlockState(root).getBlock();
        List<Vec3d> surfaces=new ArrayList<>();
        if(expected instanceof BlockLargeProgrammableDoor) {
            for(AxisAlignedBB box:((BlockLargeProgrammableDoor)expected).geometry(craft.view,root))surfaces.add(box.getCenter());
        } else {
            for(BlockPos pos:expected instanceof net.minecraft.block.BlockTrapDoor?new BlockPos[]{root,root.east()}:new BlockPos[]{root,root.up()})surfaces.add(craft.view.getBlockState(pos).getBoundingBox(craft.view,pos).offset(pos).getCenter());
        }
        for(Vec3d surface:surfaces)for(Vec3d direction:new Vec3d[]{new Vec3d(0,0,-2),new Vec3d(0,0,2),new Vec3d(-2,0,0),new Vec3d(2,0,0),new Vec3d(0,2,0),new Vec3d(0,-2,0)}) {
            aim(mc,craft.toWorld(surface.add(direction)),craft.toWorld(surface));
            VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
            if(target!=null && target.craft==craft && (target.hit.cell.pos.equals(root) || (expected instanceof net.minecraft.block.BlockTrapDoor?target.hit.cell.pos.equals(root.east()):target.hit.cell.pos.equals(root.up()) || root.equals(PAIR) && target.hit.cell.pos.equals(root.east()))) && target.hit.cell.state.getBlock()==expected && target.hit.action==VehicleSelection.Action.DOOR) {
                click(mc);return;
            }
        }
        throw new IllegalStateException("no accessible door surface at "+root+" block="+expected.getClass().getName()+" state="+craft.view.getBlockState(root)+" surfaces="+surfaces);
    }
    private static void doorState(Minecraft mc,BlockPos root,boolean open) throws Exception {
        EntityGroundVehicle client=client(mc);Block expected=client.view.getBlockState(root).getBlock();
        int count=0;boolean trap=expected instanceof net.minecraft.block.BlockTrapDoor;
        List<BlockPos> group=new ArrayList<>();
        for(BlockPos pos:doorCells)if(trap?pos.equals(root) || expected instanceof BlockProgrammableTrapdoor && pos.equals(root.east()):root.equals(LARGE)?client.view.getBlockState(pos).getBlock()==expected
                :pos.equals(root) || pos.equals(root.up()) || root.equals(PAIR) && (pos.equals(root.east()) || pos.equals(root.east().up())))group.add(pos);
        for(BlockPos pos:group) {
            require(client.view.getBlockState(pos).getValue(BlockDoor.OPEN)==open,"client complete door group");count++;
            net.minecraft.tileentity.TileEntity localTile=client.view.getTileEntity(pos);
            if(localTile!=null && localTile.getClass().getName().startsWith("net.malisis.doors.tileentity."))
                require(localTile.getClass().getMethod("getState").invoke(localTile).toString().equals(open?"OPENED":"CLOSED"),"Malisis rendered tile state follows block operation");
            if(expected instanceof BlockProgrammableTrapdoor)require(((TileEntityProgrammableTrapdoor)client.view.getTileEntity(pos)).getHousingTexture()==14,"trapdoor material preserved");
            else if(client.view.getTileEntity(pos) instanceof TileEntitySpaceDoor)require(((TileEntitySpaceDoor)client.view.getTileEntity(pos)).getFaceTexture()==14,"private appearance preserved");
        }
        require(count==(trap?(expected instanceof BlockProgrammableTrapdoor?2:1):root.equals(LARGE)?9:root.equals(PAIR)?4:2),"expected group size");
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityGroundVehicle craft=server(mc);
            for(BlockPos pos:group)
                require(craft.view.getBlockState(pos).getValue(BlockDoor.OPEN)==open,"server complete door group");
            if(open) {
                List<VehicleStructure.Cell> closed=new ArrayList<>();
                for(VehicleStructure.Cell cell:craft.structure.cells)closed.add(new VehicleStructure.Cell(cell.pos,group.contains(cell.pos)?cell.state.withProperty(BlockDoor.OPEN,false):cell.state,cell.tileData()));
                VehicleCollision closedGeometry=new VehicleCollision(new VehicleWorld(craft.world,new VehicleStructure(closed,craft.structure.seat,craft.structure.facing,craft.structure.gearCount)));
                require(!closedGeometry.boxes.toString().equals(craft.collision.boxes.toString()),"open/closed operation rebuilds distinct collision geometry");
            }
        }).get();
    }
    private static void screenClick(Minecraft mc,int index)throws Exception{
        EntityGroundVehicle craft=client(mc);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(SCREEN);IBlockState state=craft.view.getBlockState(SCREEN);
        for(int y=0;y<=32;y++)for(int x=0;x<=32;x++){
            Vec3d a=new Vec3d(x/32D,y/32D,-2),b=a.addVector(0,0,4);
            double column=RedstoneScreenInteractions.hitColumn(state,tile,0,a,b);
            if(column<90 || column>=100 || RedstoneScreenInteractions.hitRow(state,tile,0,a,b,2)!=index
                    || RedstoneScreenInteractions.hitRow(state,tile,0,a.addVector(0,.01,0),b.addVector(0,.01,0),2)!=index
                    || RedstoneScreenInteractions.hitRow(state,tile,0,a.addVector(0,-.01,0),b.addVector(0,-.01,0),2)!=index)continue;
            aim(mc,craft.toWorld(a.add(new Vec3d(SCREEN))),craft.toWorld(b.add(new Vec3d(SCREEN))));
            VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
            require(target!=null && target.craft==craft && target.hit.action==VehicleSelection.Action.SCREEN && target.hit.cell.pos.equals(SCREEN),"rotated screen surface selected");click(mc);return;
        }
        throw new IllegalStateException("no accessible screen row "+index);
    }
    public static boolean tick(Minecraft mc,File output) throws Exception {
        if(finished)return true;
        require(++totalTicks<=5000,"timed out at phase="+phase+" door-case="+doorCase+" elapsed-ticks="+totalTicks);
        if(ticks++==0 && phase==0) {
            net.minecraft.entity.Entity old=mc.world.getEntityByID(-40000);if(old!=null)old.setDead();
            mc.world.removeEntityFromWorld(-40000);mc.gameSettings.hideGUI=false;mc.gameSettings.thirdPersonView=0;
            mc.getIntegratedServer().addScheduledTask(()->setup(mc)).get();
            System.out.println("[vandorlabs][reprolab] vehicle-clicks START door-cases="+(6+trapRoots.size()*2));
        }
        if(phase==13){if(HoverClientRuntimeChecks.tick(mc,output)){VehicleStatusClientRuntimeChecks.begin(mc);phase=14;}return false;}
        if(phase==14){if(VehicleStatusClientRuntimeChecks.tick(mc,output)){finished=true;return true;}return false;}
        if(ticks<25)return false;
        ticks=0;
        if(phase==10){screenClick(mc,0);phase++;return false;}
        if(phase==11 || phase==12){
            RedstoneScreenContents controls=((TileEntityAnimatedScreenSelector)client(mc).view.getTileEntity(SCREEN)).redstoneScreen(0);
            require(controls.rows().get(0).active() && controls.rows().get(1).level()>0,"client screen rows reflect received channel update");
            if(phase==12)require(controls.rows().get(1).level()==controls.rows().get(1).segmentValue(2),"slider click changes the synchronized level from full power");
            mc.getIntegratedServer().addScheduledTask(()->require(com.vandorlabs.redstone.RedstoneChannels.level(player(mc).world,19372)>0,"actual screen click publishes server channel")).get();
            if(phase==11){screenClick(mc,1);phase++;return false;}
            mc.getIntegratedServer().addScheduledTask(()->server(mc).setDead()).get();
            System.out.println("[vandorlabs][reprolab] vehicle-screen-clicks PASS rotated-button slider client-sync server-channels");HoverClientRuntimeChecks.begin(mc);phase=13;return false;
        }
        if(phase==0){seatClick(mc,false);phase++;return false;}
        if(phase==1) {
            require(mc.player.getRidingEntity()==client(mc),"real click mounts distant lower seat on client");
            mc.getIntegratedServer().addScheduledTask(()->{
                require(player(mc).getRidingEntity()==server(mc),"real click mounts on server");
                player(mc).dismountRidingEntity();server(mc).rotationYaw=90;server(mc).motionY=0;server(mc).setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());
            }).get();phase++;return false;
        }
        if(phase==2){seatClick(mc,true);phase++;return false;}
        if(phase==3) {
            require(mc.player.getRidingEntity()==client(mc),"real click mounts rotated distant upper seat");
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),true);phase++;return false;
        }
        if(phase==4) {
            require(Math.abs(client(mc).rotationYaw-90)>3 && client(mc).view.propulsionLevel>0,"real click enables W/A-D network controls");
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle craft=server(mc);player(mc).dismountRidingEntity();craft.driveSpeed=0;craft.steering=0;craft.rotationYaw=0;
                craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());
            }).get();phase++;return false;
        }
        BlockPos root=doorCase<2?DOOR:doorCase<4?PAIR:doorCase<6?LARGE:trapRoots.get((doorCase-6)/2);
        if(phase==5){doorClick(mc,root);phase++;return false;}
        if(phase==6){doorState(mc,root,true);doorClick(mc,root);phase++;return false;}
        if(phase==7) {
            doorState(mc,root,false);doorCase++;
            System.out.println("[vandorlabs][reprolab] vehicle-clicks progress="+doorCase+"/"+(6+trapRoots.size()*2)+" elapsed-ticks="+totalTicks);
            if(doorCase==6+trapRoots.size()*2) {
                javax.imageio.ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_vehicle_clicks.png"));
                System.out.println("[vandorlabs][reprolab] vehicle-clicks PASS distant-lower-upper-seats rotated-boarding network-driving programmable-2cell-paired-3x3-doors open-close state-sync private-settings vanilla-programmable-diagonal-trapdoors-all-positions-both-motions");
                mc.getIntegratedServer().addScheduledTask(()->{
                    VehicleStructure original=server(mc).structure;
                    require(original.write().equals(originalStructure),"all door/trapdoor cycles preserve complete private settings and links");
                    VehicleStructure cycle=original;
                    for(int turn=0;turn<4;turn++) {
                        cycle=VehicleRotation.rotate(cycle,new VehicleWorld(server(mc).world,cycle),1);
                        VehicleWorld rotatedView=new VehicleWorld(server(mc).world,cycle);
                        for(VehicleStructure.Cell cell:cycle.cells)if(cell.state.getBlock() instanceof BlockProgrammableTrapdoor) {
                            TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)rotatedView.getTileEntity(cell.pos);
                            require(tile.group().size()==2,"rotated programmable and diagonal trapdoor partner links remain complete");
                            BlockPos partner=BlockPos.fromLong(cell.tileData().getLong("TrapdoorPartner"));
                            require(tile.group().stream().anyMatch(member->member.getPos().equals(partner)),"rotated group resolves its transformed saved partner");
                        }
                    }
                    require(cycle.write().equals(original.write()),"door and trapdoor state/NBT return exactly after four cardinal rotations");
                }).get();phase=10;return false;
            }
            mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle craft=server(mc);craft.rotationYaw=doorCase%2==0?0:90;craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());}).get();phase=8;return false;
        }
        phase=5;return false;
    }
}
