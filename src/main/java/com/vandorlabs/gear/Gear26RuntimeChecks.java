package com.vandorlabs.gear;

import com.vandorlabs.gear.core.*;
import com.vandorlabs.gear.track.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Actual server fixtures for unmounted columns, arbitrary mount axes and retained track styles. */
public final class Gear26RuntimeChecks {
    private static int checks;
    private Gear26RuntimeChecks(){}
    private static void require(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException("gear26: "+message);}
    private static void clear(WorldServer world,BlockPos root){for(BlockPos p:BlockPos.getAllInBox(root.add(-12,-12,-12),root.add(12,12,12)))if(!world.isAirBlock(p)){world.removeTileEntity(p);world.setBlockToAir(p);}}
    private static GearTileEntity arm(WorldServer world,EntityPlayerMP actor,BlockPos root,GearBlock block,EnumFacing face,int length){
        for(int n=0;n<length;n++){BlockPos at=root.offset(face,n);require(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,at,face,.5F,.5F,.5F,block.getDefaultState()),"item placement on "+face);}
        GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);require(tile.rebuild(),"bare arm forms "+tile.error());return tile;
    }
    public static void run(WorldServer world,EntityPlayerMP actor){
        BlockPos root=new BlockPos(620,170,260);checks=0;
        try{
            inputTransition(world,actor,root);
            for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.GEAR_MOUNT,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.LARGE_GEAR_SWING_ARM})for(EnumFacing face:EnumFacing.values())for(int count:new int[]{1,2,8}){
                clear(world,root);GearTileEntity tile=arm(world,actor,root,block,face,count);
                require(tile.mountDirection()==face&&tile.assembly().columnHeight()==count&&!tile.isLandingAssembly(),"complete unsupported column has its own axis and no false landing support");
                require(tile.members().size()==count,"every arm belongs to the same mechanism");
                for(BlockPos p:tile.members())require(((GearTileEntity)world.getTileEntity(p)).loadedController()==tile,"one controller for unsupported arm column");
                require(!tile.collisionBoxes().isEmpty(),"oriented collision is emitted");
                GearTileEntity copy=(GearTileEntity)TileEntity.create(world,tile.writeToNBT(new NBTTagCompound()));require(copy!=null&&copy.mountDirection()==face&&copy.assembly().columnHeight()==count,"axis and topology survive NBT");
            }
            for(EnumFacing face:EnumFacing.values()){
                clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,face,2);BlockPos source=root.offset(face,2);world.setBlockState(source,Blocks.STONE.getDefaultState());
                NBTTagCompound settings=tile.settings();settings.setDouble("ActiveExtension",1);settings.setBoolean("Deployed",false);tile.configure(settings);
                for(int tick=0;tick<50;tick++)tile.update();require(tile.progress()==0,"attachment extends through full stroke "+face+" "+tile.error());
                require(world.isAirBlock(source)&&world.getBlockState(source.offset(face)).getBlock()==Blocks.STONE,"integral endpoint restores actual block "+face);
                tile.target(true);for(int tick=0;tick<50;tick++)tile.update();require(tile.progress()==1&&world.getBlockState(source).getBlock()==Blocks.STONE&&world.isAirBlock(source.offset(face)),"reverse restores construction block without duplication "+face);
                settings=tile.settings();settings.setDouble("ActiveExtension",.5);settings.setBoolean("Deployed",false);tile.configure(settings);for(int tick=0;tick<50;tick++)tile.update();
                require(tile.progress()==0&&tile.payload()!=null&&!tile.payload().materialized()&&world.isAirBlock(source),"half-block endpoint retains captured attachment "+face);
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());GearTileEntity copy=(GearTileEntity)TileEntity.create(world,saved);require(copy.payload()!=null&&copy.payload().blocks.cells.size()==1,"fractional attachment survives save "+face);
                tile.target(true);for(int tick=0;tick<50;tick++)tile.update();require(world.getBlockState(source).getBlock()==Blocks.STONE,"captured attachment restores after reversal "+face);
            }
            clear(world,root);GearTileEntity swing=arm(world,actor,root,GearRegistry.GEAR_SWING_ARM,EnumFacing.DOWN,1);world.setBlockState(root.down(),Blocks.STONE.getDefaultState());
            NBTTagCompound setting=swing.settings();setting.setDouble("ActiveAngle",45);setting.setBoolean("RotatePayload",true);setting.setBoolean("Deployed",false);swing.configure(setting);for(int tick=0;tick<50;tick++)swing.update();
            require(swing.progress()==0&&swing.mode()==GearMotion.Mode.ROTATE&&swing.pose(0).angleDegrees()==45&&swing.payload()!=null,"configured rotating attachment reaches 45-degree captured endpoint");
            setting=swing.settings();setting.setDouble("ActiveAngle",Double.NaN);boolean denied=false;try{swing.configure(setting);}catch(IllegalArgumentException expected){denied=true;}require(denied,"reject nonfinite arm settings");swing.target(true);for(int tick=0;tick<50;tick++)swing.update();
            for(int style=0;style<3;style++){
                clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);
                TrackBlock track=TrackRegistry.BLOCKS[style];world.setBlockState(root.down(2),track.getDefaultState());world.setBlockState(root.down(2).south(),track.getDefaultState());TrackTile first=(TrackTile)world.getTileEntity(root.down(2));TrackWorld.rebuild(first);
                require(first.hasCompleteAssembly()&&first.trackMembers().size()==2,"native track connected topology "+style);
                require(tile.rebuild()&&tile.isLandingAssembly()&&tile.assembly().kind()==GearTopology.FootKind.TRACK,"tracks attach as landing feet "+style);
                require(tile.assembly().feet().size()==2,"entire connected tread retracts together");tile.target(false);for(int tick=0;tick<50;tick++)tile.update();require(tile.progress()==0,"native track arm retracts "+tile.error());tile.target(true);for(int tick=0;tick<50;tick++)tile.update();
                NBTTagCompound values=tile.settings();values.setInteger("TrackStyle",(style+1)%3);tile.configure(values);require(TrackStyles.style(tile)==(style+1)%3&&tile.rebuild(),"dialog style switches preserve ownership and topology");
                world.setBlockState(root.down(2).east(),TrackRegistry.BLOCKS[(style+2)%3].getDefaultState());require(tile.rebuild()&&tile.assembly().feet().size()==2,"different adjacent track style remains a separate arm component");
            }
            transactions(world,actor,root);
            topologyRecovery(world,actor,root);
            recovery(world,actor,root);
            payloadBounds(world,actor,root);
            carried(world,actor,root);
            clear(world,root);world.setBlockState(root,TrackRegistry.COMPACT.getDefaultState());world.setBlockState(root.east(),Blocks.STONE_SLAB.getDefaultState().withProperty(net.minecraft.block.BlockSlab.HALF,net.minecraft.block.BlockSlab.EnumBlockHalf.TOP));TrackTile track=(TrackTile)world.getTileEntity(root);TrackWorld.rebuild(track);require((track.appearance().sideMask&2)!=0,"top slab selects native side bracket");
        }finally{clear(world,root);}
        System.out.println("[vandorlabs][reprolab] gear26-runtime PASS checks="+checks+" six-axis unsupported columns integral/fractional payloads reversal snapshots tracks and side brackets");
    }
    private static void inputTransition(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);world.setBlockState(root,GearRegistry.GEAR_EXTENDER.getDefaultState());GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);tile.operator(actor);
        NBTTagCompound settings=tile.settings();settings.setInteger("Trigger",1);tile.configure(settings);require(!tile.target(),"unformed tile retains legacy unpowered deployment input");require(tile.rebuild(),"configured bare arm forms");tile.update();require(tile.target()&&tile.progress()==1,"forming generic topology resamples its construction-pose input");
        world.setBlockState(root.down(),GearRegistry.LANDING_PLATE.getDefaultState());require(tile.rebuild(),"generic arm becomes landing support");tile.update();require(!tile.target(),"landing topology resamples legacy deployment input after generic operation");
        System.out.println("[vandorlabs][reprolab] gear26-input-transition PASS legacy-unformed generic-construction-pose landing-deployment edge-resampling");
    }
    private static void topologyRecovery(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);world.setBlockState(root.down(2),Blocks.CHEST.getDefaultState());((net.minecraft.tileentity.TileEntityChest)world.getTileEntity(root.down(2))).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.GOLD_INGOT,6));
        NBTTagCompound values=tile.settings();values.setDouble("ActiveExtension",.5);values.setBoolean("Deployed",false);tile.configure(values);for(int tick=0;tick<50;tick++)tile.update();NBTTagCompound retained=tile.payload().write();world.setBlockState(root.down(2),Blocks.OBSIDIAN.getDefaultState());world.destroyBlock(root.down(),false);
        require(!tile.isOperational()&&tile.isController()&&tile.payload()!=null&&retained.equals(tile.payload().write()),"blocked release keeps old controller and exact retained snapshot");
        world.setBlockState(root.up(),GearRegistry.GEAR_EXTENDER.getDefaultState(),2);GearTileEntity above=(GearTileEntity)world.getTileEntity(root.up());require(!above.rebuild()&&tile.isController()&&tile.payload()!=null&&retained.equals(tile.payload().write()),"new upper arm cannot hide retained controller as internal member");
        world.removeTileEntity(root.up());world.setBlockToAir(root.up());world.setBlockToAir(root.down(2));tile.target(true);for(int tick=0;tick<80;tick++)tile.update();TileEntity chest=world.getTileEntity(root.down(2));require(chest instanceof net.minecraft.tileentity.TileEntityChest&&((net.minecraft.tileentity.TileEntityChest)chest).getStackInSlot(0).getCount()==6,"inert mechanism restores exact contents after clearing construction position and selecting Off");
        System.out.println("[vandorlabs][reprolab] gear26-topology-recovery PASS blocked-restore retained-controller no-hidden-snapshot safe-Off-recovery");
    }
    private static void recovery(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);world.setBlockState(root.down(2),Blocks.CHEST.getDefaultState());((net.minecraft.tileentity.TileEntityChest)world.getTileEntity(root.down(2))).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,5));
        NBTTagCompound values=tile.settings();values.setDouble("ActiveExtension",.5);values.setBoolean("Deployed",false);tile.configure(values);for(int tick=0;tick<50;tick++)tile.update();require(tile.payload()!=null&&!tile.payload().materialized(),"recovery fixture has retained contents");
        world.destroyBlock(root,false);net.minecraft.entity.item.EntityItem recovery=null;for(net.minecraft.entity.item.EntityItem item:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new AxisAlignedBB(root).grow(2)))if(item.getItem().hasTagCompound()&&item.getItem().getTagCompound().hasKey("ArmRecovery",10)){require(recovery==null,"controller produces one recovery item");recovery=item;}
        require(recovery!=null&&world.isAirBlock(root.down(2)),"breaking controller retains captured inventory in recovery arm");
        ItemStack item=recovery.getItem().copy();recovery.setDead();BlockPos target=root.east(8);require(((ItemBlock)item.getItem()).placeBlockAt(item,actor,world,target,EnumFacing.DOWN,.5F,.5F,.5F,GearRegistry.GEAR_EXTENDER.getDefaultState()),"recovery item placement");GearTileEntity restored=(GearTileEntity)world.getTileEntity(target);restored.target(true);for(int tick=0;tick<80;tick++)restored.update();
        TileEntity raw=world.getTileEntity(target.down(2));require(raw instanceof net.minecraft.tileentity.TileEntityChest&&((net.minecraft.tileentity.TileEntityChest)raw).getStackInSlot(0).getCount()==5,"placed recovery arm returns exact retained inventory");
        System.out.println("[vandorlabs][reprolab] gear26-recovery PASS fractional-controller-break one-recovery-item retained-inventory replacement-restore");
    }
    private static void payloadBounds(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);
        for(int x=0;x<16;x++)world.setBlockState(root.add(x,-2,0),Blocks.STONE.getDefaultState());
        NBTTagCompound values=tile.settings();values.setDouble("ActiveExtension",.5);values.setBoolean("Deployed",false);tile.configure(values);for(int tick=0;tick<50;tick++)tile.update();
        require(tile.progress()==0&&tile.payload()!=null,"wide attachment reaches retained half-block endpoint");
        for(double progress:new double[]{0,.25,.5,.75,1})for(AxisAlignedBB box:tile.payload().boxes(tile,progress))require(tile.getRenderBoundingBox().grow(1e-6).contains(new Vec3d(box.minX,box.minY,box.minZ))&&tile.getRenderBoundingBox().grow(1e-6).contains(new Vec3d(box.maxX,box.maxY,box.maxZ)),"render and spatial bounds contain entire payload stroke");
        AxisAlignedBB far=tile.payload().boxes(tile,0).get(15).shrink(.1);require(!world.getCollisionBoxes(null,far).isEmpty(),"held attachment collides across chunk boundary beyond old arm radius");
        tile.target(true);for(int tick=0;tick<50;tick++)tile.update();for(int x=0;x<16;x++)world.setBlockToAir(root.add(x,-2,0));
        System.out.println("[vandorlabs][reprolab] gear26-payload-bounds PASS 16-block-wide sweep render-culling spatial-index cross-chunk-collision");
    }
    private static void carried(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity source=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);BlockPos chestPos=root.down(2);world.setBlockState(chestPos,Blocks.CHEST.getDefaultState());
        ((net.minecraft.tileentity.TileEntityChest)world.getTileEntity(chestPos)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.EMERALD,7));ArmPayload payload=ArmPayload.discover(source,actor);
        BlockPos local=new BlockPos(3,3,3),shift=local.subtract(root),attachment=local.down(2);java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(new BlockPos(0,0,1),com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
        for(BlockPos cell:new BlockPos[]{root,root.down(),chestPos}){TileEntity tile=world.getTileEntity(cell);NBTTagCompound tag=tile==null?null:tile.writeToNBT(new NBTTagCompound());if(cell.equals(root))tag.setTag("ArmPayload",payload.write());cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(cell.add(shift),world.getBlockState(cell),tag==null?null:com.vandorlabs.vehicle.VehicleTileData.translated(tag,shift)));}
        com.vandorlabs.vehicle.EntityGroundVehicle craft=new com.vandorlabs.vehicle.EntityGroundVehicle(world);
        try{
            craft.install(new com.vandorlabs.vehicle.VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,3));craft.setPosition(root.getX()-3,root.getY()+12,root.getZ()-3);craft.signals.open();
            ((net.minecraft.tileentity.TileEntityChest)craft.view.getTileEntity(attachment)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,9));
            GearTileEntity arm=(GearTileEntity)craft.view.getTileEntity(local);NBTTagCompound values=arm.settings();values.setDouble("ActiveExtension",1);values.setBoolean("Deployed",false);arm.configure(values);
            for(int tick=0;tick<250;tick++){com.vandorlabs.vehicle.ModularGearVehicles.tick(craft);arm=(GearTileEntity)craft.view.getTileEntity(local);if(arm!=null&&arm.progress()==0)break;try{Thread.sleep(1);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}
            arm=(GearTileEntity)craft.view.getTileEntity(local);require(arm!=null&&arm.progress()==0&&!craft.hover,"generic payload remains operable in Taxi");require(craft.structure.at(attachment)==null&&arm.payload()!=null&&!arm.payload().materialized(),"ordinary carried cells become one nested snapshot before movement");
            net.minecraft.tileentity.TileEntityChest nested=(net.minecraft.tileentity.TileEntityChest)arm.payload().view(arm).tiles.get(BlockPos.ORIGIN);require(nested!=null&&nested.getStackInSlot(0).getItem()==net.minecraft.init.Items.DIAMOND&&nested.getStackInSlot(0).getCount()==9,"carried capture adopts edited materialized inventory instead of stale snapshot");
            NBTTagCompound original=arm.writeToNBT(new NBTTagCompound()),rotated=original.copy();for(int t=0;t<4;t++)GearData.rotate(rotated,1,world);require(original.equals(rotated),"nested payload and six-axis basis rotate through four parking headings without loss");
        }finally{craft.signals.close();craft.setDead();clear(world,root);}
        System.out.println("[vandorlabs][reprolab] gear26-carried PASS Taxi-animation atomic-nested-capture changed-inventory retained-snapshot four-parking-headings");
    }
    private static void transactions(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);BlockPos source=root.down(2);
        world.setBlockState(source,Blocks.CHEST.getDefaultState());world.setBlockState(source.east(),Blocks.STONE.getDefaultState());world.setBlockState(source.east(2),Blocks.DROPPER.getDefaultState());((net.minecraft.tileentity.TileEntityDropper)world.getTileEntity(source.east(2))).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.IRON_INGOT,11));
        net.minecraft.tileentity.TileEntityChest chest=(net.minecraft.tileentity.TileEntityChest)world.getTileEntity(source);chest.setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,3));
        ArmPayload payload=ArmPayload.discover(tile,actor);NBTTagCompound original=payload.write();final int[] count={0};
        ArmPayload.failureInjector=(stage,at)->{if(stage.equals("clear")&&++count[0]==2){ArmPayload.failureInjector=null;throw new IllegalStateException("injected clear failure");}};
        boolean rejected=false;try{payload.detach(tile);}catch(IllegalStateException expected){rejected=true;}finally{ArmPayload.failureInjector=null;}
        require(rejected&&payload.materialized()&&payload.matchesPlaced(tile),"second-cell clear failure restores every current cell and tile");require(original.equals(payload.write()),"detach rollback retains exact snapshot and materialization state");
        payload.detach(tile);NBTTagCompound captured=payload.write();count[0]=0;
        ArmPayload.failureInjector=(stage,at)->{if(stage.equals("install")&&++count[0]==2){ArmPayload.failureInjector=null;throw new IllegalStateException("injected install failure");}};
        rejected=false;try{payload.restore(tile,1);}catch(IllegalStateException expected){rejected=true;}finally{ArmPayload.failureInjector=null;}
        require(rejected&&!payload.materialized()&&world.isAirBlock(source)&&world.isAirBlock(source.east()),"second-cell restore failure clears all partial target cells");require(captured.equals(payload.write()),"failed restore retains exact captured snapshot");
        ArmPayload.failureInjector=(stage,at)->{if(stage.equals("prepare-tile")){ArmPayload.failureInjector=null;throw new IllegalStateException("injected tile factory failure");}};
        rejected=false;try{payload.restore(tile,1);}catch(IllegalStateException expected){rejected=true;}finally{ArmPayload.failureInjector=null;}
        require(rejected&&world.isAirBlock(source)&&world.isAirBlock(source.east())&&captured.equals(payload.write()),"tile factory failure occurs before world mutation and retains exact snapshot");
        require(payload.restore(tile,1),"snapshot restores after all injected failures");
        count[0]=0;ArmPayload.failureInjector=(stage,at)->{if(stage.equals("clear")&&++count[0]==2)throw new IllegalStateException("injected clear for rollback failure");if(stage.equals("install"))throw new IllegalStateException("injected rollback failure");};
        rejected=false;try{payload.detach(tile);}catch(IllegalStateException expected){rejected=true;}finally{ArmPayload.failureInjector=null;}
        require(rejected&&payload.hasOwnershipJournal()&&!payload.materialized()&&payload.ownershipJournalSize()==3,"failed rollback journals copied and unvisited source inventory cells");require(!payload.restore(tile,1),"journal prevents full snapshot restoration while copied cells remain");
        ArmPayload journalCopy=ArmPayload.read(payload.write());require(journalCopy.hasOwnershipJournal()&&journalCopy.write().equals(payload.write()),"ownership journal survives save and recovery relocation");require(journalCopy.reconcileOwnership(world,actor)&&world.isAirBlock(source)&&world.isAirBlock(source.east())&&world.isAirBlock(source.east(2)),"recovery removes exact original copied cells before unlocking snapshot");require(journalCopy.restore(tile,1),"journal reconciliation restores contents once");require(((net.minecraft.tileentity.TileEntityDropper)world.getTileEntity(source.east(2))).getStackInSlot(0).getCount()==11,"unvisited third-cell inventory reconciles before full snapshot restoration");payload=journalCopy;
        payload.detach(tile);count[0]=0;final int[] cleanup={0};ArmPayload.failureInjector=(stage,at)->{if(stage.equals("install")&&++count[0]==2)throw new IllegalStateException("injected restore for cleanup failure");if(stage.equals("restore-cleanup")&&++cleanup[0]==1)throw new IllegalStateException("injected cleanup failure");};
        rejected=false;try{payload.restore(tile,1);}catch(IllegalStateException expected){rejected=true;}finally{ArmPayload.failureInjector=null;}
        require(rejected&&payload.hasOwnershipJournal()&&!payload.restore(tile,1),"failed target cleanup remains locked with journal instead of duplicating live inventory");require(payload.reconcileOwnership(world,actor)&&payload.restore(tile,1),"partial target cleanup reconciles and restores exactly once");
        chest=(net.minecraft.tileentity.TileEntityChest)world.getTileEntity(source);require(chest.getStackInSlot(0).getCount()==3,"diamond inventory restored exactly once");chest.setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.EMERALD,7));
        payload=payload.refreshPlaced(tile,actor);payload.detach(tile);require(payload.restore(tile,1),"edited materialized inventory recaptured");chest=(net.minecraft.tileentity.TileEntityChest)world.getTileEntity(source);require(chest.getStackInSlot(0).getItem()==net.minecraft.init.Items.EMERALD&&chest.getStackInSlot(0).getCount()==7,"edited inventory replaces stale snapshot without duplicating old contents");
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());NBTTagCompound invalid=payload.capturedCopy().write();net.minecraft.nbt.NBTTagList cells=invalid.getCompoundTag("Blocks").getTagList("Blocks",10);for(int n=0;n<cells.tagCount();n++)if(cells.getCompoundTagAt(n).hasKey("Tile",10))cells.getCompoundTagAt(n).getCompoundTag("Tile").setString("id","vandorlabs:missing_test_tile");saved.setTag("ArmPayload",invalid);
        GearTileEntity inert=(GearTileEntity)TileEntity.create(world,saved);require(inert!=null&&!inert.isOperational()&&inert.payload()==null&&inert.collisionBoxes().isEmpty(),"missing attachment tile quarantines controller without a partial live view");require(inert.writeToNBT(new NBTTagCompound()).getCompoundTag("ArmPayload").equals(invalid),"failed deserialize retains original attachment NBT verbatim");
        System.out.println("[vandorlabs][reprolab] gear26-transactions PASS injected-clear-install-tile-factory rollback-failure-ownership-journal changed-inventory no-duplication inert-deserialize original-NBT");
    }

}
