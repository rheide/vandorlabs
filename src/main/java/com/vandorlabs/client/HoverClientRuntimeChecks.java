package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Exercise actual key bindings and network dispatch across client/server ticks. */
final class HoverClientRuntimeChecks {
    private static UUID id;
    private static final Set<Integer> plumeStyles=new HashSet<>();
    private static final Set<ParticleThrusterStream> seenParticles=Collections.newSetFromMap(new IdentityHashMap<>());
    private static int liftBirths,thrustBirths,stationaryLiftStart,sprintThrustMid,stationaryThrustStart,unoccupiedLiftStart;
    private static boolean particleGeometryChecked,finished;
    private static int stage,frame,total;
    private static double altitude,turnX,turnZ,turnYaw,heldX,heldY,heldZ,heldYaw;
    private static final BlockPos ORIGIN=new BlockPos(184,101,48),SEAT=new BlockPos(1,1,0);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("hover client: "+what);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;return null;}
    private static void key(net.minecraft.client.settings.KeyBinding binding,boolean on){KeyBinding.setKeyBindState(binding.getKeyCode(),on);}
    static void begin(Minecraft mc)throws Exception{
        VehicleControls inputs=new VehicleControls();
        for(int i=0;i<VehicleControls.LIMIT*2;i++)require(inputs.offerLatest(i,(byte)1,(byte)1,false),"Hover accepts fresh input during a burst");
        require(inputs.offerLatest(200,(byte)0,(byte)0,false),"Hover accepts release after a burst");
        require(!inputs.offerLatest(199,(byte)1,(byte)1,false),"stale input cannot replace release");
        require(!inputs.offerLatest(201,(byte)2,(byte)0,false),"invalid input cannot replace release");
        VehicleControls.Command latest=inputs.poll();require(latest!=null&&latest.sequence==200&&latest.forward==0&&latest.strafe==0&&inputs.poll()==null&&inputs.acknowledged()==200,"release takes effect on the next step without queued movement");
        System.out.println("[vandorlabs][reprolab] hover-input-burst PASS latest-release next-step bounded-queue stale-invalid-rejected");
        key(mc.gameSettings.keyBindJump,false);key(mc.gameSettings.keyBindSneak,false);key(mc.gameSettings.keyBindSprint,false);
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=player(mc);player.setSneaking(false);player.dismountRidingEntity();
            for(BlockPos at:BlockPos.getAllInBox(ORIGIN.add(-3,0,-3),ORIGIN.add(7,8,5)))world.setBlockToAir(at);
            for(BlockPos at:BlockPos.getAllInBox(ORIGIN.add(-3,-1,-3),ORIGIN.add(7,-1,5)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
            List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));
            TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(SEAT);seat.setTaxiMode(false);seat.owner=player.getUniqueID();cells.add(new VehicleStructure.Cell(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())));
            String[] lift={"antigravity_plate","repulsor_array","vertical_hover_thruster"};for(int i=0;i<3;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,0,1),Block.getBlockFromName("vandorlabs:"+lift[i]).getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.DOWN),null));
            cells.add(new VehicleStructure.Cell(new BlockPos(3,0,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.SOUTH),null));
            cells.add(new VehicleStructure.Cell(new BlockPos(4,0,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
            BlockPos store=new BlockPos(3,0,1);
            net.minecraft.tileentity.TileEntityChest chest=new net.minecraft.tileentity.TileEntityChest();
            chest.setPos(store);
            chest.setInventorySlotContents(0,new net.minecraft.item.ItemStack(Blocks.IRON_BLOCK,64));
            cells.add(new VehicleStructure.Cell(store,Blocks.CHEST.getDefaultState(),chest.writeToNBT(new NBTTagCompound())));
            VehicleStructure loaded=new VehicleStructure(cells,SEAT,EnumFacing.NORTH,1);
            EntityGroundVehicle heavy=new EntityGroundVehicle(world);
            heavy.install(loaded);
            int capacity=VehicleFlight.hoverCapacity(loaded);
            require(capacity==144&&VehicleFlight.canHover(loaded,capacity)&&!VehicleFlight.canHover(loaded,capacity+.01),"mixed lift capacity includes its boundary");
            require(!heavy.setHover(true),"64 carried iron blocks overload this craft");
            ((net.minecraft.tileentity.TileEntityChest)heavy.view.getTileEntity(store)).clear();
            require(heavy.setHover(true),"unloading the chest immediately permits Hover");
            double lightResponse=verticalResponse(heavy);
            heavy.setDead();
            EntityGroundVehicle laden=new EntityGroundVehicle(world);
            laden.install(loaded);
            ((net.minecraft.tileentity.TileEntityChest)laden.view.getTileEntity(store)).setInventorySlotContents(0,new net.minecraft.item.ItemStack(Blocks.IRON_BLOCK,40));
            require(laden.setHover(true),"40 iron blocks remain within the same lift capacity");
            double ladenResponse=verticalResponse(laden);
            require(lightResponse>ladenResponse*2,"cargo reduces lift, descent and braking acceleration");
            System.out.printf(java.util.Locale.ROOT,"[vandorlabs] hover-response PASS empty %.1f weight %.4f blocks/tick^2, loaded %.1f weight %.4f, capacity %d%n",
                    heavy.flightReadout().mass,lightResponse,laden.flightReadout().mass,ladenResponse,capacity);
            laden.setDead();
            cells.remove(cells.size()-1);
            System.out.println("[vandorlabs] hover-weight PASS overload, exact capacity, cargo removal and immediate entry retry");
            EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,1));craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());craft.setTransferInProgress(true);require(world.spawnEntity(craft),"fixture spawn");id=craft.getUniqueID();player.setPositionAndUpdate(ORIGIN.getX()-2,ORIGIN.getY()+2,ORIGIN.getZ());
        }).get();
    }
    private static double verticalResponse(EntityGroundVehicle craft){
        double y=ORIGIN.getY()+64;
        craft.setPosition(ORIGIN.getX(),y,ORIGIN.getZ());
        double response=0;
        for(int direction:new int[]{1,-1}){
            craft.motionY=0;
            craft.hoverTarget=y+direction*30;
            double start=craft.posY;
            craft.onUpdate();
            double gain=(craft.posY-start)*direction;
            require(gain>0&&gain<EntityGroundVehicle.HOVER_VERTICAL_SPEED,"bounded first step toward selected altitude");
            if(direction==1)response=gain;
            else require(Math.abs(gain-response)<1e-8,"same load response on descent");
            for(int i=0;i<20;i++)craft.onUpdate();
            double speed=craft.motionY*direction;
            require(Math.abs(speed-EntityGroundVehicle.HOVER_VERTICAL_SPEED)<1e-8,"both loads reach vertical cruise speed, direction="+direction+" speed="+speed+" y="+craft.posY);
            craft.hoverTarget=craft.posY;
            craft.onUpdate();
            require(Math.abs(speed-craft.motionY*direction-response)<1e-8,"altitude hold brakes at measured load response");
            craft.setPosition(ORIGIN.getX(),y,ORIGIN.getZ());
        }
        return response;
    }
    private static void next(){stage++;frame=0;}
    private static void tap(Minecraft mc){if(frame==0 || frame==4)key(mc.gameSettings.keyBindJump,true);if(frame==2 || frame==6)key(mc.gameSettings.keyBindJump,false);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(finished)return true;
        require(++total<650,"timeout stage="+stage+" frame="+frame);EntityGroundVehicle craft=client(mc);
        if(craft!=null && craft.hover && craft.structure!=null){observePlumes(mc,craft);if(!particleGeometryChecked){particleGeometry(mc);particleGeometryChecked=true;}}
        if(stage==0){if(craft!=null && craft.structure!=null && frame>=25){mc.getIntegratedServer().addScheduledTask(()->require(server(mc).board(player(mc)),"server boards fixture")).get();next();return false;}}
        else if(stage==1){if(frame>=25 && mc.player.getRidingEntity()==craft){require(craft.transferInProgress,"spawn synchronizes conversion hold before prediction");key(mc.gameSettings.keyBindForward,true);stage=15;frame=0;return false;}}
        else if(stage==15){if(frame>=20){
            mc.getIntegratedServer().addScheduledTask(()->require(server(mc).acknowledgedInput()==-1,"conversion hold does not accept queued movement")).get();
            require(craft.transferInProgress&&craft.posX==ORIGIN.getX()&&craft.posY==ORIGIN.getY()&&craft.posZ==ORIGIN.getZ(),"held client does not predict motion while forward is pressed");
            require(craft.collision.terrainQueries==0,"conversion hold performs no terrain collision queries");
            VehicleNetwork.Packet correction=new VehicleNetwork.Packet(VehicleNetwork.STATE);correction.serverTick=mc.world.getTotalWorldTime()+1;correction.sequence=0;correction.x=craft.posX;correction.y=craft.posY;correction.z=craft.posZ;
            craft.authoritative(correction);require(craft.collision.terrainQueries==0,"held correction does not replay queued input collision");
            mc.getIntegratedServer().addScheduledTask(()->server(mc).setTransferInProgress(false)).get();stage=16;frame=0;return false;
        }}
        else if(stage==16){if(frame>=20){require(!craft.transferInProgress&&craft.driveSpeed>0,"metadata release resumes normal client prediction");key(mc.gameSettings.keyBindForward,false);mc.getIntegratedServer().addScheduledTask(()->{server(mc).stopForParking();server(mc).setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());}).get();craft.stopForParking();craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());System.out.println("[vandorlabs][reprolab] vehicle-transfer-client-handoff PASS spawn-hold forward-held zero-terrain-queries no-replay synchronized-release");stage=2;frame=0;return false;}}
        else if(stage==2){tap(mc);if(frame>=30){require(craft.hover && mc.player.getRidingEntity()==craft,"double-space enables Hover on client");mc.getIntegratedServer().addScheduledTask(()->require(server(mc).hover,"double-space enables Hover on server")).get();altitude=craft.posY;key(mc.gameSettings.keyBindJump,true);next();return false;}}
        else if(stage==3){if(frame>=20){key(mc.gameSettings.keyBindJump,false);require(craft.posY>altitude+4,"held Space reaches the faster Hover ascent rate");next();return false;}}
        else if(stage==4){if(frame>=15){altitude=craft.posY;key(mc.gameSettings.keyBindSneak,true);next();return false;}}
        else if(stage==5){if(frame>=20){key(mc.gameSettings.keyBindSneak,false);require(mc.player.getRidingEntity()==craft && craft.hover && craft.posY<altitude-4,"Sneak reaches the faster Hover descent rate without dismounting");mc.getIntegratedServer().addScheduledTask(()->require(player(mc).getRidingEntity()==server(mc),"server preserves Hover rider while descending")).get();next();return false;}}
        else if(stage==6){if(frame>=20){altitude=craft.posY;next();return false;}}
        else if(stage==7){if(frame==0)stationaryLiftStart=liftBirths;if(frame>=40){require(liftBirths-stationaryLiftStart>=3,"stationary Hover continuously emits sparse lift particles");require(Math.abs(craft.posY-altitude)<.1,"released vertical controls hold height on client");javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_hover_controls.png"));turnX=craft.posX;turnZ=craft.posZ;turnYaw=craft.rotationYaw;key(mc.gameSettings.keyBindRight,true);stage=11;frame=0;return false;}}
        else if(stage==11){if(frame>=20){key(mc.gameSettings.keyBindRight,false);require(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(craft.rotationYaw-turnYaw))>10 && Math.abs(craft.posX-turnX)<.05 && Math.abs(craft.posZ-turnZ)<.05,"actual A/D key rotates stationary Hover without translation");mc.getIntegratedServer().addScheduledTask(()->require(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(server(mc).rotationYaw-turnYaw))>10 && server(mc).driveSpeed==0,"server accepts stationary Hover steering")).get();stage=12;frame=0;return false;}}
        else if(stage==12){if(frame>=15){stage=8;frame=0;return false;}}
        else if(stage==8){tap(mc);if(frame>=30){require(!craft.hover,"double-space selects Taxi near ground");mc.getIntegratedServer().addScheduledTask(()->require(!server(mc).hover,"server selects Taxi from double-space")).get();next();return false;}}
        else if(stage==9){tap(mc);if(frame>=30){require(craft.hover,"second double-space reenters Hover");mc.getIntegratedServer().addScheduledTask(()->prepareSpeedRun(server(mc))).get();stage=19;frame=0;return false;}}
        else if(stage==19){if(craft.posY>=219.5){key(mc.gameSettings.keyBindForward,true);stage=17;frame=0;return false;}}
        else if(stage==17){if(frame>=48){require(Math.abs(craft.driveSpeed-craft.hoverCruiseSpeed())<.005 && craft.hoverCruiseSpeed()>=EntityGroundVehicle.HOVER_CRUISE_SPEED*com.vandorlabs.physics.FlightTuning.MIN_THRUSTER_SPEED_SHARE-1e-9 && !plumeStyles.contains(3) && plumeStyles.contains(2) && plumeStyles.contains(4),"normal Hover emits lift effects without cruise-engine particles");key(mc.gameSettings.keyBindSprint,true);key(mc.gameSettings.keyBindJump,true);stage=13;frame=0;return false;}}
        else if(stage==13){if(frame==28)sprintThrustMid=thrustBirths;if(frame>=60){require(thrustBirths-sprintThrustMid>=3,"Sprint exhaust keeps emitting after its initial burst");mc.getIntegratedServer().addScheduledTask(()->require(server(mc).driveSpeed>server(mc).hoverCruiseSpeed()&&server(mc).driveSpeed<=server(mc).hoverSprintSpeed()+1e-9,"actual Sprint key boosts server Hover speed")).get();require(Math.abs(craft.driveSpeed-craft.hoverSprintSpeed())<.005,"client predicts capped Sprint speed");key(mc.gameSettings.keyBindForward,false);key(mc.gameSettings.keyBindJump,false);mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle stopped=server(mc);stopped.stopForParking();stopped.hoverTarget=stopped.posY;}).get();craft.stopForParking();craft.hoverTarget=craft.posY;stationaryThrustStart=thrustBirths;stage=18;frame=0;return false;}}
        else if(stage==18){if(frame>=40){require(craft.driveSpeed==0&&thrustBirths-stationaryThrustStart>=2,"Hover Sprint retains a sparse exhaust while stationary");key(mc.gameSettings.keyBindSprint,true);key(mc.gameSettings.keyBindSneak,true);stage=10;frame=0;return false;}}
        else if(stage==10){if(frame>=20){key(mc.gameSettings.keyBindSprint,false);key(mc.gameSettings.keyBindSneak,false);require(mc.player.getRidingEntity()!=craft,"Sprint+Sneak exits Hover on client");mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle entity=server(mc);require(player(mc).getRidingEntity()!=entity && entity.hover && !entity.isDead && !entity.taxiReturnPending,"explicit Hover exit preserves hovering vehicle");require(entity.driveSpeed==0 && entity.motionX==0 && entity.motionY==0 && entity.motionZ==0 && entity.hoverTarget==entity.posY,"exit stops motion and latches present altitude");heldX=entity.posX;heldY=entity.posY;heldZ=entity.posZ;heldYaw=entity.rotationYaw;}).get();unoccupiedLiftStart=liftBirths;stage=14;frame=0;return false;}}
        else if(stage==14){if(frame>=30){mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle entity=server(mc);require(entity.posX==heldX && entity.posY==heldY && entity.posZ==heldZ && entity.rotationYaw==heldYaw,"unoccupied Hover holds exit position and heading over time");entity.setDead();}).get();require(Math.abs(craft.posX-heldX)<.1 && Math.abs(craft.posY-heldY)<.1 && Math.abs(craft.posZ-heldZ)<.1,"client displays stationary craft after exit");require(liftBirths-unoccupiedLiftStart>=2,"unoccupied stationary Hover retains continuous lift particles");require(plumeStyles.contains(3) && plumeStyles.contains(4) && plumeStyles.contains(2),"Hover emits impulse, antigravity/vertical and repulsor particle families; observed="+plumeStyles);finished=true;System.out.println("[vandorlabs][reprolab] hover-particle-streams PASS continuous-sprint stationary-sprint stationary-lift unoccupied-lift live-family-presets rotated-position velocity fullbright");System.out.println("[vandorlabs][reprolab] hover-client-controls PASS real-double-space Taxi-Hover-Taxi network-ascend descend hold-height stationary-A-D-yaw crouch-retains-seat sprint-crouch-exit position-hold-after-exit");return true;}}
        frame++;return false;
    }
    private static double particleNumber(net.minecraft.client.particle.Particle particle,String mcp,String srg){return ((Number)net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.particle.Particle.class,particle,mcp,srg)).doubleValue();}
    private static void collectParticles(Object value,int depth,List<ParticleThrusterStream> found){
        if(value instanceof ParticleThrusterStream){found.add((ParticleThrusterStream)value);return;}if(value==null || depth>3)return;
        if(value.getClass().isArray()){for(int i=0;i<java.lang.reflect.Array.getLength(value);i++)collectParticles(java.lang.reflect.Array.get(value,i),depth+1,found);}
        else if(value instanceof Iterable)for(Object item:(Iterable<?>)value)collectParticles(item,depth+1,found);
    }
    private static void observePlumes(Minecraft mc,EntityGroundVehicle craft)throws Exception{
        List<ParticleThrusterStream> particles=new ArrayList<>();
        for(java.lang.reflect.Field field:mc.effectRenderer.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())){field.setAccessible(true);collectParticles(field.get(mc.effectRenderer),0,particles);}
        for(ParticleThrusterStream particle:particles){double x=particleNumber(particle,"posX","field_187126_f"),y=particleNumber(particle,"posY","field_187127_g"),z=particleNumber(particle,"posZ","field_187128_h");if(craft.getDistanceSq(x,y,z)>100)continue;
            java.lang.reflect.Field style=ParticleThrusterStream.class.getDeclaredField("style");style.setAccessible(true);int family=style.getInt(particle);plumeStyles.add(family);
            if(seenParticles.add(particle)){if(family==3)thrustBirths++;else if(family==2||family==4)liftBirths++;}
            require(particle.getBrightnessForRender(0)==0x00F000F0,"live stream retains full-bright family effect");
        }
    }
    private static void particleGeometry(Minecraft mc){
        String[] names={"rocket_engine","ion_engine","plasma_engine","impulse_engine","antigravity_plate","repulsor_array","vertical_hover_thruster"};int[] styles={0,1,2,3,4,2,4};
        int[] colors={0xFF8A24,0x4DEBFF,0xC36AFF,0xFF4438,0x4DEBFF,0xC36AFF,0x4DEBFF};float[] speeds={.25F,.17F,.13F,.085F,.10F,.13F,.10F};
        for(int i=0;i<names.length;i++){
            int style=com.vandorlabs.tiles.TileEntityRedstoneLight.particleStyle(names[i]);require(style==styles[i],"placed/captured family style "+names[i]);
            require(com.vandorlabs.tiles.TileEntityRedstoneLight.particleColor(style)==colors[i] && Math.abs(com.vandorlabs.tiles.TileEntityRedstoneLight.particleSpeed(style)-speeds[i])<1e-6,"placed/captured color and speed preset "+names[i]);
            for(float yaw:new float[]{90,-90,37})for(EnumFacing face:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.DOWN}){
                double centerX=183.5,centerZ=49.5;
                ParticleThrusterStream mote=new ParticleThrusterStream(mc.world,centerX,108,centerZ,face,colors[i],style,speeds[i],1);
                double x=particleNumber(mote,"posX","field_187126_f"),z=particleNumber(mote,"posZ","field_187128_h"),vx=particleNumber(mote,"motionX","field_187129_i"),vy=particleNumber(mote,"motionY","field_187130_j"),vz=particleNumber(mote,"motionZ","field_187131_k");
                mote.rotateAround(centerX,centerZ,yaw);double c=Math.cos(Math.toRadians(yaw)),sin=Math.sin(Math.toRadians(yaw));
                require(Math.abs(particleNumber(mote,"posX","field_187126_f")-(centerX+c*(x-centerX)-sin*(z-centerZ)))<1e-8 && Math.abs(particleNumber(mote,"posZ","field_187128_h")-(centerZ+sin*(x-centerX)+c*(z-centerZ)))<1e-8,"rotated plume position follows craft yaw "+names[i]+" "+face+" "+yaw);
                require(Math.abs(particleNumber(mote,"motionX","field_187129_i")-(c*vx-sin*vz))<1e-8 && Math.abs(particleNumber(mote,"motionZ","field_187131_k")-(sin*vx+c*vz))<1e-8 && particleNumber(mote,"motionY","field_187130_j")==vy,"rotated plume velocity follows craft yaw "+names[i]+" "+face+" "+yaw);
            }
        }
    }
    private static void prepareSpeedRun(EntityGroundVehicle craft){
        for(int x=-16;x<=16;x+=16)for(int z=-112;z<=0;z+=16)
            craft.world.getChunkFromBlockCoords(ORIGIN.add(x,0,z));
        craft.setPosition(ORIGIN.getX(),220,ORIGIN.getZ());
        craft.hoverTarget=220;
        craft.rotationYaw=0;
    }
    private HoverClientRuntimeChecks(){}
}
