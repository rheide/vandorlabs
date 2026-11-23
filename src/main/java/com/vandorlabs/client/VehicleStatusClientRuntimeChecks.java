package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.Block;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Real client render and interaction round trips for every vehicle mode. */
final class VehicleStatusClientRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(184,120,82),SCREEN=new BlockPos(0,1,0),CONTROL=new BlockPos(1,1,0),INPUT=new BlockPos(2,1,0),CONSOLE=new BlockPos(3,1,0);
    private static final BlockPos ENGINE=new BlockPos(5,1,0);
    private static final BlockPos ENGINE_SIGNAL=BASE.add(5,0,-3);
    private static int action;private static UUID id;private static int stage,frame,total;private static Vec3d expectedEye;
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("vehicle status client: "+what);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)world(mc).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;return null;}
    static void nativeState(Minecraft mc)throws Exception{
        try(TurntableRenderState ignored=new TurntableRenderState()){
            // Reproduce a native renderer changing the driver without updating Minecraft's cache.
            net.minecraft.client.renderer.GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            net.minecraft.client.renderer.GlStateManager.disableCull();org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL13.glActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
            net.minecraft.client.renderer.BufferBuilder local=net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();local.setTranslation(37,41,43);
            java.lang.reflect.Method white=RedstoneScreenRenderer.class.getDeclaredMethod("bindWhite");white.setAccessible(true);white.invoke(null);
            require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE)==net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit,"screen restores the physical atlas texture unit");
            require(!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE),"screen clears a stale driver culling state");
            require(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D)==mc.getTextureManager().getTexture(RenderIdentifierCache.white()).getGlTextureId(),"screen binds white after stale native texture state");
            local.begin(org.lwjgl.opengl.GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION);local.pos(0,0,0).endVertex();local.finishDrawing();
            require(local.getByteBuffer().getFloat(0)==0&&local.getByteBuffer().getFloat(4)==0&&local.getByteBuffer().getFloat(8)==0,"screen clears another tile's singleton buffer translation");local.reset();
        }
        System.out.println("[vandorlabs][reprolab] screen-native-state PASS stale-active-unit binding culling local-buffer-offset");
    }
    static void begin(Minecraft mc)throws Exception{
        mc.gameSettings.thirdPersonView=0;mc.gameSettings.hideGUI=false;
        // The elevated fixture camera approaches the cloud layer; keep pixel checks unobscured.
        mc.gameSettings.clouds=0;
        if("1".equals(System.getenv("VANDOR_LABS_TEST_MAXWELL_FLIGHT_ONLY"))){stage=18;VehicleFlightClientChecks.begin(mc);return;}
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=world(mc);EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);p.setHeldItem(EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,6,7)))w.setBlockToAir(at);
            for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,-1,7)))w.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            w.setBlockState(BASE.add(1,-1,0),ModBlocks.WAYPOINT.getDefaultState(),3);((TileEntityWaypoint)w.getTileEntity(BASE.add(1,-1,0))).autoDepart=false;
            w.setBlockState(BASE.add(SCREEN),ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),3);((TileEntityAnimatedScreenSelector)w.getTileEntity(BASE.add(SCREEN))).setSurfaceTexture(0,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);
            w.setBlockState(BASE.add(INPUT),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),3);
            net.minecraft.block.Block rack=net.minecraft.block.Block.getBlockFromName("vandorlabs:module_rack");
            w.setBlockState(BASE.add(CONTROL),rack.getDefaultState(),3);
            require(((TileEntityModuleRack)w.getTileEntity(BASE.add(CONTROL))).insertModule(0,new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.AUTOPILOT_MODULE),false),"parked Navigation module installed");
            p.setPositionAndUpdate(BASE.getX()+.5,BASE.getY()+.5,BASE.getZ()-2);
        }).get();
    }
    private static void spawn(Minecraft mc){
        WorldServer w=world(mc);w.setBlockToAir(BASE.add(SCREEN));w.setBlockToAir(BASE.add(CONTROL));w.setBlockToAir(BASE.add(INPUT));List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int x=0;x<=4;x++)for(int z=0;z<=3;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z),Blocks.STONE.getDefaultState(),null));
        for(BlockPos pos:Arrays.asList(SCREEN,INPUT,CONSOLE)){
            net.minecraft.block.Block block=pos.equals(SCREEN)?ModBlocks.ANIMATED_SCREEN_SELECTOR:pos.equals(INPUT)?ModBlocks.PROGRAMMABLE_INPUT:ModBlocks.PROGRAMMABLE_HALF_CONSOLE;
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)block.createTileEntity(w,block.getDefaultState());tile.setPos(pos);tile.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);if(pos.equals(CONSOLE))tile.setSurfaceTexture(1,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);
            cells.add(new VehicleStructure.Cell(pos,block.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));
        }
        TileEntityAutopilot controller=new TileEntityAutopilot();controller.setPos(CONTROL);cells.add(new VehicleStructure.Cell(CONTROL,ModBlocks.AUTOPILOT.getDefaultState(),controller.writeToNBT(new NBTTagCompound())));
        TileEntityVehicleController modes=new TileEntityVehicleController();modes.setPos(new BlockPos(4,1,0));modes.owner=player(mc).getUniqueID();cells.add(new VehicleStructure.Cell(modes.getPos(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),modes.writeToNBT(new NBTTagCompound())));
        BlockPos seatPos=new BlockPos(4,1,1);TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(seatPos);seat.setTaxiMode(false);seat.owner=player(mc).getUniqueID();cells.add(new VehicleStructure.Cell(seatPos,ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())));
        String[] names={"antigravity_plate","repulsor_array","vertical_hover_thruster"};for(int i=0;i<3;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,1,3),net.minecraft.block.Block.getBlockFromName("vandorlabs:"+names[i]).getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(new BlockPos(4,1,3),net.minecraft.block.Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));
        addEngine(w,cells);
        EntityGroundVehicle craft=new EntityGroundVehicle(w);craft.install(new VehicleStructure(cells,seatPos,EnumFacing.NORTH,1));craft.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());require(w.spawnEntity(craft),"fixture spawn");id=craft.getUniqueID();
    }
    private static void addEngine(WorldServer world,List<VehicleStructure.Cell> cells){
        BlockPos source=BASE.add(18,3,0), offset=ENGINE.subtract(source);
        MaxwellRuntimeChecks.assemble(world,source,3,EnumFacing.NORTH,0,0);
        for(BlockPos at:BlockPos.getAllInBox(source,source.add(2,2,3))){
            TileEntity tile=world.getTileEntity(at);
            require(tile instanceof TileEntityConstructor||tile instanceof TileEntityConstructorPart,"formed Maxwell occupies complete carried footprint");
            cells.add(new VehicleStructure.Cell(at.add(offset),world.getBlockState(at),
                    VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),offset)));
        }
        for(BlockPos at:BlockPos.getAllInBox(source,source.add(2,2,3)))world.setBlockToAir(at);
        cells.add(new VehicleStructure.Cell(ENGINE.down(),Blocks.STONE.getDefaultState(),null));
    }
    private static void aim(Minecraft mc,BlockPos local,boolean vehicle)throws Exception{
        EntityGroundVehicle craft=vehicle?client(mc):null;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)(vehicle?craft.view.getTileEntity(local):mc.world.getTileEntity(BASE.add(local)));
        net.minecraft.block.state.IBlockState state=vehicle?craft.view.getBlockState(local):mc.world.getBlockState(BASE.add(local));
        // Approach half surfaces along their normal, keeping the player above or outside the hull.
        // The horizontal console deck cannot be intersected by a horizontal ray.
        com.vandorlabs.render.ScreenSurface.Quad q=RedstoneScreenInteractions.surface(state,tile,0);
        int height=RedstoneScreenInteractions.displayHeight(state.getBlock(),0);
        if(tile.hasSplitVehiclePanel(0)){q=com.vandorlabs.render.ScreenSurface.half(q,false);height/=2;}
        com.vandorlabs.screen.VehicleStatusLayout layout=com.vandorlabs.screen.VehicleStatusLayout.forHeight(height);
        int mode=action==VehicleStatus.FLIGHT_ACTION?com.vandorlabs.screen.VehicleStatusLayout.FLIGHT:action-1;
        double u=(action==0?85:action==4?25:layout.centerX(mode))/128D;
        double v=(action==0?(layout.autopilotTop+layout.autopilotBottom)/2D:action==4?(layout.footerTop+layout.footerBottom)/2D:layout.centerY(mode))/height;
        Vec3d point=new Vec3d((q.topRight.x+(q.topLeft.x-q.topRight.x)*u)/16,(q.topRight.y+(q.bottomRight.y-q.topRight.y)*v)/16,(q.topRight.z+(q.bottomRight.z-q.topRight.z)*v)/16),normal=new Vec3d(q.nx,q.ny,q.nz),a=point.add(normal.scale(3)).addVector(0,1.8,0),b=point.subtract(a.subtract(point).scale(.1));
        require(RedstoneScreenInteractions.statusAction(state,tile,0,a,b)==action,"normal ray intersects selected status button");
        Vec3d eye=vehicle?craft.toWorld(a.add(new Vec3d(local))):a.add(new Vec3d(BASE.add(local))),at=vehicle?craft.toWorld(b.add(new Vec3d(local))):b.add(new Vec3d(BASE.add(local)));
        Vec3d delta=at.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        expectedEye=eye;
        if(!local.equals(SCREEN))mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.motionX=p.motionY=p.motionZ=0;p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,yaw,pitch);require(world(mc).getCollisionBoxes(p,p.getEntityBoundingBox().grow(-.01)).isEmpty(),"screen camera body is clear of terrain and hull at "+eye);}).get();
        else if(!vehicle)mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.setPositionAndRotation(eye.x,eye.y-p.getEyeHeight(),eye.z,yaw,pitch);require(VehicleStatus.hasController(world(mc),BASE.add(SCREEN))&&VehicleStatus.find(world(mc),BASE.add(SCREEN))!=null,"server locates parked Status controllers");}).get();
        mc.player.setSneaking(false);mc.player.capabilities.allowFlying=true;mc.player.capabilities.isFlying=true;mc.player.motionX=mc.player.motionY=mc.player.motionZ=0;mc.player.setPositionAndRotation(eye.x,eye.y-mc.player.getEyeHeight(),eye.z,yaw,pitch);mc.player.prevRotationYaw=mc.player.rotationYawHead=mc.player.prevRotationYawHead=yaw;mc.player.prevRotationPitch=pitch;
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.PositionRotation(mc.player.posX,mc.player.posY,mc.player.posZ,yaw,pitch,false));
        System.out.println("[vandorlabs][reprolab] status-camera local="+local+" expected-eye="+eye+" yaw="+yaw+" pitch="+pitch+" normal="+normal);
    }
    private static String targetDiagnostic(Minecraft mc,VehicleInteraction.Target target){return " expected-eye="+expectedEye+" actual-eye="+mc.player.getPositionEyes(1)+" target="+(target==null?"none":target.hit.cell.pos+"/"+target.hit.action)+" yaw="+mc.player.rotationYaw+" pitch="+mc.player.rotationPitch;}
    private static void click(Minecraft mc,boolean vehicle,int selected)throws Exception{action=selected;try{click(mc,vehicle);}finally{action=0;}}
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
        String shaderpack=System.getenv("VANDOR_LABS_TEST_SHADERPACK");
        if(shaderpack!=null&&!shaderpack.isEmpty()){
            shaderIndicator(image,name);
            return;
        }
        int pixels=0,indicator=0,button=name.endsWith("_on")?0x37D8AD:name.endsWith("_off")?0x495C69:-1;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y);if(near(c,background))pixels++;if(button>=0&&near(c,button))indicator++;}require(pixels>=25,"rendered "+name+" mode background pixels="+pixels);if(button>=0)require(indicator>=10,"rendered "+name+" autopilot indicator pixels="+indicator);
    }
    private static final Map<String,Integer> shaderOff=new HashMap<>();
    private static void shaderIndicator(java.awt.image.BufferedImage image,String name){
        int x0=image.getWidth()/2,y0=image.getHeight()/2,hits=0;
        // The camera points at the Autopilot strip for both images. Count its saturated
        // indicator relative to the same screen region before the toggle and shader grade.
        for(int y=y0-24;y<y0+24;y++)for(int x=x0-45;x<x0+45;x++){
            int color=image.getRGB(x,y),r=color>>16&255,g=color>>8&255,b=color&255;
            if(g>=90&&g-r>=35&&b-r>=15)hits++;
        }
        if(name.endsWith("_off"))shaderOff.put(name.substring(0,name.length()-4),hits);
        if(name.endsWith("_on")){
            int before=shaderOff.getOrDefault(name.substring(0,name.length()-3),-1);
            require(before>=0&&hits>=before+10,"shader Autopilot indicator lights up in "+name+" before="+before+" after="+hits);
            System.out.println("[vandorlabs][reprolab] shader status "+name+" colored pixels "+before+" -> "+hits);
        }
    }
    private static boolean near(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))<=4&&Math.abs((a>>8&255)-(b>>8&255))<=4&&Math.abs((a&255)-(b&255))<=4;}
    private static boolean disabledClick, parkedDisabledClick, hoverShotReady;
    private static int parkPowerPhase;
    private static void next(){stage++;frame=0;}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(stage==18)return VehicleFlightClientChecks.tick(mc,output);
        require(++total<950,"timeout stage="+stage);if(++frame<25)return false;
        if(stage==0){if(!(mc.world.getTileEntity(BASE.add(SCREEN)) instanceof TileEntityAnimatedScreenSelector))return false;aim(mc,SCREEN,false);next();return false;}
        if(stage==1){
            if(!parkedDisabledClick){
                require((VehicleStatus.availableModes(mc.world,BASE.add(SCREEN),mc.player)&2)==0,"parked hull has no valid Taxi supports");
                shot(mc,output,"block_off",0x362B19);click(mc,false,2);parkedDisabledClick=true;frame=0;return false;
            }
            mc.getIntegratedServer().addScheduledTask(()->{
                TileEntityVehicleController root=(TileEntityVehicleController)world(mc).getTileEntity(BASE.add(INPUT));
                require(root!=null&&!root.converting,"real click on disabled Taxi leaves blocks stationary");
            }).get();
            click(mc,false);next();return false;
        }
        if(stage==2){state(mc,true,false);shot(mc,output,"block_on",0x362B19);click(mc,false);next();return false;}
        if(stage==3){state(mc,false,false);mc.getIntegratedServer().addScheduledTask(()->spawn(mc)).get();next();return false;}
        if(stage==4){if(client(mc)==null||client(mc).structure==null)return false;aim(mc,SCREEN,true);next();return false;}
        if(stage==5){
            if(!disabledClick){
                require((VehicleStatus.availableModes(client(mc).view,SCREEN,mc.player)&8)==0,"flight is disabled on the Maxwell-free craft");
                shot(mc,output,"taxi_off",0x153A29);click(mc,true,VehicleStatus.FLIGHT_ACTION);disabledClick=true;frame=0;return false;
            }
            mc.getIntegratedServer().addScheduledTask(()->require(!server(mc).flying()&&!server(mc).takeoffRoll(),"real click on disabled Flight keeps Taxi")).get();
            click(mc,true);next();return false;
        }
        if(stage==6){state(mc,true,true);shot(mc,output,"taxi_on",0x153A29);click(mc,true);next();return false;}
        if(stage==7){state(mc,false,true);mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle craft=server(mc);craft.hoverTarget=craft.posY;((TileEntityWaypoint)world(mc).getTileEntity(BASE.add(1,-1,0))).stopMode=2;}).get();click(mc,true,3);next();return false;}
        if(stage==8){
            require(client(mc).hover,"Hover mode reached client renderer");
            if(!hoverShotReady){aim(mc,SCREEN,true);hoverShotReady=true;frame=0;return false;}
            shot(mc,output,"hover_off",0x152D48);click(mc,true);next();return false;
        }
        if(stage==9){state(mc,true,true);shot(mc,output,"hover_on",0x152D48);aim(mc,INPUT,true);next();return false;}
        if(stage==10){VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);require(target!=null&&target.hit.cell.pos.equals(INPUT),"half input remains in actual camera view"+targetDiagnostic(mc,target));shot(mc,output,"half_input",0x152D48);aim(mc,CONSOLE,true);next();return false;}
        if(stage==11){VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);require(target!=null&&target.hit.cell.pos.equals(CONSOLE),"half console remains in actual camera view"+targetDiagnostic(mc,target));shot(mc,output,"half_console",0x152D48);click(mc,true,0);next();return false;}
        if(stage==12){state(mc,false,true);click(mc,true,2);next();return false;}
        if(stage==13){require(!client(mc).hover,"Taxi screen button synchronized");mc.getIntegratedServer().addScheduledTask(()->{TileEntityWaypoint wp=(TileEntityWaypoint)world(mc).getTileEntity(BASE.add(1,-1,0));wp.stopMode=1;for(int z=-1;z>=-3;z--)world(mc).setBlockState(BASE.add(1,-1,z),Blocks.CONCRETE.getDefaultState(),3);}).get();click(mc,true,0);next();return false;}
        if(stage==14){state(mc,true,true);mc.getIntegratedServer().addScheduledTask(()->require(server(mc).autopilot.controller().status.equals("Parked at waypoint"),"waiting before Next pulse")).get();click(mc,true,4);next();return false;}
        if(stage==15){mc.getIntegratedServer().addScheduledTask(()->{require(server(mc).posZ<BASE.getZ()-.1,"Next button leaves manual waypoint: "+server(mc).autopilot.controller().status+" pos="+server(mc).posZ+" route="+server(mc).autopilot.write());EntityGroundVehicle craft=server(mc);craft.autopilot.controller().setEnabled(false);craft.stopForParking();craft.rotationYaw=0;craft.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());startPoweredPark(craft);VehicleNetwork.state(craft);}).get();next();return false;}
        if(stage==16){
            TileEntityAnimatedScreenSelector split=(TileEntityAnimatedScreenSelector)client(mc).view.getTileEntity(SCREEN);
            TileEntityConstructor drive=(TileEntityConstructor)client(mc).view.getTileEntity(ENGINE);
            if(!split.hasSplitVehiclePanel(0))return false;
            if(parkPowerPhase==0&&drive.maxwellActiveLevel()==15){
                mc.getIntegratedServer().addScheduledTask(()->engineSignal(world(mc)).setLocalOn(false)).get();
                parkPowerPhase++;return false;
            }
            if(parkPowerPhase==1&&drive.maxwellActiveLevel()==0){
                mc.getIntegratedServer().addScheduledTask(()->{
                    TileEntityConstructor installed=(TileEntityConstructor)server(mc).view.getTileEntity(ENGINE);
                    require(installed.maxwellFuel()>0&&installed.maxwellActiveLevel()==0&&installed.maxwellThrustLevel(false)==0,
                            "wireless off reaches both carried server and client drive levels");
                    engineSignal(world(mc)).setLocalOn(true);
                }).get();
                parkPowerPhase++;return false;
            }
            if(parkPowerPhase!=2||drive.maxwellActiveLevel()!=15)return false;
            shot(mc,output,"split_halves",0x153A29);
            click(mc,true,1);next();return false;
        }
        if(stage==17){if(client(mc)!=null)return false;mc.getIntegratedServer().addScheduledTask(()->{require(world(mc).getTileEntity(BASE.add(SCREEN)) instanceof TileEntityAnimatedScreenSelector,"Block screen button restores tiles");TileEntityConstructor drive=(TileEntityConstructor)world(mc).getTileEntity(BASE.add(ENGINE));require(drive.formed()&&drive.maxwellFuel()>0&&drive.maxwellActiveLevel()==15,"real Park click restores the entire powered drive and its fuel");for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,6,7)))world(mc).setBlockToAir(at);}).get();stage=18;System.out.println("[vandorlabs][reprolab] vehicle-status-client PASS Block-Taxi-Hover distinct-rendered-backgrounds real-screen-toggles mode-buttons next-waypoint client-server-sync half-input half-console powered-Maxwell Park from split Status");VehicleFlightClientChecks.begin(mc);return false;}
        return false;
    }
    private static void startPoweredPark(EntityGroundVehicle craft){
        WorldServer world=(WorldServer)craft.world;
        world.setBlockState(ENGINE_SIGNAL.down(),Blocks.STONE.getDefaultState(),3);
        Block lever=Block.getBlockFromName("vandorlabs:small_power_lever");
        world.setBlockState(ENGINE_SIGNAL,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true),3);
        TileEntityRedstoneChannel signal=engineSignal(world);
        signal.setRedstoneChannel(531);signal.setLocalOn(true);
        TileEntityConstructor drive=(TileEntityConstructor)craft.view.getTileEntity(ENGINE);
        require(MaxwellRuntimeChecks.inlet(drive,EnumFacing.NORTH).fill(new net.minecraftforge.fluids.FluidStack(MaxwellRuntimeChecks.testFuel(),200),true)==200,"fuel captured drive for parking check");
        drive.configureMachine(new NavigationScreenSettings(-1,false,1,true,false),new int[0],false,127,0,ChannelList.of(531));
        craft.signals.transmitterChanged(drive);
        TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(SCREEN);
        screen.frameAppearance.split=true;
        screen.frameAppearance.splitTop=screen.frameAppearance.splitBottom=TileEntityAnimatedScreenSelector.VEHICLE_SURFACE;
        screen.markDirty();
    }
    private static TileEntityRedstoneChannel engineSignal(WorldServer world){return (TileEntityRedstoneChannel)world.getTileEntity(ENGINE_SIGNAL);}
    private VehicleStatusClientRuntimeChecks(){}
}
