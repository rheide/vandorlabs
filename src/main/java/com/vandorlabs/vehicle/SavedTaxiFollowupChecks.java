package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.TileEntityCockpit;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Compact, unchanged native block/NBT excerpts from the reported saved layouts. */
public final class SavedTaxiFollowupChecks {
    private SavedTaxiFollowupChecks(){}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Saved Taxi followup: "+why);}
    private static NBTTagCompound load(WorldServer world,String name)throws Exception{
        NBTTagCompound fixture;
        try(InputStream in=SavedTaxiFollowupChecks.class.getResourceAsStream("/assets/vandorlabs/fixtures/followups3-"+name+".nbt")){
            require(in!=null,"saved fixture exists");fixture=CompressedStreamTools.readCompressed(in);
        }
        NBTTagList cells=fixture.getTagList("Cells",10);
        int minX=30000000,minY=256,minZ=30000000,maxX=-30000000,maxY=0,maxZ=-30000000;
        for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);int x=cell.getInteger("X"),y=cell.getInteger("Y"),z=cell.getInteger("Z");minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(minX-2,minY,maxZ+2),new BlockPos(maxX+2,maxY+3,minZ-2))){world.getChunkFromBlockCoords(at);world.setBlockToAir(at);}
        // Install the entire palette before loading any native tile's saved topology.
        for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"saved registry "+cell.getString("Name"));world.setBlockState(position(cell),block.getStateFromMeta(cell.getInteger("Meta")),2);}
        for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);if(cell.hasKey("Tile",10)){TileEntity tile=TileEntity.create(world,cell.getCompoundTag("Tile").copy());require(tile!=null,"saved tile factory");world.setTileEntity(position(cell),tile);}}
        for(EntityPainting old:world.getEntitiesWithinAABB(EntityPainting.class,new AxisAlignedBB(minX,minY,minZ,maxX+1,maxY+4,maxZ+1)))world.removeEntityDangerously(old);
        NBTTagList entities=fixture.getTagList("Entities",10);
        for(int n=0;n<entities.tagCount();n++){EntityPainting painting=new EntityPainting(world);painting.readFromNBT(entities.getCompoundTagAt(n).copy());require(painting.onValidSurface()&&world.spawnEntity(painting),"exact saved native painting support");}
        return fixture;
    }
    private static BlockPos position(NBTTagCompound cell){return new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));}
    /** Invoke the same inspected-screen job action as VehicleService, including raw geometry preflight. */
    private static EntityGroundVehicle assemble(WorldServer world,EntityPlayerMP actor,BlockPos controller)throws Exception{
        return assemble(world,actor,controller,null);
    }
    private static EntityGroundVehicle assemble(WorldServer world,EntityPlayerMP actor,BlockPos controller,Runnable change)throws Exception{
        actor.setPosition(controller.getX()+3.5,controller.getY()+3,controller.getZ()+3.5);
        Class<?> type=Class.forName("com.vandorlabs.vehicle.VehicleService$Job");Constructor<?> constructor=type.getDeclaredConstructor(EntityPlayerMP.class,BlockPos.class);constructor.setAccessible(true);Object job=constructor.newInstance(actor,controller);
        Field scanField=type.getDeclaredField("scan");scanField.setAccessible(true);VehicleScan scan=(VehicleScan)scanField.get(job);
        while(!scan.step(256)){}require(scan.error==null&&scan.result!=null,"exact saved scan: "+scan.error);
        for(String field:new String[]{"signal","screen"}){Field value=type.getDeclaredField(field);value.setAccessible(true);value.setBoolean(job,true);}
        Field mode=type.getDeclaredField("requestedMode");mode.setAccessible(true);mode.setInt(job,1);
        Method action=VehicleService.class.getDeclaredMethod("assembleSignal",type);action.setAccessible(true);Exception[] error={null};
        NBTTagCompound[] changedSource={null};boolean[] changed={false};NBTTagCompound before=scan.result.write();List<EntityPainting> paintings=VehiclePaintings.attached(world,scan.result,scan.origin);
        try{VehicleTransferRuntimeChecks.slowDisk(world,()->VehicleTransfer.submit(world,scan.result,scan.origin,()->{
            try{action.invoke(null,job);}catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw failure;}
        },failure->error[0]=failure),()->{if(change!=null&&!changed[0]){changed[0]=true;change.run();changedSource[0]=nativeSource(world,scan.result,scan.origin);}return (change!=null||scan.result.matches(world,scan.origin))&&before.equals(scan.result.write())&&world.getTileEntity(controller)!=null&&paintings.stream().allMatch(p->!p.isDead&&world.getEntityFromUuid(p.getUniqueID())==p);});}catch(Exception preparation){throw new IllegalStateException("Queued player service preparation failed: "+(error[0]==null?preparation:error[0]),error[0]==null?preparation:error[0]);}
        if(change!=null){require(changed[0]&&error[0]!=null,"queued source change must refuse");require(world.getTileEntity(controller)!=null&&paintings.stream().allMatch(p->world.getEntityFromUuid(p.getUniqueID())==p&&!p.isDead),"source blocks and painting retain ownership after refusal");require(changedSource[0].equals(nativeSource(world,scan.result,scan.origin)),"refusal preserves every native block, own-tile field, opaque capability and edited painting");require(!VehicleTransfer.pending(world),"refused transfer retires its unused ownership journal");return null;}
        require(error[0]==null,"queued player service assembly: "+error[0]);
        for(Entity entity:world.loadedEntityList)if(entity instanceof EntityGroundVehicle&&((EntityGroundVehicle)entity).structure!=null&&Math.abs(entity.posX-scan.origin.getX())<.01&&Math.abs(entity.posZ-scan.origin.getZ())<.01)return (EntityGroundVehicle)entity;
        throw new IllegalStateException("Saved Taxi service did not publish its vehicle");
    }
    private static NBTTagCompound nativeSource(WorldServer world,VehicleStructure source,BlockPos origin){
        NBTTagCompound snapshot=new NBTTagCompound();NBTTagList cells=new NBTTagList();
        for(VehicleStructure.Cell cell:source.cells){BlockPos pos=origin.add(cell.pos);NBTTagCompound data=new NBTTagCompound();net.minecraft.block.state.IBlockState state=world.getBlockState(pos);data.setString("Block",state.getBlock().getRegistryName().toString());data.setInteger("Meta",state.getBlock().getMetaFromState(state));TileEntity tile=world.getTileEntity(pos);if(tile!=null)data.setTag("Tile",tile.writeToNBT(new NBTTagCompound()));cells.appendTag(data);}
        snapshot.setTag("Blocks",cells);snapshot.setTag("Paintings",VehiclePaintings.entries(VehiclePaintings.capture(source,origin,VehiclePaintings.attached(world,source,origin))));return snapshot;
    }
    private static void park(WorldServer world,EntityPlayerMP actor,EntityGroundVehicle craft)throws Exception{
        Exception[] failure={null};VehicleTransfer.submit(world,()->{VehicleRotation.Plan plan=VehicleRotation.plan(craft);VehicleTransfer.park(world,craft,plan.origin,actor,false);},error->failure[0]=error);VehicleTransferRuntimeChecks.drain(world);require(failure[0]==null,"queued native parking: "+failure[0]);
    }
    private static List<GearTileEntity> roots(EntityGroundVehicle craft){List<GearTileEntity> result=new ArrayList<>();for(TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController()&&((GearTileEntity)raw).isLandingAssembly())result.add((GearTileEntity)raw);return result;}
    private static double[] feet(EntityGroundVehicle craft){return roots(craft).stream().mapToDouble(g->{double low=Double.POSITIVE_INFINITY;for(AxisAlignedBB box:GearGeometry.boxes(g,g.progress()))low=Math.min(low,box.minY);return Math.rint((low+craft.posY)*100000)/100000;}).sorted().toArray();}
    private static TileEntityCockpit cockpit(EntityGroundVehicle craft){for(TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityCockpit)return (TileEntityCockpit)raw;throw new IllegalStateException("Saved cockpit missing");}
    private static void control(EntityGroundVehicle craft,boolean on){craft.signals.tick();TileEntityCockpit.Control control=cockpit(craft).control("screen_left/0");require(control!=null&&control.getRedstoneChannels().contains(776),"saved Gear control uses channel 776");craft.signals.activateCockpit(control,on?15:0);craft.signals.tick();for(GearTileEntity gear:roots(craft))gear.input();}
    private static void settle(EntityGroundVehicle craft,boolean expanded)throws Exception{
        for(int tick=0;tick<1000;tick++){craft.signals.tick();ModularGearVehicles.tick(craft);boolean done=true;for(GearTileEntity gear:roots(craft)){int phase=gear.taxiPhase();done&=expanded?phase<0:phase==1;}if(done)return;Thread.sleep(2);}
        StringBuilder errors=new StringBuilder();for(GearTileEntity gear:roots(craft))errors.append(gear.getPos()).append(':').append(gear.error()).append(' ');throw new IllegalStateException("Exact saved track motion timed out: "+errors);
    }
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        String selected=System.getProperty("vandorlabs.followups3Case","");double x=actor.posX,y=actor.posY,z=actor.posZ;
        try{
            if(!selected.equals("tracks")){
                NBTTagCompound fixture=load(world,"painting");UUID id=fixture.getTagList("Entities",10).getCompoundTagAt(0).getUniqueId("UUID");
                EntityGroundVehicle craft=assemble(world,actor,new BlockPos(-141,86,166));
                require(VehiclePaintings.entries(craft.structure).tagCount()==1&&world.getEntityFromUuid(id)==null,"exact painting has only carried ownership");
                NBTTagCompound saved=craft.structure.write();craft.install(VehicleStructure.read(saved));require(saved.equals(craft.structure.write()),"saved painting data and opaque caps survive reload");
                for(int n=0;n<4;n++){VehicleStructure rotated=VehicleRotation.rotate(craft.structure,craft.view,1);craft.install(rotated);}require(saved.equals(craft.structure.write()),"exact painting and native tiles survive four rotations");
                park(world,actor,craft);require(world.getEntityFromUuid(id) instanceof EntityPainting,"native painting restored exactly once");
                craft=assemble(world,actor,new BlockPos(-141,86,166));require(VehiclePaintings.entries(craft.structure).tagCount()==1&&world.getEntityFromUuid(id)==null,"exact painting recapture has one owner");park(world,actor,craft);
                load(world,"painting");assemble(world,actor,new BlockPos(-141,86,166),()->{
                    for(EntityPainting painting:world.getEntitiesWithinAABB(EntityPainting.class,new AxisAlignedBB(-146,82,153,-129,92,170))){painting.art=EntityPainting.EnumArt.AZTEC;break;}
                });
                load(world,"painting");assemble(world,actor,new BlockPos(-141,86,166),()->{
                    TileEntity tile=world.getTileEntity(new BlockPos(-141,86,166));tile.getTileData().setInteger("ChangedDuringPreparation",1);tile.markDirty();
                });
                System.out.println("[vandorlabs][reprolab] saved-taxi-painting PASS exact-native-layout queued-service raw-preflight blocked-disk unchanged-source journal park reload four-turns recapture");
            }
            if(!selected.equals("painting")){
                load(world,"tracks");EntityGroundVehicle craft=assemble(world,actor,new BlockPos(-163,85,190));
                require(roots(craft).size()==3&&roots(craft).stream().allMatch(g->g.assembly().kind()==com.vandorlabs.gear.core.GearTopology.FootKind.TRACK),"actual three TRACK assemblies");
                require(roots(craft).stream().mapToInt(g->g.assembly().feet().size()).sum()==7,"actual one/three/three track footprints");
                double initial=craft.posY;double[] foot=feet(craft);int material=craft.structure.cells.size();control(craft,true);control(craft,false);settle(craft,false);
                require(Math.abs(craft.posY-(initial-1))<1e-6&&Arrays.equals(foot,feet(craft)),"exact saved chassis descends one block with anchored tracks");
                NBTTagCompound shortSaved=craft.structure.write();craft.install(VehicleStructure.read(shortSaved));require(shortSaved.equals(craft.structure.write()),"shortened saved track section ownership reload");
                BlockPos controller=new BlockPos(craft.toWorld(new Vec3d(craft.structure.controller().pos).addVector(.5,.5,.5)));park(world,actor,craft);craft=assemble(world,actor,controller);
                control(craft,true);settle(craft,true);require(Math.abs(craft.posY-initial)<1e-6&&craft.structure.cells.size()==material&&Arrays.equals(foot,feet(craft)),"park/recapture raises exact original tracks and all retained arm material");park(world,actor,craft);
                System.out.println("[vandorlabs][reprolab] saved-taxi-tracks PASS exact-three-assemblies seven-track-cells cockpit-channel776 queued-service anchored-feet one-block-descent reload park recapture retained-material raise");
            }
        }finally{actor.setPosition(x,y,z);}
    }
}
