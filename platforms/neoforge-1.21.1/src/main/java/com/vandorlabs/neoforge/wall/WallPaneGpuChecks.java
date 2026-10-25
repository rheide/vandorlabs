package com.vandorlabs.neoforge.wall;

import com.google.gson.Gson;
import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import com.vandorlabs.persistence.WallAppearance;
import com.vandorlabs.ui.WallDialog;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Actual before/after pane-stage GPU readbacks with the ordinary water layer as a rendered negative. */
public final class WallPaneGpuChecks {
    private static final BlockPos POS=new BlockPos(324,80,8);
    private static final String[] MODES={"stock","mild","normal","strong"};
    private record Pixels(int[] rgba,float[] depth){}
    private static final List<Object> results=new ArrayList<>();
    private static final Map<BlockPos,BlockState> states=new LinkedHashMap<>();
    private static final Map<BlockPos,CompoundTag> tags=new HashMap<>();
    private static GraphicsStatus graphics;
    private static boolean flight,initialized,armed,probed;
    private static int scene,stage,started,lastMove;
    private static long frames;
    private static Pixels before;
    private WallPaneGpuChecks(){}
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Wall pane GPU scene="+scene+" stage="+stage+": "+why);}
    private static String name(){return "modern-wall-pane-"+(scene<4?"fancy":"fabulous")+"-"+MODES[scene%4]+".png";}
    public static String[] captures(){List<String> names=new ArrayList<>();for(String g:new String[]{"fancy","fabulous"})for(String m:MODES)names.add("modern-wall-pane-"+g+"-"+m+".png");return names.toArray(String[]::new);}
    private static void begin(int tick){started=tick;frames=ModernClientRuntimeChecks.renderedFrames();}
    private static boolean settled(Minecraft mc,int tick){return tick-started>=30&&ModernClientRuntimeChecks.renderedFrames()-frames>=8&&mc.levelRenderer.hasRenderedAllSections();}
    private static Pixels read(){
        var mc=Minecraft.getInstance();int destination=Minecraft.useShaderTransparency()?mc.levelRenderer.getTranslucentTarget().frameBufferId:WallPaneRenderTypes.entryDraw();int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,destination);int oldBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try(var memory=MemoryStack.stackPush()){
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);int size=48*48;var bytes=memory.malloc(size*4);var depth=memory.mallocFloat(size);int x=mc.getWindow().getWidth()/2-24,y=mc.getWindow().getHeight()/2-24;
            GL11.glReadPixels(x,y,48,48,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);GL11.glReadPixels(x,y,48,48,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);int[] colors=new int[size*4];float[] depths=new float[size];for(int i=0;i<colors.length;i++)colors[i]=bytes.get(i)&255;depth.get(depths);return new Pixels(colors,depths);
        }finally{GL11.glReadBuffer(oldBuffer);com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);}
    }
    static void before(){if(armed)before=read();}
    static void after(){
        if(!armed||before==null)return;Pixels after=read();int changed=0,blue=0,depth=0;for(int p=0;p<48*48;p++){int i=p*4;int delta=Math.max(Math.abs(before.rgba[i]-after.rgba[i]),Math.max(Math.abs(before.rgba[i+1]-after.rgba[i+1]),Math.abs(before.rgba[i+2]-after.rgba[i+2])));if(delta>2)changed++;if(before.rgba[i+2]>before.rgba[i]+10)blue++;if(Math.abs(before.depth[p]-after.depth[p])>1e-7)depth++;}
        need(Minecraft.useShaderTransparency()==(scene>=4),"actual graphics pipeline");need(blue>64,"independent already-rendered native-water background");need(changed>64,"actual pane must change water pixels; a skipped pane fails");need(scene%4==3?depth>64:depth==0,"exact stock/mild/normal color-only versus strong depth-write mask; changed="+changed+" depth="+depth+" mask="+GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        results.add(Map.of("scene",name(),"fabulous",Minecraft.useShaderTransparency(),"changed",changed,"waterPixels",blue,"depthChanged",depth,"before",before.rgba,"after",after.rgba));armed=false;probed=true;before=null;
    }
    public static boolean tick(Minecraft mc,int tick){
        if(mc.player==null||mc.level==null)return false;
        if(!initialized){initialized=true;graphics=mc.options.graphicsMode().get();flight=mc.player.getAbilities().flying;mc.player.closeContainer();mc.options.keyShift.setDown(false);mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));need(mc.player.getAbilities().mayfly,"creative fixture flight");mc.player.getAbilities().flying=true;mc.player.setOnGround(false);mc.player.setDeltaMovement(Vec3.ZERO);mc.player.onUpdateAbilities();for(int x=0;x<2;x++)for(int z=0;z<3;z++){BlockPos p=POS.offset(x,0,z);states.put(p,mc.level.getBlockState(p));var tile=mc.level.getBlockEntity(p);if(tile!=null)tags.put(p,tile.saveWithFullMetadata(mc.level.registryAccess()));}begin(tick);}
        need(tick-started<500,"bounded GPU phase");
        if(stage==0){Vec3 target=new Vec3(324.5,80.4,5),delta=target.subtract(mc.player.position());if(delta.length()>.02){if(tick-lastMove>=5){Vec3 p=delta.length()>3?mc.player.position().add(delta.normalize().scale(3)):target;mc.player.setDeltaMovement(Vec3.ZERO);mc.player.setPos(p);mc.player.setOnGround(false);mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(p.x,p.y,p.z,false));lastMove=tick;}return false;}for(int x=0;x<2;x++){mc.level.setBlock(POS.offset(x,0,1),Blocks.WATER.defaultBlockState(),2);mc.level.setBlock(POS.offset(x,0,2),Blocks.LIME_CONCRETE.defaultBlockState(),2);}Vec3 d=Vec3.atCenterOf(POS).subtract(mc.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(d.z,d.x))-90,pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),yaw,pitch,false));stage=1;begin(tick);return false;}
        if(stage==1){mc.options.graphicsMode().set(scene<4?GraphicsStatus.FANCY:GraphicsStatus.FABULOUS);mc.levelRenderer.allChanged();WallEntity wall=(WallEntity)mc.level.getBlockEntity(POS);int type=scene%4;need(wall.applyWall(new WallDialog.State(0,26,0,-1,new WallAppearance(2,type==0?1:type-1,type==0?-1:0,true,true,true,true,0,0))),"scene settings");WallClientSnapshots.update(new NativeFluidRenderSnapshot.Update(wall));stage=2;begin(tick);return false;}
        if(stage==2){if(!settled(mc,tick))return false;need(mc.level.getBlockState(POS.south()).is(Blocks.WATER),"actual native source behind pane");armed=true;probed=false;stage=3;begin(tick);return false;}
        if(stage==3){if(!probed||!settled(mc,tick))return false;need(WallPaneRenderer.lastFabulous()==(scene>=4)&&WallPaneRenderer.lastBeforeTarget()==WallPaneRenderer.lastAfterTarget(),"actual pane stage binding restoration");ModernClientRuntimeChecks.capture(name());stage=4;return false;}
        if(stage==4){if(!ModernClientRuntimeChecks.captured(name()))return false;scene++;if(scene<8){stage=1;begin(tick);return false;}try{var p=mc.gameDirectory.toPath().resolve("screenshots/modern-wall-pane-gpu.json");Files.writeString(p,new Gson().toJson(Map.of("cases",results)));}catch(Exception e){throw new IllegalStateException(e);}for(var e:states.entrySet()){mc.level.setBlock(e.getKey(),e.getValue(),2);var tile=mc.level.getBlockEntity(e.getKey());if(tile!=null&&tags.containsKey(e.getKey()))tile.loadWithComponents(tags.get(e.getKey()),mc.level.registryAccess());if(tile instanceof WallEntity wall)WallClientSnapshots.update(new NativeFluidRenderSnapshot.Update(wall));}mc.options.graphicsMode().set(graphics);mc.levelRenderer.allChanged();mc.player.getAbilities().flying=flight;mc.player.setDeltaMovement(Vec3.ZERO);mc.player.onUpdateAbilities();System.out.println("[vandorlabs][modern] wall-pane-gpu PASS 8actual Fancy/Fabulous scenes; rendered native-water negatives, pane color changes, exact depth masks and draw/read binding restoration");return true;}
        return false;
    }
}
