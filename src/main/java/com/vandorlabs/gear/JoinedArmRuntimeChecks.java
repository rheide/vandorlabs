package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;
import java.util.*;

/** Ordinary supported columns, hollow platform, real ticks and asynchronous ownership journals. */
@SideOnly(Side.CLIENT)
public final class JoinedArmRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(1503,100,0);
    private static int fixture=Integer.getInteger("vandorlabs.joinedArmStart",0),stage,ticks,cases;private static final Set<BlockPos> platform=new LinkedHashSet<>();private static BlockPos load;
    private static com.vandorlabs.vehicle.EntityGroundVehicle vehicle;private static double vehicleStartY;
    private static EnumFacing face(){return fixture>=24?EnumFacing.UP:EnumFacing.values()[fixture%6];}
    private static GearBlock block(){return fixture>=24?new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM}[fixture-24]:fixture<12?(fixture<6?GearRegistry.GEAR_EXTENDER:GearRegistry.LARGE_GEAR_EXTENDER):(fixture<18?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM);}
    private static boolean swing(){return fixture>=12&&fixture<24||fixture>=26;}
    private static boolean carries(){return !swing()&&face().getAxis()==EnumFacing.Axis.Y;}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("joined-arm fixture="+fixture+" stage="+stage+": "+why);}
    private static BlockPos at(int x,int y,int z){return BASE.add(ArmOrientation.vector(face(),new BlockPos(x,y,z)));}
    public static boolean tick(Minecraft mc)throws Exception{
        if(Boolean.getBoolean("vandorlabs.armEndpointOnly"))return ArmEndpointRuntimeChecks.tick(mc);
        if(fixture>=28)return true;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static boolean run(WorldServer world,EntityPlayerMP player)throws Exception{
        require(++ticks<500,"bounded move: "+(world.getTileEntity(BASE) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(BASE)).error():"no controller"));
        if(stage==0){
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(BASE.getX()+8,104,8,90,0);
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-12,-10,-12),BASE.add(12,10,12)))world.setBlockToAir(p);
            int[][] anchors=fixture>=24?(swing()?new int[][]{{0,0},{4,0},{8,0}}:new int[][]{{0,0},{4,0},{8,0},{0,4},{4,4},{8,4}}):swing()?new int[][]{{0,0},{4,0}}:new int[][]{{0,0},{4,0},{0,4},{4,4}};
            for(int[] c:anchors){world.setBlockState(at(c[0],1,c[1]),Blocks.STONE.getDefaultState(),2);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));for(int n=0;n<2;n++)require(player.getHeldItemMainhand().onItemUse(player,world,at(c[0],1-n,c[1]),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"place supported column");GearTileEntity tile=(GearTileEntity)world.getTileEntity(at(c[0],0,c[1]));require(tile.rebuild(),"column forms");NBTTagCompound settings=tile.settings();settings.setInteger("Direction",0);settings.setBoolean("JoinArms",true);settings.setInteger("JoinRange",4);settings.setBoolean("CarryLoad",carries());settings.setInteger("LoadHeight",2);tile.configure(settings);}
            platform.clear();for(int x=0;x<=(fixture>=24?8:4);x++)for(int z=0;z<=(swing()?0:4);z++)if(swing()||x==0||z==0||x==(fixture>=24?8:4)||z==4){BlockPos p=at(x,-2,z);platform.add(p);world.setBlockState(p,Blocks.STONE.getDefaultState(),2);}
            load=at(2,-2,0).up();if(carries())world.setBlockState(load,Blocks.CHEST.getDefaultState(),2);else if(!swing()&&face()==EnumFacing.UP)world.setBlockState(load,Blocks.CHEST.getDefaultState(),2);
            if(world.getTileEntity(load) instanceof net.minecraft.inventory.IInventory)((net.minecraft.inventory.IInventory)world.getTileEntity(load)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,37));
            if(fixture==24||fixture==25){for(int x=4;x<=7;x++){BlockPos floor=at(x,-2,1);platform.add(floor);world.setBlockState(floor,Blocks.STONE.getDefaultState(),2);}vehicle=vehicle(world);BlockPos origin=at(5,-2,1).up();vehicle.setPosition(origin.getX(),origin.getY(),origin.getZ());vehicleStartY=vehicle.posY;require(world.spawnEntity(vehicle),"spawn vehicle load");}
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);stage=1;ticks=0;return false;
        }
        GearTileEntity member=(GearTileEntity)world.getTileEntity(BASE);GearTileEntity arm=member==null?null:member.loadedController();require(arm!=null,"joined controller remains available");
        if(stage==1){if(ticks<8)return false;require(arm.columns().size()==(fixture>=24?(swing()?3:6):(swing()?2:4)),"all compatible columns share one controller, got "+arm.columns().size());if(fixture==24)unloadedMember(world,player,arm);if(vehicle!=null){NBTTagCompound settings=arm.settings();settings.setBoolean("CarryLoad",false);arm.configure(settings);stage=5;ticks=0;}else stage=2;arm.target(false);return false;}
        if(ArmTransactions.locked(world,arm.getPos()))return false;
        if(stage==5||stage==6){if(ticks<20)return false;require(arm.progress()==1&&arm.target(),"vehicle refuses disabled/insufficient load height without a partial move");require(Math.abs(vehicle.posY-vehicleStartY)<.05,"refused vehicle stays at its original position");for(BlockPos p:platform)require(world.getBlockState(p).getBlock()==Blocks.STONE,"refused platform restored once");NBTTagCompound settings=arm.settings();settings.setBoolean("CarryLoad",true);settings.setInteger("LoadHeight",stage==5?1:2);arm.configure(settings);arm.target(false);stage=stage==5?6:2;ticks=0;return false;}
        if(stage==2){require(!(ticks>20&&arm.progress()==1&&arm.target()),"requested move refused: "+arm.error()+" vehicle="+(vehicle==null?"none":vehicle.getEntityBoundingBox()+" allowed="+JoinedArms.acceptsVehicle(arm,vehicle,arm.progress())+" eligible="+com.vandorlabs.motion.PlatformRiders.eligible(vehicle)));if(arm.progress()!=0)return false;if(vehicle!=null){require(vehicle.posY<vehicleStartY-.5,"vehicle descends with platform");require(((net.minecraft.inventory.IInventory)vehicle.view.getTileEntity(new BlockPos(2,0,0))).getStackInSlot(0).getCount()==13,"vehicle cargo retained while moving");}require(arm.payload()!=null,"platform retained");require(arm.payload().blocks.cells.size()==platform.size()+(carries()?1:0),"only connected platform and enabled load captured");Set<BlockPos> placed=arm.payload().materialized()?arm.payload().placedCells(arm):Collections.emptySet();if(arm.payload().materialized())require(arm.payload().matchesPlaced(arm),"native endpoint owns the exact current payload");for(BlockPos p:platform)require(placed.contains(p)||world.isAirBlock(p),"old cells are empty unless occupied by the translated native load");require(world.isAirBlock(at(2,-2,2)),"interior air hole remains empty");NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());GearTileEntity decoded=new GearTileEntity();decoded.readFromNBT(saved);require(decoded.joinEnabled()&&decoded.joinRange()==4&&decoded.columns().size()==arm.columns().size()&&decoded.payload()!=null,"joined settings topology and payload survive NBT");arm.target(true);stage=3;return false;}
        if(stage==3){if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;if(vehicle!=null){require(Math.abs(vehicle.posY-vehicleStartY)<.05,"vehicle returns with platform");vehicle.setDead();world.removeEntityDangerously(vehicle);vehicle=null;}for(BlockPos p:platform)require(world.getBlockState(p).getBlock()==Blocks.STONE,"platform returned once");if(carries())require(((net.minecraft.inventory.IInventory)world.getTileEntity(load)).getStackInSlot(0).getCount()==37,"carried chest inventory preserved");if(!swing()){world.setBlockToAir(at(1,-2,0));world.setBlockToAir(at(0,-2,1));arm.target(false);stage=4;ticks=0;return false;}return next();}
        if(ticks<20)return false;require(arm.progress()==1&&arm.target(),"disconnected corner refuses whole move at safe endpoint");require(world.getBlockState(at(0,-2,0)).getBlock()==Blocks.STONE,"disconnected corner remains selectable in world");return next();
    }
    private static boolean next(){System.out.println("[vandorlabs] joined-arm case PASS fixture="+fixture+" face="+face()+" hollow="+!swing()+" load="+carries());if(++fixture==28){System.out.println("[vandorlabs] joined-arm PASS cases="+(cases+1)+" completed selected fixtures; per-case coverage above");return true;}cases++;stage=0;ticks=0;return false;}
    private static void unloadedMember(WorldServer world,EntityPlayerMP player,GearTileEntity arm){
        player.connection.setPlayerLocation(BASE.getX()-4,110,-8,0,0);BlockPos far=at(8,0,0);int x=far.getX()>>4,z=far.getZ()>>4;
        require((arm.getPos().getX()>>4)!=x||(arm.getPos().getZ()>>4)!=z,"fixture spans separate chunks");
        int members=arm.columns().size();net.minecraft.world.chunk.Chunk chunk=world.getChunkFromBlockCoords(far);world.getChunkProvider().queueUnload(chunk);for(int i=0;i<20&&world.isBlockLoaded(far,false);i++)world.getChunkProvider().tick();
        require(world.getChunkProvider().getLoadedChunk(x,z)==null,"actual member chunk unloaded saving="+world.disableLevelSaving+" forced="+world.getPersistentChunks().containsKey(new net.minecraft.util.math.ChunkPos(x,z))+" droppable="+world.provider.canDropChunk(x,z)+" queued="+chunk.unloadQueued);
        require(!arm.rebuild()&&arm.columns().size()==members&&arm.progress()==1,"partial group preserves topology and endpoint");
        require(world.getChunkProvider().getLoadedChunk(x,z)==null,"rebuild did not force-load missing member");
        world.getChunkFromChunkCoords(x,z);com.vandorlabs.tiles.DeferredTileLoad.flush(world);require(arm.rebuild()&&arm.columns().size()==members,"whole group resumes after chunk reload");
    }
    private static com.vandorlabs.vehicle.EntityGroundVehicle vehicle(WorldServer world){
        java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new ArrayList<>();
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),null));
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN.up(),com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState().withProperty(com.vandorlabs.blocks.BlockPilotSeat.UPPER,true),null));
        net.minecraft.block.Block controller=net.minecraft.block.Block.getBlockFromName("vandorlabs:vehicle_controller");net.minecraft.tileentity.TileEntity tile=controller.createTileEntity(world,controller.getDefaultState());tile.setPos(new BlockPos(1,0,0));cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(tile.getPos(),controller.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));
        net.minecraft.tileentity.TileEntityChest chest=new net.minecraft.tileentity.TileEntityChest();chest.setPos(new BlockPos(2,0,0));chest.setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.EMERALD,13));cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(chest.getPos(),Blocks.CHEST.getDefaultState(),chest.writeToNBT(new NBTTagCompound())));
        com.vandorlabs.vehicle.EntityGroundVehicle result=new com.vandorlabs.vehicle.EntityGroundVehicle(world);result.install(new com.vandorlabs.vehicle.VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));return result;
    }
    private JoinedArmRuntimeChecks(){}
}
