package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Real click, asynchronous vehicle assembly, native foreign data and client display pixels. */
final class ForeignBuildingLiveChecks {
    private static final BlockPos BASE=new BlockPos(1600,110,160),SEAT=BASE.add(2,2,1);
    private static int stage,ticks;private static boolean clicked,complete;private static UUID vehicle;private static BlockPos parkedSeat;
    private static List<VehicleStructure.Cell> parked;private static BlockPos parkedOrigin;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("foreign-building-live stage="+stage+" tick="+ticks+": "+why);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return vehicle==null?null:(EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(vehicle);}
    private static EntityGroundVehicle client(Minecraft mc){if(mc.world!=null)for(EntityGroundVehicle e:VehicleLookup.loaded(mc.world))if(e.getUniqueID().equals(vehicle))return e;return null;}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(complete)return true;mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;require(++ticks<700,"bounded native transfer/client synchronization");
        if(stage==0){mc.getIntegratedServer().callFromMainThread(()->{setup(mc.getIntegratedServer().getWorld(0),actor(mc));return null;}).get();stage=1;ticks=0;return false;}
        if(stage==1&&!clicked&&ticks>20&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(BASE.getX()+2.5,BASE.getY()+4,BASE.getZ()-.5)<.1&&mc.world.getBlockState(SEAT).getBlock()==ModBlocks.PILOT_SEAT){
            mc.playerController.processRightClickBlock(mc.player,mc.world,SEAT,EnumFacing.UP,new Vec3d(SEAT).addVector(.5,.5,.5),EnumHand.MAIN_HAND);clicked=true;
        }
        if(stage==1){
            boolean ready=mc.getIntegratedServer().callFromMainThread(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);
                for(EntityGroundVehicle e:VehicleLookup.loaded(world))if(e.structure!=null&&e.posX>BASE.getX()-5&&e.posX<BASE.getX()+15){vehicle=e.getUniqueID();if(!e.transferInProgress){require(!e.hover,"ordinary wheeled craft enters Taxi");return true;}}
                return false;
            }).get();
            if(ready){stage=2;ticks=0;}return false;
        }
        EntityGroundVehicle observed=client(mc);
        if(stage==2){
            if(ticks<25||observed==null||observed.view==null)return false;
            int screens=0,cabinets=0,tvs=0;
            for(TileEntity tile:observed.view.tiles.values()){
                String type=tile.getClass().getName();
                if(type.endsWith("TileEntityScreen")){
                    require(tile.getClass().getField("screen").get(tile)!=null,"carried Galacticraft screen renderer initialized");
                    BufferedImage image=screen(mc,tile);int ink=0;for(int color:image.getRGB(0,0,128,128,null,0,128))if((color&0xffffff)!=0x203040)ink++;
                    require(ink>300,"carried Galacticraft display draws visible pixels: "+ink);
                    if(screens++==0)javax.imageio.ImageIO.write(image,"png",new File(output,"shot_foreign_building_screen.png"));
                }
                if(type.endsWith("TileEntityCabinet")){require(((net.minecraft.inventory.IInventory)tile).getStackInSlot(0).getCount()==3,"carried furniture inventory retained");cabinets++;}
                if(type.endsWith("TileEntityTV")){require(((Number)tile.getClass().getMethod("getWidth").invoke(tile)).intValue()>0,"native TV dimensions retained");tvs++;}
            }
            require(screens==4&&cabinets==1&&tvs==1,"all native foreign tiles reached the client");
            mc.getIntegratedServer().callFromMainThread(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityGroundVehicle e=server(mc);e.stopForParking();actor(mc).dismountRidingEntity();e.rotationYaw=90;
                VehicleRotation.Plan plan=VehicleRotation.plan(e);parked=new ArrayList<>(plan.structure.cells);parkedOrigin=plan.origin;parkedSeat=plan.origin.add(plan.structure.seat);
                VehicleTransfer.park(world,e,plan.origin,actor(mc),false);require(e.isDead&&!VehicleTransfer.pending(world),"parking completes native ownership handoff");
                for(VehicleStructure.Cell cell:parked)require(world.getBlockState(parkedOrigin.add(cell.pos))==cell.state,"native rotated state restored");
                return null;
            }).get();stage=3;ticks=0;return false;
        }
        if(stage==3&&ticks>100){
            mc.getIntegratedServer().callFromMainThread(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);VehicleScan scan=new VehicleScan(world,parkedSeat);for(int n=0;n<1000&&!scan.step(256);n++);
                require(scan.result!=null&&scan.error==null,"native parked build can be captured again: "+scan.error);
                int screens=0,cabinets=0;
                for(VehicleStructure.Cell cell:parked){TileEntity tile=world.getTileEntity(parkedOrigin.add(cell.pos));if(tile==null)continue;String name=tile.getClass().getName();
                    if(name.endsWith("TileEntityScreen")){require(tile.writeToNBT(new NBTTagCompound()).getInteger("type")==2,"parked Galacticraft channel retained");screens++;}
                    if(name.endsWith("TileEntityCabinet")){require(((net.minecraft.inventory.IInventory)tile).getStackInSlot(0).getCount()==3,"parked cabinet items retained exactly once");cabinets++;}
                }
                require(screens==4&&cabinets==1,"native screen rectangle and furniture remain after world ticks");return null;
            }).get();
            System.out.println("[vandorlabs] foreign-building-live PASS real player click async Taxi assembly mixed coil/furniture/GC/Thermal platform four Galacticraft TESR frames TV constructor dimensions quarter-turn native parking world ticks recapture exact cabinet contents");complete=true;return true;
        }
        return false;
    }
    private static Block block(String id){Block b=Block.getBlockFromName(id);require(b!=null,"installed fixture block "+id);return b;}
    private static void place(WorldServer world,EntityPlayerMP actor,BlockPos pos,String id,int meta){
        IBlockState state=block(id).getStateFromMeta(meta);world.setBlockState(pos,state,2);state.getBlock().onBlockPlacedBy(world,pos,state,actor,new ItemStack(state.getBlock()));
    }
    private static void setup(WorldServer world,EntityPlayerMP actor){
        actor.dismountRidingEntity();actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();
        for(BlockPos p:BlockPos.getAllInBox(BASE.add(-3,-1,-3),BASE.add(12,7,8))){world.getChunkFromBlockCoords(p);world.setBlockState(p,p.getY()==BASE.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
        BlockTelescopicLandingGear gear=null;for(Block b:Block.REGISTRY)if(b instanceof BlockTelescopicLandingGear&&!(b instanceof BlockNonWheeledLandingGear)){gear=(BlockTelescopicLandingGear)b;break;}
        require(gear!=null,"native wheel support fixture");for(BlockPos p:Arrays.asList(BASE,BASE.east(8),BASE.south(4))){world.setBlockState(p,gear.getDefaultState(),2);require(((TileEntityLandingGear)world.getTileEntity(p)).configure(0,0,0,0),"retracted supports");}
        for(int x=0;x<=8;x++)for(int z=0;z<=4;z++)world.setBlockState(BASE.add(x,1,z),Blocks.STONE.getDefaultState(),2);
        place(world,actor,SEAT,"vandorlabs:pilot_seat",0);place(world,actor,BASE.add(1,2,1),"vandorlabs:vehicle_controller",0);place(world,actor,BASE.add(3,2,1),"vandorlabs:impulse_engine",0);
        place(world,actor,BASE.add(1,2,2),"libvulpes:coil0",0);place(world,actor,BASE.add(4,2,2),"cfm:cabinet_oak",0);
        ((net.minecraft.inventory.IInventory)world.getTileEntity(BASE.add(4,2,2))).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,3));
        place(world,actor,BASE.add(4,2,3),"cfm:modern_tv",0);place(world,actor,BASE.add(7,2,2),"thermalfoundation:glass",0);place(world,actor,BASE.add(8,2,2),"galacticraftcore:slab_gc_half",0);
        for(int x=0;x<2;x++)for(int y=0;y<2;y++){
            BlockPos p=BASE.add(5+x,2+y,2);IBlockState state=block("galacticraftcore:view_screen").getStateFromMeta(EnumFacing.NORTH.getIndex());world.setBlockState(p,state,2);TileEntity tile=world.getTileEntity(p);NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());
            data.setInteger("type",2);data.setInteger("connectionsLeft",x);data.setInteger("connectionsRight",1-x);data.setInteger("connectionsDown",y);data.setInteger("connectionsUp",1-y);data.setBoolean("multiscreen",true);tile.readFromNBT(data);tile.markDirty();
        }
        actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();actor.connection.setPlayerLocation(BASE.getX()+2.5,BASE.getY()+4,BASE.getZ()-.5,0,40);
    }
    @SuppressWarnings("unchecked") private static BufferedImage screen(Minecraft mc,TileEntity tile){
        Framebuffer frame=new Framebuffer(128,128,true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.6,.6,-.6,.6,-4,4);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try{
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);mc.entityRenderer.disableLightmap();
            EnumFacing face=EnumFacing.getFront(tile.getBlockMetadata());if(face.getAxis().isHorizontal())GlStateManager.rotate(face.getHorizontalAngle(),0,1,0);else GlStateManager.rotate(face==EnumFacing.UP?90:-90,1,0,0);
            TileEntitySpecialRenderer<TileEntity> renderer=TileEntityRendererDispatcher.instance.getRenderer(tile);require(renderer!=null,"native Galacticraft renderer registered");renderer.render(tile,-.5,-.5,-.5,0,-1,1);
            return net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private ForeignBuildingLiveChecks(){}
}
