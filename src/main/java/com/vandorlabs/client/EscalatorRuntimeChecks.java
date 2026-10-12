package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.ramp.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Focused real-world checks, including player support and sleeping controllers. */
final class EscalatorRuntimeChecks {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("escalator: "+message);}
    private static void field(TileEntityRampController tile,String name,Object value) {
        try{java.lang.reflect.Field f=TileEntityRampController.class.getDeclaredField(name);f.setAccessible(true);f.set(tile,value);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void sync(TileEntityRampController tile) {
        try {java.lang.reflect.Method method=TileEntityRampController.class.getDeclaredMethod("syncParts");method.setAccessible(true);method.invoke(tile);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void step(TileEntityRampController tile) {
        field(tile,"lastStepTick",tile.getWorld().getTotalWorldTime()-1);tile.scheduledTick();
    }
    static TileEntityRampController fixture(World world,EntityPlayerMP player,BlockPos root,int mode,int rise,int pixels,EnumFacing face) {
        return fixture(world,player,root,mode,rise,pixels,face,0);
    }
    static TileEntityRampController fixture(World world,EntityPlayerMP player,BlockPos root,int mode,int rise,int pixels,EnumFacing face,int interpolation) {
        if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(true);
        for(BlockPos p:BlockPos.getAllInBox(root.add(-6,-5,-6),root.add(6,8,6)))world.setBlockToAir(p);
        world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,face),3);
        for(int row=1;row<=4;row++)for(int width=0;width<2;width++)
            world.setBlockState(root.offset(face,row).offset(face.rotateY(),width),Blocks.STONE_SLAB.getDefaultState(),3);
        player.setPosition(root.getX()+.5,root.getY()+1,root.getZ()+.5);
        TileEntityRampController tile=(TileEntityRampController)world.getTileEntity(root);
        require(tile.configureHalfOffsets(player,0,rise*2,pixels,true,false,false,face,0,false,1,true,false,interpolation,mode),tile.status);
        ControllerRuntimeChecks.elapsed(tile,tile.durationTicks()+1);
        require(tile.attached() && tile.isOpen() && !tile.error && !tile.isEscalatorRunning(),"always extended without power: "+tile.status);
        return tile;
    }
    private static double liveStartZ,liveStartY,liveStoppedZ;
    private static int liveMode,liveItem;
    private static double liveItemStartZ;
    private static long liveStartTick;
    private static int liveWaitTicks;
    private static final BlockPos LIVE=new BlockPos(64,30,64);
    private static void server(net.minecraft.client.Minecraft mc,Runnable action) {
        try{mc.getIntegratedServer().addScheduledTask(action).get();}
        catch(Exception e){throw new IllegalStateException("Live escalator rider",e);}
    }
    static void beginPlayerRide(net.minecraft.client.Minecraft mc) {
        liveMode++;
        server(mc,()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
            TileEntityRampController tile=fixture(player.world,player,LIVE,liveMode==2?2:1,liveMode==3?0:liveMode==4?4:liveMode==5?1:2,liveMode==4?16:liveMode==5?1:8,EnumFacing.SOUTH,liveMode==4?1:0);
            liveStartZ=LIVE.getZ()+(liveMode==2?4.25:2.25);
            liveStartY=LIVE.getY()+.5+EscalatorGeometry.height(liveMode==1?1.5:3.5,4,8,2);
            liveStartY=Double.NEGATIVE_INFINITY;
            sync(tile);
            for(BlockPos cell:tile.vehicleCells())for(AxisAlignedBB local:((TileEntityControlledRamp)player.world.getTileEntity(cell)).boxes(0)) {
                AxisAlignedBB b=local.offset(cell);
                if(LIVE.getX()+.8>b.minX && LIVE.getX()+.2<b.maxX && liveStartZ+.3>b.minZ && liveStartZ-.3<b.maxZ)liveStartY=Math.max(liveStartY,b.maxY);
            }
            player.capabilities.isFlying=false;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(LIVE.getX()+.5,liveStartY,liveStartZ,0,0);
            player.motionX=player.motionY=player.motionZ=0;player.onGround=true;
            net.minecraft.entity.item.EntityItem item=new net.minecraft.entity.item.EntityItem(player.world,LIVE.getX()-.5,liveStartY,liveStartZ,new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT));
            item.motionX=item.motionY=item.motionZ=0;item.setPickupDelay(32767);player.world.spawnEntity(item);liveItem=item.getEntityId();liveItemStartZ=item.posZ;
            player.world.setBlockState(LIVE.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.updatePower();
            liveStartTick=player.world.getTotalWorldTime();liveWaitTicks=0;
        });
        mc.player.capabilities.isFlying=false;mc.player.setPosition(LIVE.getX()+.5,liveStartY,liveStartZ);
        mc.player.motionX=mc.player.motionY=mc.player.motionZ=0;mc.player.onGround=true;
    }
    static boolean rideReady(net.minecraft.client.Minecraft mc) {
        final boolean[] ready={false};
        server(mc,()->ready[0]=mc.getIntegratedServer().getEntityWorld().getTotalWorldTime()-liveStartTick>=80);
        require(++liveWaitTicks<600,"server did not advance 80 ride ticks");
        return ready[0];
    }
    static void stopPlayerRide(net.minecraft.client.Minecraft mc) {
        server(mc,()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
            TileEntityRampController tile=(TileEntityRampController)player.world.getTileEntity(LIVE);
            require(!tile.error && tile.isEscalatorRunning(),"live player stopped belt: "+tile.status);
            require(liveMode==2?player.posZ-liveStartZ< -1 && player.posY-liveStartY< -.3:player.posZ-liveStartZ>1 && (liveMode==3?Math.abs(player.posY-liveStartY)<.1:player.posY-liveStartY>.3),"live player was not carried in direction "+liveMode);
            require(Math.abs(player.posZ-mc.player.posZ)<.4 && Math.abs(player.posY-mc.player.posY)<.55,"client/server rider positions diverged");
            net.minecraft.entity.Entity item=player.world.getEntityByID(liveItem);
            require(item instanceof net.minecraft.entity.item.EntityItem && !item.isDead,"dropped item disappeared");
            require(liveMode==2?item.posZ-liveItemStartZ< -1:item.posZ-liveItemStartZ>1,"dropped item rocked instead of travelling, mode="+liveMode+", distance="+(item.posZ-liveItemStartZ)+", serverTicks="+(player.world.getTotalWorldTime()-liveStartTick)+", beltDistance="+tile.beltDistance(0)+", itemY="+item.posY+", playerDistance="+(player.posZ-liveStartZ));
            net.minecraft.entity.Entity clientItem=mc.world.getEntityByID(liveItem);
            require(clientItem!=null && Math.abs(item.posZ-clientItem.posZ)<.4,"item client/server transport diverged");
            player.world.setBlockToAir(LIVE.north());tile.updatePower();liveStoppedZ=player.posZ;
        });
    }
    static void finishPlayerRide(net.minecraft.client.Minecraft mc) {
        server(mc,()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUsername(mc.player.getName());
            TileEntityRampController tile=(TileEntityRampController)player.world.getTileEntity(LIVE);
            require(!tile.isEscalatorRunning() && tile.isOpen() && Math.abs(player.posZ-liveStoppedZ)<.1,"live rider kept moving with redstone off");
            net.minecraft.entity.Entity item=player.world.getEntityByID(liveItem);if(item!=null)item.setDead();
            player.capabilities.isFlying=true;player.sendPlayerAbilities();
            player.connection.setPlayerLocation(LIVE.getX(),LIVE.getY()+5,LIVE.getZ(),0,0);tile.recover(false);
            System.out.println("[vandorlabs][reprolab] escalator-player-live PASS real client/server rider transport and redstone stop, mode="+liveMode+"");
        });
        mc.player.capabilities.isFlying=true;
    }
    static void run(World world,EntityPlayerMP player) {
        double px=player.posX,py=player.posY,pz=player.posZ;boolean flying=player.capabilities.isFlying;
        BlockPos root=new BlockPos(64,30,64);
        try {
            for(EnumFacing face:EnumFacing.HORIZONTALS)for(int mode=1;mode<=2;mode++)for(int rise:new int[]{-4,-2,0,2,4})
            for(int interpolation=0;interpolation<3;interpolation++)
            for(int pixels:new int[]{4,8,16}) {
                TileEntityRampController tile=fixture(world,player,root,mode,rise,pixels,face,interpolation);
                player.capabilities.isFlying=true;
                world.setBlockState(root.offset(face.getOpposite()),Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.updatePower();
                require(tile.isEscalatorRunning(),"powered escalator did not run");
                Set<BlockPos> cells=new HashSet<>(tile.vehicleCells());
                Map<BlockPos,Object> identities=new HashMap<>();for(BlockPos p:cells)identities.put(p,world.getTileEntity(p));
                double initial=tile.beltDistance(0);
                for(int tick=0;tick<12;tick++)step(tile);
                require(tile.beltDistance(0)!=initial,"belt did not advance");
                require(cells.equals(tile.vehicleCells()),"circulation changed reservations");
                for(BlockPos p:cells)require(world.getTileEntity(p)==identities.get(p),"circulation replaced a world cell");
                sync(tile);
                // A rider straddling treads follows the highest support, once per tick.
                BlockPos center=root.offset(face,2);
                double x=center.getX()+.5,z=center.getZ()+.5;
                double top=Double.NEGATIVE_INFINITY;
                for(BlockPos p:cells)for(AxisAlignedBB b:((TileEntityControlledRamp)world.getTileEntity(p)).boxes(0)) {
                    b=b.offset(p);if(x+.3>b.minX && x-.3<b.maxX && z+.3>b.minZ && z-.3<b.maxZ)top=Math.max(top,b.maxY);
                }
                player.capabilities.isFlying=false;player.motionY=0;player.setPosition(x,top,z);player.onGround=true;
                double beforeX=player.posX,beforeY=player.posY,beforeZ=player.posZ;
                step(tile);
                double along=(player.posX-beforeX)*face.getFrontOffsetX()+(player.posZ-beforeZ)*face.getFrontOffsetZ();
                require(Math.abs(Math.abs(along)-EscalatorGeometry.rate(tile.speed))<1e-6,"rider not transported once");
                require(mode==1?player.posY>=beforeY-1e-7:player.posY<=beforeY+1e-7,"rider went the wrong vertical direction");
                if(face==EnumFacing.SOUTH && mode==1 && rise==2 && pixels==4 && interpolation==0) {
                    net.minecraft.entity.item.EntityItem item=new net.minecraft.entity.item.EntityItem(world,x,0,z,new net.minecraft.item.ItemStack(net.minecraft.init.Items.IRON_INGOT));
                    double itemTop=Double.NEGATIVE_INFINITY;
                    for(BlockPos cell:cells)for(AxisAlignedBB box:((TileEntityControlledRamp)world.getTileEntity(cell)).boxes(0)) {
                        box=box.offset(cell);if(x+.125>box.minX && x-.125<box.maxX && z+.125>box.minZ && z-.125<box.maxZ)itemTop=Math.max(itemTop,box.maxY);
                    }
                    item.setPosition(x,itemTop,z);item.motionX=item.motionY=item.motionZ=0;world.spawnEntity(item);step(tile);
                    double carried=item.posZ;
                    item.setPosition(item.posX+.12,item.posY+.08,item.posZ-.1);item.motionY=.2;
                    step(tile);
                    require(Math.abs(item.posZ-carried-EscalatorGeometry.rate(tile.speed))<1e-6,"item push-out impulse defeats belt support");
                    require(Math.abs(((TileEntityControlledRamp)world.getTileEntity(cells.iterator().next())).beltDistance(0)-tile.beltDistance(0))<1e-9,"server tread collision phase differs from rider phase");
                    item.setDead();
                }
                player.capabilities.isFlying=true;player.setPosition(root.getX(),root.getY()+4,root.getZ());
                world.setBlockToAir(root.offset(face.getOpposite()));tile.updatePower();
                double stopped=tile.beltDistance(0);step(tile);
                require(!tile.isEscalatorRunning() && tile.isOpen() && tile.beltDistance(0)==stopped,"off signal retracted or moved ramp");
                world.setBlockState(root.offset(face.getOpposite()),Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.updatePower();
                require(tile.isEscalatorRunning(),"resume failed");
                player.setPosition(root.getX()+100,root.getY(),root.getZ());tile.refreshEscalatorActivity();
                require(!tile.isEscalatorRunning(),"no-player sleep failed");
                player.setPosition(root.getX()+38,root.getY()+2,root.getZ());tile.refreshEscalatorActivity();
                require(!tile.isEscalatorRunning(),"sleep hysteresis woke too early");
                player.setPosition(root.getX(),root.getY()+4,root.getZ());tile.refreshEscalatorActivity();
                require(tile.isEscalatorRunning(),"near-player wake failed");
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());double phase=tile.beltDistance(0);
                tile.readFromNBT(saved);tile.resumeAfterLoad();tile.refreshEscalatorActivity();
                require(tile.escalator==mode && Math.abs(tile.beltDistance(0)-phase)<1e-7,"saved phase/mode changed");
                require(tile.recover(false),"restoration failed");
                for(int row=1;row<=4;row++)for(int width=0;width<2;width++)
                    require(world.getBlockState(root.offset(face,row).offset(face.rotateY(),width)).equals(Blocks.STONE_SLAB.getDefaultState()),"source not restored");
            }
            TileEntityRampController landing=fixture(world,player,root,2,-4,16,EnumFacing.EAST);
            world.setBlockState(root.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);landing.updatePower();
            player.capabilities.isFlying=false;player.motionY=0;
            player.setPosition(root.getX()+1.1,root.getY()+1,root.getZ()+.5);player.onGround=true;
            double entryX=player.posX,entryY=player.posY;
            step(landing);
            require(!landing.error && landing.isEscalatorRunning() && player.posX>entryX
                    && Math.abs(player.posY-entryY)<1e-7,"entry landing floor falsely obstructed descending tread");
            player.capabilities.isFlying=true;landing.recover(false);
            for(EnumFacing exitFace:EnumFacing.HORIZONTALS){
                TileEntityRampController exiting=fixture(world,player,root,1,2,8,exitFace);
                world.setBlockState(root.offset(exitFace.getOpposite()),Blocks.REDSTONE_BLOCK.getDefaultState(),3);exiting.updatePower();sync(exiting);
                double ex=root.getX()+.5+exitFace.getFrontOffsetX()*.82,ez=root.getZ()+.5+exitFace.getFrontOffsetZ()*.82;
                player.capabilities.isFlying=false;player.motionY=0;player.setPosition(ex,root.getY()+.75,ez);player.onGround=true;
                for(int walk=0;walk<12;walk++){
                    player.motionX=-exitFace.getFrontOffsetX()*.12;player.motionZ=-exitFace.getFrontOffsetZ()*.12;
                    step(exiting);player.move(net.minecraft.entity.MoverType.SELF,player.motionX,0,player.motionZ);
                }
                double progress=(player.posX-(root.getX()+.5))*exitFace.getFrontOffsetX()+(player.posZ-(root.getZ()+.5))*exitFace.getFrontOffsetZ();
                require(!exiting.error && exiting.isEscalatorRunning() && progress<.45 && player.posY>=root.getY()+1-1e-6,"counterflow lower exit snagged at "+exitFace+", progress="+progress+", feet="+player.posY);
                player.capabilities.isFlying=true;exiting.recover(false);
            }
            TileEntityRampController blocked=fixture(world,player,root,1,2,8,EnumFacing.EAST);
            world.setBlockState(root.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);blocked.updatePower();
            double riderX=root.getX()+2.69,riderZ=root.getZ()+.5;
            double riderY=root.getY()+.5+EscalatorGeometry.height(1.5,4,8,2);
            player.capabilities.isFlying=false;player.motionY=0;player.setPosition(riderX,riderY,riderZ);player.onGround=true;
            BlockPos wall=new BlockPos(root.getX()+3,(int)Math.ceil(riderY+1),root.getZ());
            world.setBlockState(wall,Blocks.STONE.getDefaultState(),3);
            step(blocked);
            require(!blocked.error && blocked.isEscalatorRunning() && player.getEntityBoundingBox().maxX<=wall.getX()+1e-7,"wall contact disabled belt or pushed rider through wall");
            require(world.getBlockState(wall).getBlock()==Blocks.STONE,"obstruction overwritten");
            player.capabilities.isFlying=true;player.setPosition(root.getX(),root.getY()+4,root.getZ());
            world.setBlockToAir(wall);world.setBlockToAir(root.west());blocked.updatePower();
            world.setBlockState(root.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);blocked.updatePower();
            require(!blocked.error && blocked.isEscalatorRunning(),"obstruction retry failed");blocked.recover(false);
            for(int pixels:new int[]{1,2,4,8,16}) {
                TileEntityRampController tile=fixture(world,player,root,1,3,pixels,EnumFacing.SOUTH);
                for(double phase:new double[]{-.001,0,.01,.49,.99,3.99,4.001}) {
                    field(tile,"beltDistance",phase);sync(tile);
                    double volume=0;
                    for(BlockPos p:tile.vehicleCells()) {
                        TileEntityControlledRamp part=(TileEntityControlledRamp)world.getTileEntity(p);
                        for(RampGeometry.Box b:part.geometry(0)) {
                            volume+=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);
                            double bottom=part.sideTextureV(b,b.minY,0),top=part.sideTextureV(b,b.maxY,0);
                            require(Math.abs(bottom-top-(b.maxY-b.minY))<1e-7 && top>=.5-1e-7 && bottom<=1+1e-7,"clipped escalator side UVs changed");
                        }
                    }
                    require(Math.abs(volume-4)<1e-7,"reserved cells omitted moving geometry at phase "+phase+" / "+pixels+" px: "+volume);
                }
                tile.recover(false);
            }
            System.out.println("[vandorlabs][reprolab] escalator-live PASS directions, signed slopes, redstone, riders, fixed cells, sleep/wake, save/load, restoration");
        } finally {
            if(world.getTileEntity(root) instanceof TileEntityRampController)((TileEntityRampController)world.getTileEntity(root)).recover(true);
            player.capabilities.isFlying=flying;player.connection.setPlayerLocation(px,py,pz,player.rotationYaw,player.rotationPitch);
        }
    }
}
