package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Real client render and interaction round trips for every vehicle mode. */
final class VehicleStatusClientRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(184,120,82),SCREEN=new BlockPos(0,1,0),CONTROL=new BlockPos(1,1,0),INPUT=new BlockPos(2,1,2),CONSOLE=new BlockPos(3,1,0);
    private static UUID id;private static int stage,frame,total;
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("vehicle status client: "+what);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)world(mc).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;return null;}
    static void begin(Minecraft mc)throws Exception{
        mc.gameSettings.thirdPersonView=0;mc.gameSettings.hideGUI=false;
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);p.setHeldItem(EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,6,7)))w.setBlockToAir(at);
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,-1,7)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            w.setBlockState(BASE.add(1,-1,0),ModBlocks.WAYPOINT.getDefaultState(),3);((TileEntityWaypoint)w.getTileEntity(BASE.add(1,-1,0))).autoDepart=false;
            w.setBlockState(BASE.add(SCREEN),ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),3);((TileEntityAnimatedScreenSelector)w.getTileEntity(BASE.add(SCREEN))).setSurfaceTexture(0,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);
            w.setBlockState(BASE.add(CONTROL),ModBlocks.AUTOPILOT.getDefaultState(),3);p.setPositionAndUpdate(BASE.getX()+.5,BASE.getY()+.5,BASE.getZ()-2);
        }).get();
    }
    private static void spawn(Minecraft mc){
        WorldServer w=world(mc);w.setBlockToAir(BASE.add(SCREEN));w.setBlockToAir(BASE.add(CONTROL));List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int x=0;x<=4;x++)for(int z=0;z<=3;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z),Blocks.STONE.getDefaultState(),null));
        for(BlockPos pos:Arrays.asList(SCREEN,INPUT,CONSOLE)){
            net.minecraft.block.Block block=pos.equals(SCREEN)?ModBlocks.ANIMATED_SCREEN_SELECTOR:pos.equals(INPUT)?ModBlocks.PROGRAMMABLE_INPUT:ModBlocks.PROGRAMMABLE_HALF_CONSOLE;
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)block.createTileEntity(w,block.getDefaultState());tile.setPos(pos);tile.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);if(pos.equals(CONSOLE))tile.setSurfaceTexture(1,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);
            cells.add(new VehicleStructure.Cell(pos,block.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));
        }
        TileEntityAutopilot controller=new TileEntityAutopilot();controller.setPos(CONTROL);cells.add(new VehicleStructure.Cell(CONTROL,ModBlocks.AUTOPILOT.getDefaultState(),controller.writeToNBT(new NBTTagCompound())));
        BlockPos seatPos=new BlockPos(4,1,1);TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(seatPos);seat.setTaxiMode(false);seat.owner=player(mc).getUniqueID();cells.add(new VehicleStructure.Cell(seatPos,ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())));
        String[] names={"antigravity_plate","repulsor_array","vertical_hover_thruster"};for(int i=0;i<3;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,1,3),net.minecraft.block.Block.getBlockFromName("vandorlabs:"+names[i]).getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(new BlockPos(4,1,3),net.minecraft.block.Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));
        EntityGroundVehicle craft=new EntityGroundVehicle(w);craft.install(new VehicleStructure(cells,seatPos,EnumFacing.NORTH,1));craft.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());require(w.spawnEntity(craft),"fixture spawn");id=craft.getUniqueID();
    }
    private static void aim(Minecraft mc,BlockPos local,boolean vehicle){
        EntityGroundVehicle craft=vehicle?client(mc):null;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)(vehicle?craft.view.getTileEntity(local):mc.world.getTileEntity(BASE.add(local)));
        net.minecraft.block.state.IBlockState state=vehicle?craft.view.getBlockState(local):mc.world.getBlockState(BASE.add(local));
        if(local.equals(SCREEN))for(int y=0;y<=64;y++)for(int x=0;x<=32;x++){
            Vec3d a=new Vec3d(x/32D,y/32D,-2),b=a.addVector(0,0,4);
            double column=RedstoneScreenInteractions.hitColumn(state,tile,0,a,b);
            if(column<70||column>100||RedstoneScreenInteractions.hitRow(state,tile,0,a,b,1)!=0)continue;
            Vec3d eye=vehicle?craft.toWorld(a.add(new Vec3d(local))):a.add(new Vec3d(BASE.add(local))),at=vehicle?craft.toWorld(b.add(new Vec3d(local))):b.add(new Vec3d(BASE.add(local)));
            Vec3d delta=at.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
            mc.player.setPositionAndRotation(eye.x,eye.y-mc.player.getEyeHeight(),eye.z,yaw,pitch);mc.player.prevRotationYaw=mc.player.rotationYawHead=mc.player.prevRotationYawHead=yaw;mc.player.prevRotationPitch=pitch;
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,yaw,pitch,false));return;
        }
        // Approach half surfaces along their normal, keeping the player above or outside the hull.
        // The horizontal console deck cannot be intersected by a horizontal ray.
        com.vandorlabs.render.ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile,0);double u=85/128D,v=(RedstoneScreenInteractions.rowTop(state.getBlock(),0)+5D)/RedstoneScreenInteractions.displayHeight(state.getBlock(),0);
        Vec3d point=new Vec3d((q.topRight.x+(q.topLeft.x-q.topRight.x)*u)/16,(q.topRight.y+(q.bottomRight.y-q.topRight.y)*v)/16,(q.topRight.z+(q.bottomRight.z-q.topRight.z)*v)/16),normal=new Vec3d(q.nx,q.ny,q.nz),a=point.add(normal.scale(3)),b=point.subtract(normal.scale(.25));
        require(RedstoneScreenInteractions.hitRow(state,tile,0,a,b,1)==0,"normal ray intersects console status row");
        Vec3d eye=vehicle?craft.toWorld(a.add(new Vec3d(local))):a.add(new Vec3d(BASE.add(local))),at=vehicle?craft.toWorld(b.add(new Vec3d(local))):b.add(new Vec3d(BASE.add(local)));
        Vec3d delta=at.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        mc.player.setPositionAndRotation(eye.x,eye.y-mc.player.getEyeHeight(),eye.z,yaw,pitch);mc.player.prevRotationYaw=mc.player.rotationYawHead=mc.player.prevRotationYawHead=yaw;mc.player.prevRotationPitch=pitch;
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,yaw,pitch,false));
    }
    private static void click(Minecraft mc,boolean vehicle)throws Exception{
        aim(mc,SCREEN,vehicle);
        if(vehicle){VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);require(target!=null&&target.hit.action==VehicleSelection.Action.SCREEN&&target.hit.cell.pos.equals(SCREEN),"captured status screen selected");mc.objectMouseOver=new RayTraceResult(RayTraceResult.Type.MISS,mc.player.getPositionEyes(1),EnumFacing.UP,BlockPos.ORIGIN);net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);}
        else mc.playerController.processRightClickBlock(mc.player,mc.world,BASE.add(SCREEN),EnumFacing.NORTH,new Vec3d(BASE.add(SCREEN)).addVector(.5,.8,0),EnumHand.MAIN_HAND);
    }
    private static void state(Minecraft mc,boolean on,boolean vehicle)throws Exception{
        TileEntityAutopilot tile=vehicle?client(mc).autopilot.controller():(TileEntityAutopilot)mc.world.getTileEntity(BASE.add(CONTROL));require(tile!=null&&tile.enabled()==on,"client autopilot label state "+on);
        mc.getIntegratedServer().addScheduledTask(()->{TileEntityAutopilot actual=vehicle?server(mc).autopilot.controller():(TileEntityAutopilot)world(mc).getTileEntity(BASE.add(CONTROL));require(actual.enabled()==on,"server click round trip "+on);}).get();
    }
    private static void shot(Minecraft mc,File output,String name,int background)throws Exception{
        java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());javax.imageio.ImageIO.write(image,"png",new File(output,"shot_vehicle_status_"+name+".png"));
        int pixels=0,indicator=0,button=name.endsWith("_on")?0x37D8AD:name.endsWith("_off")?0x495C69:-1;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y);if(near(c,background))pixels++;if(button>=0&&near(c,button))indicator++;}require(pixels>=25,"rendered "+name+" mode background pixels="+pixels);if(button>=0)require(indicator>=10,"rendered "+name+" autopilot indicator pixels="+indicator);
    }
    private static boolean near(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))<=4&&Math.abs((a>>8&255)-(b>>8&255))<=4&&Math.abs((a&255)-(b&255))<=4;}
    private static void next(){stage++;frame=0;}
    static boolean tick(Minecraft mc,File output)throws Exception{
        require(++total<550,"timeout stage="+stage);if(++frame<25)return false;
        if(stage==0){if(!(mc.world.getTileEntity(BASE.add(SCREEN)) instanceof TileEntityAnimatedScreenSelector))return false;aim(mc,SCREEN,false);next();return false;}
        if(stage==1){shot(mc,output,"block_off",0x362B19);click(mc,false);next();return false;}
        if(stage==2){state(mc,true,false);shot(mc,output,"block_on",0x362B19);click(mc,false);next();return false;}
        if(stage==3){state(mc,false,false);mc.getIntegratedServer().addScheduledTask(()->spawn(mc)).get();next();return false;}
        if(stage==4){if(client(mc)==null||client(mc).structure==null)return false;aim(mc,SCREEN,true);next();return false;}
        if(stage==5){shot(mc,output,"taxi_off",0x153A29);click(mc,true);next();return false;}
        if(stage==6){state(mc,true,true);shot(mc,output,"taxi_on",0x153A29);click(mc,true);next();return false;}
        if(stage==7){state(mc,false,true);mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle craft=server(mc);require(craft.setHover(true),"Hover status fixture capability");craft.hoverTarget=craft.posY;((TileEntityWaypoint)world(mc).getTileEntity(BASE.add(1,-1,0))).stopMode=2;}).get();next();return false;}
        if(stage==8){require(client(mc).hover,"Hover mode reached client renderer");shot(mc,output,"hover_off",0x152D48);click(mc,true);next();return false;}
        if(stage==9){state(mc,true,true);shot(mc,output,"hover_on",0x152D48);aim(mc,INPUT,true);next();return false;}
        if(stage==10){VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);require(target!=null&&target.hit.cell.pos.equals(INPUT),"half input remains in actual camera view");shot(mc,output,"half_input",0x152D48);aim(mc,CONSOLE,true);next();return false;}
        if(stage==11){VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);require(target!=null&&target.hit.cell.pos.equals(CONSOLE),"half console remains in actual camera view");shot(mc,output,"half_console",0x152D48);mc.getIntegratedServer().addScheduledTask(()->{server(mc).setDead();for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,6,7)))world(mc).setBlockToAir(at);}).get();System.out.println("[vandorlabs][reprolab] vehicle-status-client PASS Block-Taxi-Hover distinct-rendered-backgrounds real-screen-toggles client-server-sync half-input half-console");return true;}
        return false;
    }
    private VehicleStatusClientRuntimeChecks(){}
}
