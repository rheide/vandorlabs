package com.vandorlabs.client;

import com.vandorlabs.navigation.*;
import com.vandorlabs.network.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.render.ScreenSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** One cached raster and fixed-size control atlas per assembly. Network/texture work runs on ticks/replies. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class NavigationMapRenderer {
    private NavigationMapRenderer(){}
    private static final Map<com.vandorlabs.vehicle.EntityGroundVehicle,Map<BlockPos,Entry>> CARRIED_IMAGES=new LinkedHashMap<>();
    private static long nextRequest;
    private static boolean ticking,trimPending;
    private static final Map<BlockPos,Entry> IMAGES=new LinkedHashMap<BlockPos,Entry>(32,.75f,true);
    private static final com.vandorlabs.animation.VisualClock CLOCK=new com.vandorlabs.animation.VisualClock();
    private static net.minecraft.world.World world;private static int sequence;private static double visualTicks;
    public static long mapUploads,uiUploads,acknowledgements;
    private static final String[] LABELS={"<",">","^","v","+","-","","SET"};
    private static final class Entry {
        TileEntity tile;final int slot;boolean vehicleFollow,vehicleMissing;NavigationCanvas canvas;Object shape;DynamicTexture texture,ui;ResourceLocation resource,uiResource;
        int serial,revision,promptHeight;long requested,received,due;boolean waiting,pending,interactionPending;double loadingStarted;MessageNavigationImage data,queued;final NavigationMotion motion=new NavigationMotion();int targetZoom=4;
        // Logical pixels -> local render coordinates, recalculated only when the surface changes.
        double ox,oy,oz,ux,uy,uz,vx,vy,vz,nx,ny,nz;Object geometry;
        Entry(TileEntity tile,int slot){this.tile=tile;this.slot=slot;}
        void delete(){Minecraft mc=Minecraft.getMinecraft();if(resource!=null)mc.getTextureManager().deleteTexture(resource);if(uiResource!=null)mc.getTextureManager().deleteTexture(uiResource);}
        int bytes(){return (texture==null?0:texture.getTextureData().length*8)+(ui==null?0:ui.getTextureData().length*8)+(data==null||data.pixels==null?0:data.pixels.length*4)+(queued==null||queued.pixels==null?0:queued.pixels.length*4)+640;}
    }
    public static void clear(){for(Entry e:IMAGES.values())e.delete();IMAGES.clear();for(Map<BlockPos,Entry> entries:CARRIED_IMAGES.values())for(Entry e:entries.values())e.delete();CARRIED_IMAGES.clear();nextRequest=0;}
    private static void checkWorld(){if(world!=Minecraft.getMinecraft().world){clear();world=Minecraft.getMinecraft().world;}}
    private static void trim(){
        // A queued raster can grow during tick traversal. Evict only after all active
        // iterators have finished; applying it must not invalidate the tick iterator.
        if(ticking){trimPending=true;return;}
        int bytes=0,count=IMAGES.size();for(Entry e:IMAGES.values())bytes+=e.bytes();for(Map<BlockPos,Entry> entries:CARRIED_IMAGES.values()){count+=entries.size();for(Entry e:entries.values())bytes+=e.bytes();}java.util.List<Map<BlockPos,Entry>> groups=new ArrayList<>();groups.add(IMAGES);groups.addAll(CARRIED_IMAGES.values());for(Map<BlockPos,Entry> group:groups){Iterator<Entry> it=group.values().iterator();while((count>32||bytes>16*1024*1024)&&it.hasNext()){Entry e=it.next();bytes-=e.bytes();count--;e.delete();it.remove();}}}
    private static Map<BlockPos,Entry> images(TileEntity tile){if(!NavigationVehicle.carried(tile))return IMAGES;com.vandorlabs.vehicle.EntityGroundVehicle craft=NavigationVehicle.craft(tile.getWorld());return CARRIED_IMAGES.computeIfAbsent(craft,k->new LinkedHashMap<BlockPos,Entry>(16,.75F,true));}
    private static Entry entry(TileEntity raw,int slot){
        checkWorld();TileEntity tile=NavigationService.owner(raw);if(tile==null)tile=raw;Map<BlockPos,Entry> cache=images(tile);Entry e=cache.get(tile.getPos());
        if(e==null||!NavigationVehicle.carried(tile)&&e.tile!=tile||e.slot!=slot){if(e!=null)e.delete();e=new Entry(tile,slot);cache.put(tile.getPos(),e);}else if(e.tile!=tile){e.tile=tile;e.shape=null;}
        refresh(e);
        if(e.texture==null){e.texture=new DynamicTexture(1,1);e.texture.getTextureData()[0]=NavigationMap.OUTSIDE;e.texture.updateDynamicTexture();e.resource=Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("navigation_map",e.texture);updateUI(e);trim();}
        return e;
    }
    private static void refresh(Entry e){
        TileEntity tile=e.tile;int slot=e.slot;Object shape=tile instanceof TileEntityNavigationTable?((TileEntityNavigationTable)tile).navigationPlane():tile instanceof TileEntityAnimatedScreenSelector&&tile.getBlockType().getClass()==BlockAnimatedScreenSelector.class?ScreenChipGroups.group((TileEntityAnimatedScreenSelector)tile):tile.getWorld().getBlockState(tile.getPos());
        if(e.canvas==null||e.shape!=shape){NavigationCanvas canvas=NavigationViewport.canvas(tile,slot);boolean resized=e.canvas==null||e.canvas.width!=canvas.width||e.canvas.height!=canvas.height;e.shape=shape;e.canvas=canvas;e.geometry=null;if(resized){e.requested=0;e.revision=0;e.pending=true;e.due=0;}}
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)return;checkWorld();Minecraft mc=Minecraft.getMinecraft();visualTicks=CLOCK.sample(System.nanoTime(),mc.isGamePaused());if(world==null||mc.player==null||mc.isGamePaused())return;long now=Minecraft.getSystemTime();
        ticking=true;
        try{
            tick(IMAGES,now);
            for(Iterator<Map.Entry<com.vandorlabs.vehicle.EntityGroundVehicle,Map<BlockPos,Entry>>> it=CARRIED_IMAGES.entrySet().iterator();it.hasNext();){Map.Entry<com.vandorlabs.vehicle.EntityGroundVehicle,Map<BlockPos,Entry>> group=it.next();com.vandorlabs.vehicle.EntityGroundVehicle craft=group.getKey();if(!NavigationVehicle.live(craft)||mc.world.getEntityByID(craft.getEntityId())!=craft){for(Entry e:group.getValue().values())e.delete();it.remove();continue;}tick(group.getValue(),now);}
        }finally{ticking=false;if(trimPending){trimPending=false;trim();}}
    }
    private static void tick(Map<BlockPos,Entry> images,long now){Minecraft mc=Minecraft.getMinecraft();for(Iterator<Entry> it=images.values().iterator();it.hasNext();){Entry e=it.next();boolean carried=NavigationVehicle.carried(e.tile);if(carried){TileEntity current=e.tile.getWorld().getTileEntity(e.tile.getPos());if(current!=null&&NavigationService.owner(current)==current&&current!=e.tile){e.tile=current;e.shape=null;}}
        if(e.tile.isInvalid()||NavigationService.owner(e.tile)!=e.tile){e.delete();it.remove();continue;}refresh(e);
        net.minecraft.world.World context=e.tile.getWorld();if(!context.isBlockLoaded(e.tile.getPos(),false)||context.getTileEntity(e.tile.getPos())!=e.tile||!NavigationService.selected(e.tile,e.slot)||!NavigationService.usable(mc.player,e.tile))continue;
        if(e.queued!=null&&!e.motion.settling(visualTicks)){MessageNavigationImage queued=e.queued;e.queued=null;apply(e,queued);}
        long interval=carried?750:2000;boolean request=e.pending&&now>=e.due||!e.waiting&&now-e.requested>interval||e.waiting&&now-e.requested>12000;
        if(request&&now>=nextRequest){nextRequest=now+150;e.serial=++sequence;e.requested=now;e.pending=false;e.waiting=true;int aspect=Math.max(8,Math.min(64,(int)Math.round(e.canvas.width*16.0/e.canvas.height)));if(carried)PacketHandler.INSTANCE.sendToServer(new MessageVehicleNavigationRequest(NavigationVehicle.craft(context),e.tile.getPos(),e.slot,e.serial,aspect,e.revision));else PacketHandler.INSTANCE.sendToServer(new MessageNavigationRequest(e.tile.getPos(),e.slot,e.serial,aspect,e.revision));}
    }}

    /** Compatibility entry point used by existing screen callers and the before/after benchmark. */
    public static void bind(TileEntity raw,int slot,double ignoredAspect){Entry e=entry(raw,slot);Minecraft.getMinecraft().getTextureManager().bindTexture(e.resource);GlStateManager.color(1,1,1,1);}
    public static void changed(BlockPos pos){changed(pos,0);}
    public static void changed(BlockPos pos,int action){change(IMAGES.get(pos),action);}
    public static void changed(TileEntity raw,int action){TileEntity tile=NavigationService.owner(raw);if(tile==null)return;change(images(tile).get(tile.getPos()),action);}
    private static void change(Entry e,int action){if(e!=null){
        if(e.vehicleFollow&&e.tile instanceof TileEntityAnimatedScreenSelector&&action>=1&&action<=4)return;
        if(e.vehicleFollow&&e.data!=null){net.minecraft.util.math.Vec3d center=NavigationVehicle.center(NavigationVehicle.craft(e.tile.getWorld()));e.motion.snap(center.x,center.z,e.motion.scale(visualTicks));}
        if(NavigationVehicle.carried(e.tile)){if(action>=1&&action<=4)e.vehicleFollow=false;if(action==8&&NavigationVehicle.hasObserver(NavigationVehicle.craft(e.tile.getWorld())))e.vehicleFollow=true;}
        if(e.data!=null&&action>0&&action!=7){double x=e.motion.active?e.motion.targetX:e.data.x,z=e.motion.active?e.motion.targetZ:e.data.z;int zoom=e.motion.active?e.targetZoom:e.data.zoom;NavigationCanvas c=e.canvas;
            int horizontal=Math.max(1,(int)Math.round(c.width*c.scale(zoom)/4)),vertical=Math.max(1,(int)Math.round((c.mapBottom-c.mapTop)*c.scale(zoom)/4));
            switch(action){case 1:x-=horizontal;break;case 2:x+=horizontal;break;case 3:z-=vertical;break;case 4:z+=vertical;break;case 5:zoom=Math.max(0,zoom-1);break;case 6:zoom=Math.min(NavigationMap.SPANS.length-1,zoom+1);break;case 8:if(e.vehicleFollow){net.minecraft.util.math.Vec3d center=NavigationVehicle.center(NavigationVehicle.craft(e.tile.getWorld()));x=center.x;z=center.z;}else if(e.data.marker){x=e.data.chipX;z=e.data.chipZ;}else if(Minecraft.getMinecraft().player!=null){x=Math.round(Minecraft.getMinecraft().player.posX);z=Math.round(Minecraft.getMinecraft().player.posZ);}break;}
            e.targetZoom=zoom;e.motion.move(NavigationMap.bound(Math.round(x)),NavigationMap.bound(Math.round(z)),c.scale(zoom),visualTicks);
        }
        e.loadingStarted=CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());visualTicks=e.loadingStarted;e.interactionPending=true;e.queued=null;e.serial=++sequence;e.waiting=true;e.pending=true;e.due=Minecraft.getSystemTime()+100;
    }}
    static boolean ready(BlockPos pos){Entry e=IMAGES.get(pos);return e!=null&&e.received>0;}
    public static void receive(MessageNavigationImage packet){checkWorld();Entry e=IMAGES.get(packet.pos);if(e==null||world==null||!world.isBlockLoaded(packet.pos,false)||world.getTileEntity(packet.pos)!=e.tile)return;receive(e,packet);}
    public static void receive(MessageVehicleNavigationImage packet){checkWorld();if(world==null)return;net.minecraft.entity.Entity raw=world.getEntityByID(packet.entity);if(!(raw instanceof com.vandorlabs.vehicle.EntityGroundVehicle)||!raw.getUniqueID().equals(packet.token))return;com.vandorlabs.vehicle.EntityGroundVehicle craft=(com.vandorlabs.vehicle.EntityGroundVehicle)raw;Map<BlockPos,Entry> group=CARRIED_IMAGES.get(craft);Entry e=group==null?null:group.get(packet.image.pos);if(e==null||!NavigationVehicle.live(craft)||!NavigationService.selected(craft.view.getTileEntity(packet.image.pos),packet.image.slot)||e.serial!=packet.image.serial)return;boolean changed=e.vehicleFollow!=packet.follow||e.vehicleMissing!=packet.missing;e.vehicleFollow=packet.follow;e.vehicleMissing=packet.missing;long uploads=uiUploads;receive(e,packet.image);if(changed&&uploads==uiUploads)updateUI(e);}
    private static void receive(Entry e,MessageNavigationImage packet){if(e.serial!=packet.serial||e.slot!=packet.slot)return;
        if(packet.revision>0&&(packet.canvasWidth!=e.canvas.width||packet.canvasHeight!=e.canvas.height)){e.pending=true;e.due=Minecraft.getSystemTime()+250;return;}
        if(e.motion.settling(visualTicks)){e.queued=packet;trim();return;}apply(e,packet);
    }
    private static void apply(Entry e,MessageNavigationImage packet){
        MessageNavigationImage old=e.data;
        if(packet.pixels!=null){if(e.texture.getTextureData().length!=packet.pixels.length||old!=null&&(old.width!=packet.width||old.height!=packet.height)){Minecraft.getMinecraft().getTextureManager().deleteTexture(e.resource);e.texture=new DynamicTexture(packet.width,packet.height);e.resource=Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("navigation_map",e.texture);}System.arraycopy(packet.pixels,0,e.texture.getTextureData(),0,packet.pixels.length);e.texture.updateDynamicTexture();mapUploads++;}
        else if(e.revision!=packet.revision){e.revision=0;e.pending=true;e.due=0;return;}else acknowledgements++;
        e.waiting=false;e.pending=false;e.interactionPending=false;e.received=Minecraft.getSystemTime();e.revision=packet.revision;e.data=packet;e.motion.snap(packet.x,packet.z,e.canvas.scale(packet.zoom));e.targetZoom=packet.zoom;
        if(old==null||old.x!=packet.x||old.z!=packet.z||old.zoom!=packet.zoom||old.hasChip!=packet.hasChip||old.noSignal!=packet.noSignal||old.write!=packet.write||old.unlinked!=packet.unlinked||old.noPower!=packet.noPower)updateUI(e);trim();
    }
    private static void updateUI(Entry e){
        BufferedImage image=new BufferedImage(384,128,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{
            g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11));g.setColor(new Color(0xcce7ee));String title=e.data==null?"ACQUIRING TERRAIN":e.data.noPower?"NO POWER":(e.data.noSignal||e.vehicleMissing)?"NO SIGNAL":"X "+e.data.x+" Z "+e.data.z+" N^";if(e.data!=null&&e.data.write)title=NavigationOverlay.zoomLabel(e.data.zoom)+"  "+title;
            g.drawString(title,3,14);
            for(int i=0;i<8;i++){int x=i*24;g.setColor(new Color(0x304958));g.fillRect(x+1,25,22,22);g.setColor(i==7&&(e.data==null||!e.data.hasChip)||e.vehicleFollow&&e.tile instanceof TileEntityAnimatedScreenSelector&&i<4?new Color(0x71818a):Color.WHITE);if(i==6){g.drawOval(x+7,31,10,10);g.drawLine(x+12,28,x+12,34);g.drawLine(x+12,38,x+12,44);g.drawLine(x+4,36,x+10,36);g.drawLine(x+14,36,x+20,36);}else g.drawString(LABELS[i],x+(24-g.getFontMetrics().stringWidth(LABELS[i]))/2,40);}
            int font=Math.min(14,Math.max(10,(e.canvas.mapBottom-e.canvas.mapTop-4)/3-4));int lineHeight=font+4;String[] prompt=e.data!=null&&e.data.noPower?com.vandorlabs.navigation.ObserverPower.noPowerHaiku():e.vehicleMissing?new String[]{"No scans reach this ship","Fit a Dish or Array here","Then the maps will wake"}:e.data!=null&&e.data.unlinked?new String[]{"Seek a Survey Dish","Or a Sentinel Array","Set redstone channels"}:new String[]{"NO SIGNAL"};g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,font));int available=Math.min(384,e.canvas.width-8);while(font>6){int widest=0;for(String line:prompt)widest=Math.max(widest,g.getFontMetrics().stringWidth(line));if(widest<=available)break;g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,--font));}lineHeight=font+4;e.promptHeight=lineHeight*prompt.length;g.setColor(new Color(0xcce7ee));for(int i=0;i<prompt.length;i++)g.drawString(prompt[i],(384-g.getFontMetrics().stringWidth(prompt[i]))/2,64+font+i*lineHeight);
        }finally{g.dispose();}
        if(e.ui==null){e.ui=new DynamicTexture(384,128);e.uiResource=Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("navigation_controls",e.ui);}image.getRGB(0,0,384,128,e.ui.getTextureData(),0,384);e.ui.updateDynamicTexture();uiUploads++;
    }
    /** Retained for pixel-level fixture checks; the live path uploads the server raster directly. */
    static BufferedImage image(MessageNavigationImage p,String status){BufferedImage result=new BufferedImage(384,384,BufferedImage.TYPE_INT_ARGB),map=new BufferedImage(p.width,p.height,BufferedImage.TYPE_INT_ARGB);map.setRGB(0,0,p.width,p.height,p.pixels,0,p.width);Graphics2D g=result.createGraphics();try{g.drawImage(map,0,20,384,330,null);}finally{g.dispose();}return result;}
    public static void table(TileEntityNavigationTable tile){
        Entry e=entry(tile,0);NavigationLayout.Screen s=tile.navigationPlane();if(e.geometry!=s){e.geometry=s;e.ox=s.topLeft.x-tile.getPos().getX();e.oy=s.topLeft.y-tile.getPos().getY();e.oz=s.topLeft.z-tile.getPos().getZ();e.ux=s.basisU.x/NavigationCanvas.PIXELS_PER_BLOCK;e.uy=s.basisU.y/NavigationCanvas.PIXELS_PER_BLOCK;e.uz=s.basisU.z/NavigationCanvas.PIXELS_PER_BLOCK;e.vx=-s.basisV.x/NavigationCanvas.PIXELS_PER_BLOCK;e.vy=-s.basisV.y/NavigationCanvas.PIXELS_PER_BLOCK;e.vz=-s.basisV.z/NavigationCanvas.PIXELS_PER_BLOCK;e.nx=s.normal.x;e.ny=s.normal.y;e.nz=s.normal.z;}
        draw(e);
    }
    static void display(TileEntityAnimatedScreenSelector tile,int slot){
        ScreenChipGroups.Group group=tile.getBlockType().getClass()==BlockAnimatedScreenSelector.class?ScreenChipGroups.group(tile):null;
        Entry e=entry(tile,slot);ScreenSurface.Quad q=RedstoneScreenInteractions.surface(tile.getWorld().getBlockState(tile.getPos()),tile,slot);
        GlStateManager.pushMatrix();try{
            // Submit one complete rectangle from the group's anchor. Other members retain their housing only.
            if(group!=null&&tile!=group.owner)return;
            int cols=group==null?1:group.columns,rows=group==null?1:group.rows;
            e.ox=q.topRight.x+16*(cols-1);e.oy=q.topRight.y+16*(rows-1);e.oz=q.topRight.z;e.ux=-16.0/NavigationCanvas.PIXELS_PER_BLOCK;e.uy=0;e.uz=0;e.vx=0;e.vy=(q.bottomRight.y-q.topRight.y-16*(rows-1))/e.canvas.height;e.vz=(q.bottomRight.z-q.topRight.z)/e.canvas.height;e.nx=0;e.ny=q.ny;e.nz=q.nz;draw(e);
        }finally{GlStateManager.popMatrix();}
    }
    private static void draw(Entry e){
        NavigationCanvas c=e.canvas;Minecraft mc=Minecraft.getMinecraft();
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.color(1,1,1,1);GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.01F);GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(-1,-4);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);
            boolean loading=e.interactionPending||e.pending||e.queued!=null||e.data==null;double viewX=e.data==null?0:e.motion.x(visualTicks),viewZ=e.data==null?0:e.motion.z(visualTicks),scale=e.data==null?c.scale(4):e.motion.scale(visualTicks);if(e.vehicleFollow){net.minecraft.util.math.Vec3d center=NavigationVehicle.center(NavigationVehicle.craft(e.tile.getWorld()));double partial=mc.getRenderPartialTicks();com.vandorlabs.vehicle.EntityGroundVehicle craft=NavigationVehicle.craft(e.tile.getWorld());viewX=center.x+(craft.lastTickPosX-craft.posX)*(1-partial);viewZ=center.z+(craft.lastTickPosZ-craft.posZ)*(1-partial);if(e.data!=null&&(Math.abs(viewX-e.data.x)>scale*.1||Math.abs(viewZ-e.data.z)>scale*.1))loading=true;}
            GlStateManager.depthMask(false);GlStateManager.disableTexture2D();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);rect(b,e,0,loading?0:c.mapTop,c.width,loading?c.height:c.mapBottom,loading?NavigationMotion.pulse(visualTicks-e.loadingStarted+20):NavigationMap.OUTSIDE);Tessellator.getInstance().draw();GlStateManager.depthMask(true);GlStateManager.enableTexture2D();
            mc.getTextureManager().bindTexture(e.resource);b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);
            if(e.data!=null){double ratio=c.scale(e.data.zoom)/scale,w=e.data.canvasWidth*ratio,h=Math.max(1,e.data.canvasHeight-(c.height-(c.mapBottom-c.mapTop)))*ratio,left=c.width/2.0+(e.data.x-viewX)/scale-w/2,top=(c.mapTop+c.mapBottom)/2.0+(e.data.z-viewZ)/scale-h/2;double x=Math.max(0,left),y=Math.max(c.mapTop,top),X=Math.min(c.width,left+w),Y=Math.min(c.mapBottom,top+h);if(X>x&&Y>y)quad(b,e,x,y,X,Y,(x-left)/w,(y-top)/h,(X-left)/w,(Y-top)/h);}
            Tessellator.getInstance().draw();
            GlStateManager.depthMask(false);GL11.glPolygonOffset(-2,-8);mc.getTextureManager().bindTexture(e.uiResource);b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX);double header=Math.min(c.width-20,384);uiQuad(b,e,0,0,header,20,0,0,header/384,20.0/128);
            for(int i=0;i<c.count;i++){int x=c.buttonX(i),y=c.buttonY(i);uiQuad(b,e,x,y,x+24,y+24,i*24.0/384,24.0/128,(i+1)*24.0/384,48.0/128);}if(e.data!=null&&(e.data.noSignal||e.data.noPower||e.vehicleMissing)){double width=Math.min(c.width-8,384),left=(c.width-width)/2,top=(c.mapTop+c.mapBottom-e.promptHeight)/2.0,u=(384-width)/2/384;uiQuad(b,e,left,top,left+width,top+e.promptHeight,u,64.0/128,u+width/384,(64.0+e.promptHeight)/128);}Tessellator.getInstance().draw();GL11.glPolygonOffset(-1,-4);GlStateManager.depthMask(true);
            GlStateManager.disableTexture2D();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);MessageNavigationImage p=e.data;
            if(p!=null&&!p.noSignal&&!e.vehicleMissing){double cx=c.width/2.0,cy=(c.mapTop+c.mapBottom)/2.0;
                if(p.hasChip||e.vehicleFollow){rect(b,e,0,cy-.6,cx-5,cy+.6,0xe8ffffff);rect(b,e,cx+5,cy-.6,c.width,cy+.6,0xe8ffffff);rect(b,e,cx-.6,c.mapTop,cx+.6,cy-5,0xe8ffffff);rect(b,e,cx-.6,cy+5,cx+.6,c.mapBottom,0xe8ffffff);ring(b,e,cx,cy,5,0xe8ffffff);}
                if(p.write){
                    p.overlay.drawCircles(c,viewX,viewZ,scale,(x,y,endX,endY,color)->circleSegment(b,e,x,y,endX,endY,color));
                    p.overlay.drawRadar(c,viewX,viewZ,scale,mc.world.getTotalWorldTime(),
                        (x,y,endX,endY,color)->circleSegment(b,e,x,y,endX,endY,color),
                        (x,y,endX,endY,color)->circleSegment(b,e,x,y,endX,endY,color));
                    for(int i=0;i<p.overlay.players.length;i+=2){
                        double px=cx+(p.overlay.players[i]-viewX)/scale,py=cy+(p.overlay.players[i+1]-viewZ)/scale;
                        if(px>=3&&px<=c.width-3&&py>=c.mapTop+3&&py<=c.mapBottom-3)
                            rect(b,e,px-2.5,py-2.5,px+2.5,py+2.5,0xfffcadff);
                    }
                }
                if(p.marker){double x=c.width/2.0+(p.chipX-viewX)/scale,y=cy+(p.chipZ-viewZ)/scale;if(x>=5&&x<c.width-5&&y>=c.mapTop+5&&y<c.mapBottom-5)ring(b,e,x,y,5,0xffffdf46);}
                if(mc.player!=null){double x=c.width/2.0+(mc.player.posX-viewX)/scale,y=cy+(mc.player.posZ-viewZ)/scale;double direction=Math.toRadians(mc.player.rotationYaw),dx=-Math.sin(direction),dy=Math.cos(direction);if(x<7||x>c.width-7||y<c.mapTop+7||y>c.mapBottom-7){double ax=x-c.width/2.0,ay=y-cy,len=Math.hypot(ax,ay);dx=ax/Math.max(1,len);dy=ay/Math.max(1,len);x=Math.max(7,Math.min(c.width-7,x));y=Math.max(c.mapTop+7,Math.min(c.mapBottom-7,y));}point(b,e,x+dx*7,y+dy*7,0xff5ef1ff);point(b,e,x-dx*4-dy*4,y-dy*4+dx*4,0xff5ef1ff);point(b,e,x-dx*2,y-dy*2,0xff5ef1ff);point(b,e,x-dx*4+dy*4,y-dy*4-dx*4,0xff5ef1ff);}
            }

            Tessellator.getInstance().draw();GlStateManager.enableTexture2D();
        }
    }
    private static void quad(BufferBuilder b,Entry e,double x,double y,double X,double Y,double u,double v,double U,double V){tex(b,e,x,y,u,v);tex(b,e,x,Y,u,V);tex(b,e,X,Y,U,V);tex(b,e,X,y,U,v);}
    private static void uiQuad(BufferBuilder b,Entry e,double x,double y,double X,double Y,double u,double v,double U,double V){tex(b,e,x,y,u,v,.008);tex(b,e,x,Y,u,V,.008);tex(b,e,X,Y,U,V,.008);tex(b,e,X,y,U,v,.008);}
    private static void tex(BufferBuilder b,Entry e,double x,double y,double u,double v){tex(b,e,x,y,u,v,.004);}
    private static void tex(BufferBuilder b,Entry e,double x,double y,double u,double v,double layer){b.pos(e.ox+e.ux*x+e.vx*y+e.nx*layer,e.oy+e.uy*x+e.vy*y+e.ny*layer,e.oz+e.uz*x+e.vz*y+e.nz*layer).tex(u,v).endVertex();}
    private static void circleSegment(BufferBuilder b,Entry e,double x,double y,double endX,double endY,int color){
        double dx=endX-x,dy=endY-y,half=.85/Math.hypot(dx,dy),nx=dy*half,ny=-dx*half;
        point(b,e,x+nx,y+ny,color);point(b,e,x-nx,y-ny,color);
        point(b,e,endX-nx,endY-ny,color);point(b,e,endX+nx,endY+ny,color);
    }
    private static void point(BufferBuilder b,Entry e,double x,double y,int argb){b.pos(e.ox+e.ux*x+e.vx*y+e.nx*.0045,e.oy+e.uy*x+e.vy*y+e.ny*.0045,e.oz+e.uz*x+e.vz*y+e.nz*.0045).color(argb>>16&255,argb>>8&255,argb&255,argb>>>24).endVertex();}
    private static void rect(BufferBuilder b,Entry e,double x,double y,double X,double Y,int argb){point(b,e,x,y,argb);point(b,e,x,Y,argb);point(b,e,X,Y,argb);point(b,e,X,y,argb);}
    private static void ring(BufferBuilder b,Entry e,double x,double y,double r,int color){rect(b,e,x-r,y-r,x+r,y-r+1,color);rect(b,e,x-r,y+r-1,x+r,y+r,color);rect(b,e,x-r,y-r,x-r+1,y+r,color);rect(b,e,x+r-1,y-r,x+r,y+r,color);}
}
