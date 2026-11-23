package com.vandorlabs.vehicle;
import com.vandorlabs.navigation.CruiseAtlas;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.client.CruiseSky;
import com.vandorlabs.client.VehicleClient;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;

@SideOnly(Side.CLIENT)
public final class CruiseRuntimeChecks {
    private static final String RESTART = System.getenv("VANDOR_LABS_CRUISE_PHASE");
    private static final String SAVE_KEY = "VandorCruiseFixture";
    private static int stage,ticks,teleportSettled;
    private static Future<?> work;
    private static EntityGroundVehicle craft;
    private static double startX,destination;
    private static volatile int routeSteps;
    private static long initialWeatherDraws;
    private static int smoothSamples,groundSamples,transitTicks,climbSamples,diveSamples;
    private static void require(boolean ok,String detail){if(!ok)throw new IllegalStateException("Cruise: "+detail);}

    @SideOnly(Side.CLIENT)
    public static final class TransitChunks {
        private int loads;

        @SubscribeEvent public void fly(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent event){
            if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END||routeSteps<=0)return;
            boolean climbing=routeSteps>32;
            require(craft.cruise.move(64,climbing?48:-16,0),"moving high-speed route");
            craft.applyFlightPose(new VehiclePose(craft.posX,craft.posY,craft.posZ,craft.rotationYaw,
                    climbing?55:-55,craft.structure.facing));
            routeSteps--;
        }

        @SubscribeEvent public void loaded(ChunkEvent.Load event) {
            if (event.getWorld()!=craft.world) return;
            int x=event.getChunk().x*16, z=event.getChunk().z*16;
            if (x>=startX+512 && x<=startX+4112 && Math.abs(z-craft.posZ)<256) loads++;
        }
    }
    /** Render the same distant surface over two extreme sky colors in one frame. */
    @SideOnly(Side.CLIENT)
    public static final class OpacityProbe {
        boolean requested,passed,transition;
        @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
        public void render(net.minecraftforge.client.event.RenderWorldLastEvent event){
            if(!requested)return;
            requested=false;
            Minecraft mc=Minecraft.getMinecraft();
            int x=transition?mc.displayWidth/8:mc.displayWidth/4,y=transition?mc.displayHeight/12:mc.displayHeight*3/5;
            int width=transition?mc.displayWidth*3/4:12,height=transition?mc.displayHeight/2:12;
            java.nio.ByteBuffer[] images=new java.nio.ByteBuffer[transition?4:2];
            for(int i=0;i<images.length;i++)images[i]=org.lwjgl.BufferUtils.createByteBuffer(width*height*4);
            int trimmed=0;
            java.nio.ByteBuffer background=org.lwjgl.BufferUtils.createByteBuffer(4);
            // Shader packs can draw to a different FBO from the current pixel read target.
            int previousRead=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
            int drawing=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,drawing);
            int previousBuffer=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_READ_BUFFER);
            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_SCISSOR_BIT|org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
            try{
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
                org.lwjgl.opengl.GL11.glScissor(x,y,width,height);
                org.lwjgl.opengl.GL11.glReadBuffer(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_DRAW_BUFFER0));
                for(int pass=0;pass<images.length;pass++){
                    int backdrop=pass&1;
                    org.lwjgl.opengl.GL11.glClearColor(backdrop,0,backdrop,1);
                    org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT);
                    org.lwjgl.opengl.GL11.glReadPixels(x,y,1,1,org.lwjgl.opengl.GL11.GL_RGBA,
                            org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,background);
                    require(Math.abs((background.get(0)&255)-255*backdrop)<=2,"cannot read the active cruise color buffer");
                    if(transition)trimmed=CruiseSky.coverMask(event,pass>=2);else CruiseSky.render(event);
                    org.lwjgl.opengl.GL11.glReadPixels(x,y,width,height,org.lwjgl.opengl.GL11.GL_RGBA,
                            org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,images[pass]);
                }
            }finally{
                org.lwjgl.opengl.GL11.glPopAttrib();
                org.lwjgl.opengl.GL11.glReadBuffer(previousBuffer);
                org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,previousRead);
            }
            int largest=0,covered=0;
            for(int i=0;i<images[0].capacity();i+=4){
                if(transition&&difference(images[0],images[1],i)>2)continue; // sky, hull or native depth
                int pair=transition?2:0;
                largest=Math.max(largest,difference(images[pair],images[pair+1],i));
                covered++;
            }
            if(transition){
                if(trimmed==0){requested=true;return;} // First fine ring is still streaming.
                require(covered>width*height/4&&largest<=3,"LOD coverage lost pixels, opaque="+covered+" delta="+largest);
                System.out.println("[vandorlabs] transition LOD coverage PASS trimmed="+trimmed+" opaquePixels="+covered+" largest delta="+largest);
            }else require(largest<=2,"opaque cruise surface leaks backdrop; greatest RGB change="+largest);
            passed=true;
            if(!transition)System.out.println("[vandorlabs] cruise surface backdrop PASS largest RGB delta="+largest);
        }
        private int difference(java.nio.ByteBuffer a,java.nio.ByteBuffer b,int i){
            int delta=0;
            for(int c=0;c<3;c++)delta=Math.max(delta,Math.abs((a.get(i+c)&255)-(b.get(i+c)&255)));
            return delta;
        }
    }
    private static final OpacityProbe opacityProbe=new OpacityProbe();

    @SideOnly(Side.CLIENT)
    public static final class DiveProbe {
        private boolean intermediate;
        private volatile boolean done;
        @SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent event){
            if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)return;
            double y=craft.flightAltitude();
            if(y<400 && y>320 && craft.driveSpeed>3 && craft.driveSpeed<15)intermediate=true;
            if(y>256)return;
            require(intermediate&&craft.flying()&&craft.driveSpeed<=1.51,
                    "forward dive slows through real terrain band, speed at build height="+craft.driveSpeed);
            MinecraftForge.EVENT_BUS.unregister(this);
            done=true;
            System.out.println("[vandorlabs] altitude band dive PASS entry=15 low="+craft.driveSpeed);
        }
    }
    private static final DiveProbe diveProbe=new DiveProbe();

    private static final TransitChunks transitChunks=new TransitChunks();

    private static void observeRoute() {
        transitChunks.loads=0;
        MinecraftForge.EVENT_BUS.register(transitChunks);
    }

    private static void checkRoute() {
        MinecraftForge.EVENT_BUS.unregister(transitChunks);
        require(transitChunks.loads==0,"loaded "+transitChunks.loads+" transit chunks before descent");
    }

    private static void checkForecast(){
        int fine=8,span=CruiseAtlas.SIZE*fine;
        double x=-1,z=1;
        double atSpeed=CruiseAtlas.forecastDistance(15,0,fine);
        require(atSpeed==span&&CruiseAtlas.forecastDistance(0,0,fine)==0
                &&CruiseAtlas.forecastDistance(2,60,fine)<121,"bounded horizontal flight forecast");
        java.util.List<CruiseAtlas.Tile> ahead=CruiseTiles.footprint(x,z,fine,x+atSpeed,z);
        require(ahead.size()==68&&new java.util.HashSet<>(ahead).size()==68,"fixed unique seed tile budget");
        require(ahead.contains(new CruiseAtlas.Tile(2*span,0,fine))&&!ahead.contains(new CruiseAtlas.Tile(-3*span,0,fine))
                &&ahead.contains(new CruiseAtlas.Tile(-span,0,fine)),"fine coverage ahead, behind and under the aircraft");
        require(CruiseTiles.footprint(x,z,fine,x-atSpeed,z).contains(new CruiseAtlas.Tile(-4*span,0,fine)),
                "reversing heading reverses the detail footprint across negative coordinates");
    }

    private static void place(Minecraft mc){
        checkForecast();
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        WorldServer world=pilot.getServerWorld();
        BlockPos seat=BlockPos.ORIGIN;
        craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null),
                new VehicleStructure.Cell(seat.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null)),seat,EnumFacing.NORTH,1));
        require(com.vandorlabs.navigation.CruiseAtlas.forWorld(world).usesDetachedLayers(),"default biome provider has a detached seed worker");
        startX=pilot.posX;
        craft.setPosition(startX,255.9,pilot.posZ);
        world.spawnEntity(craft);
        pilot.setPositionAndUpdate(startX,256,pilot.posZ);
        craft.assignSeat(pilot,seat);
        require(pilot.startRiding(craft,true),"pilot boards");
        craft.hover=true;craft.hoverTarget=255.9;
    }

    private static void enter(){
        // Set a recovery flight so the production physics levels the craft and descends on its own.
        NBTTagCompound state=new NBTTagCompound();state.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(state);
        craft.setPosition(startX,511.9,craft.posZ);

        observeRoute();
        require(craft.cruise.move(0,32,0),"cross cruise boundary");
        routeSteps=64;
    }
    private static void finishLeg(){
        destination=craft.cruise.x();
        require(Math.abs(craft.posX-startX)<.001&&destination-startX==4096,"horizontal route keeps physical anchor");
        require(craft.cruise.altitude()>1200&&Double.isInfinite(craft.groundClearance()),"logical altitude without a clearance query");
        for(int i=512;i<=4096;i+=256)require(!craft.world.isBlockLoaded(new BlockPos(startX+i,64,craft.posZ)),"cruise loaded route at "+i);
        int modes=VehicleStatus.availableModes(craft.view,BlockPos.ORIGIN,(EntityPlayerMP)craft.getControllingPassenger());
        require((modes & 6)==0 && (modes & 8)!=0,"only Flight remains selectable at high altitude");
        require(Math.abs(com.vandorlabs.navigation.NavigationVehicle.center(craft).x-destination)<3,"navigation follows geographic route");
        NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());
        EntityGroundVehicle loaded=new EntityGroundVehicle(craft.world);
        loaded.readFromNBT(saved);
        require(loaded.cruise.active()&&loaded.cruise.x()==destination&&loaded.cruise.altitude()==craft.cruise.altitude(),"persisted route and altitude");
    }

    private static void verifyRelief(WorldServer world){
        com.vandorlabs.navigation.CruiseAtlas atlas=com.vandorlabs.navigation.CruiseAtlas.forWorld(world);
        int x=(int)Math.floor(craft.posX/256)*256,z=(int)Math.floor(craft.posZ/256)*256;
        com.vandorlabs.navigation.CruiseAtlas.Tile key=new com.vandorlabs.navigation.CruiseAtlas.Tile(x,z,4);
        com.vandorlabs.network.MessageCruiseTerrain tile=atlas.request(world,key);
        require(tile!=null&&tile.heights!=null&&tile==atlas.request(world,key),"cached near tile includes elevations");
        net.minecraft.world.gen.ChunkGeneratorOverworld generator=(net.minecraft.world.gen.ChunkGeneratorOverworld)world.getChunkProvider().chunkGenerator;
        int worst=0;
        for(int row=2;row<=14;row+=6)for(int col=2;col<=14;col+=6){
            net.minecraft.world.chunk.ChunkPrimer primer=new net.minecraft.world.chunk.ChunkPrimer();
            generator.setBlocksInChunk((x>>4)+col,(z>>4)+row,primer);
            int y=255;
            while(y>0 && primer.getBlockState(0,y,0).getBlock()==net.minecraft.init.Blocks.AIR)y--;
            int estimate=tile.heights[row*17+col]&255;
            worst=Math.max(worst,Math.abs(estimate-y));
        }
        require(worst<=2,"seed elevations against nine vanilla terrain columns, largest error="+worst);
        System.out.println("[vandorlabs] seed relief vs nine generated columns max error="+worst+" blocks");
    }
    private static void saveFlight(Minecraft mc) {
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        checkRoute();
        NBTTagCompound marker=new NBTTagCompound();
        marker.setUniqueId("Aircraft",craft.getUniqueID());
        marker.setDouble("OriginX",startX);
        marker.setDouble("DestinationX",destination);
        pilot.getEntityData().setTag(SAVE_KEY,marker);
        mc.getIntegratedServer().getPlayerList().saveAllPlayerData();
        mc.getIntegratedServer().saveAllWorlds(false);
    }

    private static void resumeFlight(Minecraft mc) {
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        NBTTagCompound marker=pilot.getEntityData().getCompoundTag(SAVE_KEY);
        require(marker.hasUniqueId("Aircraft"),"cold restart has the saved player");
        require(pilot.getRidingEntity() instanceof EntityGroundVehicle,"cold restart restores the mounted pilot");
        craft=(EntityGroundVehicle)pilot.getRidingEntity();
        require(craft.getUniqueID().equals(marker.getUniqueId("Aircraft")),"same saved aircraft");
        startX=marker.getDouble("OriginX");
        destination=marker.getDouble("DestinationX");
        require(craft.cruise.active() && craft.cruise.x()==destination && craft.posX==startX,"restored cruise route and anchor");
        require(craft.cruise.altitude()>256 && craft.descendingFromFlight(),"restored airborne recovery");
        observeRoute();
    }

    public static boolean tick(Minecraft mc,File out) throws Exception {
        if(CruisePhysicalChecks.enabled())return CruisePhysicalChecks.tick(mc,out);
        if(++ticks>(stage==2?2000:600))throw new IllegalStateException("Cruise fixture timeout at stage "+stage+" rider="+mc.player.getRidingEntity()+" position="+mc.player.getPosition()+" start="+startX+" dest="+destination+" "+CruiseSky.coverageDiagnostic());
        if(work!=null){if(!work.isDone())return false;work.get();work=null;ticks=0;}
        if(stage==0 && "read".equals(RESTART)){
            work=mc.getIntegratedServer().addScheduledTask(()->resumeFlight(mc));stage=2;
        }else if(stage==0){
            work=mc.getIntegratedServer().addScheduledTask(()->place(mc));stage=1;
        }else if(stage==1&&ticks>10&&mc.player.getRidingEntity() instanceof EntityGroundVehicle){
            work=mc.getIntegratedServer().addScheduledTask(()->{
                NBTTagCompound flight=new NBTTagCompound();
                flight.setByte("MaxwellFlightPhase",(byte)3);
                craft.readFlightMode(flight);
            });stage=9;
        }else if(stage==9 && CruiseSky.reliefAtCenter()){
            require(!CruiseSky.ready(),"preview arrived before crossing Y=256");
            mc.gameSettings.thirdPersonView=1;
            mc.player.rotationPitch=mc.player.prevRotationPitch=35;
            mc.gameSettings.hideGUI=true;
            work=mc.getIntegratedServer().addScheduledTask(()->{
                verifyRelief((WorldServer)craft.world);
                craft.setPosition(startX,(256+craft.cruise.handoffAltitude())/2.,craft.posZ);
                VehicleNetwork.state(craft,true);
            });
            stage=13;ticks=0;
        }else if(stage==13 && ticks>12 && mc.player.getRidingEntity() instanceof EntityGroundVehicle){
            EntityGroundVehicle local=(EntityGroundVehicle)mc.player.getRidingEntity();
            require(!local.cruise.active()&&local.posY>256&&local.posY<local.cruise.handoffAltitude(),"real coordinates during blended flight at "+local.posY);
            if(com.vandorlabs.client.CruiseTransitionProfile.ENABLED
                    && !com.vandorlabs.client.CruiseTransitionProfile.atPose(craft))return false;
            int pilotDraws=VehicleClient.cruisePilotDraws();
            // The shader pack compiles and can run catch-up simulation ticks before its
            // first rendered transition frame. Wait for a draw, still reject double draws.
            if(pilotDraws==0&&ticks<60)return false;
            require(pilotDraws==1,"transition band draws the third-person pilot once, draws="+pilotDraws);
            require(mc.world.isBlockLoaded(new BlockPos(local.posX,64,local.posZ)),"real terrain remains available during transition");
            ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(out,"cruise-blend-mid.png"));
            opacityProbe.transition=true;opacityProbe.requested=true;opacityProbe.passed=false;
            MinecraftForge.EVENT_BUS.register(opacityProbe);
            stage=15;ticks=0;
        }else if(stage==15&&opacityProbe.passed){
            MinecraftForge.EVENT_BUS.unregister(opacityProbe);
            opacityProbe.transition=false;opacityProbe.passed=false;
            work=mc.getIntegratedServer().addScheduledTask(()->{
                NBTTagCompound glide=new NBTTagCompound();
                glide.setByte("MaxwellFlightPhase",(byte)EntityGroundVehicle.GLIDING_FLIGHT);
                glide.setFloat("MaxwellFlightPitch",-75);
                glide.setDouble("MaxwellFlightSpeed",30);
                craft.readFlightMode(glide);
                require(craft.driveSpeed==15,"legacy overspeed save clamped to 300 m/s");
                require(craft.cruise.move(0,530-craft.posY,0),"stage accelerated high-altitude descent");
                MinecraftForge.EVENT_BUS.register(diveProbe);
            });
            stage=14;
        }else if(stage==14&&diveProbe.done){
            work=mc.getIntegratedServer().addScheduledTask(CruiseRuntimeChecks::enter);stage=11;
        }else if(stage==11 && mc.player.getRidingEntity() instanceof EntityGroundVehicle){
            EntityGroundVehicle mounted=(EntityGroundVehicle)mc.player.getRidingEntity();
            if(mounted.cruise.active()){
                transitTicks++;
                double climb=mounted.visualPose(1).y-mounted.visualPose(0).y;
                if(Math.abs(climb)>1){
                    double before=mounted.visualPose(.25F).y;
                    // Vanilla sends its absolute tracker teleport when the craft moves over eight
                    // blocks between tracker samples. Re-enter its real client handler mid-pose.
                    net.minecraft.network.play.server.SPacketEntityTeleport tracker=
                            new net.minecraft.network.play.server.SPacketEntityTeleport(mounted);
                    mc.getConnection().handleEntityTeleport(tracker);
                    double jump=mounted.visualPose(.25F).y-before;
                    require(Math.abs(jump)<1e-6,"vanilla tracker moved an in-flight camera between frames by "+jump+" blocks");
                    float pitch=mounted.visualPose(1).pitch;
                    boolean firstClimb=climb>0&&pitch>40&&climbSamples++==0;
                    boolean firstDive=climb<0&&pitch< -40&&diveSamples++==0;
                    if(firstClimb||firstDive)ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,
                            mc.displayHeight,mc.getFramebuffer()),"png",new File(out,firstClimb?"cruise-steep-climb.png":"cruise-steep-dive.png"));
                }
                double early=mounted.cruise.view(true,.05F),late=mounted.cruise.view(true,.8F);
                if(late>early&&late-early<192)smoothSamples++;
                if(CruiseSky.ready())groundSamples++;
            }
            if(routeSteps==0){
                require(climbSamples>=1&&diveSamples>=1,"interpolated both vertical directions: climb="+climbSamples+" dive="+diveSamples);
                require(smoothSamples>=4,"geographic route updated between tick boundaries, samples="+smoothSamples);
                System.out.println("[vandorlabs] steep cruise camera PASS climb="+climbSamples+" dive="+diveSamples+" accepted tracker displacement=0");
                require(groundSamples*5>=transitTicks*4,"seed ground coverage during high-speed movement "+groundSamples+"/"+transitTicks);
                System.out.println("[vandorlabs] moving seed atlas ground="+groundSamples+"/"+transitTicks+" interpolated="+smoothSamples);
                work=mc.getIntegratedServer().addScheduledTask(CruiseRuntimeChecks::finishLeg);stage=2;
            }
        }else if(stage==2&&mc.player.getRidingEntity() instanceof EntityGroundVehicle&&CruiseSky.detailed()){
            mc.gameSettings.thirdPersonView=0;
            stage=6;ticks=0;
        }else if(stage==6&&ticks>10&&CruiseSky.covered()&&mc.player.getRidingEntity() instanceof EntityGroundVehicle
                && Math.abs(((EntityGroundVehicle)mc.player.getRidingEntity()).visualPose(1).pitch)<.01){
            // Let the aircraft level after the steep dive before sampling a fixed ground pixel.
            require(mc.world.provider.getWeatherRenderer()!=null,"local cruise weather interceptor installed");
            System.out.println("[vandorlabs] cruise atlas covers two 5x5 detail layers and two 3x3 horizon layers after "+ticks+" client ticks from first detail");
            require(VehicleClient.cruisePilotDraws()==0,"first-person cruise leaves the pilot out of the main render pass");
            if (System.getenv("VANDOR_LABS_TEST_JOURNEYMAP")!=null) verifyJourneyMap(mc);
            initialWeatherDraws=com.vandorlabs.client.CanopyRainShelter.suppressedDraws();
            mc.world.setRainStrength(1);
            mc.gameSettings.thirdPersonView=1;
            mc.player.rotationPitch=mc.player.prevRotationPitch=35;
            mc.gameSettings.hideGUI=true;
            MinecraftForge.EVENT_BUS.register(opacityProbe);
            stage=3;ticks=0;
        }else if(stage==3&&ticks==12){
            opacityProbe.requested=true;
        }else if(stage==3&&ticks>15&&opacityProbe.passed){
            MinecraftForge.EVENT_BUS.unregister(opacityProbe);
            require(com.vandorlabs.client.CanopyRainShelter.suppressedDraws()>initialWeatherDraws,"rain pass suppressed over cruise");
            int pilotDraws=VehicleClient.cruisePilotDraws();
            require(pilotDraws==1,"third-person cruise draws the local pilot exactly once, draws="+pilotDraws);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            ImageIO.write(shot,"png",new File(out,"maxwell-cruise.png"));
            if("write".equals(RESTART)){
                work=mc.getIntegratedServer().addScheduledTask(()->saveFlight(mc));stage=5;return false;
            }
            work=mc.getIntegratedServer().addScheduledTask(()->{
                checkRoute();
                require(craft.cruise.active(),"craft still cruising");
                require(craft.cruise.move(0,craft.cruise.handoffAltitude()-1-craft.cruise.altitude(),0),"reenter");
            });stage=4;
        }else if(stage==4&&mc.player.getRidingEntity() instanceof EntityGroundVehicle&&Math.abs(mc.player.posX-destination)<4&&mc.player.posY<550){
            work=mc.getIntegratedServer().addScheduledTask(()->{
                require(!craft.cruise.active()&&Math.abs(craft.posX-destination)<.01,"reentered at geographic destination");
                require(craft.world.isBlockLoaded(new BlockPos(destination,64,craft.posZ)),"destination prepared");
                require(!craft.world.isBlockLoaded(new BlockPos(startX+2048,64,craft.posZ)),"middle of route stayed unloaded");
                EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                int result=mc.getIntegratedServer().getCommandManager().executeCommand(mc.getIntegratedServer(),
                        "tp "+pilot.getName()+" "+(int)startX+" 85 0");
                require(result==1 && !pilot.isRiding() && Math.abs(pilot.posX-startX)<2,"vanilla tp releases pilot");
                require(!craft.flying() && !craft.hover && !craft.cruise.active() && craft.onGround
                        && Math.abs(craft.posX-destination)<2 && craft.posY<255,"last pilot leaves craft landed at route");
            });stage=7;
        }else if(stage==7 || stage==12) {
            if (mc.player.isRiding() || Math.abs(mc.player.posX-startX)>=2 || Math.abs(mc.player.posZ)>=2) {
                teleportSettled=0;
                return false;
            }
            if (++teleportSettled<12) return false;
            if (stage==7 && System.getenv("VANDOR_LABS_TEST_JOURNEYMAP")!=null) {
                work=mc.getIntegratedServer().addScheduledTask(()->prepareJourneyMapTeleport(mc));
                teleportSettled=0;
                stage=8;
                return false;
            }
            work=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                require(!pilot.isRiding() && Math.abs(pilot.posX-startX)<2 && Math.abs(pilot.posZ)<2,
                        "authoritative rider remains at the external teleport destination");
            });
            stage=10;
        }else if(stage==8 && ticks>8 && mc.player.getRidingEntity() instanceof EntityGroundVehicle) {
            EntityGroundVehicle mounted=(EntityGroundVehicle)mc.player.getRidingEntity();
            if(!mounted.getUniqueID().equals(craft.getUniqueID()) || !mounted.cruise.active())return false;
            work=mc.getIntegratedServer().addScheduledTask(()->journeyMapTeleport(mc));
            stage=12;
        }else if(stage==10) {
            System.out.println("[vandorlabs] maxwell-cruise PASS route-unloaded rider-seed-view saved-route destination-remount settled-external-teleport");
            return true;
        }
        if(stage==5 && work==null){
            System.out.println("[vandorlabs] maxwell-cruise PASS saved mounted pilot in flight for a cold restart");
            return true;
        }
        return false;
    }
    private static void prepareJourneyMapTeleport(Minecraft mc) {
        place(mc);
        NBTTagCompound state=new NBTTagCompound();
        state.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(state);
        require(craft.cruise.move(2048,512,0),"enter high cruise before waypoint teleport");
    }
    private static void journeyMapTeleport(Minecraft mc) {
        double site=craft.cruise.x();
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        try {
            Class<?> location=Class.forName("journeymap.common.feature.Location");
            Object target=location.getConstructor(double.class,double.class,double.class,int.class)
                    .newInstance(startX,85.0,0.0,0);
            Class<?> feature=Class.forName("journeymap.common.feature.JourneyMapTeleport");
            boolean accepted=(Boolean)feature.getMethod("attemptTeleport",net.minecraft.entity.Entity.class,location)
                    .invoke(feature.getMethod("instance").invoke(null),pilot,target);
            require(accepted && !pilot.isRiding() && Math.abs(pilot.posX-startX)<2,"JourneyMap releases and relocates pilot");
            require(!craft.cruise.active() && !craft.flying() && craft.onGround && craft.posY<255
                    && Math.abs(craft.posX-site)<1 && craft.chunkCoordX==((int)Math.floor(site)>>4),
                    "JourneyMap lands and reindexes the aircraft at its geographic position");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    static void verifyJourneyMap(Minecraft mc) throws Exception {
        Class<?> manager=Class.forName("journeymap.client.ui.UIManager");
        Object minimap=manager.getMethod("getMiniMap").invoke(manager.getField("INSTANCE").get(null));
        String location=(String)minimap.getClass().getMethod("getLocation").invoke(minimap);
        int route=net.minecraft.util.math.MathHelper.floor(com.vandorlabs.client.CruiseMapView.x(mc.player.posX));
        Class<?> waypoint=Class.forName("journeymap.client.model.Waypoint");
        Object mark=waypoint.getMethod("of",net.minecraft.entity.player.EntityPlayer.class).invoke(null,mc.player);
        require(((Integer)waypoint.getMethod("getX").invoke(mark))==route,"JourneyMap new waypoint follows route");
        int altitude=net.minecraft.util.math.MathHelper.floor(com.vandorlabs.client.CruiseMapView.y(mc.player.posY));
        require(((Integer)waypoint.getMethod("getY").invoke(mark))==altitude,"JourneyMap new waypoint uses flight altitude");
        require(location.contains(String.valueOf(route)),"JourneyMap minimap location "+location+" expected X "+route);
    }
    private CruiseRuntimeChecks(){}
}
