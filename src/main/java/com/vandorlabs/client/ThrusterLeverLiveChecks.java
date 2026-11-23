package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockSignalControl;
import com.vandorlabs.blocks.BlockVandorSwitch;
import com.vandorlabs.drives.DriveRuntimeChecks;
import com.vandorlabs.drives.DriveSettings;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.RedstoneChannelMember;
import com.vandorlabs.redstone.RedstoneChannels;
import com.vandorlabs.redstone.TransmitterSlots;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntitySignalControl;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleInteraction;
import com.vandorlabs.vehicle.VehicleLookup;
import com.vandorlabs.vehicle.VehicleStructure;
import com.vandorlabs.vehicle.VehicleTileData;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Sends the real creative mining and ordinary use inputs against installed levers. */
final class ThrusterLeverLiveChecks {
    private static final BlockPos PLACED=new BlockPos(24,100,25), ORIGIN=PLACED.east(12).down();
    private static final BlockPos FREE=new BlockPos(0,1,0), LINKED=new BlockPos(5,1,0), ENGINE=new BlockPos(2,1,1);
    private static final int CHANNEL=29341;
    // Give the carried cutout meshes time to rebuild in shader clients before capture.
    private static final int CAPTURE_TICKS=40;
    private static int stage,ticks;
    private static UUID owner;
    private static Vec3d eye;
    private static Probe probe;

    private ThrusterLeverLiveChecks(){}

    static boolean tick(Minecraft mc,File output)throws Exception{
        require(++ticks<180,"stage="+stage+" mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition());
        switch(stage){
            case 0:
                mc.getIntegratedServer().addScheduledTask(()->setup(mc)).get();next();return false;
            case 1:
                if(!ready(mc)||mc.world.getBlockState(PLACED).getBlock()!=lever())return false;
                clickPlaced(mc,false);clickPlaced(mc,false);next();return false;
            case 2:
                if(!at((TileEntitySignalControl)mc.world.getTileEntity(PLACED),2,10)||ticks<CAPTURE_TICKS)return false;
                mc.getIntegratedServer().addScheduledTask(()->expectLevels(10)).get();
                shot(mc,output,"shot_thruster-placed-medium.png");
                sneaking(mc,()->{clickPlaced(mc,true);clickPlaced(mc,true);});next();return false;
            case 3:
                if(!at((TileEntitySignalControl)mc.world.getTileEntity(PLACED),0,0)||ticks<CAPTURE_TICKS)return false;
                mc.getIntegratedServer().addScheduledTask(()->{expectLevels(10,0);spawn(mc);}).get();next();return false;
            case 4:
                if(!ready(mc)||client(mc)==null)return false;
                clickCarried(mc,FREE,false);next();return false;
            case 5:
                if(!carried(mc,FREE,1,5)||ticks<CAPTURE_TICKS)return false;
                mc.getIntegratedServer().addScheduledTask(()->{
                    EntityGroundVehicle craft=server(mc);IBlockState state=craft.view.getBlockState(FREE);
                    require(lever().getWeakPower(state,craft.view,FREE,EnumFacing.UP)==5,"unchannelled carried lever delivers physical Low");
                }).get();
                shot(mc,output,"shot_thruster-carried-low.png");
                sneaking(mc,()->clickCarried(mc,FREE,true));next();return false;
            case 6:
                if(!carried(mc,FREE,0,0)||ticks<CAPTURE_TICKS)return false;
                mc.getIntegratedServer().addScheduledTask(()->require(server(mc).board(player(mc)),"pilot boards actual carried seat")).get();
                next();return false;
            case 7:
                EntityGroundVehicle craft=client(mc);
                if(craft==null||mc.player.getRidingEntity()!=craft||ticks<8)return false;
                aimPilot(mc,craft);clickCarried(mc,LINKED,false);next();return false;
            case 8:
                if(!carried(mc,LINKED,1,5)||!engineAt(mc,5)||ticks<CAPTURE_TICKS)return false;
                mc.getIntegratedServer().addScheduledTask(()->{
                    expectLevels(5);
                    require(((TileEntityConstructor)server(mc).view.getTileEntity(ENGINE)).maxwellActiveLevel()==5,"seated pilot's lever powers the real carried drive at Low");
                }).get();
                shot(mc,output,"shot_thruster-pilot-low.png");
                aimPilot(mc,client(mc));
                mc.player.movementInput.sneak=true;
                try{clickCarried(mc,LINKED,true);}finally{mc.player.movementInput.sneak=false;}
                next();return false;
            case 9:
                if(!carried(mc,LINKED,0,0)||!engineAt(mc,0)||ticks<CAPTURE_TICKS)return false;
                mc.getIntegratedServer().addScheduledTask(()->{
                    expectLevels(5,0);
                    TileEntitySignalControl control=(TileEntitySignalControl)server(mc).view.getTileEntity(LINKED);
                    require(player(mc).getRidingEntity()==server(mc)&&!control.writeToNBT(new NBTTagCompound()).hasKey("ControlPreviewStep"),"seated reverse stops the drive and the snapshot retains only persistent output");
                }).get();
                shot(mc,output,"shot_thruster-pilot-off.png");
                System.out.println("[vandorlabs][reprolab] thruster-lever-live PASS real placed and seated carried clicks, creative reverse without breaking, physical and wireless output, carried Maxwell receiver, no intermediate pulses");
                return true;
            default: throw new IllegalStateException("Unknown Thruster Lever check stage "+stage);
        }
    }
    private static void setup(Minecraft mc){
        WorldServer world=world(mc);EntityPlayerMP player=player(mc);
        player.setGameType(GameType.CREATIVE);player.inventory.currentItem=0;
        player.connection.sendPacket(new SPacketHeldItemChange(0));
        player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();
        world.getChunkFromBlockCoords(PLACED);
        world.setBlockState(PLACED.south(),Blocks.STONE.getDefaultState(),3);
        require(LiveGallery.place(world,player,new ItemStack(lever()),PLACED.south(),EnumFacing.NORTH,.5F,.5F,.5F),"player places wall-mounted Thruster Lever");
        ((TileEntitySignalControl)world.getTileEntity(PLACED)).setRedstoneChannel(CHANNEL);
        registerProbe(world,PLACED);aim(mc,PLACED);
    }
    private static void spawn(Minecraft mc){
        WorldServer world=world(mc);
        RedstoneChannels.unregister(probe);world.setBlockToAir(PLACED);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(BlockPos pos:Arrays.asList(FREE,LINKED)){
            TileEntitySignalControl tile=new TileEntitySignalControl();tile.setPos(pos);
            if(pos.equals(LINKED))tile.setRedstoneChannel(CHANNEL);
            EnumFacing mount=pos.equals(LINKED)?EnumFacing.EAST:EnumFacing.NORTH;
            cells.add(new VehicleStructure.Cell(pos,lever().getDefaultState().withProperty(BlockVandorSwitch.FACING,mount),tile.writeToNBT(new NBTTagCompound())));
            cells.add(new VehicleStructure.Cell(pos.offset(mount.getOpposite()),Blocks.STONE.getDefaultState(),null));
        }
        addEngine(world,cells);
        for(BlockPos pos:BlockPos.getAllInBox(ORIGIN.add(-3,-1,-3),ORIGIN.add(9,-1,6)))world.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(DriveRuntimeChecks.withCrew(cells,EnumFacing.NORTH));
        craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());require(world.spawnEntity(craft),"carried fixture spawn");
        TransmitterSlots.configure((TileEntitySignalControl)craft.view.getTileEntity(LINKED),ChannelList.of(CHANNEL),16,false);
        owner=craft.getUniqueID();registerProbe(world,ORIGIN);aim(mc,ORIGIN.add(FREE));
    }
    private static void addEngine(WorldServer world,List<VehicleStructure.Cell> cells){
        BlockPos source=ORIGIN.add(0,3,12),offset=ENGINE.subtract(source);
        world.getChunkFromBlockCoords(source);
        TileEntityConstructor drive=MaxwellRuntimeChecks.assemble(world,source,3,EnumFacing.NORTH,0,0);
        drive.configureDrive(new DriveSettings(true,false,1,8));
        drive.configureMachine(new NavigationScreenSettings(-1,false,1,true,false),new int[0],false,127,0,ChannelList.of(CHANNEL));
        require(MaxwellRuntimeChecks.inlet(drive,EnumFacing.NORTH).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),64),true)==64,"fuel the formed carried analog receiver");
        for(BlockPos at:BlockPos.getAllInBox(source,source.add(2,2,3)))cells.add(new VehicleStructure.Cell(at.add(offset),world.getBlockState(at),
                VehicleTileData.translated(world.getTileEntity(at).writeToNBT(new NBTTagCompound()),offset)));
        for(BlockPos at:BlockPos.getAllInBox(source,source.add(2,2,3)))world.setBlockToAir(at);
    }
    private static void aim(Minecraft mc,BlockPos target){
        EntityPlayerMP player=player(mc);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);
        player.inventoryContainer.detectAndSendChanges();
        eye=new Vec3d(target).addVector(.5,.48,-2);
        player.connection.setPlayerLocation(eye.x,eye.y-player.getEyeHeight(),eye.z,0,0);
    }
    private static boolean ready(Minecraft mc){
        return ticks>6&&mc.playerController.isInCreativeMode()&&mc.player.getHeldItemMainhand().isEmpty()
                &&mc.player.getHeldItemOffhand().isEmpty()&&mc.player.getPositionEyes(1).squareDistanceTo(eye)<.02;
    }
    private static void aimPilot(Minecraft mc,EntityGroundVehicle craft){
        Vec3d target=craft.toWorld(new Vec3d(LINKED).addVector(.4,.48,.5),LINKED);
        Vec3d heading=target.subtract(VehicleInteraction.eyes(mc.player));
        float yaw=(float)Math.toDegrees(Math.atan2(-heading.x,heading.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(heading.y,Math.hypot(heading.x,heading.z)));
        mc.player.prevRotationYaw=mc.player.rotationYaw=yaw;mc.player.prevRotationPitch=mc.player.rotationPitch=pitch;
        mc.player.connection.sendPacket(new CPacketPlayer.Rotation(yaw,pitch,false));
    }
    private static void clickPlaced(Minecraft mc,boolean reverse){
        Vec3d from=mc.player.getPositionEyes(1);RayTraceResult ray=mc.world.rayTraceBlocks(from,from.add(mc.player.getLook(1).scale(4)));
        require(ray!=null&&PLACED.equals(ray.getBlockPos()),"ordinary aim reaches installed Thruster Lever: "+ray);
        if(reverse)mc.playerController.clickBlock(ray.getBlockPos(),ray.sideHit);
        else mc.playerController.processRightClickBlock(mc.player,mc.world,ray.getBlockPos(),ray.sideHit,ray.hitVec,EnumHand.MAIN_HAND);
    }
    private static void clickCarried(Minecraft mc,BlockPos pos,boolean reverse)throws Exception{
        VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
        require(target!=null&&target.craft==client(mc)&&pos.equals(target.hit.cell.pos),"real vehicle reach selects Thruster Lever "+pos+" at "+mc.player.getPositionEyes(1));
        mc.objectMouseOver=new RayTraceResult(RayTraceResult.Type.MISS,mc.player.getPositionEyes(1),EnumFacing.UP,BlockPos.ORIGIN);
        if(reverse)ReflectionHelper.findMethod(Minecraft.class,"clickMouse","func_147116_af").invoke(mc);
        else ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
    }
    private interface Click {void run()throws Exception;}
    private static void sneaking(Minecraft mc,Click click)throws Exception{
        mc.player.movementInput.sneak=true;
        mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.START_SNEAKING));
        try{click.run();}finally{
            mc.player.movementInput.sneak=false;
            mc.player.connection.sendPacket(new CPacketEntityAction(mc.player,CPacketEntityAction.Action.STOP_SNEAKING));
        }
    }
    private static boolean at(TileEntitySignalControl tile,int step,int level){return tile!=null&&tile.getStep()==step&&tile.getOutputLevel()==level;}
    private static boolean carried(Minecraft mc,BlockPos pos,int step,int level){
        EntityGroundVehicle craft=client(mc);if(craft==null)return false;
        IBlockState state=craft.view.getBlockState(pos);
        return at((TileEntitySignalControl)craft.view.getTileEntity(pos),step,level)
                &&lever().getActualState(state,craft.view,pos).getValue(BlockSignalControl.LEVEL)==step;
    }
    private static boolean engineAt(Minecraft mc,int level){
        TileEntityConstructor tile=(TileEntityConstructor)client(mc).view.getTileEntity(ENGINE);
        return tile!=null&&tile.maxwellFuel()>0&&tile.maxwellActiveLevel()==level;
    }
    private static BlockSignalControl lever(){return (BlockSignalControl)Block.getBlockFromName("vandorlabs:thruster_lever");}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)world(mc).getEntityFromUuid(owner);}
    private static EntityGroundVehicle client(Minecraft mc){
        for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(owner)&&craft.view!=null)return craft;
        return null;
    }
    private static void registerProbe(WorldServer world,BlockPos pos){probe=new Probe(world,pos);RedstoneChannels.register(probe);probe.changes.clear();}
    private static void expectLevels(Integer... levels){require(probe.changes.equals(Arrays.asList(levels)),"only final detents reach the channel. Expected "+Arrays.toString(levels)+", observed "+probe.changes);}
    private static final class Probe implements RedstoneChannelMember {
        final TileEntity anchor=new TileEntity(){};
        final List<Integer> changes=new ArrayList<>();
        Probe(WorldServer world,BlockPos pos){anchor.setWorld(world);anchor.setPos(pos);}
        public TileEntity channelTile(){return anchor;}
        public boolean isChannelMemberActive(){return true;}
        public int getRedstoneChannel(){return CHANNEL;}
        public void setRedstoneChannel(int ignored){throw new UnsupportedOperationException();}
        public boolean hasLocalRedstoneSignal(){return false;}
        public void setChannelSignal(boolean ignored){throw new UnsupportedOperationException();}
        public void setChannelLevel(int value){changes.add(value);}
    }
    private static void shot(Minecraft mc,File output,String name)throws Exception{
        javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(output,name));
    }
    private static void next(){stage++;ticks=0;}
    private static void require(boolean ok,String what){if(!ok)throw new IllegalStateException("Thruster Lever live: "+what);}
}
