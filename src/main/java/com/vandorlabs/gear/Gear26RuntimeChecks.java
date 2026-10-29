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
    private static void pushBlocks(WorldServer world,EntityPlayerMP actor,BlockPos root){
        for(EnumFacing face:EnumFacing.HORIZONTALS){
            clear(world,root);world.setBlockState(root.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),2);
            GearTileEntity tile=arm(world,actor,root,GearRegistry.LARGE_GEAR_EXTENDER,face,4);tile.operator(actor);
            BlockPos home=root.offset(face),extended=root.offset(face,4),contact=root.offset(face,3).up(),target=root.offset(face,5).up();
            net.minecraft.block.Block surface=net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface");
            world.setBlockState(extended,surface.getDefaultState(),2);world.setBlockState(extended.up(),surface.getDefaultState(),2);tile.target(false);
            for(int tick=0;tick<60;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==0&&world.getBlockState(home).getBlock()==surface&&world.getBlockState(home.up()).getBlock()==surface,"original attachment retracts before enabling pushing "+face+" "+tile.error());
            NBTTagCompound settings=tile.settings();settings.setBoolean("PushBlocks",true);tile.configure(settings);
            require(!tile.members().contains(contact)&&!tile.members().contains(target)&&world.isAirBlock(contact)&&world.isAirBlock(target),"push obstacles are outside every reserved arm section "+face);
            world.setBlockState(contact,Blocks.IRON_BLOCK.getDefaultState(),2);world.setBlockState(target,Blocks.BEDROCK.getDefaultState(),2);tile.target(true);
            for(int tick=0;tick<60;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==0&&world.getBlockState(home).getBlock()==surface&&world.getBlockState(home.up()).getBlock()==surface&&world.getBlockState(contact).getBlock()==Blocks.IRON_BLOCK&&world.getBlockState(target).getBlock()==Blocks.BEDROCK,"complete blocked push path retains both native sources "+face+" "+tile.error());
            world.setBlockToAir(target);tile.target(true);
            for(int tick=0;tick<60;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==1&&world.getBlockState(extended).getBlock()==surface&&world.getBlockState(extended.up()).getBlock()==surface&&world.getBlockState(target).getBlock()==Blocks.IRON_BLOCK,"outward stroke pushes newly contacted block beyond gap "+face+" "+tile.error());
            NBTTagCompound save=tile.writeToNBT(new NBTTagCompound());require(((GearTileEntity)TileEntity.create(world,save)).pushBlocks(),"push option survives old-compatible NBT");
            tile.target(false);for(int tick=0;tick<60;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==0&&world.getBlockState(home).getBlock()==surface&&world.getBlockState(home.up()).getBlock()==surface&&world.isAirBlock(extended)&&world.isAirBlock(extended.up())&&world.getBlockState(target).getBlock()==Blocks.IRON_BLOCK,"retraction returns original attachment and leaves newly pushed block forward "+face+" "+tile.error());
        }
    }
    private static void rotatingCeilingContact(WorldServer world,EntityPlayerMP actor,BlockPos root){
        int cases=0;
        for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM})for(int direction=0;direction<4;direction++){
            clear(world,root);world.setBlockState(root.west(),Blocks.STONE.getDefaultState(),3);
            GearTileEntity tile=arm(world,actor,root,block,EnumFacing.EAST,2);tile.operator(actor);
            NBTTagCompound settings=tile.settings();settings.setBoolean("RotatePayload",true);settings.setInteger("Direction",direction);tile.configure(settings);
            world.setBlockState(root.east(2),Blocks.STONE.getDefaultState(),3);
            ArmPayload selected=ArmPayload.discover(tile,actor);Vec3d center=selected.transform(tile,0,.5,.5,.5).add(new Vec3d(root));BlockPos end=new BlockPos(center);
            if(end.getY()<=root.getY())continue;
            tile.target(false);require(tile.preparePayloadMotion(),"ceiling contact payload prepared");
            world.setBlockState(end.up(),Blocks.STONE.getDefaultState(),3);
            for(int tick=0;tick<80;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==0,"rotating payload fits below endpoint ceiling block "+block.getRegistryName()+" direction="+direction+" endpoint="+end+" progress="+tile.progress()+" error="+tile.error());
            tile.target(true);for(int tick=0;tick<80;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==1,"ceiling-contact reversal returns to source");
            world.setBlockState(end,Blocks.BEDROCK.getDefaultState(),3);tile.target(false);
            for(int tick=0;tick<80;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()!=0&&world.getBlockState(end).getBlock()==Blocks.BEDROCK,"solid endpoint obstruction remains refused");cases++;
        }
        require(cases==2,"both regular and Large upward rotation fixtures exercised");clear(world,root);
    }
    private static void joinedSurfaceLayer(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);
        net.minecraft.block.Block surface=net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface");
        GearTileEntity first=null;
        for(int x:new int[]{0,2})for(int z:new int[]{0,2}){
            BlockPos at=root.add(x,0,z);world.setBlockState(at.down(),Blocks.STONE.getDefaultState(),2);
            GearTileEntity tile=arm(world,actor,at,GearRegistry.GEAR_EXTENDER,EnumFacing.UP,2);tile.operator(actor);
            NBTTagCompound settings=tile.settings();settings.setBoolean("JoinArms",true);tile.configure(settings);if(first==null)first=tile;
        }
        for(int x=-1;x<=3;x++)for(int z=-1;z<=3;z++)world.setBlockState(root.add(x,2,z),surface.getDefaultState(),2);
        require(first.rebuild(),"joined Surface fixture forms");
        ArmPayload selected=ArmPayload.discover(first,actor);
        require(selected!=null&&selected.blocks.cells.size()==25,"joined 3x3 arm footprint captures complete 5x5 Surface layer, actual="+(selected==null?0:selected.blocks.cells.size()));
        clear(world,root);
    }
    private static void surfaceLayers(WorldServer world,EntityPlayerMP actor,BlockPos root){
        net.minecraft.block.Block surface=net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface");
        for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM})for(EnumFacing face:EnumFacing.values())for(boolean rotate:new boolean[]{false,true}){
            clear(world,root);world.setBlockState(root.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),2);
            GearTileEntity tile=arm(world,actor,root,block,face,1);tile.operator(actor);
            if(tile.isSwingArmController()){NBTTagCompound settings=tile.settings();settings.setBoolean("RotatePayload",rotate);tile.configure(settings);}
            BlockPos tip=root.offset(face),across=tip.add(ArmOrientation.vector(face,new BlockPos(1,0,0))),layer=tip.offset(face);
            world.setBlockState(tip,surface.getDefaultState(),2);world.setBlockState(across,surface.getDefaultState(),2);world.setBlockState(layer,surface.getDefaultState(),2);
            ArmPayload selected=ArmPayload.discover(tile,actor);
            require(selected!=null&&selected.blocks.cells.size()==2,"implicit connected surface selects only tip layer "+block+" "+face);
            require(selected.placedCells(tile).contains(tip)&&selected.placedCells(tile).contains(across)&&!selected.placedCells(tile).contains(layer),"stacked surface stays outside payload "+face);
            world.setBlockToAir(layer);tile.target(false);
            for(int tick=0;tick<60;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==0&&tile.payload()!=null&&tile.payload().blocks.cells.size()==2,"connected surface completes stroke "+block+" "+face+" "+tile.error());
            tile.target(true);for(int tick=0;tick<60;tick++)for(BlockPos member:new java.util.ArrayList<>(tile.members())){TileEntity raw=world.getTileEntity(member);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
            require(tile.progress()==1&&world.getBlockState(tip).getBlock()==surface&&world.getBlockState(across).getBlock()==surface,"connected surface reverses to native blocks "+face+" "+tile.error());
        }
    }
    private static final class MotionObstacle {
        final WorldServer world;final AxisAlignedBB box;
        MotionObstacle(WorldServer world,BlockPos at){this.world=world;box=new AxisAlignedBB(at);}
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void collide(net.minecraftforge.event.world.GetCollisionBoxesEvent event){if(event.getWorld()==world&&event.getAabb().intersects(box))event.getCollisionBoxesList().add(box);}
    }
    private static void tickMountedStack(WorldServer world,BlockPos root,int count){
        for(int tick=0;tick<count;tick++)for(BlockPos at:new BlockPos[]{root,root.up()}){TileEntity raw=world.getTileEntity(at);if(raw instanceof GearTileEntity)((GearTileEntity)raw).update();}
    }
    public static void run(WorldServer world,EntityPlayerMP actor){try(AutoCloseable matrix=ArmTransactions.matrix()){runMatrices(world,actor);}catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static void runMatrices(WorldServer world,EntityPlayerMP actor){
        BlockPos root=new BlockPos(620,170,260);checks=0;
        try{
            ArmPayloadSelectionChecks.run(world,actor);
            TrackLifecycleRuntimeChecks.run(world,actor,root);
            pushBlocks(world,actor,root);
            rotatingCeilingContact(world,actor,root);
            joinedSurfaceLayer(world,actor,root);
            surfaceLayers(world,actor,root);
            placementAndClearance(world,actor,root);
            mountedUpwardStack(world,actor,root);
            standaloneEndpoints(world,actor,root);
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
                require(world.isAirBlock(source)&&tile.payload()!=null&&tile.payload().materialized(),"integral retracted endpoint restores native blocks while storing arm sections "+face);
                tile.target(true);for(int tick=0;tick<50;tick++)tile.update();require(tile.progress()==1&&world.getBlockState(source).getBlock()==Blocks.STONE&&(tile.payload()==null||tile.payload().materialized()),"reverse restores construction block without duplication "+face);
                settings=tile.settings();settings.setDouble("ActiveExtension",.5);settings.setBoolean("Deployed",false);tile.configure(settings);retainMidStroke(tile);
                require(tile.progress()>0&&tile.progress()<1&&tile.payload()!=null&&!tile.payload().materialized()&&world.isAirBlock(source),"mid-stroke retains captured attachment "+face);
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());GearTileEntity copy=(GearTileEntity)TileEntity.create(world,saved);require(copy.payload()!=null&&copy.payload().blocks.cells.size()==1,"mid-stroke attachment survives save "+face);
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
        System.out.println("[vandorlabs][reprolab] gear26-runtime PASS checks="+checks+" six-axis unsupported columns integral/mid-stroke payloads reversal snapshots tracks and side brackets");
    }
    private static void mountedUpwardStack(WorldServer world,EntityPlayerMP actor,BlockPos root){
        ItemStack held=actor.getHeldItemMainhand();boolean creative=actor.capabilities.isCreativeMode;double x=actor.posX,y=actor.posY,z=actor.posZ;
        try{actor.capabilities.isCreativeMode=true;actor.setPosition(root.getX()+2,root.getY()+1,root.getZ()+2);
            for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER})for(int click=0;click<2;click++){
                clear(world,root);for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)world.setBlockState(root.down().add(dx,0,dz),Blocks.STONE.getDefaultState(),2);
                actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(block,4));
                require(Item.getItemFromBlock(block).onItemUse(actor,world,root.down(),net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"place first upward arm on ground");
                require(Item.getItemFromBlock(block).onItemUse(actor,world,root,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"stack second upward arm");
                actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(Blocks.STONE,4));
                require(Item.getItemFromBlock(Blocks.STONE).onItemUse(actor,world,root.up(),net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"place ordinary block on upward arm");
                GearTileEntity part=(GearTileEntity)world.getTileEntity(root);require(part!=null,"first upward arm retains its tile");
                actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);
                BlockPos clicked=root.up(click);require(block.onBlockActivated(world,clicked,world.getBlockState(clicked),actor,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F),"right-click upward arm section "+click);
                GearTileEntity tile=part.loadedController();
                require(tile!=null&&tile.assembly().columnHeight()==2&&!tile.target(),"normal placement and right-click form and retract two-section upward arm: root="+part.getPos()+" owner="+part.owner()+" controller="+(tile==null?"null":tile.getPos()+" "+tile.error()+" height="+(tile.assembly()==null?"none":tile.assembly().columnHeight())));
                BlockPos tip=root.up(2);tickMountedStack(world,root,50);
                require(tile.progress()==0&&tile.payload()!=null&&tile.payload().materialized()&&world.isAirBlock(tip),"ground-mounted upward two-section arm retracts its attached block: "+block.getRegistryName()+" "+tile.error());
                tile.target(true);tickMountedStack(world,root,50);
                require(tile.progress()==1&&world.getBlockState(tip).getBlock()==Blocks.STONE,"ground-mounted arm restores construction pose and payload: "+block.getRegistryName()+" "+tile.error());
                MotionObstacle obstacle=new MotionObstacle(world,tip);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(obstacle);
                try{tile.target(false);tickMountedStack(world,root,1);}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(obstacle);}
                require(tile.progress()==1&&tile.target()&&world.getBlockState(tip).getBlock()==Blocks.STONE&&tile.payload()!=null&&tile.payload().materialized()&&!tile.motionBlocked(),"blocked first step restores prior endpoint and its ordinary block: "+block.getRegistryName()+" "+tile.error());
                RayTraceResult hit=world.rayTraceBlocks(new Vec3d(tip).addVector(.5,2,.5),new Vec3d(tip).addVector(.5,.5,.5),false,false,false);require(hit!=null&&hit.getBlockPos().equals(tip),"restored payload remains selectable after blocked stroke");
                tile.captureBlocked(true);Vec3d armCenter=new Vec3d(root).addVector(.5,.5,.5);RayTraceResult armHit=world.rayTraceBlocks(armCenter.addVector(2,0,0),armCenter,false,false,false);require(armHit!=null&&armHit.getBlockPos().equals(root),"blocked arm source remains normally selectable");tile.captureBlocked(false);
                require(block.onBlockActivated(world,root,world.getBlockState(root),actor,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)&&!tile.target(),"right-click retries after blocked stroke");tickMountedStack(world,root,50);require(tile.progress()==0&&world.isAirBlock(tip),"arm retries normally after obstacle clears");tile.target(true);tickMountedStack(world,root,50);require(world.getBlockState(tip).getBlock()==Blocks.STONE,"retry still restores one ordinary block");
                NBTTagCompound dialog=tile.settings();dialog.setBoolean("Deployed",false);tile.configure(dialog);tickMountedStack(world,root,50);require(tile.progress()==0&&world.isAirBlock(tip),"dialog target retracts the same placed arm stack");tile.target(true);tickMountedStack(world,root,50);require(world.getBlockState(tip).getBlock()==Blocks.STONE,"dialog reversal restores its ordinary block");
            }
        }finally{actor.capabilities.isCreativeMode=creative;actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);actor.setPosition(x,y,z);clear(world,root);}
    }
    private static void standaloneEndpoints(WorldServer world,EntityPlayerMP actor,BlockPos root){
        int cases=0;
        for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER})for(EnumFacing face:EnumFacing.values())for(int length:new int[]{1,2,3,8}){
            clear(world,root);GearTileEntity tile=arm(world,actor,root,block,face,length);BlockPos source=root.offset(face,length);world.setBlockState(source,Blocks.STONE.getDefaultState(),2);
            require(tile.preparePayloadMotion()&&tile.payload()!=null,"retraction fixture captures payload");double stroke=length==1?.5:length-1;
            Vec3d start=tile.payload().transform(tile,1,0,0,0),end=tile.payload().transform(tile,0,0,0,0);Vec3d expected=new Vec3d(face.getDirectionVec()).scale(-stroke);
            require(end.subtract(start).squareDistanceTo(expected)<1e-12,"exact retraction endpoint "+block.getRegistryName()+face+length);
            for(int n=0;n<=24;n++){Vec3d moved=tile.payload().transform(tile,n/24D,0,0,0).subtract(start);double travel=moved.dotProduct(new Vec3d(face.getDirectionVec()));require(travel<=1e-8&&travel>=-stroke-1e-8,"never exceeds placed maximum");}
            require(tile.payload().restore(tile,1),"retraction fixture returns construction block");cases++;
        }
        for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM})for(EnumFacing face:EnumFacing.values())for(int direction=0;direction<4;direction++)for(boolean rotate:new boolean[]{false,true}){
            clear(world,root);GearTileEntity tile=arm(world,actor,root,block,face,3);NBTTagCompound settings=tile.settings();settings.setInteger("Direction",direction);settings.setBoolean("RotatePayload",rotate);tile.configure(settings);world.setBlockState(root.offset(face,3),Blocks.STONE.getDefaultState(),2);require(tile.preparePayloadMotion()&&tile.payload()!=null,"rotating alignment captures payload");
            for(int corner=0;rotate&&corner<8;corner++){Vec3d v=tile.payload().transform(tile,0,corner&1,(corner>>1)&1,(corner>>2)&1);require(Math.abs(v.x-Math.rint(v.x))+Math.abs(v.y-Math.rint(v.y))+Math.abs(v.z-Math.rint(v.z))<1e-8,"quarter-turn cube corners align to world grid "+face+direction+rotate);}
            {if(rotate){require(tile.payload().restore(tile,0),"quarter-turn payload becomes a regular world block");ArmPayload loaded=ArmPayload.read(tile.payload().write());require(loaded.matchesPlaced(tile)&&loaded.detach(tile)&&loaded.restore(tile,1),"rotated endpoint reload and reverse restore exactly once");}else require(tile.payload().restore(tile,1),"connected level payload restores construction block");}
            cases++;
        }
        System.out.println("[vandorlabs] arm-standalone-endpoints PASS cases="+cases+" regular/Large six axes bounded half/integral retraction quarter-turn grid corners level payload materialization/reload/reversal");
    }
    private static void placementAndClearance(WorldServer world,EntityPlayerMP actor,BlockPos root){
        ItemStack original=actor.getHeldItemMainhand();boolean creative=actor.capabilities.isCreativeMode;int cases=0;
        try{
            actor.capabilities.isCreativeMode=true;
            for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM,GearRegistry.GEAR_MOUNT,GearRegistry.LARGE_GEAR_MOUNT})for(EnumFacing face:EnumFacing.values()){
                clear(world,root);world.setBlockState(root.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),2);
                actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(block,16));
                require(Item.getItemFromBlock(block).onItemUse(actor,world,root.offset(face.getOpposite()),net.minecraft.util.EnumHand.MAIN_HAND,face,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"normal ItemBlock placement from mounting face "+block.getRegistryName()+face);
                GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);require(tile!=null&&tile.mountDirection()==face&&tile.rebuild(),"clicked mount creates correct six-axis mechanism");
                int max=block.kind()==GearBlock.Kind.MOUNT?1:3;
                for(int length=1;length<=max;length++){
                    Vec3d tip=new Vec3d(root).addVector(.5,.5,.5).add(new Vec3d(face.getDirectionVec()).scale(length-.5));Vec3d start=tip.add(new Vec3d(face.getDirectionVec()).scale(2)),end=tip.subtract(new Vec3d(face.getDirectionVec()).scale(1.5));
                    RayTraceResult hit=GearInteractions.trace(world,start,end);BlockPos last=root.offset(face,length-1);
                    require(hit!=null&&hit.getBlockPos().equals(last)&&hit.sideHit==face&&hit.hitVec.squareDistanceTo(tip)<1e-12,"outward tip click selects actual final arm cell and exact adjacent boundary "+block.getRegistryName()+face+length+" hit="+hit);
                    RayTraceResult vanilla=world.rayTraceBlocks(start,end,false,false,false);require(vanilla!=null&&vanilla.getBlockPos().equals(last)&&vanilla.sideHit==face,"normal player block ray sees actual outward arm face "+block.getRegistryName()+face+length+" hit="+vanilla);
                    actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(Blocks.STONE,16));
                    require(Item.getItemFromBlock(Blocks.STONE).onItemUse(actor,world,hit.getBlockPos(),net.minecraft.util.EnumHand.MAIN_HAND,hit.sideHit,(float)(hit.hitVec.x-last.getX()),(float)(hit.hitVec.y-last.getY()),(float)(hit.hitVec.z-last.getZ()))==net.minecraft.util.EnumActionResult.SUCCESS,"ordinary block places directly from tip click");
                    BlockPos payload=root.offset(face,length);require(world.getBlockState(payload).getBlock()==Blocks.STONE,"placed payload is outside tip, never inside arm");
                    require(tile.preparePayloadMotion()&&tile.payload()!=null,"adjacent ordinary block is discovered on every mounting axis");
                    for(AxisAlignedBB box:tile.payload().boxes(tile,1))require(!box.intersects(new AxisAlignedBB(last).shrink(1e-6)),"construction payload geometry leaves final arm cell clear");
                    require(tile.payload().restore(tile,1),"placement fixture restores attachment without loss");
                    // A column extension changes the mechanism's topology, so discard the test
                    // attachment only after it is safely materialized in its original cell.
                    require(tile.releaseAssembly(),"release placed attachment before lengthening column");world.removeTileEntity(payload);world.setBlockToAir(payload);
                    if(length<max){actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(block,16));require(Item.getItemFromBlock(block).onItemUse(actor,world,last,net.minecraft.util.EnumHand.MAIN_HAND,face,.5F,.5F,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"clicked tip stacks another matching arm section");require(tile.rebuild()&&tile.assembly().columnHeight()==length+1,"stacked sections extend one shared mechanism");}
                    else require(tile.rebuild(),"re-form final column");
                    cases++;
                }
                if(block.kind()==GearBlock.Kind.EXTENDER){
                    NBTTagCompound settings=tile.settings();settings.setDouble("ActiveExtension",-tile.assembly().columnHeight());tile.configure(settings);require(tile.activeExtension()==tile.minimumExtension(),"stack retraction ignores legacy extension settings");
                    BlockPos source=root.offset(face,max);world.setBlockState(source,Blocks.STONE.getDefaultState(),2);settings=tile.settings();settings.setDouble("ActiveExtension",tile.minimumExtension());settings.setBoolean("Deployed",false);tile.configure(settings);for(int tick=0;tick<50;tick++)tile.update();require(tile.progress()==0&&tile.payload()!=null,"automatic stack reach completes motion "+face+tile.error());for(AxisAlignedBB box:tile.payload().boxes(tile,0))require(!box.intersects(new AxisAlignedBB(root).shrink(1e-6)),"automatic reach cannot bury payload inside mounting base");tile.target(true);for(int tick=0;tick<50;tick++)tile.update();require(tile.progress()==1&&world.getBlockState(source).getBlock()==Blocks.STONE,"automatic reach reverses and restores the adjacent payload");
                }
            }
        }finally{actor.capabilities.isCreativeMode=creative;actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,original);clear(world,root);}
        System.out.println("[vandorlabs][reprolab] arm-placement-clearance PASS cases="+cases+" actual ItemBlock mounting/clicked-tip payloads six axes regular/large stacked columns min-one-block reach");
    }
    private static void inputTransition(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);world.setBlockState(root,GearRegistry.GEAR_EXTENDER.getDefaultState());GearTileEntity tile=(GearTileEntity)world.getTileEntity(root);tile.operator(actor);
        NBTTagCompound settings=tile.settings();settings.setInteger("Trigger",1);tile.configure(settings);require(!tile.target(),"unformed tile retains legacy unpowered deployment input");require(tile.rebuild(),"configured bare arm forms");tile.update();require(tile.target()&&tile.progress()==1,"forming generic topology resamples its construction-pose input");
        world.setBlockState(root.down(),GearRegistry.LANDING_PLATE.getDefaultState());require(tile.rebuild(),"generic arm becomes landing support");tile.update();require(!tile.target(),"landing topology resamples legacy deployment input after generic operation");
        System.out.println("[vandorlabs][reprolab] gear26-input-transition PASS legacy-unformed generic-construction-pose landing-deployment edge-resampling");
    }
    private static void retainMidStroke(GearTileEntity tile){
        for(int tick=0;tick<50;tick++){tile.update();if(tile.payload()!=null&&!tile.payload().materialized()&&tile.progress()>0&&tile.progress()<1){tile.captureProgress(.5);return;}}
        throw new IllegalStateException("Fixture failed to capture actual moving payload: "+tile.error());
    }
    private static void topologyRecovery(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);world.setBlockState(root.down(2),Blocks.CHEST.getDefaultState());((net.minecraft.tileentity.TileEntityChest)world.getTileEntity(root.down(2))).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.GOLD_INGOT,6));
        NBTTagCompound values=tile.settings();values.setDouble("ActiveExtension",.5);values.setBoolean("Deployed",false);tile.configure(values);retainMidStroke(tile);NBTTagCompound retained=tile.payload().write();world.setBlockState(root.down(2),Blocks.OBSIDIAN.getDefaultState());world.destroyBlock(root.down(),false);
        require(!tile.isOperational()&&tile.isController()&&tile.payload()!=null&&retained.equals(tile.payload().write()),"blocked release keeps old controller and exact retained snapshot");
        world.setBlockState(root.up(),GearRegistry.GEAR_EXTENDER.getDefaultState(),2);GearTileEntity above=(GearTileEntity)world.getTileEntity(root.up());require(!above.rebuild()&&tile.isController()&&tile.payload()!=null&&retained.equals(tile.payload().write()),"new upper arm cannot hide retained controller as internal member");
        world.removeTileEntity(root.up());world.setBlockToAir(root.up());world.setBlockToAir(root.down(2));tile.target(true);for(int tick=0;tick<80;tick++)tile.update();TileEntity chest=world.getTileEntity(root.down(2));require(chest instanceof net.minecraft.tileentity.TileEntityChest&&((net.minecraft.tileentity.TileEntityChest)chest).getStackInSlot(0).getCount()==6,"inert mechanism restores exact contents after clearing construction position and selecting Off");
        System.out.println("[vandorlabs][reprolab] gear26-topology-recovery PASS blocked-restore retained-controller no-hidden-snapshot safe-Off-recovery");
    }
    private static void recovery(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);world.setBlockState(root.down(2),Blocks.CHEST.getDefaultState());((net.minecraft.tileentity.TileEntityChest)world.getTileEntity(root.down(2))).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,5));
        NBTTagCompound values=tile.settings();values.setDouble("ActiveExtension",.5);values.setBoolean("Deployed",false);tile.configure(values);retainMidStroke(tile);require(tile.payload()!=null&&!tile.payload().materialized(),"recovery fixture has retained contents");
        world.destroyBlock(root,false);net.minecraft.entity.item.EntityItem recovery=null;for(net.minecraft.entity.item.EntityItem item:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new AxisAlignedBB(root).grow(2)))if(item.getItem().hasTagCompound()&&item.getItem().getTagCompound().hasKey("ArmRecovery",10)){require(recovery==null,"controller produces one recovery item");recovery=item;}
        require(recovery!=null&&world.isAirBlock(root.down(2)),"breaking controller retains captured inventory in recovery arm");
        ItemStack item=recovery.getItem().copy();recovery.setDead();BlockPos target=root.east(8);require(((ItemBlock)item.getItem()).placeBlockAt(item,actor,world,target,EnumFacing.DOWN,.5F,.5F,.5F,GearRegistry.GEAR_EXTENDER.getDefaultState()),"recovery item placement");GearTileEntity restored=(GearTileEntity)world.getTileEntity(target);restored.target(true);for(int tick=0;tick<80;tick++)restored.update();
        TileEntity raw=world.getTileEntity(target.down(2));require(raw instanceof net.minecraft.tileentity.TileEntityChest&&((net.minecraft.tileentity.TileEntityChest)raw).getStackInSlot(0).getCount()==5,"placed recovery arm returns exact retained inventory");
        System.out.println("[vandorlabs][reprolab] gear26-recovery PASS mid-stroke-controller-break one-recovery-item retained-inventory replacement-restore");
    }
    private static void payloadBounds(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);
        for(int x=0;x<16;x++)world.setBlockState(root.add(x,-2,0),Blocks.STONE.getDefaultState());
        NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());legacy.setTag("ArmPayload",savedAttachment(world,root,root.down(2),16).write());tile.readFromNBT(legacy);tile.operator(actor);
        NBTTagCompound values=tile.settings();values.setDouble("ActiveExtension",.5);values.setBoolean("Deployed",false);tile.configure(values);retainMidStroke(tile);
        require(tile.progress()>0&&tile.progress()<1&&tile.payload()!=null,"wide attachment retained in mid-stroke");
        for(double progress:new double[]{0,.25,.5,.75,1})for(AxisAlignedBB box:tile.payload().boxes(tile,progress))require(tile.getRenderBoundingBox().grow(1e-6).contains(new Vec3d(box.minX,box.minY,box.minZ))&&tile.getRenderBoundingBox().grow(1e-6).contains(new Vec3d(box.maxX,box.maxY,box.maxZ)),"render and spatial bounds contain entire payload stroke");
        AxisAlignedBB far=tile.payload().boxes(tile,tile.progress()).get(15).shrink(.1);require(!world.getCollisionBoxes(null,far).isEmpty(),"held attachment collides across chunk boundary beyond old arm radius");
        tile.target(true);for(int tick=0;tick<50;tick++)tile.update();for(int x=0;x<16;x++)world.setBlockToAir(root.add(x,-2,0));
        System.out.println("[vandorlabs][reprolab] gear26-payload-bounds PASS 16-block-wide sweep render-culling spatial-index cross-chunk-collision");
    }
    private static void carried(WorldServer world,EntityPlayerMP actor,BlockPos root){for(int phase=0;phase<3;phase++)carried(world,actor,root,phase);}
    private static void carried(WorldServer world,EntityPlayerMP actor,BlockPos root,int phase){
        boolean retracted=phase>0,empty=phase==2;
        clear(world,root);GearTileEntity source=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);BlockPos chestPos=root.down(2);world.setBlockState(chestPos,Blocks.CHEST.getDefaultState());
        ((net.minecraft.tileentity.TileEntityChest)world.getTileEntity(chestPos)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.EMERALD,7));ArmPayload payload=ArmPayload.discover(source,actor);
        if(retracted){source.target(false);for(int tick=0;tick<50;tick++)source.update();require(source.progress()==0&&source.hasStoredSections()&&source.payload().materialized(),"carried regression starts with native retracted chest and separately stored arm section");if(empty){world.setBlockToAir(root.down());source.update();require(source.payload()==null,"native chest removal leaves only stored arm sections");}payload=source.payload();}
        BlockPos local=new BlockPos(3,3,3),shift=local.subtract(root),attachment=local.down(retracted?1:2);java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(new BlockPos(0,0,1),com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
        for(BlockPos cell:new BlockPos[]{root,root.down(),chestPos}){if(world.isAirBlock(cell))continue;TileEntity tile=world.getTileEntity(cell);NBTTagCompound tag=tile==null?null:tile.writeToNBT(new NBTTagCompound());if(cell.equals(root)&&payload!=null)tag.setTag("ArmPayload",payload.write());cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(cell.add(shift),world.getBlockState(cell),tag==null?null:com.vandorlabs.vehicle.VehicleTileData.translated(tag,shift)));}
        com.vandorlabs.vehicle.EntityGroundVehicle craft=new com.vandorlabs.vehicle.EntityGroundVehicle(world);
        try{
            craft.install(new com.vandorlabs.vehicle.VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,3));craft.setPosition(root.getX()-3,root.getY()+12,root.getZ()-3);craft.signals.open();
            if(!empty)((net.minecraft.tileentity.TileEntityChest)craft.view.getTileEntity(attachment)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,9));
            GearTileEntity arm=(GearTileEntity)craft.view.getTileEntity(local);NBTTagCompound values=arm.settings();values.setDouble("ActiveExtension",1);values.setBoolean("Deployed",retracted);arm.configure(values);
            for(int tick=0;tick<250;tick++){com.vandorlabs.vehicle.ModularGearVehicles.tick(craft);arm=(GearTileEntity)craft.view.getTileEntity(local);if(arm!=null&&arm.progress()==(retracted?1:0))break;try{Thread.sleep(1);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}
            arm=(GearTileEntity)craft.view.getTileEntity(local);require(arm!=null&&arm.progress()==(retracted?1:0)&&!craft.hover,"generic payload remains operable in Taxi");require((retracted?craft.structure.at(attachment)!=null&&craft.structure.at(attachment).state.getBlock()==GearRegistry.GEAR_EXTENDER:craft.structure.at(attachment)==null)&&!arm.hasStoredSections(),"carried native endpoint restores stored arm sections exactly once");require(empty?arm.payload()==null:arm.payload()!=null&&!arm.payload().materialized(),"ordinary carried cells become one nested snapshot before movement");
            if(!empty){net.minecraft.tileentity.TileEntityChest nested=(net.minecraft.tileentity.TileEntityChest)arm.payload().view(arm).tiles.get(BlockPos.ORIGIN);require(nested!=null&&nested.getStackInSlot(0).getItem()==net.minecraft.init.Items.DIAMOND&&nested.getStackInSlot(0).getCount()==9,"carried capture adopts edited materialized inventory instead of stale snapshot");}
            NBTTagCompound original=arm.writeToNBT(new NBTTagCompound()),rotated=original.copy();for(int t=0;t<4;t++)GearData.rotate(rotated,1,world);require(original.equals(rotated),"nested payload and six-axis basis rotate through four parking headings without loss");
        }finally{craft.signals.close();craft.setDead();clear(world,root);}
        System.out.println("[vandorlabs][reprolab] gear26-carried PASS phase="+phase+" Taxi-animation atomic-nested-capture changed-inventory retained-snapshot four-parking-headings");
    }
    /** A persisted pre-tip-only payload must retain its entire authorized footprint. */
    private static ArmPayload savedAttachment(WorldServer world,BlockPos root,BlockPos source,int count){
        java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();
        for(int x=0;x<count;x++){BlockPos at=source.east(x);TileEntity raw=world.getTileEntity(at);NBTTagCompound data=raw==null?null:com.vandorlabs.vehicle.VehicleTileData.translated(raw.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(source));cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(new BlockPos(x,0,0),world.getBlockState(at),data));}
        NBTTagCompound saved=new NBTTagCompound();saved.setTag("Blocks",com.vandorlabs.vehicle.VehicleStructure.display(cells).write());saved.setLong("Offset",source.subtract(root).toLong());saved.setLong("PlacedOffset",source.subtract(root).toLong());saved.setBoolean("Materialized",true);return ArmPayload.read(saved);
    }
    private static void transactions(WorldServer world,EntityPlayerMP actor,BlockPos root){
        clear(world,root);GearTileEntity tile=arm(world,actor,root,GearRegistry.GEAR_EXTENDER,EnumFacing.DOWN,2);BlockPos source=root.down(2);
        world.setBlockState(source,Blocks.CHEST.getDefaultState());world.setBlockState(source.east(),Blocks.STONE.getDefaultState());world.setBlockState(source.east(2),Blocks.DROPPER.getDefaultState());((net.minecraft.tileentity.TileEntityDropper)world.getTileEntity(source.east(2))).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.IRON_INGOT,11));
        net.minecraft.tileentity.TileEntityChest chest=(net.minecraft.tileentity.TileEntityChest)world.getTileEntity(source);chest.setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,3));
        ArmPayload payload=savedAttachment(world,root,source,3);NBTTagCompound original=payload.write();final int[] count={0};
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
