package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.cockpit.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Real-world placement, reservations, channels, copy, collision and captured output contracts. */
final class CockpitRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(360,103,120);
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        for(String id:CockpitData.IDS){Block block=Block.getBlockFromName("vandorlabs:"+id);NonNullList<ItemStack> catalog=NonNullList.create();block.getSubBlocks(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB,catalog);require(block.getCreativeTabToDisplayOn()==com.vandorlabs.VandorLabs.VANDOR_LABS_TAB&&catalog.stream().anyMatch(s->s.getItem()==net.minecraft.item.Item.getItemFromBlock(block)),"creative cockpit entry "+id);}
        Vec3d before=player.getPositionVector();int surfaces=0;
        try{
            player.setPositionAndUpdate(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+3);
            for(String id:CockpitData.IDS)for(EnumFacing facing:EnumFacing.HORIZONTALS){
                clear(world);Block block=Block.getBlockFromName("vandorlabs:"+id);world.setBlockState(ROOT,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing));CockpitAssembly.install(world,ROOT,player);
                TileEntityCockpit tile=(TileEntityCockpit)world.getTileEntity(ROOT);tile.setTaxiMode(false);tile.setSideTexture(14);
                for(BlockPos p:tile.data().cells(ROOT,facing)){require(CockpitAssembly.root(world,p)==tile,"anchor resolution "+id+facing);for(AxisAlignedBB b:CockpitAssembly.boxes(world,p))require(b.minX>=-1e-8 && b.maxX<=1+1e-8 && b.minY>=-1e-8 && b.maxY<=1+1e-8 && b.minZ>=-1e-8 && b.maxZ<=1+1e-8,"clipped collision");}
                if(id.equals("cockpit")){
                    require(tile.display("touch_center_keyboard")!=null && tile.control("touch_center_keyboard/2")!=null,"new center keyboard");
                    AxisAlignedBB footwell=new AxisAlignedBB(.60,.25,-.20,.61,.35,-.19);
                    for(AxisAlignedBB b:tile.data().boxes)require(!b.intersects(footwell),"angled console must clear footwell");
                }
                for(CockpitData.Surface surface:tile.data().surfaces)if("touch".equals(surface.role))require(Math.abs(surface.u.lengthVector()/surface.v.lengthVector()-2)<1e-6,"half-height keyboard aspect "+id+surface.id);
                if(id.equals("cockpit")){require(tile.display("touch_left_aux")==null&&tile.display("touch_right_trackpad")==null,"retired armrest touch areas removed");for(Vec3d pivot:tile.data().pivots.values())require(pivot.z<.5,"throttle assemblies moved forward");}
                for(CockpitData.Surface surface:tile.data().surfaces)if("touch".equals(surface.role)){TileEntityCockpit.Display blank=tile.display(surface.id);int previous=blank.texture;blank.texture=-1;Vec3d p=surface.point(.5,.5);require(!tile.use(CockpitData.rotate(p.add(surface.normal.scale(.4)),facing),CockpitData.rotate(p.subtract(surface.normal.scale(.3)),facing)),"blank touch area remains inactive");blank.texture=previous;}
                int channel=62000;
                for(TileEntityCockpit.Control c:tile.controls){c.setRedstoneChannels(ChannelList.of(channel++));c.kind=1;for(int step=0;step<4;step++){c.activate(step/3D);require(RedstoneChannels.level(world,c.getRedstoneChannel())==step*5,"independent detents "+c.id);}c.kind=2;c.activate(.5);require(c.level==15,"pulse starts");tile.expire(c.until);require(c.level==0 && RedstoneChannels.level(world,c.getRedstoneChannel())==0,"pulse releases");}
                for(CockpitData.Surface surface:tile.data().surfaces){TileEntityCockpit.Display display=tile.display(surface.id);display.texture=surfaces%ScreenHousingTextures.IDS.length;display.speed=3;surfaces++;
                    if("touch".equals(surface.role)){TileEntityCockpit.Control c=tile.control(surface.id+"/1");c.kind=0;c.level=0;Vec3d p=surface.point(.5,.5);require(tile.use(CockpitData.rotate(p.add(surface.normal.scale(.4)),facing),CockpitData.rotate(p.subtract(surface.normal.scale(.3)),facing)),"touch ray "+id+surface.id);require(c.level==15,"touch row "+id+surface.id);}
                }
                TileEntityCockpit copy=(TileEntityCockpit)TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));require(copy.design().equals(id) && copy.displays.size()==tile.displays.size() && copy.controls.size()==tile.controls.size(),"saved surface identities");require(copy.sideTexture()==14,"casing material saved independently");
                for(int i=0;i<copy.displays.size();i++)require(copy.displays.get(i).texture==tile.displays.get(i).texture && copy.displays.get(i).speed==3,"independent screen persistence");
                // Older kits used a different surface order and lacked the center keyboard.
                NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());
                net.minecraft.nbt.NBTTagList oldScreens=new net.minecraft.nbt.NBTTagList();
                for(int i=tile.displays.size()-1;i>=0;i--)if(!tile.displays.get(i).id.equals("touch_center_keyboard"))oldScreens.appendTag(tile.displays.get(i).write());
                legacy.setTag("CockpitDisplays",oldScreens);copy.readFromNBT(legacy);
                for(TileEntityCockpit.Display d:tile.displays)if(!d.id.equals("touch_center_keyboard"))require(copy.display(d.id).texture==d.texture,"legacy display identity "+d.id);
                ItemStack picked=block.getPickBlock(world.getBlockState(ROOT),null,world,ROOT,player);require(picked.getSubCompound("BlockEntityTag").getString("CockpitDesign").equals(id),"configured item");
                com.vandorlabs.container.ContainerCockpit dialog=new com.vandorlabs.container.ContainerCockpit(tile,null);
                require(dialog.canInteractWith(player),"fresh reachable dialog");
                if(!tile.displays.isEmpty()){
                    NBTTagCompound settings=tile.displays.get(0).write();settings.setInteger("Display",0);settings.setInteger("Speed",5);dialog.update(player,0,settings);
                    require(tile.displays.get(0).speed==5,"reachable display edit applied");
                    NBTTagCompound invalid=settings.copy();invalid.setInteger("Display",tile.displays.size());dialog.update(player,0,invalid);
                    Vec3d near=player.getPositionVector();player.setPositionAndUpdate(ROOT.getX()+20,ROOT.getY(),ROOT.getZ());require(!dialog.canInteractWith(player),"distant dialog rejected");
                    NBTTagCompound snapshot=tile.writeToNBT(new NBTTagCompound());dialog.update(player,0,settings);require(snapshot.equals(tile.writeToNBT(new NBTTagCompound())),"distant edit mutated cockpit");player.setPositionAndUpdate(near.x,near.y,near.z);
                }
                BlockPos ghost=null;for(BlockPos p:tile.data().cells(ROOT,facing))if(!p.equals(ROOT)){ghost=p;break;}
                world.destroyBlock(ghost==null?ROOT:ghost,false);require(world.isAirBlock(ROOT),"part removes anchor");for(BlockPos p:tile.data().cells(ROOT,facing))require(world.isAirBlock(p),"reservation cleanup");
                require(!dialog.canInteractWith(player),"stale dialog rejected after removal");
                require(!tile.hasLiveBlock(),"removed tile must not render");tile.facing();tile.getRenderBoundingBox();
                copy.setWorld(world);copy.setPos(ROOT);copy.validate();
                for(net.minecraft.block.state.IBlockState replacement:new net.minecraft.block.state.IBlockState[]{Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState(),block.getDefaultState()}){
                    world.setBlockState(ROOT,replacement);require(!copy.hasLiveBlock(),"detached replacement tile must not render");copy.facing();copy.getRenderBoundingBox();
                }
                world.setBlockToAir(ROOT);
            }
            roundTrip(world,player);
        }finally{clear(world);player.setPositionAndUpdate(before.x,before.y,before.z);}
        System.out.println("[vandorlabs][reprolab] cockpit-runtime PASS three models four facings reservations clipped-collision independent-surfaces touch detents pulses save configured-items stale-render-bounds vehicle-round-trip");
    }
    private static void roundTrip(WorldServer world,EntityPlayerMP player)throws Exception{
        EntityGroundVehicle craft=null;net.minecraft.entity.passive.EntityVillager passenger=null;BlockPos base=ROOT.add(-1,-2,-2);
        try{
            for(int x=-1;x<5;x++)for(int z=-1;z<5;z++)world.setBlockState(base.add(x,-2,z),Blocks.STONE.getDefaultState());
            for(int x=0;x<4;x++)for(int z=0;z<4;z++)world.setBlockState(base.add(x,1,z),Blocks.STONE.getDefaultState());
            BlockTelescopicLandingGear gear=(BlockTelescopicLandingGear)Block.getBlockFromName("vandorlabs:landing_strut");
            for(BlockPos p:new BlockPos[]{base,base.east(3),base.south(3)}){world.setBlockState(p,gear.getDefaultState());TileEntityLandingGear t=(TileEntityLandingGear)world.getTileEntity(p);t.configure(0,0,16,0);require(gear.setExtended(world,p,true),"vehicle supports");t.progress=t.previous=1;}
            world.setBlockState(ROOT,Block.getBlockFromName("vandorlabs:cockpit").getDefaultState());CockpitAssembly.install(world,ROOT,player);TileEntityCockpit cockpit=(TileEntityCockpit)world.getTileEntity(ROOT);cockpit.setTaxiMode(false);
            TileEntityCockpit.Control output=cockpit.controls.get(0);output.kind=1;output.setRedstoneChannels(ChannelList.of(63000));output.activate(2/3D);
            world.setBlockState(base.add(0,2,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState());for(int x=1;x<4;x++)world.setBlockState(base.add(x,2,0),Block.getBlockFromName("vandorlabs:antigravity_plate").getDefaultState());
            BlockPos passengerChair=base.add(3,2,3);world.setBlockState(passengerChair,ModBlocks.PROGRAMMABLE_CHAIR.getDefaultState());ModBlocks.PROGRAMMABLE_CHAIR.onBlockPlacedBy(world,passengerChair,world.getBlockState(passengerChair),player,new ItemStack(ModBlocks.PROGRAMMABLE_CHAIR));
            TileEntityProgrammableChair chairSettings=(TileEntityProgrammableChair)world.getTileEntity(passengerChair);chairSettings.setStyle(4);chairSettings.setHeight(2);
            passenger=new net.minecraft.entity.passive.EntityVillager(world);passenger.setNoAI(true);passenger.setPosition(passengerChair.getX()+.5,passengerChair.getY()+1,passengerChair.getZ()+.5);require(world.spawnEntity(passenger),"passenger fixture spawn");
            com.vandorlabs.entity.EntityChairSeat stationary=new com.vandorlabs.entity.EntityChairSeat(world,passengerChair,VehicleSeats.height(world,passengerChair));require(world.spawnEntity(stationary)&&passenger.startRiding(stationary),"occupied Programmable Chair fixture");
            VehicleScan scan=new VehicleScan(world,ROOT);for(int i=0;i<200 && !scan.step(128);i++);require(scan.error==null && scan.result!=null,"cockpit discovery "+scan.error);require(scan.result.controller()!=null && scan.result.controller().state.getBlock() instanceof BlockCockpit,"combined controller");
            player.setPositionAndUpdate(ROOT.getX()+.5,ROOT.getY()+1,ROOT.getZ()+2);craft=VehicleTransfer.assemble(world,scan.result,scan.origin,player);craft.signals.tick();require(craft.hover,"direct Hover assembly");
            require(passenger.getRidingEntity()==craft&&craft.seatForPassenger(passenger).equals(passengerChair.subtract(scan.origin)),"Programmable Chair passenger transferred to vehicle");
            TileEntityCockpit captured=(TileEntityCockpit)craft.view.getTileEntity(ROOT.subtract(scan.origin));require(captured!=null && captured.controls.get(0).level==10,"captured controls");captured.controls.get(0).activate(1);craft.signals.cockpitChanged(captured);require(RedstoneChannels.level(world,63000)==15,"captured channel output");
            craft.setPosition(craft.posX+1,craft.posY,craft.posZ+1);craft.rotationYaw=90;craft.updatePassenger(passenger);
            Vec3d expectedPassenger=craft.seatPosition(new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw),passenger);require(passenger.getPositionVector().squareDistanceTo(expectedPassenger)<1e-8,"passenger follows vehicle translation and turn");
            craft.rotationYaw=0;craft.setPosition(scan.origin.getX(),scan.origin.getY(),scan.origin.getZ());
            VehicleTransfer.park(world,craft,scan.origin,player);craft=null;require(passenger.getRidingEntity() instanceof com.vandorlabs.entity.EntityChairSeat&&((com.vandorlabs.entity.EntityChairSeat)passenger.getRidingEntity()).getChairPos().equals(passengerChair),"parking restores occupied Programmable Chair");require(((TileEntityCockpit)world.getTileEntity(ROOT)).controls.get(0).level==15,"parked controls");
        }finally{if(craft!=null && !craft.isDead)VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);if(passenger!=null){passenger.dismountRidingEntity();world.removeEntityDangerously(passenger);}clear(world);}
    }
    private static void clear(WorldServer world){for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-3,-4,-4),ROOT.add(5,4,4)))world.setBlockToAir(p);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("cockpit: "+message);}
}
