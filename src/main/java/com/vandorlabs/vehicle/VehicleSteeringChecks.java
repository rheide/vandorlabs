package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.track.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Native support snapshots, weighted radii, reversing, wheelbase and swept stationary Track turns. */
public final class VehicleSteeringChecks {
    private static int checks;
    private static void require(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Taxi steering: "+why);}
    private static void near(double actual,double expected,String why){require(Math.abs(actual-expected)<1e-7,why+" actual="+actual+" expected="+expected);}
    public static void run(World world){
        checks=0;
        for(double mean:new double[]{1,.65,.45,.08})for(double span:new double[]{0,1,2,4,16,64,10000})for(float steer:new float[]{-1,-.4F,.4F,1}){
            VehicleSteering.Profile profile=new VehicleSteering.Profile(mean==.08?0:3,mean==.08?3:0,mean,span);
            near(VehicleControls.autopilotYawLimit(profile,false),(float)Math.min(8,3/profile.radiusFactor),"Autopilot Taxi uses same mean radius and wheelbase with bounded route yaw");near(VehicleControls.autopilotYawLimit(profile,true),3,"Autopilot Hover route yaw remains unchanged");
            float forward=VehicleControls.yawChange(steer,.2,false,false,profile,true),reverse=VehicleControls.yawChange(steer,-.2,false,false,profile,true);
            require(forward==-reverse&&Math.signum(forward)==Math.signum(steer),"reverse retains intuitive inverted steering for every support kind");require(Float.isFinite(forward)&&Math.abs(forward)<=8,"degenerate and maximum wheelbases have finite bounded angular steps");
            if(mean==1&&span==4)near(forward,VehicleControls.yawChange(steer,.2,false,false),"Large four-block reference preserves current Taxi steering");
            near(VehicleControls.yawChange(steer,0,true,false,profile,true),VehicleControls.yawChange(steer,0,true,false),"stationary Hover unchanged");near(VehicleControls.yawChange(steer,-.2,true,true,profile,true),0,"Hover Autopilot remains responsible for yaw");
            require((VehicleControls.yawChange(steer,0,false,false,profile,true)!=0)==(mean==.08),"only pure Tracks pivot without rolling");near(VehicleControls.yawChange(steer,0,false,false,profile,false),0,"airborne Tracks cannot pivot in place");
        }
        BlockWheel small=wheel("wheel_small"),medium=wheel("wheel_medium"),large=wheel("wheel_large");
        for(EnumFacing front:EnumFacing.HORIZONTALS)for(BlockWheel wheel:new BlockWheel[]{large,medium,small}){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int n=0;n<3;n++)addWheel(cells,wheel,new BlockPos(4+(n==1?4:0),4,4+(n==2?4:0)));
            VehicleSteering.Profile p=profile(world,cells,front);require(p.wheels==3&&p.tracks==0,"all three actual wheels counted once; Large reserved source cells never multiply contacts");near(p.meanKindRadius,wheel==large?1:wheel==medium?.65:.45,"native wheel type radius");near(p.wheelbase,4,"wheelbase respects every local forward facing");
        }
        List<VehicleStructure.Cell> mixed=new ArrayList<>();addWheel(mixed,large,new BlockPos(4,4,4));addWheel(mixed,medium,new BlockPos(8,4,4));addWheel(mixed,small,new BlockPos(4,4,8));addWheel(mixed,small,new BlockPos(8,4,8));
        VehicleSteering.Profile p=profile(world,mixed,EnumFacing.NORTH);require(p.wheels==4,"native Small two-pixel tire offset stays in ground support band");near(p.meanKindRadius,(1+.65+.45+.45)/4,"radius average weights physical multiplicity rather than roots or turn rates");
        List<VehicleStructure.Cell> elevated=new ArrayList<>(mixed);addWheel(elevated,large,new BlockPos(16,10,16));p=profile(world,elevated,EnumFacing.NORTH);require(p.wheels==4,"higher decorative/cargo wheel does not alter support radius or wheelbase");
        for(int style=0;style<3;style++){
            List<VehicleStructure.Cell> tracks=new ArrayList<>();for(int x=4;x<=6;x++)addTrack(tracks,new BlockPos(x,4,4),style);p=profile(world,tracks,EnumFacing.NORTH);require(p.wheels==0&&p.tracks==3&&p.tracksOnly(),"connected physical Tracks all count individually for every selected native style");near(p.meanKindRadius,.08,"all Track appearances share tank radius");near(p.wheelbase,1,"side-by-side tracks bound zero longitudinal spacing");
            VehicleWorld view=new VehicleWorld(world,structure(tracks,EnumFacing.NORTH));VehicleSteering cache=new VehicleSteering();VehicleSteering.Profile first=cache.profile(view);require(cache.profile(view)==first,"profile reused on unchanged vehicle frames");view.tileRevision++;VehicleSteering.Profile refreshed=cache.profile(view);require(refreshed!=first&&refreshed.tracks==3,"tile revisions reevaluate only indexed support candidates");
            List<VehicleStructure.Cell> withWheel=new ArrayList<>(tracks);addWheel(withWheel,medium,new BlockPos(10,4,4));p=profile(world,withWheel,EnumFacing.NORTH);require(p.wheels==1&&p.tracks==3&&!p.tracksOnly(),"mixed wheel and connected Track count each physical contact");near(p.meanKindRadius,(.65+3*.08)/4,"mixed physical radius mean");near(VehicleControls.yawChange(1,0,false,false,p,true),0,"mixed wheeled craft still need travel");
        }
        for(int length:new int[]{1,2,4,12,40}){List<VehicleStructure.Cell> cells=new ArrayList<>();addWheel(cells,large,new BlockPos(4,4,4));addWheel(cells,large,new BlockPos(4,4,4+length));p=profile(world,cells,EnumFacing.NORTH);near(p.radiusFactor,length/4D,"longitudinal wheelbase changes actual radius proportionally");}
        slowTurns();legacy(world);modular(world,false);modular(world,true);sweptTrackTurn(world);actualController(world,large);
        System.out.println("[vandorlabs][reprolab] taxi-steering PASS checks="+checks+" native wheel/regular-Large modular/three Track styles physical counts radius mean wheelbase reverse stationary ground-only tank pivot unchangedHover bounded swept-collision");
    }
    private static void slowTurns(){
        for(boolean tracks:new boolean[]{false,true})for(double span:new double[]{1,4,16}){
            VehicleSteering.Profile p=new VehicleSteering.Profile(tracks?0:3,tracks?2:0,tracks?.08:.45,span);
            float previous=VehicleControls.yawChange(1,0,false,false,p,true);
            if(tracks)near(previous,.6F,"gentle stationary tracked pivot");
            for(int n=1;n<=240;n++){
                double speed=n*.001;float next=VehicleControls.yawChange(1,speed,false,false,p,true);
                require(next>=previous-1e-6&&next-previous<.15,"continuous crawl-to-roll steering without threshold jump");
                near(VehicleControls.yawChange(1,-speed,false,false,p,true),-next,"slow reverse steering mirrors forward");
                near(VehicleControls.yawChange(-1,speed,false,false,p,true),-next,"slow left and right steering symmetric");
                if(p.radiusFactor<1&&speed<=.02)require(next<=1.15,"tight running gear never snaps into high-speed yaw while creeping");
                if(p.radiusFactor<1)require(next<=3.2+1e-6,"tight running gear stays at a controlled crawl-to-roll turn rate");
                previous=next;
            }
        }
    }
    private static BlockWheel wheel(String name){return (BlockWheel)Block.getBlockFromName("vandorlabs:"+name);}
    private static void addWheel(List<VehicleStructure.Cell> cells,BlockWheel wheel,BlockPos at){IBlockState state=wheel.getDefaultState();List<BlockPos> members=wheel.members(at,state);for(int n=0;n<members.size();n++)cells.add(new VehicleStructure.Cell(members.get(n),state.withProperty(BlockWheel.PART,n),null));}
    private static void addTrack(List<VehicleStructure.Cell> cells,BlockPos at,int style){TrackTile tile=new TrackTile();tile.setPos(at);tile.style(style);cells.add(new VehicleStructure.Cell(at,TrackRegistry.COMPACT.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));}
    private static VehicleStructure structure(List<VehicleStructure.Cell> original,EnumFacing facing){List<VehicleStructure.Cell> cells=new ArrayList<>(original);BlockPos seat=new BlockPos(30,30,30);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));return new VehicleStructure(cells,seat,facing,3);}
    private static VehicleSteering.Profile profile(World world,List<VehicleStructure.Cell> cells,EnumFacing facing){return new VehicleSteering().profile(new VehicleWorld(world,structure(cells,facing)));}
    private static void legacy(World world){
        BlockTelescopicLandingGear gear=null;for(Block block:Block.REGISTRY)if(block instanceof BlockTelescopicLandingGear&&!(block instanceof BlockNonWheeledLandingGear)){gear=(BlockTelescopicLandingGear)block;break;}require(gear!=null,"legacy wheel block remains registered for saved vehicles");
        for(int size=0;size<BlockTelescopicLandingGear.SIZES.length;size++){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int x=4;x<=20;x+=8){BlockPos at=new BlockPos(x,8,4);com.vandorlabs.tiles.TileEntityLandingGear tile=new com.vandorlabs.tiles.TileEntityLandingGear();tile.setPos(at);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setInteger("GearSize",size);tag.setFloat("Progress",1);cells.add(new VehicleStructure.Cell(at,gear.getDefaultState().withProperty(BlockTelescopicLandingGear.EXTENDED,true),tag));com.vandorlabs.tiles.TileEntityLandingGear reserved=new com.vandorlabs.tiles.TileEntityLandingGear();reserved.setPos(at.down());reserved.setOwner(at);cells.add(new VehicleStructure.Cell(at.down(),gear.getDefaultState().withProperty(BlockTelescopicLandingGear.LOWER,true).withProperty(BlockTelescopicLandingGear.EXTENDED,true),reserved.writeToNBT(new NBTTagCompound())));}
            VehicleSteering.Profile p=profile(world,cells,EnumFacing.EAST);require(p.wheels==3&&p.tracks==0,"legacy saved roots count once, excluding lower reservation cells");near(p.meanKindRadius,size>=2?1:size==1?.65:.45,"actual legacy saved Small/Medium/Large/extraLarge native wheel size curvature");
        }
    }
    private static void modular(World world,boolean large){
        BlockPos root=new BlockPos(1450,190,1000);GearBlock arm=large?GearRegistry.LARGE_GEAR_MOUNT:GearRegistry.GEAR_MOUNT,foot=large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL;
        for(BlockPos at:BlockPos.getAllInBox(root.add(-3,-3,-3),root.add(8,3,3)))world.getChunkFromBlockCoords(at);
        try{
            for(int x=0;x<3;x++){BlockPos at=root.east(x*(large?3:1));world.setBlockState(at,arm.getDefaultState(),2);world.setBlockState(at.down(),foot.getDefaultState(),2);if(large)foot.onBlockPlacedBy(world,at.down(),world.getBlockState(at.down()),null,new net.minecraft.item.ItemStack(foot));require(((GearTileEntity)world.getTileEntity(at)).rebuild(),"native modular wheel assembly forms");}
            List<VehicleStructure.Cell> cells=new ArrayList<>();BlockPos origin=root.add(-2,-3,-2);
            for(BlockPos at:BlockPos.getAllInBox(root.add(-1,-2,-1),root.add(8,0,2)))if(world.getBlockState(at).getBlock() instanceof GearBlock){net.minecraft.tileentity.TileEntity tile=world.getTileEntity(at);cells.add(new VehicleStructure.Cell(at.subtract(origin),world.getBlockState(at),tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));}
            VehicleSteering.Profile p=profile(world,cells,EnumFacing.NORTH);require(p.wheels==3&&p.tracks==0,"all native modular physical wheels count once regardless connected arm roots/reserved Large footprint");near(p.meanKindRadius,large?1:.45,"regular/Large landing-wheel curvature");
        }finally{for(BlockPos at:BlockPos.getAllInBox(root.add(-2,-2,-2),root.add(9,1,3)))world.removeTileEntity(at);for(BlockPos at:BlockPos.getAllInBox(root.add(-2,-2,-2),root.add(9,1,3)))world.setBlockToAir(at);}
    }
    private static void actualController(World world,BlockWheel large){
        try{
            java.lang.reflect.Method advance=EntityGroundVehicle.class.getDeclaredMethod("advanceController",boolean.class);advance.setAccessible(true);java.lang.reflect.Field strafe=EntityGroundVehicle.class.getDeclaredField("strafe");strafe.setAccessible(true);
            for(int kind=0;kind<3;kind++)for(boolean hovering:new boolean[]{false,true}){
                List<VehicleStructure.Cell> cells=new ArrayList<>();if(kind!=1){addTrack(cells,new BlockPos(4,4,4),0);addTrack(cells,new BlockPos(8,4,8),0);}if(kind!=0)addWheel(cells,large,new BlockPos(12,4,12));
                EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(structure(cells,EnumFacing.NORTH));craft.setPosition(1500,180,1050);craft.hover=hovering;craft.hoverTarget=180;craft.onGround=true;craft.steering=1;strafe.setByte(craft,(byte)1);
                try{
                    VehicleSteering.Profile profile=new VehicleSteering().profile(craft.view);float expected=hovering?1.7F:kind==0?.6F:0;
                    AxisAlignedBB bounds=craft.collision.bounds(0).union(craft.collision.bounds(8)).union(craft.collision.bounds(-8)).offset(craft.posX,craft.posY,craft.posZ).grow(1);
                    for(int x=net.minecraft.util.math.MathHelper.floor(bounds.minX)>>4;x<=net.minecraft.util.math.MathHelper.floor(bounds.maxX)>>4;x++)for(int z=net.minecraft.util.math.MathHelper.floor(bounds.minZ)>>4;z<=net.minecraft.util.math.MathHelper.floor(bounds.maxZ)>>4;z++)world.getChunkFromChunkCoords(x,z);
                    boolean loaded=craft.collision.loaded(world,craft.posX,craft.posY,craft.posZ,0,0,0,expected);float swept=craft.collision.turn(world,craft,craft.posX,craft.posY,craft.posZ,0,expected);
                    String diagnostic=" kind="+kind+" hover="+hovering+" wheels="+profile.wheels+" tracks="+profile.tracks+" factor="+profile.radiusFactor+" grounded="+craft.onGround+" loaded="+loaded+" swept="+swept+" bounds="+bounds+" xyz="+craft.posX+","+craft.posY+","+craft.posZ;
                    require(loaded,"actual controller native rotated footprint is fully loaded"+diagnostic);require(Math.abs(swept-expected)<1e-5,"actual controller fixture has unobstructed native yaw"+diagnostic);
                    near(VehicleAutopilot.yawLimit(craft),hovering?3:(float)Math.min(8,3/profile.radiusFactor),"actual route-controller limit uses vehicle support profile");advance.invoke(craft,true);require(Math.abs(craft.rotationYaw-expected)<1e-5,"actual authoritative/predicted controller expected="+expected+" actual="+craft.rotationYaw+diagnostic);
                }finally{craft.signals.close();craft.setDead();}
            }
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Actual Taxi controller fixture",failure);}
    }
    private static void sweptTrackTurn(World world){
        BlockPos origin=new BlockPos(1500,180,1050);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromBlockCoords(origin.add(x*16,0,z*16));
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(new BlockPos(4,4,4),Blocks.STONE.getDefaultState(),null));cells.add(new VehicleStructure.Cell(new BlockPos(8,4,4),Blocks.STONE.getDefaultState(),null));VehicleWorld view=new VehicleWorld(world,VehicleStructure.display(cells));VehicleCollision collision=new VehicleCollision(view);
        float pivot=VehicleControls.yawChange(1,0,false,false,new VehicleSteering.Profile(0,2,.08,4),true);
        near(collision.turn(world,null,origin.getX(),origin.getY(),origin.getZ(),0,pivot),pivot,"normal collision sweep accepts open-ground stationary Track yaw");
        Vec3d point=new VehiclePose(origin.getX(),origin.getY(),origin.getZ(),0).world(new Vec3d(4.5,4.5,4.5),view.structure.bounds.maxX/2,view.structure.bounds.maxZ/2);BlockPos obstruction=new BlockPos(point);try{world.setBlockState(obstruction,Blocks.STONE.getDefaultState(),2);near(collision.turn(world,null,origin.getX(),origin.getY(),origin.getZ(),0,pivot),0,"stationary tank yaw cannot rotate through an actual solid obstacle");}finally{world.setBlockToAir(obstruction);}
    }
    private VehicleSteeringChecks(){}
}
