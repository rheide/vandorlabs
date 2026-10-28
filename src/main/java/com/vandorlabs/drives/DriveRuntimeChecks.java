package com.vandorlabs.drives;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import io.netty.buffer.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Actual drive receivers and carried snapshots retain level controls without becoming propulsion. */
public final class DriveRuntimeChecks {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Drive controls: "+why);}
    private static final class Source implements RedstoneChannelMember {
        final TileEntity anchor=new TileEntity(){};final int channel;int level;boolean active=true;
        Source(WorldServer world,BlockPos pos,int channel){anchor.setWorld(world);anchor.setPos(pos);this.channel=channel;}
        public TileEntity channelTile(){return anchor;}public boolean isChannelMemberActive(){return active;}public int getRedstoneChannel(){return channel;}public void setRedstoneChannel(int ignored){}public boolean hasLocalRedstoneSignal(){return level>0;}public int localSignalLevel(int ignored){return level;}public void setChannelSignal(boolean ignored){}
        void value(int next){level=next;RedstoneChannels.inputChanged(this);}
    }
    private static TileEntityConstructor build(WorldServer world,BlockPos root,FrameMachineDesign spec,EnumFacing facing){
        BlockConstructorCore core=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+spec.block);world.setBlockState(root,core.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);Block matter=Block.getBlockFromName("vandorlabs:programmable_matter_block");
        for(int x=0;x<spec.width;x++)for(int y=0;y<spec.height;y++)for(int z=0;z<spec.depth;z++)if(tile.frame(x,y,z)&&!tile.cell(x,y,z).equals(root))world.setBlockState(tile.cell(x,y,z),matter.getDefaultState(),2);
        require(tile.assemble(),"formed actual "+spec.design+" "+facing);DeferredTileLoad.flush(world);return tile;
    }
    public static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        checks=0;BlockPos root=new BlockPos(1130,150,430);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromChunkCoords((root.getX()>>4)+x,(root.getZ()>>4)+z);Source source=new Source(world,root.up(8),64961);RedstoneFixtureTransmitters.equip(source);RedstoneChannels.register(source);double px=player.posX,py=player.posY,pz=player.posZ;
        try{player.setPosition(root.getX(),root.getY()+3,root.getZ()-3);codec();
            for(String design:new String[]{"longburn_drive","meridian_core","oculus_drive"})for(EnumFacing facing:EnumFacing.HORIZONTALS){clear(world,root);FrameMachineDesign spec=FrameMachineDesign.get(design);TileEntityConstructor tile=build(world,root,spec,facing);require(tile.isDrive()&&tile.mount()!=null&&!tile.supportsSecurity(),"all drive data holders are visible without security");int[] textures=new int[tile.surfaceCount()];Arrays.fill(textures,-2);
                tile.configureMachine(new NavigationScreenSettings(-1,true,1,true,true),textures,false,16,3,ChannelList.of(source.channel));require(!tile.screenSettings().security&&tile.any&&tile.allowed==127&&tile.behavior==0,"legacy security filter/return configuration cannot gate data");
                for(boolean inverted:new boolean[]{false,true})for(boolean levels:new boolean[]{false,true})for(int intensity=0;intensity<4;intensity++)for(int threshold:new int[]{0,8,15}){
                    DriveSettings settings=new DriveSettings(levels,inverted,intensity,threshold);tile.configureDrive(settings);require(tile.driveSettings().particles==(design.equals("longburn_drive")?intensity:0),"non-Maxwell rejects requested particle intensity while other drives preserve it");
                    for(int value=0;value<16;value++){source.value(value);int activation=inverted?15-value:value;require(tile.driveLevel()==activation&&tile.screenOn()==(activation>0),"actual level receiver "+design+facing+value+inverted);require(settings.plume(tile.screenOn(),activation)==(activation>0&&intensity>0&&(!levels||activation>=threshold)),"Off/Light/Medium/Heavy threshold policy");require(tile.getOutputLevel()==0&&tile.localSignalLevel(source.channel)==0,"drive receiver never emits redstone");}
                }
                tile.configureDrive(DriveSettings.DEFAULT);source.value(3);BlockPos inlet=tile.cell(spec.width-1,spec.height-1,spec.depth-1).up();world.setBlockState(inlet,Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.sampleInput(inlet.down());require(tile.driveLevel()==15,"physical level on remote assembly member overrides weaker channel");world.setBlockToAir(inlet);tile.refreshInputs();
                for(int company=0;company<7;company++){ItemStack chip=new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,company);chip.setStackDisplayName("Persistent drive data "+company);chip.setTagInfo("DrivePayload",new net.minecraft.nbt.NBTTagString("unchanged"));require(tile.authorized(chip)&&tile.isItemValidForSlot(0,chip),"all companies are data, not credentials");tile.setInventorySlotContents(0,chip);require(ItemStack.areItemStacksEqual(chip,tile.getStackInSlot(0)),"full inserted data chip retained");tile.removeStackFromSlot(0);}
                require(!tile.accepts(new ItemStack(com.vandorlabs.items.ModItems.ACCESS_CARD)),"Access Card security remains a separate item type");
                NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());legacy.setBoolean("NavigationSecurity",true);legacy.setBoolean("AnyToken",false);legacy.setInteger("AllowedTokens",16);legacy.setInteger("TokenBehavior",3);TileEntityConstructor loaded=new TileEntityConstructor();loaded.readFromNBT(legacy);require(!loaded.screenSettings().security&&loaded.any&&loaded.allowed==127&&loaded.behavior==0,"legacy Security true loads as data-only without privilege/filter semantics");
                carried(world,tile,root,source);tile.clear();
            }
        }finally{source.active=false;RedstoneChannels.unregister(source);clear(world,root);player.setPosition(px,py,pz);}
        System.out.println("[vandorlabs][reprolab] drive-controls PASS assertions="+checks+" three drives four facings level0..15 inversion brightness intensity threshold physical/channel max carried snapshots persistent data mounts and negative propulsion");
    }
    private static void carried(WorldServer world,TileEntityConstructor tile,BlockPos root,Source source){
        List<VehicleStructure.Cell> cells=new ArrayList<>();FrameMachineDesign spec=tile.core().specification();BlockPos minimum=root;for(int x=0;x<spec.width;x++)for(int y=0;y<spec.height;y++)for(int z=0;z<spec.depth;z++){BlockPos p=tile.cell(x,y,z);minimum=new BlockPos(Math.min(minimum.getX(),p.getX()),Math.min(minimum.getY(),p.getY()),Math.min(minimum.getZ(),p.getZ()));}BlockPos origin=minimum,coreLocal=root.subtract(origin);for(int x=0;x<spec.width;x++)for(int y=0;y<spec.height;y++)for(int z=0;z<spec.depth;z++){BlockPos pos=tile.cell(x,y,z);TileEntity member=world.getTileEntity(pos);cells.add(new VehicleStructure.Cell(pos.subtract(origin),world.getBlockState(pos),member==null?null:VehicleTileData.translated(member.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));}
        VehicleStructure snapshot=withCrew(cells,tile.facing());require(!VehicleFlight.lift(tile.core())&&!VehicleFlight.exhaust(tile.core())&&!snapshot.hasThruster&&!VehicleFlight.canHover(snapshot),"drive never satisfies Taxi/Hover propulsion or lift");boolean rejected=false;try{VehicleFlight.requireThruster(snapshot);}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"decorative drive cannot replace functional vehicle thruster");
        EntityGroundVehicle craft=new EntityGroundVehicle(world);try{craft.install(snapshot);craft.setPosition(root.getX()+30,root.getY()+10,root.getZ());craft.signals.open();craft.signals.tick();TileEntityConstructor carried=(TileEntityConstructor)craft.view.getTileEntity(coreLocal);carried.configureDrive(new DriveSettings(true,false,2,8));for(int level:new int[]{0,4,8,15}){source.value(level);craft.signals.tick();carried=(TileEntityConstructor)craft.view.getTileEntity(coreLocal);require(carried.driveLevel()==level&&carried.screenOn()==(level>0),"carried drive receives parent-world numeric level "+level);require(carried.mount()!=null,"carried holder independent of activation/chip");}Vec3d point=DriveParticles.center(carried),worldPoint=craft.toWorld(point);require(Double.isFinite(worldPoint.x)&&worldPoint.y>root.getY(),"authored emission point follows parent pose");}finally{craft.signals.close();craft.setDead();}
    }
    /** A real controller, complete pilot and three registered gear cells make an installable non-propelled hull. */
    public static VehicleStructure withCrew(List<VehicleStructure.Cell> original,EnumFacing facing){
        List<VehicleStructure.Cell> cells=new ArrayList<>(original);int x=0;for(VehicleStructure.Cell cell:cells)x=Math.max(x,cell.pos.getX()+1);BlockPos seat=new BlockPos(x+1,1,1),control=seat.east();Block pilot=Block.getBlockFromName("vandorlabs:pilot_seat"),controller=Block.getBlockFromName("vandorlabs:vehicle_controller");
        TileEntityPilotSeat tile=new TileEntityPilotSeat();tile.setPos(seat);cells.add(new VehicleStructure.Cell(seat,pilot.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));cells.add(new VehicleStructure.Cell(seat.up(),pilot.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null));TileEntityVehicleController owner=new TileEntityVehicleController();owner.setPos(control);cells.add(new VehicleStructure.Cell(control,controller.getDefaultState(),owner.writeToNBT(new NBTTagCompound())));
        Block gear=null;for(Block candidate:Block.REGISTRY)if(candidate instanceof BlockTelescopicLandingGear&&!(candidate instanceof BlockNonWheeledLandingGear)){gear=candidate;break;}require(gear!=null,"registered wheeled support fixture");for(int n=0;n<3;n++){BlockPos at=new BlockPos(x+n,0,1);TileEntityLandingGear support=new TileEntityLandingGear();support.setPos(at);cells.add(new VehicleStructure.Cell(at,gear.getDefaultState(),support.writeToNBT(new NBTTagCompound())));}
        cells.add(new VehicleStructure.Cell(new BlockPos(x,0,0),Blocks.STONE.getDefaultState(),null));VehicleStructure result=new VehicleStructure(cells,seat,facing,3);require(result.hasDedicatedController()&&result.pilotSeats().size()==1,"carried fixture has actual pilot/controller");return result;
    }
    private static void codec(){for(boolean levels:new boolean[]{false,true})for(boolean inverted:new boolean[]{false,true})for(int particles=0;particles<4;particles++)for(int threshold=0;threshold<16;threshold++){DriveSettings settings=new DriveSettings(levels,inverted,particles,threshold);NBTTagCompound saved=new NBTTagCompound();settings.write(saved);NBTTagCompound again=new NBTTagCompound();DriveSettings.read(saved).write(again);require(saved.equals(again),"drive settings NBT roundtrip");ByteBuf buffer=Unpooled.buffer();try{settings.write(buffer);require(buffer.readableBytes()==4,"bounded four-byte settings suffix");DriveSettings decoded=DriveSettings.read(buffer);require(decoded.levels==levels&&decoded.inverted==inverted&&decoded.particles==particles&&decoded.threshold==threshold&&!buffer.isReadable(),"drive settings packet exact roundtrip");}finally{buffer.release();}}}
    private static void clear(WorldServer world,BlockPos root){for(BlockPos pos:BlockPos.getAllInBox(root.add(-5,-2,-5),root.add(7,6,7)))world.setBlockToAir(pos);}
    private DriveRuntimeChecks(){}
}
