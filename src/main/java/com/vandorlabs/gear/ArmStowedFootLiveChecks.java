package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Survival sneaking placement against an existing posed Ski, including ordinary packet fallback. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmStowedFootLiveChecks {
    private static final BlockPos ROOT=new BlockPos(1280,155,120);
    private static final int START=Integer.parseInt(System.getenv().getOrDefault("VANDOR_STOWED_FOOT_START","0")),END=Integer.parseInt(System.getenv().getOrDefault("VANDOR_STOWED_FOOT_END","48"));
    private static int fixture=START,stage,ticks,started;private static Vec3d position,sourceAim;private static boolean sent,aimed;private static float expectedYaw,expectedPitch;private static EnumFacing clickedFace;private static BlockPos source,extra,standingSupport;
    private static EnumFacing mount(){return EnumFacing.values()[fixture%6];}
    private static boolean large(){return fixture/6%2==1;}
    private static boolean rotates(){return fixture/12%2==0;}
    private static boolean joined(){return fixture>=24;}
    private static int count(){return joined()?4:1;}
    private static GearBlock arm(){return large()?GearRegistry.LARGE_GEAR_SWING_ARM:GearRegistry.GEAR_SWING_ARM;}
    private static BlockPos at(int x,int y,int z){return ROOT.add(ArmOrientation.vector(mount(),new BlockPos(mount()==EnumFacing.WEST?-x:x,y,z)));}
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("stowed-ski fixture="+fixture+" stage="+stage+" tick="+ticks+" mount="+mount()+" Large="+large()+" joined="+joined()+" rotates="+rotates()+": "+why);}
    private static boolean clientOwner(Minecraft mc,double phase,int feet){
        net.minecraft.tileentity.TileEntity raw=mc.world.getTileEntity(ROOT);if(!(raw instanceof GearTileEntity))return false;GearTileEntity root=(GearTileEntity)raw;
        if(root.progress()!=phase||root.assembly()==null||root.assembly().feet().size()!=feet||root.mountDirection()!=mount()||!com.vandorlabs.gear.client.GearRenderer.visible(root))return false;
        int renderers=0;for(BlockPos pos:root.members()){net.minecraft.tileentity.TileEntity tile=mc.world.getTileEntity(pos);if(!(tile instanceof GearTileEntity)||((GearTileEntity)tile).loadedController()!=root||((GearTileEntity)tile).mountDirection()!=mount())return false;if(com.vandorlabs.gear.client.GearRenderer.visible((GearTileEntity)tile))renderers++;}
        return renderers==1;
    }
    public static boolean tick(Minecraft mc)throws Exception{
        if(fixture==END){KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);System.out.println("[vandorlabs][reprolab] utility26-focused PASS stowed-ski cases="+(END-START)+" sixMounts/regularLarge/joinedIndependent/bothPayloadModes/SurvivalSneaking/bothPacketRoutes/clientOneOwner/reversal/reload/mining");return true;}
        ticks++;mc.displayGuiScreen(null);
        if(stage!=2)KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),false);
        if(stage==2){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(),true);
            need(ticks-started<300,"client synchronization mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" expected="+position+" sneak="+mc.player.isSneaking()+" flying="+mc.player.capabilities.isFlying+" ground="+mc.player.onGround+" "+ArmInteractionSync.status());
            if(!sent){if(mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=net.minecraft.item.Item.getItemFromBlock(GearRegistry.LANDING_SKI)||mc.player.getHeldItemMainhand().getCount()!=2||!mc.player.isSneaking()||mc.player.getPositionVector().squareDistanceTo(position)>.0001||!clientOwner(mc,0,count())||!ArmInteractionSync.acknowledged(mc))return false;
                boolean sneaking=mc.getIntegratedServer().callFromMainThread(()->mc.getIntegratedServer().getPlayerList().getPlayers().get(0).isSneaking()).get();if(!sneaking)return false;
                if(!aimed){mc.getIntegratedServer().callFromMainThread(()->{
                    EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);Vec3d look=sourceAim.subtract(actor.getPositionEyes(1));expectedYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));expectedPitch=(float)-Math.toDegrees(Math.atan2(look.y,Math.sqrt(look.x*look.x+look.z*look.z)));
                    actor.connection.setPlayerLocation(position.x,position.y,position.z,expectedYaw,expectedPitch);
                    RayTraceResult actual=GearInteractions.trace(actor.world,actor.getPositionEyes(1),actor.getPositionEyes(1).add(look.normalize().scale(4.5)));
                    need(actual!=null&&actual.getBlockPos().equals(source)&&actual.sideHit==clickedFace,"sneaking eye ray retains selected posed face "+actual+" expected="+clickedFace);return null;
                }).get();aimed=true;return false;}
                if(Math.abs(MathHelper.wrapDegrees(mc.player.rotationYaw-expectedYaw))>.01||Math.abs(mc.player.rotationPitch-expectedPitch)>.01)return false;
                if((fixture&1)==0)com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageModularGearUse(source));
                else mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(source,clickedFace,EnumHand.MAIN_HAND,.5F,.5F,.5F));
                sent=true;started=ticks;
            }
        }
        if(stage==3||stage==5||stage==7){if(ticks-started>=300)need(false,"client one-owner/mount/pose publication"+serverStatus(mc));if(!clientOwner(mc,stage==3?0:1,stage==7?count():count()+1))return false;stage++;started=ticks;}
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(stage==0){
                for(BlockPos cell:BlockPos.getAllInBox(ROOT.add(-8,-8,-8),ROOT.add(8,8,8))){world.removeTileEntity(cell);world.setBlockToAir(cell);}
                actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.rotationYaw=0;actor.rotationPitch=0;
                for(int x:joined()?new int[]{0,3}:new int[]{0}){
                    world.setBlockState(at(x,1,0),Blocks.STONE.getDefaultState(),2);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(arm(),2));
                    for(int n=0;n<2;n++)need(actor.getHeldItemMainhand().onItemUse(actor,world,at(x,1-n,0),EnumHand.MAIN_HAND,mount(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported arm placement");
                    GearTileEntity column=(GearTileEntity)world.getTileEntity(at(x,0,0));column.operator(actor);need(column.rebuild(),"column forms");NBTTagCompound settings=column.settings();settings.setBoolean("RotatePayload",rotates());settings.setInteger("Direction",0);settings.setBoolean("JoinArms",joined());settings.setInteger("JoinRange",3);column.configure(settings);
                }
                for(int x=0;x<count();x++){
                    actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.LANDING_SKI));BlockPos foot=at(x,-2,0);
                    try(ArmFootComponentFrame.Placement placement=ArmFootComponentFrame.placing(mount())){need(actor.getHeldItemMainhand().onItemUse(actor,world,foot,EnumHand.MAIN_HAND,mount(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary Ski placement");}
                    GearTileEntity tile=(GearTileEntity)world.getTileEntity(foot);tile.setMountDirection(mount());tile.componentFrame(mount());
                }
                GearTileEntity root=(GearTileEntity)world.getTileEntity(ROOT);root.operator(actor);need(root.rebuild()&&root.isLandingAssembly()&&root.columns().size()==(joined()?2:1)&&root.assembly().feet().size()==count(),"expected native Ski assembly forms");root.target(false);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);stage=1;started=ticks;sent=false;aimed=false;return false;
            }
            GearTileEntity root=(GearTileEntity)world.getTileEntity(ROOT);need(ticks-started<400,"motion completes phase="+root.progress()+" target="+root.target()+" error="+root.error());
            if(ArmTransactions.locked(world,ROOT)||ArmTransactions.blocked(root))return false;
            if(stage==1){
                if(root.progress()!=0)return false;source=at(count()-1,-2,0);
                boolean across=(fixture+fixture/6)%2==1;BlockPos localStep=new BlockPos(across?0:mount()==EnumFacing.WEST?-1:1,0,across?1:0);extra=source.add(ArmOrientation.vector(mount(),localStep));
                com.vandorlabs.gear.core.GearMotion.Pose pose=root.pose(0);com.vandorlabs.gear.core.GearMotion.Vec a=pose.transformFootVertex(0,0,0),b=pose.transformFootVertex(localStep.getX(),0,localStep.getZ());Vec3d normal=ArmOrientation.vector(mount(),b.x-a.x,b.y-a.y,b.z-a.z);clickedFace=EnumFacing.getFacingFromVector((float)normal.x,(float)normal.y,(float)normal.z);
                int index=-1;for(int i=0;i<root.assembly().feet().size();i++)if(root.localCell(root.assembly().feet().get(i)).equals(source))index=i;need(index>=0,"selected source is an actual foot");java.util.List<AxisAlignedBB> boxes=root.collisionBoxes();AxisAlignedBB foot=boxes.get(boxes.size()-count()*2+index*2);
                Vec3d aim=new Vec3d(clickedFace==EnumFacing.EAST?foot.maxX:clickedFace==EnumFacing.WEST?foot.minX:(foot.minX+foot.maxX)/2,clickedFace==EnumFacing.UP?foot.maxY:clickedFace==EnumFacing.DOWN?foot.minY:(foot.minY+foot.maxY)/2,clickedFace==EnumFacing.SOUTH?foot.maxZ:clickedFace==EnumFacing.NORTH?foot.minZ:(foot.minZ+foot.maxZ)/2);
                java.util.List<AxisAlignedBB> appendStroke=expandedStroke(root,extra);boolean found=false;int occupied=0,occluded=0,missed=0,stroke=0;
                for(Vec3d tangent:new Vec3d[]{Vec3d.ZERO,new Vec3d(1.5,0,0),new Vec3d(-1.5,0,0),new Vec3d(0,0,1.5),new Vec3d(0,0,-1.5),new Vec3d(3,0,0),new Vec3d(-3,0,0),new Vec3d(0,0,3),new Vec3d(0,0,-3)}){
                    if(Math.abs(tangent.dotProduct(normal))>1e-6)continue;
                    Vec3d eye=aim.add(normal.scale(3)).add(tangent);BlockPos support=new BlockPos(eye.x,eye.y-actor.getEyeHeight()-.01,eye.z);
                    if(!world.isAirBlock(support)||root.members().contains(support)){occupied++;continue;}
                    eye=new Vec3d(eye.x,support.getY()+1D+actor.getEyeHeight(),eye.z);
                    if(new AxisAlignedBB(support).grow(.001).calculateIntercept(eye,aim)!=null){occluded++;continue;}
                    Vec3d d=aim.subtract(eye);RayTraceResult hit=GearInteractions.trace(world,eye,eye.add(d.normalize().scale(4.5)));
                    if(hit==null||!hit.getBlockPos().equals(source)||hit.sideHit!=clickedFace){missed++;continue;}
                    boolean clear=true;for(AxisAlignedBB box:appendStroke)if(box.intersects(new AxisAlignedBB(support).shrink(.0001)))clear=false;if(!clear){stroke++;continue;}
                    sourceAim=hit.hitVec;standingSupport=support;world.setBlockState(support,Blocks.STONE.getDefaultState(),2);position=new Vec3d(eye.x,support.getY()+1D,eye.z);float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z)));
                    actor.setGameType(GameType.SURVIVAL);actor.capabilities.isFlying=false;actor.sendPlayerAbilities();actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.LANDING_SKI,2));actor.inventoryContainer.detectAndSendChanges();actor.connection.setPlayerLocation(position.x,position.y,position.z,yaw,pitch);found=true;break;
                }
                need(found,"visible posed Ski face and non-occluding standing support source="+source+" face="+clickedFace+" bounds="+foot+" candidateRejected occupied="+occupied+" floorRay="+occluded+" posedRay="+missed+" fullStroke="+stroke);
                stage=2;started=ticks;return false;
            }
            if(stage==2){if(!sent||ticks-started<20)return false;boolean appended=world.getBlockState(extra).getBlock()==GearRegistry.LANDING_SKI&&root.assembly().feet().size()==count()+1&&world.getTileEntity(extra) instanceof GearTileEntity&&((GearTileEntity)world.getTileEntity(extra)).loadedController()==root;if(!appended)need(false,"extra Ski belongs to stowed owner, held="+actor.getHeldItemMainhand()+" error="+root.error()+appendStatus(root,actor));need(root.progress()==0&&!root.target()&&actor.getHeldItemMainhand().getCount()==1,"placement preserves pose and consumes one");root.sync();stage=3;started=ticks;return false;}
            if(stage==4){NBTTagCompound saved=root.writeToNBT(new NBTTagCompound());root.readFromNBT(saved);root.operator(actor);root.onLoad();root.sync();root.target(true);stage=5;started=ticks;return false;}
            if(stage==6){need(root.progress()==1&&root.assembly().feet().size()==count()+1,"extended group survives reversal and reload");actor.setGameType(GameType.CREATIVE);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);need(actor.interactionManager.tryHarvestBlock(extra)&&world.isAirBlock(extra)&&root.rebuild()&&root.assembly().feet().size()==count(),"native endpoint mining preserves original joined ownership");stage=7;started=ticks;return false;}
            if(stage==8){need(root.payload()==null&&!root.hasRetainedPayload(),"no retained duplicate foot owner");System.out.println("[vandorlabs][reprolab] stowed-ski case PASS fixture="+fixture+" mount="+mount()+" Large="+large()+" joined="+joined()+" rotates="+rotates()+" route="+((fixture&1)==0?"arm":"ordinary"));fixture++;stage=0;started=ticks;sent=false;return false;}
            return false;
        }).get();
    }
    private static String appendStatus(GearTileEntity root,EntityPlayerMP actor){
        Vec3d eye=actor.getPositionEyes(1),end=eye.add(Vec3d.fromPitchYaw(actor.rotationPitch,actor.rotationYaw).scale(actor.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue()+.5));RayTraceResult ray=GearInteractions.trace(actor.world,eye,end);
        String result=" source="+source+" target="+extra+" face="+clickedFace+" player="+actor.getPositionVector()+" eye="+eye+" playerBounds="+actor.getEntityBoundingBox()+" sneak="+actor.isSneaking()+" ray="+(ray==null?"none":ray.getBlockPos()+"/"+ray.sideHit+"/"+ray.hitVec);
        try(ArmFootComponentFrame.Placement scope=ArmFootComponentFrame.placing(root.mountDirection())){
            net.minecraft.block.state.IBlockState state=GearRegistry.LANDING_SKI.getDefaultState();AxisAlignedBB nativeBox=state.getCollisionBoundingBox(actor.world,extra);if(nativeBox!=null)nativeBox=nativeBox.offset(extra);
            AxisAlignedBB expected=ArmOrientation.box(root.mountDirection(),new AxisAlignedBB(0,0,0,1,5/16D,1)).offset(extra);
            result+=" placementFrame="+ArmFootComponentFrame.placement()+" nativeRestBounds="+nativeBox+" expectedRestBounds="+expected+" nativeActorOverlap="+(nativeBox!=null&&nativeBox.intersects(actor.getEntityBoundingBox()))+" expectedActorOverlap="+expected.intersects(actor.getEntityBoundingBox())+" vanillaMayPlace="+actor.world.mayPlace(GearRegistry.LANDING_SKI,extra,false,clickedFace,null);
        }
        GearTileEntity projection=expandedProjection(root,extra);java.util.List<AxisAlignedBB> boxes=GearGeometry.boxes(projection,0),posed=new java.util.ArrayList<>();boolean overlap=false;int start=boxes.size()-projection.assembly().feet().size()*2;
        for(int i=0;i<projection.assembly().feet().size();i++)if(projection.localCell(projection.assembly().feet().get(i)).equals(extra))for(int piece=0;piece<2;piece++){AxisAlignedBB box=boxes.get(start+i*2+piece);posed.add(box);overlap|=box.intersects(actor.getEntityBoundingBox());}
        return result+" actualPosedAddedBounds="+posed+" actualPosedActorOverlap="+overlap;
    }
    private static GearTileEntity expandedProjection(GearTileEntity root,BlockPos added){
        NBTTagCompound saved=root.writeToNBT(new NBTTagCompound());BlockPos local=ArmOrientation.inverse(root.mountDirection(),added.subtract(root.getPos()));
        saved.getTagList("GearFeet",11).appendTag(new net.minecraft.nbt.NBTTagIntArray(new int[]{local.getX(),local.getY(),local.getZ()}));
        GearTileEntity projection=new GearTileEntity();projection.readFromNBT(saved);projection.setWorld(root.getWorld());
        need(projection.assembly()!=null&&projection.assembly().feet().size()==count()+1,"complete detached append projection");return projection;
    }
    /** The camera floor stays native throughout the test and must clear the new mesh's entire stroke. */
    private static java.util.List<AxisAlignedBB> expandedStroke(GearTileEntity root,BlockPos added){
        GearTileEntity projection=expandedProjection(root,added);
        java.util.List<AxisAlignedBB> result=new java.util.ArrayList<>(),before=GearGeometry.boxes(projection,0);
        for(int step=1;step<=GearTileEntity.ANIMATION_TICKS;step++){
            double a=(step-1)/(double)GearTileEntity.ANIMATION_TICKS,b=step/(double)GearTileEntity.ANIMATION_TICKS;java.util.List<AxisAlignedBB> after=GearGeometry.boxes(projection,b);
            need(before.size()==after.size(),"stable projected component count");for(int i=0;i<before.size();i++)result.add(GearGeometry.swept(projection,i,before.get(i),after.get(i),a,b));before=after;
        }
        need(root.getWorld().getTileEntity(root.getPos())==root&&root.progress()==0&&root.assembly().feet().size()==count(),"forecast leaves authoritative assembly unchanged");
        return result;
    }
    private static String serverStatus(Minecraft mc)throws Exception{
        net.minecraft.tileentity.TileEntity clientTile=mc.world.getTileEntity(ROOT);
        String client=" clientRoot="+clientTile;
        if(clientTile instanceof GearTileEntity){GearTileEntity tile=(GearTileEntity)clientTile;client+=" clientPhase="+tile.progress()+" clientTarget="+tile.target()+" clientError="+tile.error()+" clientFeet="+(tile.assembly()==null?-1:tile.assembly().feet().size())+" clientMount="+tile.mountDirection()+" clientRenderer="+com.vandorlabs.gear.client.GearRenderer.visible(tile);}
        return client+mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);net.minecraft.tileentity.TileEntity raw=world.getTileEntity(ROOT);
            if(!(raw instanceof GearTileEntity))return " serverRoot="+raw;
            GearTileEntity arm=(GearTileEntity)raw;StringBuilder out=new StringBuilder(" serverPhase="+arm.progress()+" target="+arm.target()+" error="+arm.error()+" operational="+arm.isOperational()+" owner="+arm.owner()+" feet="+(arm.assembly()==null?-1:arm.assembly().feet().size())+" transactions="+ArmTransactions.locked(world,ROOT)+" standingSupport="+standingSupport);
            double next=com.vandorlabs.gear.core.GearMotion.advance(arm.progress(),arm.target(),1,GearTileEntity.ANIMATION_TICKS);
            java.util.List<AxisAlignedBB> before=GearGeometry.boxes(arm,arm.progress()),after=GearGeometry.boxes(arm,next);int found=0;
            for(int i=0;i<Math.min(before.size(),after.size())&&found<4;i++)if(!before.get(i).equals(after.get(i))){
                AxisAlignedBB swept=GearGeometry.swept(arm,i,before.get(i),after.get(i),arm.progress(),next);
                java.util.List<AxisAlignedBB> obstacles=GearGeometry.foreign(world,arm,swept);
                if(!obstacles.isEmpty()){out.append(" piece=").append(i).append(" sweep=").append(swept).append(" obstacles=").append(obstacles);for(AxisAlignedBB obstacle:obstacles)for(BlockPos cell:BlockPos.getAllInBox(new BlockPos(obstacle.minX,obstacle.minY,obstacle.minZ),new BlockPos(obstacle.maxX-.0001,obstacle.maxY-.0001,obstacle.maxZ-.0001)))if(!world.isAirBlock(cell))out.append(" block=").append(cell).append(':').append(world.getBlockState(cell));found++;}
            }
            return out.toString();
        }).get();
    }
    private ArmStowedFootLiveChecks(){}
}
