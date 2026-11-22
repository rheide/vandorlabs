package com.vandorlabs.neoforge.shape;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Client half of the Corner Wall run: the client publishes the joins itself, the terrain model bends
 * the wall slab onto every bevel and drops the end caps a joined wall covers, and the named views
 * are captured. Removing the stacked corners must change the stack view, so the corners are drawn.
 */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CornerWallClientChecks {
    private static final String[] VIEWS={"flat","diagonal0","diagonal1","diagonal2","stack"};
    private static int stage,ticks,wait,view,checks;
    private static volatile String capture;private static volatile boolean captured,requested;private static int frames;
    private static void need(boolean yes,String text){checks++;if(!yes)throw new AssertionError("Corner wall client "+text);}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!CornerWallFixture.enabled()||stage==99)return;var mc=Minecraft.getInstance();
        if(stage==90){if(mc.level==null){System.out.println("[vandorlabs][corner-wall] client PASS checks="+checks+" client joins, bent terrain model with joined caps dropped, views captured, stacked corners drawn");stage=99;mc.stop();}return;}
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
        if(mc.level==null||mc.player==null)return;
        if(++ticks>4000)throw new AssertionError("Corner wall client timeout stage="+stage+" view="+view+" pos="+mc.player.position());
        if(mc.screen!=null)return;
        switch(stage){
            case 0->{
                for(int depth=0;depth<3;depth++)if(!(mc.level.getBlockEntity(CornerWallFixture.flatCorner(depth)) instanceof NativeWallEntity))return;
                for(int y=0;y<4;y++)if(!(mc.level.getBlockEntity(CornerWallFixture.STACK.above(y)) instanceof NativeWallEntity))return;
                if(++wait<60)return;
                mc.options.hideGui=true;mc.options.fov().set(70);mc.options.bobView().set(false);mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                wait=0;stage=1;
            }
            case 1->{
                // The client publishes each flat corner's joins from its own loaded neighbours.
                for(int depth=0;depth<3;depth++){
                    var host=(NativeWallEntity)mc.level.getBlockEntity(CornerWallFixture.flatCorner(depth));
                    if(!(host.wallAppearance().geometry() instanceof NativeCornerWallGeometry published)||published.joined()!=3){need(++wait<400,"publishes both joins at depth "+depth+": "+host.wallAppearance().geometry());return;}
                }
                models(mc);wait=0;stage=2;
            }
            case 2->{
                if(view<VIEWS.length){
                    if(wait==0)mc.player.connection.sendCommand("cornerwall "+VIEWS[view]);
                    double[] v=CornerWallFixture.view(VIEWS[view]);var origin=CornerWallFixture.ORIGIN;
                    if(mc.player.position().distanceToSqr(origin.getX()+v[0],origin.getY()+v[1],origin.getZ()+v[2])>.05){need(++wait<400,"reaches the "+VIEWS[view]+" view: "+mc.player.position());return;}
                    if(++wait<60)return;
                    if(!requested){requested=true;captured=false;frames=0;capture="corner-wall-"+VIEWS[view]+".png";return;}
                    if(!captured)return;
                    requested=false;
                    if(VIEWS[view].startsWith("diagonal"))diagonalModels(mc,VIEWS[view].charAt(8)-'0');
                    captured=false;wait=0;view++;return;
                }
                mc.player.connection.sendCommand("cornerwall remove-stack");wait=0;stage=3;
            }
            case 3->{
                if(mc.level.getBlockEntity(CornerWallFixture.STACK) instanceof NativeWallEntity){need(++wait<400,"stack removed");return;}
                if(++wait<60)return;
                if(!requested){requested=true;captured=false;frames=0;capture="corner-wall-stack-removed.png";return;}
                if(!captured)return;
                requested=false;
                compareStack(mc);mc.disconnect();stage=90;
            }
            default->{}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!CornerWallFixture.enabled()||capture==null||++frames<8)return;String name=capture;capture=null;var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->captured=true);
    }
    private static List<BakedQuad> quads(Minecraft mc,BlockPos pos,ModelData data){
        var state=mc.level.getBlockState(pos);
        return mc.getBlockRenderer().getBlockModel(state).getQuads(state,null,RandomSource.create(0),data,null);
    }
    /** Every corner draws a bent body; a joined corner draws fewer quads than the same corner unjoined. */
    private static void models(Minecraft mc){
        for(int depth=0;depth<3;depth++){
            BlockPos pos=CornerWallFixture.flatCorner(depth);var host=(NativeWallEntity)mc.level.getBlockEntity(pos);var state=host.getBlockState();
            var joined=quads(mc,pos,host.getModelData());
            var a=host.wallAppearance();var block=(NativeCornerWallBlock)state.getBlock();
            var open=new NativeWallEntity.WallAppearance(a.base(),a.walls(),a.frames(),a.glass(),block.publication(state,com.vandorlabs.render.CornerGlassGeometry.FULL,0),a.aperture(),a.playback(),a.chunkBody(),a.kind(),a.diagonalGlass());
            var unjoined=quads(mc,pos,ModelData.builder().with(NativeWallEntity.WALL_APPEARANCE,open).build());
            need(!joined.isEmpty()&&joined.size()<unjoined.size(),"joined depth "+depth+" corner drops its end caps: joined="+joined.size()+" unjoined="+unjoined.size());
            // The bent body stays inside its cell and spans it diagonally, unlike a 4-pixel straight slab.
            float minZ=2,maxZ=-1,minX=2,maxX=-1;
            for(var quad:joined){int[] v=quad.getVertices();int stride=v.length/4;
                for(int i=0;i<4;i++){float x=Float.intBitsToFloat(v[i*stride]),z=Float.intBitsToFloat(v[i*stride+2]);need(x>-.2&&x<1.2&&z>-.2&&z<1.2,"depth "+depth+" corner vertex inside its cell: "+x+","+z);
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
            }
            need(maxX-minX>.3&&maxZ-minZ>.3,"depth "+depth+" corner body is bent across its cell: x "+minX+".."+maxX+" z "+minZ+".."+maxZ);
        }
    }
    /** Each diagonal corner of one size draws a body; checked from that size's view, where its chunks are loaded. */
    private static void diagonalModels(Minecraft mc,int mode){
        int drawn=0;
        for(int f=0;f<4;f++)for(int lean=0;lean<2;lean++)for(int end=0;end<2;end++){
            BlockPos wall=CornerWallFixture.diagonalWall(mode,f,lean,end);
            for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL){
                BlockPos at=wall.relative(side);
                if(mc.level.getBlockState(at).getBlock() instanceof NativeCornerWallBlock&&mc.level.getBlockEntity(at) instanceof NativeWallEntity host){
                    need(!quads(mc,at,host.getModelData()).isEmpty(),"diagonal corner at "+at+" draws a body");drawn++;
                }
            }
        }
        need(drawn==16,"every size "+mode+" diagonal corner draws: "+drawn);
    }
    private static void compareStack(Minecraft mc)throws Exception{
        File dir=new File(mc.gameDirectory,"screenshots");
        BufferedImage with=ImageIO.read(new File(dir,"corner-wall-stack.png")),without=ImageIO.read(new File(dir,"corner-wall-stack-removed.png"));
        int differ=0,total=0;
        for(int y=with.getHeight()/8;y<with.getHeight()*7/8;y++)for(int x=with.getWidth()*2/5;x<with.getWidth()*3/5;x++){
            int a=with.getRGB(x,y),b=without.getRGB(x,y);total++;
            if(Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255))>40)differ++;
        }
        System.out.println("[vandorlabs][corner-wall] stack draws: "+differ+" of "+total+" pixels change when it is removed");
        need(differ>total/50,"stacked corner walls are drawn: only "+differ+" of "+total+" pixels change when they are removed");
    }
    private CornerWallClientChecks(){}
}
