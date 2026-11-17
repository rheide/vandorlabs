package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.navigation.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.awt.image.BufferedImage;

/** Actual chunk-model backdrop and map overlay at distant approaches, including large world coordinates. */
final class NavigationFlightChecks {
    private static final int[] DISTANCES={64,48,32,16,8,4};
    private static int stage,ticks,index,location;private static BlockPos base,owner;private static boolean capture,done;private static File output;
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void server(Minecraft mc,Runnable r)throws Exception{mc.getIntegratedServer().addScheduledTask(r).get();}
    static boolean tick(Minecraft mc,File out)throws Exception{
        output=out;mc.gameSettings.hideGUI=true;if(++ticks>1000)throw new IllegalStateException("navigation flight timeout stage="+stage+" location="+location+" distance="+DISTANCES[index]);
        if(stage==0){base=location==0?new BlockPos(120,200,120):new BlockPos(12000120,200,-12000120);server(mc,()->{WorldServer w=player(mc).getServerWorld();for(BlockPos at:BlockPos.getAllInBox(base.add(-1,-1,-1),base.add(5,1,2)))w.setBlockToAir(at);Block table=Block.getBlockFromName("vandorlabs:navigation_table_angled");for(int x=0;x<4;x++)w.setBlockState(base.east(x),table.getDefaultState(),3);NavigationAssemblies.rebuild(w,base);TileEntityNavigationTable root=((TileEntityNavigationTable)w.getTileEntity(base)).reader();owner=root.getPos();ItemStack pin=new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP);NavigationChip.write(pin,0,base.getX(),base.getZ());root.setInventorySlotContents(0,pin);aim(player(mc),root,4);});stage=1;ticks=0;return false;}
        if(stage==1){if(ticks<40||!(mc.world.getTileEntity(owner) instanceof TileEntityNavigationTable)||!NavigationMapRenderer.ready(owner))return false;stage=2;ticks=0;return false;}
        if(stage==2){server(mc,()->aim(player(mc),((TileEntityNavigationTable)player(mc).world.getTileEntity(base)).reader(),DISTANCES[index]));stage=3;ticks=0;return false;}
        if(stage==3){if(ticks<16)return false;capture=true;done=false;stage=4;return false;}
        if(stage==4&&done){if(++index<DISTANCES.length){stage=2;ticks=0;return false;}index=0;if(++location<2){stage=0;ticks=0;return false;}System.out.println("[vandorlabs] navigation-flight PASS actual angled chunk housing/map depth approaches=12 near=4 far=64 large-coordinate=12000120 all sampled centers visible");return true;}
        return false;
    }
    private static void aim(EntityPlayerMP p,TileEntityNavigationTable root,int distance){NavigationLayout.Vec3 point=root.navigationPlane().overlayPoint(.42,.40);Vec3d target=new Vec3d(point.x,point.y,point.z),eye=target.addVector(0,distance*.4,-distance),d=target.subtract(eye);p.capabilities.isFlying=true;p.sendPlayerAbilities();p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));p.motionX=p.motionY=p.motionZ=0;}
    static void render(Minecraft mc){if(!capture)return;capture=false;try{BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int visible=0,cx=image.getWidth()/2,cy=image.getHeight()/2;for(int y=cy-1;y<=cy+1;y++)for(int x=cx-1;x<=cx+1;x++){int rgb=image.getRGB(x,y);if((rgb>>8&255)>45||(rgb&255)>65)visible++;}if(visible<5)throw new IllegalStateException("Angled map hidden/flickering location="+location+" distance="+DISTANCES[index]+" visible samples="+visible);javax.imageio.ImageIO.write(image,"png",new File(output,"navigation-flight-"+location+"-"+DISTANCES[index]+".png"));done=true;}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
