package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockFixedCanopyGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.*;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Random;

/** Complementary's skylight-based rain wind treats a transparent roof as outdoors.
 * Preserve normal lighting and weather; disable only that wind uniform during
 * the sheltered precipitation pass, after its shader program has been selected. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class CanopyRainShelter {
    private static final ResourceLocation RAIN=new ResourceLocation("textures/environment/rain.png"),SNOW=new ResourceLocation("textures/environment/snow.png");
    private static final FloatBuffer VALUE=org.lwjgl.BufferUtils.createFloatBuffer(1);
    private static final ShaderHooks SHADERS=ShaderHooks.find();
    private static WorldClient world;
    private static final IRenderHandler WEATHER=new IRenderHandler(){@Override public void render(float partial,WorldClient view,Minecraft mc){renderWeather(mc,view,partial);}};
    static boolean sheltered(WorldClient view,BlockPos player){if(!view.isBlockLoaded(player))return false;BlockPos roof=view.getPrecipitationHeight(player);return roof.getY()>player.getY()&&view.getBlockState(roof.down()).getBlock() instanceof BlockFixedCanopyGlass;}
    private static void clear(){if(world!=null&&world.provider.getWeatherRenderer()==WEATHER)world.provider.setWeatherRenderer(null);world=null;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();WorldClient next=mc.world;
        if(next!=world){clear();world=next;}if(world==null)return;
        boolean active=mc.player!=null&&SHADERS!=null&&SHADERS.active()&&world.getRainStrength(1)>0&&sheltered(world,mc.player.getPosition());
        IRenderHandler current=world.provider.getWeatherRenderer();
        // A dimension/mod-owned weather renderer retains control of its own pass.
        if(active&&current==null)world.provider.setWeatherRenderer(WEATHER);else if(!active&&current==WEATHER)world.provider.setWeatherRenderer(null);
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote)Minecraft.getMinecraft().addScheduledTask(()->{if(world==event.getWorld())clear();});}
    static void withoutWind(Runnable draw){
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),location=program==0?-1:GL20.glGetUniformLocation(program,"eyeBrightnessM2");
        if(location<0){draw.run();return;}VALUE.clear();GL20.glGetUniform(program,location,VALUE);float previous=VALUE.get(0);GL20.glUniform1f(location,0);
        try{draw.run();}finally{int active=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(active!=program)GL20.glUseProgram(program);GL20.glUniform1f(location,previous);if(active!=program)GL20.glUseProgram(active);}
    }
    private static void renderWeather(Minecraft mc,WorldClient view,float partial){
        Entity camera=mc.getRenderViewEntity();if(camera==null||view.getRainStrength(partial)<=0)return;
        try(TurntableRenderState saved=new TurntableRenderState()){
            mc.entityRenderer.enableLightmap();GlStateManager.disableCull();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.depthMask(false);GlStateManager.disableLighting();
            SHADERS.begin();try{withoutWind(()->columns(mc,view,camera,partial));}finally{SHADERS.end();mc.entityRenderer.disableLightmap();}
        }
    }
    /** Native precipitation heights still clip every column; only shader wind changes under glass. */
    private static void columns(Minecraft mc,WorldClient view,Entity camera,float partial){
        double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        int px=MathHelper.floor(cx),py=MathHelper.floor(cy),pz=MathHelper.floor(cz),radius=mc.gameSettings.fancyGraphics?10:5;float strength=view.getRainStrength(partial);double time=mc.ingameGUI.getUpdateCounter()+partial;
        BufferBuilder b=Tessellator.getInstance().getBuffer();int kind=-1;Random random=new Random();
        try{
            for(int z=pz-radius;z<=pz+radius;z++)for(int x=px-radius;x<=px+radius;x++){
                BlockPos at=new BlockPos(x,py,z);if(!view.isBlockLoaded(at))continue;Biome biome=view.getBiome(at);if(!biome.canRain()&&!biome.getEnableSnow())continue;
                int roof=view.getPrecipitationHeight(at).getY(),low=Math.max(py-radius,roof),high=Math.max(py+radius,roof);if(low==high)continue;
                boolean snow=biome.getTemperature(at)<.15F;int next=snow?1:0;
                if(next!=kind){if(kind>=0)Tessellator.getInstance().draw();kind=next;mc.getTextureManager().bindTexture(snow?SNOW:RAIN);b.begin(GL11.GL_QUADS,DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);b.setTranslation(-cx,-cy,-cz);}
                double dx=x+.5-cx,dz=z+.5-cz,length=Math.sqrt(dx*dx+dz*dz),vx=length<1e-6?.5:-dz/length*.5,vz=length<1e-6?0:dx/length*.5;
                long seed=(long)x*x*3121+x*45238971L^(long)z*z*418711+z*13761L;random.setSeed(seed);
                double scroll=snow?-.01*time:-(Math.floorMod((long)time+seed,32)+partial)/32D*(3+random.nextDouble());double u=snow?random.nextDouble()+time*.01*random.nextGaussian():0;
                float alpha=(float)(((1-Math.min(1,length/radius))*.5+.5)*strength);int light=view.getCombinedLight(new BlockPos(x,Math.max(roof,py),z),0);if(snow)light=(light*3+15728880)/4;
                vertex(b,x+.5-vx,high,z+.5-vz,u,high*.25+scroll,alpha,light);vertex(b,x+.5+vx,high,z+.5+vz,u+1,high*.25+scroll,alpha,light);vertex(b,x+.5+vx,low,z+.5+vz,u+1,low*.25+scroll,alpha,light);vertex(b,x+.5-vx,low,z+.5-vz,u,low*.25+scroll,alpha,light);
            }
            if(kind>=0)Tessellator.getInstance().draw();
        }finally{b.setTranslation(0,0,0);}
    }
    private static void vertex(BufferBuilder b,double x,double y,double z,double u,double v,float alpha,int light){b.pos(x,y,z).tex(u,v).color(1,1,1,alpha).lightmap(light>>>16,light&65535).endVertex();}
    private static final class ShaderHooks {
        final Method active,name,begin,end;
        ShaderHooks()throws ReflectiveOperationException{Class<?> config=Class.forName("Config"),shaders=Class.forName("net.optifine.shaders.Shaders");active=config.getMethod("isShaders");name=shaders.getMethod("getShaderPackName");begin=shaders.getMethod("beginWeather");end=shaders.getMethod("endWeather");}
        static ShaderHooks find(){try{return new ShaderHooks();}catch(ClassNotFoundException absent){return null;}catch(ReflectiveOperationException|LinkageError unsupported){com.vandorlabs.VandorLabs.logger.warn("Canopy rain shelter shader hook unavailable",unsupported);return null;}}
        boolean active(){try{return Boolean.TRUE.equals(active.invoke(null))&&String.valueOf(name.invoke(null)).toLowerCase(java.util.Locale.ROOT).contains("complementary");}catch(ReflectiveOperationException error){return false;}}
        void begin(){try{begin.invoke(null);}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot enter canopy weather pass",e);}}
        void end(){try{end.invoke(null);}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot leave canopy weather pass",e);}}
    }
    private CanopyRainShelter(){}
}
