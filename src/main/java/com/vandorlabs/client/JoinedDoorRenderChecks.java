package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockLargeProgrammableDoor;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.TileEntityLargeProgrammableDoor;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Reported joined Large Door rendering faults, on two joined door modules (6x3):
 * <ul>
 * <li>They disappeared from the corner of the eye. The group is drawn by its anchor door, so it must
 * stay drawn whenever any part is in view, even when the anchor's 16-block render section is not.
 * The modules straddle a section boundary and the camera turns away frame by frame; every frame with
 * the far columns in the view frustum but the anchor's section outside it must draw the group.</li>
 * <li>Texture Tile must repeat the artwork per block across the whole group, unlike Stretch.</li>
 * <li>Opened rotating doors must swing out as far as the group's half width, not one module's.</li>
 * </ul>
 */
public final class JoinedDoorRenderChecks {
    /** x is 12 within its 16-block section, so the second module's last two columns fall in the next one. */
    private static final BlockPos ANCHOR=new BlockPos(780,90,49);
    private static int stage,ticks,frames,checked,missed,yawStep;
    private static volatile boolean serverDone;
    private static volatile Throwable failure;
    private static boolean registered;
    private static LiveGallery gallery;
    private static File output;
    /** Each reported fault is checked even when an earlier one fails, so one run shows them all. */
    private static final java.util.List<String> failures=new java.util.ArrayList<>();
    private JoinedDoorRenderChecks(){}

    private static void server(Minecraft mc,ServerStep step){
        serverDone=false;
        mc.getIntegratedServer().addScheduledTask(()->{
            try{step.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));serverDone=true;}
            catch(RuntimeException|LinkageError error){failure=error;}
        });
    }
    private interface ServerStep{void run(WorldServer world,EntityPlayerMP player);}
    private static BlockLargeProgrammableDoor door(){return (BlockLargeProgrammableDoor)Block.getBlockFromName("vandorlabs:large_programmable_door");}
    private static BufferedImage image(String name)throws java.io.IOException{return ImageIO.read(new File(output,"joined-door-"+name+".png"));}

    public static boolean tick(Minecraft mc,File out)throws Exception{
        output=out;
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(failure!=null)throw new IllegalStateException("joined-door-render server step",failure);
        switch(stage){
            case 0:
                if(mc.player==null||++ticks<40)return false;
                server(mc,JoinedDoorRenderChecks::build);stage=1;return false;
            case 1:
                if(!serverDone)return false;
                if(gallery==null)gallery=new LiveGallery("joined-door",ANCHOR).shot("front",3,0,9,180,5);
                if(!gallery.tick(mc,out))return false;
                TileEntityLargeProgrammableDoor door=(TileEntityLargeProgrammableDoor)mc.world.getTileEntity(ANCHOR);
                if(door==null||door.joinedGroup().members.size()!=2)throw new IllegalStateException("joined-door-render client sees the joined 2x1 group: "+(door==null?"missing":door.joinedGroup().members.size()));
                if(!registered){MinecraftForge.EVENT_BUS.register(JoinedDoorRenderChecks.class);registered=true;}
                stage=2;yawStep=0;frames=0;ticks=0;return false;
            case 2:{
                // Stand south of the door, level with the second module, and turn right a little each tick.
                Entity eye=mc.player;
                eye.setPosition(ANCHOR.getX()+5.5,ANCHOR.getY(),ANCHOR.getZ()+6.5);
                float yaw=180+yawStep*.5F;
                eye.rotationYaw=eye.prevRotationYaw=yaw;eye.rotationPitch=eye.prevRotationPitch=5;
                if(++ticks%2==0)yawStep++;
                if(yaw<=270)return false;
                if(checked<5)throw new IllegalStateException("joined-door-render never had the door in view without its anchor section: checked="+checked+" frames="+frames);
                if(missed>0)failures.add("joined door vanished in "+missed+" of "+checked+" frames where it was in view but its anchor's render section was not");
                else System.out.println("[vandorlabs] joined-door-render culling ok frames="+checked);
                server(mc,(world,player)->layout(world,TextureLayoutMode.STRETCH));gallery=null;stage=3;return false;
            }
            case 3:
                if(!serverDone)return false;
                if(gallery==null)gallery=new LiveGallery("joined-door",ANCHOR).shot("stretch",3,0,9,180,5);
                if(!gallery.tick(mc,out))return false;
                server(mc,(world,player)->layout(world,TextureLayoutMode.TILE));gallery=null;stage=4;return false;
            case 4:{
                if(!serverDone)return false;
                if(gallery==null)gallery=new LiveGallery("joined-door",ANCHOR).shot("tile",3,0,9,180,5);
                if(!gallery.tick(mc,out))return false;
                BufferedImage stretch=image("stretch"),tile=image("tile");
                int differ=0,total=0;
                for(int y=stretch.getHeight()/4;y<stretch.getHeight()*3/4;y++)for(int x=stretch.getWidth()/4;x<stretch.getWidth()*3/4;x++){
                    int a=stretch.getRGB(x,y),b=tile.getRGB(x,y);total++;
                    if(Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255))>40)differ++;
                }
                System.out.println("[vandorlabs] joined-door-render texture Tile differs from Stretch in "+differ+" of "+total+" central pixels");
                if(differ<total/20)failures.add("Texture Tile looks the same as Stretch on a joined door: "+differ+" of "+total+" pixels differ");
                server(mc,JoinedDoorRenderChecks::openRotating);gallery=null;stage=5;ticks=0;return false;
            }
            case 5:{
                if(!serverDone||++ticks<60)return false;
                // From straight above: the opened leaves' reach across the doorway against the group's width.
                if(gallery==null)gallery=new LiveGallery("joined-door",ANCHOR).shot("open-top",3,10,.5F,180,90);
                if(!gallery.tick(mc,out))return false;
                BufferedImage top=image("open-top");
                int minX=Integer.MAX_VALUE,maxX=-1,minY=Integer.MAX_VALUE,maxY=-1;
                // Only the floor area around the door; the frame edge shows grass beyond the quartz.
                for(int y=top.getHeight()/5;y<top.getHeight()*4/5;y++)for(int x=top.getWidth()/6;x<top.getWidth()*5/6;x++){
                    int rgb=top.getRGB(x,y),level=((rgb>>16&255)+(rgb>>8&255)+(rgb&255))/3;
                    if(level<120){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
                }
                if(maxX<0)throw new IllegalStateException("joined-door-render opened door not visible from above");
                double ratio=(maxY-minY+1)/(double)(maxX-minX+1);
                System.out.println("[vandorlabs] joined-door-render opened rotating door reach/width="+ratio+" box="+minX+","+minY+".."+maxX+","+maxY);
                if(ratio<.4)failures.add("opened rotating leaves reach only "+ratio+" of the group's width (expected about half): they open as wide as one module");
                server(mc,JoinedDoorRenderChecks::glassTile);gallery=null;stage=6;ticks=0;return false;
            }
            case 6:{
                if(!serverDone||++ticks<60)return false;
                if(gallery==null)gallery=new LiveGallery("joined-door",ANCHOR).shot("glass-tile",3,0,9,180,5);
                if(!gallery.tick(mc,out))return false;
                // Tile glass repeats per block across the whole 6x3 group: each leaf is 3 wide and 3 tall.
                double sx=TESlidingDoor.lastGlassScaleX,sy=TESlidingDoor.lastGlassScaleY;
                System.out.println("[vandorlabs] joined-door-render glass tiling scale "+sx+"x"+sy);
                if(Math.abs(sx-3)>1e-9||Math.abs(sy-1.5)>1e-9)failures.add("Tile glass is laid out for one module ("+sx+"x"+sy+"), not the joined leaf (3.0x1.5)");
                if(!failures.isEmpty())throw new IllegalStateException("joined-door-render "+failures.size()+" fault(s): "+String.join(" | ",failures));
                System.out.println("[vandorlabs] joined-door-render PASS checks="+checked+" culling frames drew the group; Tile artwork and glass repeat across the group; rotating leaves open to half the group width");
                return true;
            }
            default:return false;
        }
    }

    private static boolean visible(Minecraft mc){return mc.world!=null&&mc.world.getTileEntity(ANCHOR) instanceof TileEntityLargeProgrammableDoor;}

    /** Each frame: was the group drawn while part of it was in view and its anchor section was not? */
    @SubscribeEvent public static void frame(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();
        int draws=TESlidingDoor.largeGroupDraws;TESlidingDoor.largeGroupDraws=0;
        if(stage!=2||!visible(mc))return;
        Entity view=mc.getRenderViewEntity();float partial=event.getPartialTicks();
        double x=view.lastTickPosX+(view.posX-view.lastTickPosX)*partial,y=view.lastTickPosY+(view.posY-view.lastTickPosY)*partial,z=view.lastTickPosZ+(view.posZ-view.lastTickPosZ)*partial;
        Frustum frustum=new Frustum();frustum.setPosition(x,y,z);
        // The second module's two columns beyond the section boundary, and the anchor's render section.
        AxisAlignedBB beyond=new AxisAlignedBB(ANCHOR.getX()+4,ANCHOR.getY(),ANCHOR.getZ(),ANCHOR.getX()+6,ANCHOR.getY()+3,ANCHOR.getZ()+1);
        int sx=ANCHOR.getX()>>4<<4,sy=ANCHOR.getY()>>4<<4,sz=ANCHOR.getZ()>>4<<4;
        AxisAlignedBB section=new AxisAlignedBB(sx,sy,sz,sx+16,sy+16,sz+16);
        frames++;
        if(frustum.isBoundingBoxInFrustum(beyond)&&!frustum.isBoundingBoxInFrustum(section)){
            checked++;
            if(draws==0){missed++;System.out.println("[vandorlabs] joined-door-render missing draw yaw="+view.rotationYaw+" frame="+frames);}
        }
    }

    private static void members(WorldServer world,java.util.function.Consumer<TileEntityLargeProgrammableDoor> action){
        for(int module=0;module<2;module++){TileEntityLargeProgrammableDoor unit=door().root(world,ANCHOR.east(3*module));action.accept(unit);}
    }
    private static void layout(WorldServer world,int mode){
        int furnace=com.vandorlabs.tiles.CustomBlockMaterials.choice(new ItemStack(Blocks.FURNACE));
        members(world,unit->{unit.setFaceTexture(furnace);unit.setTextureLayout(mode);unit.markDirty();});
        world.notifyBlockUpdate(ANCHOR,world.getBlockState(ANCHOR),world.getBlockState(ANCHOR),3);
    }
    private static void openRotating(WorldServer world,EntityPlayerMP player){
        members(world,unit->{unit.configure(unit.getDesign(),unit.getDetail(),false,0,true,false);unit.markDirty();});
        door().toggleDoor(world,ANCHOR,true);
    }
    private static void glassTile(WorldServer world,EntityPlayerMP player){
        door().toggleDoor(world,ANCHOR,false);
        int furnace=com.vandorlabs.tiles.CustomBlockMaterials.choice(new ItemStack(Blocks.FURNACE));
        members(world,unit->{unit.setFaceTexture(-1);unit.configure(15,unit.getDetail(),false,0,true,true);
            unit.setGlassAppearance(new com.vandorlabs.tiles.DoorGlassAppearance(furnace,0,0,TextureLayoutMode.TILE));unit.markDirty();});
        world.notifyBlockUpdate(ANCHOR,world.getBlockState(ANCHOR),world.getBlockState(ANCHOR),3);
    }
    private static void build(WorldServer world,EntityPlayerMP player){
        player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();
        world.setWorldTime(6000);
        for(BlockPos at:BlockPos.getAllInBox(ANCHOR.add(-14,-1,-6),ANCHOR.add(20,12,12)))
            world.setBlockState(at,at.getY()==ANCHOR.getY()-1?Blocks.QUARTZ_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),2);
        BlockLargeProgrammableDoor door=door();
        // Placing while looking north faces the door south, with its two modules running east.
        player.connection.setPlayerLocation(ANCHOR.getX()+3.5,ANCHOR.getY(),ANCHOR.getZ()+4.5,180,10);player.rotationYaw=180;
        ItemStack stack=new ItemStack(door,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);
        for(int module=0;module<2;module++){
            BlockPos at=ANCHOR.east(3*module);
            if(stack.getItem().onItemUse(player,world,at.east().down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)!=EnumActionResult.SUCCESS)
                throw new IllegalStateException("joined-door-render door module "+module+" placement at "+at);
            TileEntityLargeProgrammableDoor unit=door.root(world,at);
            if(unit==null||!unit.getPos().equals(at))throw new IllegalStateException("joined-door-render module root "+module+" expected "+at+" got "+(unit==null?"none":unit.getPos()));
            unit.setJoin(true);door.joinChanged(world,unit);
        }
        TileEntityLargeProgrammableDoor root=door.root(world,ANCHOR);
        if(root==null||root.joinedGroup().members.size()!=2)throw new IllegalStateException("joined-door-render joined 2x1 group on the server");
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
    }
}
