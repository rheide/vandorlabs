package com.vandorlabs.gear;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.blocks.BlockTurntableSurface;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.render.LargeDoorJoin;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Documentation capture for the 2.12 page: a joined 2x2 horizontal Large Door hatch opens in an
 * X, four joined Extenders lift a 6x6 Turntable Surface platform up through it and well above
 * the floor, then lower it and the hatch closes. Everything is built through the real item and
 * arm paths and driven by real world ticks; the client only records the rendered frames.
 * Frames and timings go to {@code -Dvandorlabs.xDoorLiftOut}.
 */
@SideOnly(Side.CLIENT)
public final class XDoorLiftAnimation {
    private static final BlockPos FLOOR=new BlockPos(104,70,40);
    /** Minimum corner of the 6x6 shaft, platform and door footprint. */
    private static final int X0=FLOOR.getX(),Z0=FLOOR.getZ(),SIZE=6,SECTIONS=8;
    private static final BlockPos ROOT=FLOOR.up();
    private static final int[][] COLUMNS={{1,1},{1,4},{4,1},{4,4}};
    private static final long FRAME_NS=100_000_000L;
    private static int step,ticks,wait,hatchY=-1;
    private static BlockLargeProgrammableDoor door;
    private static TileEntityLargeProgrammableDoor root;
    private static boolean registered;
    private static volatile boolean recording,complete;
    private static volatile String phase="setup";
    /** Fixed documentation camera, applied on the client every frame once set. */
    private static volatile double[] camera;
    private static volatile Throwable failure;
    private static final List<BufferedImage> frames=new ArrayList<>();
    private static final StringBuilder times=new StringBuilder("file\telapsed_ms\tphase\n");
    private static long start,next;
    private XDoorLiftAnimation(){}

    private static void require(boolean value,String message,WorldServer world){
        if(!value)throw new IllegalStateException("x-door-lift step="+step+" tick="+ticks+" "+message+" "+state(world));
    }
    private static BlockPos column(int index){return ROOT.add(COLUMNS[index][0],0,COLUMNS[index][1]);}
    private static GearTileEntity arm(WorldServer world){
        return world.getTileEntity(column(0)) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(column(0))).loadedController():null;
    }
    private static boolean settled(WorldServer world,GearTileEntity arm,double progress){
        return arm!=null&&arm.progress()==progress&&!ArmTransactions.locked(world,arm.getPos())&&arm.payload()!=null&&arm.payload().materialized();
    }
    private static String state(WorldServer world){
        GearTileEntity arm=world==null?null:arm(world);
        return "arm="+(arm==null?"missing":"progress="+arm.progress()+" target="+arm.target()+" columns="+arm.columns().size()+" error="+arm.error())
                +" hatchY="+hatchY+(root==null||world==null?"":" open="+world.getBlockState(root.getPos()).getValue(BlockVandorDoor.OPEN));
    }
    private static boolean platformAt(WorldServer world,int y){
        for(int x=0;x<SIZE;x++)for(int z=0;z<SIZE;z++)if(!(world.getBlockState(new BlockPos(X0+x,y,Z0+z)).getBlock() instanceof BlockTurntableSurface))return false;
        return true;
    }
    private static boolean inShaft(int x,int z){return x>=X0&&x<X0+SIZE&&z>=Z0&&z<Z0+SIZE;}

    public static boolean tick(Minecraft mc){
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){
            mc.gameSettings.hideGUI=true;mc.gameSettings.clouds=0;mc.gameSettings.pauseOnLostFocus=false;
            MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));MinecraftForge.EVENT_BUS.register(new XDoorLiftAnimation());registered=true;
        }
        if(failure!=null)throw new IllegalStateException("X door lift animation",failure);
        if(complete&&!recording){write();return true;}
        return false;
    }
    /** Recorded after the real world render; images are written only after the cycle. */
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!recording)return;
        Minecraft mc=Minecraft.getMinecraft();long now=System.nanoTime();
        if(start==0){start=now;next=now;}
        if(now<next)return;
        next+=FRAME_NS;if(next<now)next=now+FRAME_NS;
        BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
        // Centre 4:3 crop, kept at capture resolution; the encoder scales it once.
        int height=shot.getHeight(),width=Math.min(shot.getWidth(),height*4/3),left=(shot.getWidth()-width)/2;
        BufferedImage cropped=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics=cropped.createGraphics();graphics.drawImage(shot,0,0,width,height,left,0,left+width,height,null);graphics.dispose();
        String name=String.format(Locale.ROOT,"frame-%04d.png",frames.size());frames.add(cropped);
        times.append(name).append('\t').append(String.format(Locale.ROOT,"%.1f",(now-start)/1e6)).append('\t').append(phase).append('\n');
    }
    private static void write(){
        File out=new File(System.getProperty("vandorlabs.xDoorLiftOut","x-door-lift"));
        if(!out.mkdirs()&&!out.isDirectory())throw new IllegalStateException("Capture directory unavailable: "+out);
        try{
            for(int i=0;i<frames.size();i++)ImageIO.write(frames.get(i),"png",new File(out,String.format(Locale.ROOT,"frame-%04d.png",i)));
            Files.write(new File(out,"frames.tsv").toPath(),times.toString().getBytes(StandardCharsets.UTF_8));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("[vandorlabs][reprolab] utility26-focused PASS x-door-lift frames="+frames.size()+" out="+out);
        frames.clear();
    }

    @SideOnly(Side.CLIENT)
    public static final class Observer {
        private final MinecraftServer server;
        private Observer(MinecraftServer server){this.server=server;}
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
            if(event.phase!=TickEvent.Phase.END||complete||failure!=null||server.getPlayerList().getPlayers().isEmpty())return;
            try{complete=run(server.getWorld(0),server.getPlayerList().getPlayers().get(0));}
            catch(RuntimeException|LinkageError error){failure=error;recording=false;}
            if(complete||failure!=null)MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
    private static void build(WorldServer world,EntityPlayerMP player){
        player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
        world.setWorldTime(6000);world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.getWorldInfo().setRaining(false);
        // Clear stage, pit floor under the shaft.
        for(BlockPos at:BlockPos.getAllInBox(FLOOR.add(-12,0,-12),FLOOR.add(SIZE+11,SECTIONS+12,SIZE+11)))
            world.setBlockState(at,at.getY()==FLOOR.getY()?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        Block surface=Block.getBlockFromName("vandorlabs:turntable_surface");
        for(int c=0;c<COLUMNS.length;c++)for(int n=0;n<SECTIONS;n++)
            require(((ItemBlock)Item.getItemFromBlock(GearRegistry.GEAR_EXTENDER)).placeBlockAt(new ItemStack(GearRegistry.GEAR_EXTENDER),player,world,column(c).up(n),EnumFacing.UP,.5F,.5F,.5F,GearRegistry.GEAR_EXTENDER.getDefaultState()),"Extender section "+c+"/"+n,world);
        for(int x=0;x<SIZE;x++)for(int z=0;z<SIZE;z++)
            world.setBlockState(new BlockPos(X0+x,ROOT.getY()+SECTIONS,Z0+z),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,EnumFacing.UP),3);
        // One landing-pad artwork stretched across the whole joined 6x6 pad.
        ((com.vandorlabs.tiles.TileEntityTurntableSurface)world.getTileEntity(new BlockPos(X0,ROOT.getY()+SECTIONS,Z0))).configure("landing","landing",com.vandorlabs.render.TextureLayoutMode.STRETCH,player);
        for(int c=0;c<COLUMNS.length;c++){
            GearTileEntity member=(GearTileEntity)world.getTileEntity(column(c));member.operator(player);
            require(member.rebuild(),"column "+c+" forms "+member.error(),world);
            NBTTagCompound settings=member.settings();settings.setBoolean("JoinArms",true);member.configure(settings);
        }
        GearTileEntity built=arm(world);
        require(built!=null&&built.rebuild()&&built.columns().size()==4,"four joined Extenders carry the platform",world);
    }
    /** Ground flush with the hatch, with the 6x6 shaft left open below it. */
    private static void ground(WorldServer world){
        for(int x=X0-10;x<X0+SIZE+10;x++)for(int z=Z0-10;z<Z0+SIZE+10;z++){
            if(inShaft(x,z))continue;
            for(int y=FLOOR.getY()+1;y<=hatchY;y++)world.setBlockState(new BlockPos(x,y,z),y==hatchY?Blocks.QUARTZ_BLOCK.getDefaultState():Blocks.STONE.getDefaultState(),2);
        }
    }
    private static ItemStack doorStack(){
        TileEntityLargeProgrammableDoor template=new TileEntityLargeProgrammableDoor();
        NBTTagCompound settings=template.itemSettings();
        settings.setBoolean("SpaceDoorSliding",true);settings.setInteger("SpaceSlideDirection",3);
        // No frame or panel, so the whole 6x6 aperture clears for the platform.
        settings.setBoolean("SpaceFramed",false);settings.setBoolean("SpaceDoorPanel",false);
        settings.setBoolean(LargeDoorJoin.TAG,true);
        ItemStack stack=new ItemStack(door,4);stack.setTagInfo("SpaceDoorSettings",settings);return stack;
    }
    private static void hatch(WorldServer world,EntityPlayerMP player){
        door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
        ItemStack stack=doorStack();player.setHeldItem(EnumHand.MAIN_HAND,stack);
        // First row from the west support edge, then the second row from each module's east edge.
        for(int z:new int[]{1,4})
            require(stack.getItem().onItemUse(player,world,new BlockPos(X0-1,hatchY,Z0+z),EnumHand.MAIN_HAND,EnumFacing.EAST,1F,.95F,.5F)==EnumActionResult.SUCCESS,"first-row module z="+z,world);
        for(int z:new int[]{1,4}){
            TileEntityLargeProgrammableDoor first=door.root(world,new BlockPos(X0+1,hatchY,Z0+z));
            require(first!=null,"first-row module installed z="+z,world);
            require(stack.getItem().onItemUse(player,world,new BlockPos(X0+2,hatchY,Z0+z),EnumHand.MAIN_HAND,EnumFacing.EAST,1F,.95F,.5F)==EnumActionResult.SUCCESS,"second-row module from the edge z="+z,world);
        }
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        root=door.root(world,new BlockPos(X0,hatchY,Z0));
        require(root!=null&&root.isHorizontal()&&door.complete(world,root),"horizontal hatch installed",world);
        LargeDoorJoin.Group<TileEntityLargeProgrammableDoor> group=root.joinedGroup();
        require(group!=null&&group.columns==2&&group.rows==2&&group.members.size()==4,"one joined 2x2 hatch",world);
        for(TileEntityLargeProgrammableDoor member:group.members)
            require(member.isXSplit()&&!member.isFramed()&&!member.hasPanel(),"every module opens in an X without frame or panel",world);
        root=group.anchor;
    }
    private static boolean open(WorldServer world){return world.getBlockState(root.getPos()).getValue(BlockVandorDoor.OPEN);}
    private static void camera(EntityPlayerMP player){
        // From the south, 20 degrees off-axis and above the raised pad: the hatch's diagonal
        // seams still read as an X while all four arms and the pad's top stay visible.
        double cx=X0+SIZE/2D,cz=Z0+SIZE/2D,angle=Math.toRadians(20),distance=10;
        double x=cx-distance*Math.sin(angle),z=cz+distance*Math.cos(angle);
        float yaw=(float)-Math.toDegrees(Math.atan2(cx-x,cz-z));
        double[] view={x,hatchY+8,z,yaw,35};
        player.connection.setPlayerLocation(view[0],view[1],view[2],(float)view[3],(float)view[4]);camera=view;
    }
    /** Hold the client camera still: no gravity, drift or interpolation between frames. */
    @SubscribeEvent public void hold(TickEvent.RenderTickEvent event){
        double[] view=camera;Minecraft mc=Minecraft.getMinecraft();
        if(event.phase!=TickEvent.Phase.START||view==null||mc.player==null)return;
        net.minecraft.client.entity.EntityPlayerSP p=mc.player;
        p.capabilities.isFlying=true;p.motionX=p.motionY=p.motionZ=0;
        p.setPositionAndRotation(view[0],view[1],view[2],(float)view[3],(float)view[4]);
        p.prevPosX=p.lastTickPosX=view[0];p.prevPosY=p.lastTickPosY=view[1];p.prevPosZ=p.lastTickPosZ=view[2];
        p.prevRotationYaw=p.rotationYawHead=p.prevRotationYawHead=(float)view[3];p.prevRotationPitch=(float)view[4];
        p.cameraYaw=p.prevCameraYaw=p.cameraPitch=p.prevCameraPitch=0;p.distanceWalkedModified=p.prevDistanceWalkedModified=0;
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        GearTileEntity arm=step==0?null:arm(world);
        require(++ticks<4000,"bounded capture",world);
        switch(step){
            case 0:
                build(world,player);player.connection.setPlayerLocation(X0-8,FLOOR.getY()+SECTIONS+6,Z0-8,-45,30);
                step=1;return false;
            case 1:
                if(!arm.isOperational())return false;
                arm.operator(player);arm.target(false);step=2;return false;
            case 2:{
                if(!settled(world,arm,0))return false;
                require(arm.payload().placedCells(arm).size()==SIZE*SIZE,"arm carries the whole 6x6 platform cells="+arm.payload().placedCells(arm).size(),world);
                int lowered=-1;for(int y=ROOT.getY();y<=ROOT.getY()+SECTIONS&&lowered<0;y++)if(platformAt(world,y))lowered=y;
                require(lowered>0&&ROOT.getY()+SECTIONS-lowered>=4,"retracted platform sits well below its raised height y="+lowered,world);
                hatchY=lowered+1;ground(world);hatch(world,player);camera(player);
                wait=ticks;step=3;return false;
            }
            case 3:
                // Let chunk rebuilds and the camera settle before the first frame.
                if(ticks-wait<60)return false;
                phase="closed";recording=true;wait=ticks;step=4;return false;
            case 4:
                if(ticks-wait<16)return false;
                phase="opening";door.toggleDoor(world,root.getPos(),true);require(open(world),"hatch opens",world);
                wait=ticks;step=5;return false;
            case 5:
                if(ticks-wait<26)return false;
                phase="rising";arm.operator(player);arm.target(true);wait=ticks;step=6;return false;
            case 6:
                require(!arm.error().toLowerCase(Locale.ROOT).contains("block"),"open hatch lets the platform through",world);
                if(!settled(world,arm,1)){require(ticks-wait<900,"platform rises",world);return false;}
                require(platformAt(world,ROOT.getY()+SECTIONS),"platform raised above the floor",world);
                phase="raised";wait=ticks;step=7;return false;
            case 7:
                if(ticks-wait<24)return false;
                phase="lowering";arm.operator(player);arm.target(false);wait=ticks;step=8;return false;
            case 8:
                if(!settled(world,arm,0)){require(ticks-wait<900,"platform lowers",world);return false;}
                phase="lowered";wait=ticks;step=9;return false;
            case 9:
                if(ticks-wait<8)return false;
                phase="closing";door.toggleDoor(world,root.getPos(),false);require(!open(world),"hatch closes once the platform is clear",world);
                wait=ticks;step=10;return false;
            case 10:
                if(ticks-wait<36)return false;
                recording=false;return true;
            default:return false;
        }
    }
}
