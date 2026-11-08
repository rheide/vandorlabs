package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.segmented.SegmentedGateJoin;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Private actual native model/atlas consumer; no controller/catalog/placed-world claim. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeSegmentedGateRenderChecks {
    private static boolean enabled(){return "true".equals(System.getenv("VANDOR_SEGMENTED_GATE_RENDER_CHECK"));}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Models {
        @SubscribeEvent public static void additional(ModelEvent.RegisterAdditional event){if(enabled())for(int kind=0;kind<3;kind++)for(int mask:new int[]{0,2,3,1})event.register(ModelResourceLocation.standalone(ResourceLocation.parse(SegmentedGateJoin.model(kind,mask))));}
    }
    private static final class Gallery extends net.minecraft.client.gui.screens.Screen {Gallery(){super(net.minecraft.network.chat.Component.literal("Segmented gates"));}public void render(net.minecraft.client.gui.GuiGraphics gui,int x,int y,float partial){}}
    private static NativeSegmentedGateMeshes bake;private static List<net.minecraft.client.renderer.block.model.BakedQuad>[] reference;private static int frames,generation,phase,checks;private static int[][] negative,baseline;private static boolean capture,done;private static final double[] POSE={.5,.5,.5,0,1,1};private static final int[] MASKS={0,2,3,1};
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Segmented gate GPU: "+why);}
    private static com.vandorlabs.neoforge.segmented.NativeSegmentedVisual[][] runtime;
    private static com.vandorlabs.segmented.SegmentedGateContext[][] contexts;
    private static int[] runtimeOff,runtimeBlend,runtimeBaseline;
    private static final int[] LIGHTS={0xf000f0,0xf000f0,0xf000f0};
    private static final Discard RUNTIME_DISCARD=new Discard();
    private static void runtimePrepare(){
        runtime=new com.vandorlabs.neoforge.segmented.NativeSegmentedVisual[4][3];contexts=new com.vandorlabs.segmented.SegmentedGateContext[4][3];
        for(int style=0;style<4;style++){
            String id=style<3?com.vandorlabs.segmented.SegmentedArtworkPolicy.gateId(style):com.vandorlabs.segmented.SegmentedArtworkPolicy.tileId(2);int selected=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if(id.equals(ModernFinishCatalog.ENTRIES.get(i).id()))selected=i;need(selected>=0,"runtime supplied choice "+id);
            var cfg=new com.vandorlabs.neoforge.segmented.NativeSegmentedDoorSettings(0,false,style<3?1:0,0,style<3,2,true,style<3?com.vandorlabs.neoforge.segmented.NativeSegmentedDoorSettings.DEFAULT.glass:new com.vandorlabs.neoforge.door.NativeDoorGlass(-1,1,1,1),0);
            int[] kinds={style<3?style:-1,style<3?style:-1,style<3?style:-1};
            for(int column=0;column<3;column++){
                runtime[style][column]=new com.vandorlabs.neoforge.segmented.NativeSegmentedVisual(3,3,column,net.minecraft.core.Direction.NORTH,selected,cfg,java.util.Collections.nCopies(3,cfg),java.util.Collections.nCopies(3,com.vandorlabs.persistence.SegmentedWindowLayout.SINGLE),true,false,false,false);
                contexts[style][column]=new com.vandorlabs.segmented.SegmentedGateContext(3,3,column,0,false,0,true,(column>0?1:0)|(column<2?2:0),kinds,column>0?kinds:null,column<2?kinds:null);
            }
        }
    }
    private static void runtimeDraw(net.minecraft.client.gui.GuiGraphics gui,int mode){
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var pane=NativeSegmentedRenderer.paneLayer();var opaque=NativePanelPlaybackRenderer.renderType(TextureAtlas.LOCATION_BLOCKS,true);
        MultiBufferSource routed=type->type==pane&&mode==0?RUNTIME_DISCARD:buffers.getBuffer(type==pane&&mode==2?opaque:type);
        var poses=gui.pose();poses.pushPose();try{poses.translate(20,160,350);poses.scale(48,-48,48);for(int style=0;style<4;style++)for(int column=0;column<3;column++){poses.pushPose();try{poses.translate(style*3+column,0,0);NativeSegmentedRenderer.renderClosed(runtime[style][column],poses,routed,LIGHTS,OverlayTexture.NO_OVERLAY,contexts[style][column]);}finally{poses.popPose();}}}finally{poses.popPose();}buffers.endBatch();gui.flush();
    }
    private static void runtimeWarm(){
        PoseStack poses=new PoseStack();MultiBufferSource discard=type->RUNTIME_DISCARD;for(int i=0;i<10000;i++)NativeSegmentedRenderer.renderClosed(runtime[i%4][1],poses,discard,LIGHTS,OverlayTexture.NO_OVERLAY,contexts[i%4][1]);var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)NativeSegmentedRenderer.renderClosed(runtime[i%4][1],poses,discard,LIGHTS,OverlayTexture.NO_OVERLAY,contexts[i%4][1]);long allocated=meter.getThreadAllocatedBytes(thread)-before;need(allocated==0,"full runtime model/glazing emitter warm allocation "+allocated);System.out.println("[vandorlabs][segmented-gates] runtimeWarm10000Bytes="+allocated+" generation="+generation);
    }
    private static void prepare()throws Exception{
        var mc=Minecraft.getInstance();java.util.function.Function<String,BakedModel> models=id->mc.getModelManager().getModel(ModelResourceLocation.standalone(ResourceLocation.parse(id)));bake=NativeSegmentedRenderer.currentGateBake();need(bake!=null,"production bakery-generation gate preparation");reference=new List[12];runtimePrepare();
        for(int kind=0;kind<3;kind++)for(int part=0;part<4;part++){String id=SegmentedGateJoin.model(kind,MASKS[part]);need(NativeDoorArtwork.canonicalGeometry(id,mc.getResourceManager()),"canonical generated model "+id);reference[kind*4+part]=NativeDoorArtwork.quads(id,models.apply(id));need(reference[kind*4+part]!=null&&reference[kind*4+part].size()*4==bake.baseVertices(kind,MASKS[part]),"actual native authored model coverage");}
        int records=0,vertices=0;for(String line:Files.readAllLines(Path.of(System.getenv("VANDOR_SEGMENTED_GATE_REFERENCE")))){var row=JsonParser.parseString(line).getAsJsonObject();if(row.get("yaw").getAsInt()!=0)continue;String id=row.get("id").getAsString();var actual=NativeDoorArtwork.quads(id,models.apply(id));var expected=row.getAsJsonArray("quads");need(actual.size()==expected.size(),"actual Forge/native face count "+id);for(int q=0;q<actual.size();q++){var quad=actual.get(q);var old=expected.get(q).getAsJsonObject();need(quad.getSprite().contents().name().toString().equals(old.get("texture").getAsString())&&quad.getSprite().contents().width()==128&&quad.getSprite().contents().height()==128,"actual authored sprite present "+id+" expected="+old.get("texture")+" actual="+quad.getSprite().contents().name());need(quad.getDirection().get3DDataValue()==old.get("face").getAsInt()&&quad.isShade()==old.get("shade").getAsBoolean(),"actual Forge/native direction/shade "+id);var words=old.getAsJsonArray("vertices");int[] raw=quad.getVertices();int stride=raw.length/4;for(int v=0;v<4;v++){for(int a=0;a<3;a++)need(raw[v*stride+a]==words.get(v*7+a).getAsInt(),"actual Forge/native float position "+id+" q="+q+" v="+v+" axis="+a);need((raw[v*stride+7]&0xffffff)==(words.get(v*7+6).getAsInt()&0xffffff),"actual Forge/native packed normal "+id);vertices++;}}records++;}
        need(records==12&&vertices==3040,"actual native bake reference coverage");for(String name:new String[]{"amber","black","dark","lattice","red","white","zinc"})image("segmented_gates/"+name);for(String name:new String[]{"raised_black_panels","ribbed_blue_steel","spiral_aluminum_glazing"})image("segmented_doors/"+name);warm();runtimeWarm();
    }
    private static void image(String path)throws Exception{
        String id="vandorlabs:blocks/"+path;var contents=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(id)).contents();need(contents.name().toString().equals(id)&&contents.width()==128&&contents.height()==128,"requested authored PNG "+id);try(var stream=NativeSegmentedGateRenderChecks.class.getResourceAsStream("/assets/vandorlabs/textures/blocks/"+path+".png")){var source=javax.imageio.ImageIO.read(stream);var actual=contents.getOriginalImage();for(int y=0;y<128;y++)for(int x=0;x<128;x++){int argb=source.getRGB(x,y),abgr=(argb&0xff00ff00)|((argb&255)<<16)|((argb>>>16)&255);need(actual.getPixelRGBA(x,y)==abgr,"actual supplied PNG pixel "+id);}}
    }
    private static final class Discard implements VertexConsumer {public VertexConsumer addVertex(float x,float y,float z){return this;}public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}}
    private static void warm(){var poses=new PoseStack();var discard=new Discard();for(int i=0;i<10000;i++)bake.emit(i%3,12|(i&3),3,3,poses.last(),discard,POSE,0,0xf000f0,OverlayTexture.NO_OVERLAY);var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().threadId(),before=meter.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)bake.emit(i%3,12|(i&3),3,3,poses.last(),discard,POSE,0,0xf000f0,OverlayTexture.NO_OVERLAY);long allocated=meter.getThreadAllocatedBytes(thread)-before;need(allocated==0,"actual native prepared warm allocation "+allocated);System.out.println("[vandorlabs][segmented-gates] warm10000Bytes="+allocated+" retainedBytes="+bake.retainedBytes()+" generation="+generation);}
    @SubscribeEvent public static void draw(ScreenEvent.Render.Post event)throws Exception{
        if(done||!enabled())return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;if(event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen){mc.setScreen(new Gallery());return;}if(!(event.getScreen() instanceof Gallery))return;if(bake==null)prepare();var gui=event.getGuiGraphics();gui.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff202830);gui.flush();com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);var buffers=mc.renderBuffers().bufferSource();var layer=NativePanelPlaybackRenderer.frameRenderType();
        if(phase==0)for(int scene=0;scene<12;scene++){
            int x=10+(scene%4)*155,y=6+(scene/4)*112;for(int copy=0;copy<2;copy++){
                gui.enableScissor(x+copy*75,y,x+copy*75+70,y+106);var poses=gui.pose();poses.pushPose();try{poses.translate(x+35+copy*75,y+53,350);poses.scale(58,-58,58);poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(10));poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-20));poses.translate(-.5,-.5,-.5);var target=buffers.getBuffer(layer);if(copy==0)for(var quad:reference[scene])target.putBulkData(poses.last(),quad,1,1,1,1,0xf000f0,OverlayTexture.NO_OVERLAY,true);else if(frames>=5)bake.emit(scene/4,MASKS[scene%4],0,0,poses.last(),target,POSE,0,0xf000f0,OverlayTexture.NO_OVERLAY);}finally{poses.popPose();}buffers.endBatch();gui.flush();gui.disableScissor();}
        }else if(phase==1)for(int kind=0;kind<3;kind++){
            var poses=gui.pose();poses.pushPose();try{poses.translate(22+kind*205,200,350);poses.scale(53,-53,53);for(int row=0;row<2;row++)for(int column=0;column<3;column++){poses.pushPose();try{poses.translate(column,row,0);int horizontal=column==0?2:column==2?1:3,mask=horizontal|(frames>=5?(row==0?8:4):0);bake.emit(kind,mask,horizontal,horizontal,poses.last(),buffers.getBuffer(layer),POSE,0,0xf000f0,OverlayTexture.NO_OVERLAY);}finally{poses.popPose();}}}finally{poses.popPose();}buffers.endBatch();gui.flush();}
        if(phase>=2)runtimeDraw(gui,phase-2);
        capture=++frames>=10;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event)throws Exception{
        if(done||(!capture&&(frames!=5||phase>=2)))return;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int scale=(int)mc.getWindow().getGuiScale(),w=70*scale,h=106*scale;if(frames==5&&phase<2){if(phase==0){negative=new int[12][w*h];for(int scene=0;scene<12;scene++){int x=(10+(scene%4)*155+75)*scale,y=(6+(scene/4)*112)*scale;for(int py=0;py<h;py++)for(int px=0;px<w;px++)negative[scene][py*w+px]=image.getPixelRGBA(x+px,y+py);}}else{negative=new int[3][(int)(180*scale*130*scale)];for(int kind=0;kind<3;kind++)for(int y=0;y<130*scale;y++)for(int x=0;x<180*scale;x++)negative[kind][y*180*scale+x]=image.getPixelRGBA((15+kind*205)*scale+x,90*scale+y);}return;}
            var path=mc.gameDirectory.toPath().resolve("screenshots/segmented-gates-"+generation+"-"+phase+".png");Files.createDirectories(path.getParent());image.writeToFile(path);
            if(phase==0){int total=0,totalMismatch=0;int[][] pixels=new int[12][w*h];for(int scene=0;scene<12;scene++){int x=(10+(scene%4)*155)*scale,y=(6+(scene/4)*112)*scale,changed=0,mismatch=0;for(int py=0;py<h;py++)for(int px=0;px<w;px++){int left=image.getPixelRGBA(x+px,y+py),right=image.getPixelRGBA(x+75*scale+px,y+py);pixels[scene][py*w+px]=right;if(left!=right)mismatch++;if(right!=negative[scene][py*w+px])changed++;}need(changed>30,"actual gate pixels scene="+scene+" changed="+changed);need(mismatch<30,"native authored model vs prepared mesh pixels scene="+scene+" mismatch="+mismatch);if(generation>0)need(Arrays.equals(baseline[scene],pixels[scene]),"actual atlas reload pixels scene="+scene);total+=changed;totalMismatch+=mismatch;}baseline=pixels;System.out.println("[vandorlabs][segmented-gates] generation="+generation+" nativeModelVsPreparedScenes=12 visiblePixels="+total);}else if(phase==1)for(int kind=0;kind<3;kind++){int changed=0;for(int y=0;y<130*scale;y++)for(int x=0;x<180*scale;x++)if(negative[kind][y*180*scale+x]!=image.getPixelRGBA((15+kind*205)*scale+x,90*scale+y))changed++;need(changed>5,"actual vertical connectors visible kind="+kind+" changed="+changed);System.out.println("[vandorlabs][segmented-gates] verticalConnectorPixels="+changed+" kind="+kind+" generation="+generation);}
        }
        if(phase>=2){try(var sampled=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int scale=(int)mc.getWindow().getGuiScale(),width=576*scale,height=144*scale;int[] pixels=new int[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=sampled.getPixelRGBA(20*scale+x,112*scale+y);
            if(phase==2)runtimeOff=pixels;else if(phase==3){runtimeBlend=pixels;if(generation>0)need(Arrays.equals(runtimeBaseline,pixels),"full runtime model/glazing reload pixels");runtimeBaseline=pixels;}else{
                int fractional=0,changed=0;for(int y=0;y<height;y++)for(int x=0;x<width;x++){int at=y*width+x,a=runtimeOff[at],b=runtimeBlend[at],c=pixels[at];if(x<432*scale){need(a==b,"latent gate pane leaked into runtime alpha pass");continue;}if(a!=b)changed++;if(a==b||b==c)continue;double alpha=-1;boolean valid=true;for(int shift:new int[]{0,8,16}){int bg=a>>shift&255,value=b>>shift&255,source=c>>shift&255;if(Math.abs(source-bg)<12)continue;double next=(value-bg)/(double)(source-bg);if(alpha<0)alpha=next;else valid&=Math.abs(alpha-next)<.08;}if(valid&&alpha>.01&&alpha<.99)fractional++;}
                need(changed>1000&&fractional>1000,"actual native runtime glazing blend changed="+changed+" fractional="+fractional);System.out.println("[vandorlabs][segmented-gates] runtimeBlend PASS generation="+generation+" changed="+changed+" fractional="+fractional+" opaque/noImage negatives and opaque model/edges");runtimeOff=runtimeBlend=null;
            }
        }}
        frames=0;capture=false;negative=null;if(phase++<4)return;phase=0;if(generation++==0){bake=null;reference=null;mc.reloadResourcePacks();return;}done=true;System.out.println("[vandorlabs][segmented-gates] title PASS actualForge/nativeBake/basePixels/verticalJoins/threeStyles/fourHorizontalVariants/runtimeRows/glazingBlend/opaqueEdges/reload/zeroWarmAllocation checks="+checks);mc.stop();
    }
}
