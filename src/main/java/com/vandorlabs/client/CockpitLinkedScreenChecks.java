package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.cockpit.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Mixed regular/cockpit latch controls, real consumers and carried ramp snapshot changes. */
final class CockpitLinkedScreenChecks {
    private static final BlockPos BASE=new BlockPos(740,150,420),REGULAR=BASE.west(4),DOOR=BASE.east(4),RAMP=BASE.east(6);
    private static final int FIRST=68100;
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("Cockpit linked screens: "+reason);}
    private static TileEntityCockpit cockpit(EntityGroundVehicle craft){for(TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityCockpit)return (TileEntityCockpit)tile;throw new IllegalStateException("Missing carried cockpit");}
    private static TileEntityRampController ramp(EntityGroundVehicle craft){for(TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityRampController)return (TileEntityRampController)tile;throw new IllegalStateException("Missing carried ramp");}
    private static boolean door(EntityGroundVehicle craft){for(VehicleStructure.Cell cell:craft.structure.cells)if(cell.state.getBlock() instanceof BlockProgrammableTrapdoor)return cell.state.getValue(BlockDoor.OPEN);throw new IllegalStateException("Missing carried door");}
    private static NBTTagList configuration(){NBTTagList rows=new NBTTagList();for(int i=0;i<6;i++){NBTTagCompound row=new NBTTagCompound();row.setString("Label",new String[]{"Door","Ramp","Partial","Slider","Pulse","Bank"}[i]);row.setInteger("Kind",i==3?1:i==4?2:0);row.setInteger("Min",5);row.setInteger("Max",15);ChannelData.write(row,i==2?ChannelList.of(FIRST+2,FIRST+5):ChannelList.of(FIRST+i));rows.appendTag(row);}return rows;}
    private static void click(TileEntityCockpit tile,EntityPlayerMP player,int row){
        CockpitData.Surface plane=null;for(CockpitData.Surface surface:tile.data().surfaces)if(surface.id.equals("screen_right"))plane=surface;require(plane!=null,"right display exists");
        Vec3d eye=new Vec3d(tile.seatLocation().x,tile.data().riderY+player.getEyeHeight(),tile.seatLocation().z),at=plane.point(109/128D,1-(row+.5)/6D),end=eye.add(at.subtract(eye).normalize().scale(8));
        require(tile.use(CockpitData.rotate(eye,tile.facing()),CockpitData.rotate(end,tile.facing()),player),"seated right-screen ray row "+row);
    }
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        EntityGroundVehicle craft=null;RedstoneScreenContents regular=null;Vec3d before=player.getPositionVector();
        try{
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-5,-2,-4),BASE.add(48,14,6))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
            player.setPositionAndUpdate(BASE.getX()+.5,BASE.getY()+2,BASE.getZ()+3);
            world.setBlockState(BASE,Block.getBlockFromName("vandorlabs:cockpit").getDefaultState(),2);CockpitAssembly.install(world,BASE,player);TileEntityCockpit tile=(TileEntityCockpit)world.getTileEntity(BASE);tile.setTaxiMode(false);
            int index=tile.displays.indexOf(tile.display("screen_right"));require(tile.configureRows(index,configuration()),"six full-screen rows accepted");require(tile.display("screen_right").rows==6,"six rows retained");require(tile.display("touch_right_keyboard").maxRows()==3,"small touch panel keeps readable capacity");
            int throttleIndex=tile.controls.indexOf(tile.control("throttle_left"));require(throttleIndex==tile.data().surfaces.size()*3,"legacy throttle index retained");
            NBTTagCompound stable=tile.writeToNBT(new NBTTagCompound());NBTTagList tooMany=configuration();tooMany.appendTag(tooMany.getCompoundTagAt(0).copy());require(!tile.configureRows(index,tooMany)&&stable.equals(tile.writeToNBT(new NBTTagCompound())),"seventh row rejected before changes");
            world.setBlockState(REGULAR,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),2);regular=((TileEntityAnimatedScreenSelector)world.getTileEntity(REGULAR)).redstoneScreen(0);
            List<String> labels=Arrays.asList("Door","Ramp","Partial","Slider","Pulse","Bank");List<ChannelList> channels=new ArrayList<>();for(int i=0;i<6;i++)channels.add(ChannelList.of(FIRST+i));require(regular.configureRows(labels,channels,0),"regular screen fixture");
            NBTTagList regularConfig=regular.rowConfiguration();regularConfig.getCompoundTagAt(3).setBoolean("Slider",true);regularConfig.getCompoundTagAt(3).setInteger("Min",5);regularConfig.getCompoundTagAt(3).setInteger("Max",15);require(regular.applyRowConfiguration(regularConfig),"regular slider fixture");
            world.setBlockState(DOOR,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState(),2);((TileEntityProgrammableTrapdoor)world.getTileEntity(DOOR)).configure(0,0,false,1,FIRST);
            world.setBlockState(RAMP,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState(),2);TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(RAMP);for(int x=0;x<2;x++)for(int z=0;z<2;z++)world.setBlockState(RAMP.north().add(x,0,-z),Blocks.STONE.getDefaultState(),2);require(ramp.configure(player,2,8,false,true,false,false),"ramp fixture");ramp.setRedstoneChannels(ChannelList.of(FIRST+1));ramp.updatePower();
            for(int row=0;row<2;row++){
                regular.toggleRow(row);require(tile.control("screen_right/"+row).level==15,"regular ON mirrors cockpit");click(tile,player,row);require(RedstoneChannels.level(world,FIRST+row)==0&&!regular.rows().get(row).active(),"cockpit OFF clears regular ON");
                click(tile,player,row);require(regular.rows().get(row).active(),"cockpit ON mirrors regular");require(row==0?world.getBlockState(DOOR).getValue(BlockDoor.OPEN):ramp.isOpen(),"real door/ramp receives cockpit ON");regular.toggleRow(row);require(tile.control("screen_right/"+row).level==0,"regular OFF clears cockpit ON");
            }
            regular.toggleRow(2);require(RedstoneChannels.level(world,FIRST+2)==15&&RedstoneChannels.level(world,FIRST+5)==0,"partial bank source");require(tile.control("screen_right/2").level==0,"partial channel bank is not ALL on");click(tile,player,2);regular.toggleRow(2);TileEntityCockpit loaded=new TileEntityCockpit();loaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(loaded.control("screen_right/2").latchedLevel(FIRST+2)==0&&loaded.control("screen_right/2").latchedLevel(FIRST+5)==15,"partial channel levels survive save/load");regular.toggleRow(5);
            for(int step=0;step<4;step++){regular.selectSegment(3,step);require(tile.control("screen_right/3").level==TileEntitySignalControl.levelForStep(step,5,15),"regular slider level mirrors cockpit");tile.control("screen_right/3").activate((3-step)/3D);require(RedstoneChannels.level(world,FIRST+3)==TileEntitySignalControl.levelForStep(3-step,5,15),"cockpit slider updates shared level");}
            click(tile,player,4);require(RedstoneChannels.level(world,FIRST+4)==15,"cockpit pulse starts");tile.expire(world.getTotalWorldTime()+21);require(RedstoneChannels.level(world,FIRST+4)==0,"pulse releases without latching");
            for(int row:new int[]{0,1,2,3,5})RedstoneChannels.latchLevelChanged(regular.rows().get(row),0);ramp.recover(true);
            BlockPos origin=BASE.add(-2,-1,-3);List<VehicleStructure.Cell> cells=new ArrayList<>();List<BlockPos> source=new ArrayList<>();for(BlockPos at:BlockPos.getAllInBox(origin,BASE.add(9,3,4)))if(!world.isAirBlock(at)){TileEntity part=world.getTileEntity(at);cells.add(new VehicleStructure.Cell(at.subtract(origin),world.getBlockState(at),part==null?null:VehicleTileData.translated(part.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));source.add(at.toImmutable());}
            craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,BASE.subtract(origin),EnumFacing.NORTH,3));craft.hover=true;craft.setPosition(origin.getX()+30,origin.getY()+8,origin.getZ());require(world.spawnEntity(craft),"carried fixture spawn");for(BlockPos at:source)world.setBlockToAir(at);craft.signals.tick();
            for(int row=0;row<2;row++){
                regular.toggleRow(row);craft.signals.tick();require(cockpit(craft).control("screen_right/"+row).level==15,"regular ON mirrors carried cockpit");click(cockpit(craft),player,row);craft.signals.tick();require(RedstoneChannels.level(world,FIRST+row)==0,"carried cockpit OFF clears regular ON row="+row+" level="+RedstoneChannels.level(world,FIRST+row)+" local="+cockpit(craft).control("screen_right/"+row).level+" viewRemote="+craft.view.isRemote);
                click(cockpit(craft),player,row);craft.signals.tick();require(row==0?door(craft):ramp(craft).isOpen(),"carried cockpit operates real door/ramp");regular.toggleRow(row);craft.signals.tick();require(cockpit(craft).control("screen_right/"+row).level==0,"regular OFF clears carried cockpit ON");
            }
            for(int step=0;step<4;step++){regular.selectSegment(3,step);craft.signals.tick();require(cockpit(craft).control("screen_right/3").level==TileEntitySignalControl.levelForStep(step,5,15),"regular slider mirrors carried cockpit");cockpit(craft).control("screen_right/3").activate((3-step)/3D);craft.signals.tick();require(RedstoneChannels.level(world,FIRST+3)==TileEntitySignalControl.levelForStep(3-step,5,15),"carried slider updates shared level");}
            click(cockpit(craft),player,4);craft.signals.tick();require(RedstoneChannels.level(world,FIRST+4)==15,"carried pulse starts");cockpit(craft).expire(world.getTotalWorldTime()+21);craft.signals.cockpitChanged(cockpit(craft));require(RedstoneChannels.level(world,FIRST+4)==0,"carried pulse expires");
            channels.set(1,ChannelList.EMPTY);require(regular.configureRows(labels,channels,0),"remove independent ramp latch");craft.signals.tick();click(cockpit(craft),player,1);for(int i=0;i<3;i++)craft.signals.tick();require(ramp(craft).isOpen()&&cockpit(craft).control("screen_right/1").level==15&&RedstoneChannels.level(world,FIRST+1)==15,"ramp endpoint clone retains the cockpit's sole latch source");
            System.out.println("[vandorlabs][reprolab] cockpit-linked-screens PASS six rows, seated rays, bidirectional toggles/sliders, partial-bank NBT, pulse expiry, real placed/carried doors and ramps, sole-source ramp rebuild");
        }finally{if(craft!=null){craft.signals.close();craft.setDead();}if(regular!=null)regular.configureRows(Collections.emptyList(),Collections.emptyList(),0);for(BlockPos at:BlockPos.getAllInBox(BASE.add(-5,-2,-4),BASE.add(12,5,6)))world.setBlockToAir(at);player.setPositionAndUpdate(before.x,before.y,before.z);}
    }
}
