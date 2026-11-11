package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Execute the actual compiled primary-artwork branch with recording texture/GL sinks. */
public final class SelectorTextureRoutingChecks {
    public static final class Animation {
        public final ResourceLocation texture=new ResourceLocation("vandorlabs","fixture/legacy");
        public final int frameCount=8;
    }
    private static final Animation ANIMATION=new Animation();
    private static final ResourceLocation CONFIGURED=new ResourceLocation("vandorlabs","fixture/configured");
    private static int legacyLoads,configuredCalls,binds;
    private static ResourceLocation lastBound;
    private static float[] observed;
    public static Animation animation(String id){legacyLoads++;return ANIMATION;}
    public static double[] configured(TileEntityAnimatedScreenSelector tile,int slot,int choice){configuredCalls++;bind(null,CONFIGURED);return new double[]{.125,.375,.25,.75};}
    public static void bind(Object renderer,ResourceLocation location){binds++;lastBound=location;}
    public static void light(int unit,float block,float sky){}
    public static ResourceLocation off(boolean framed){return ScreenTextureLocations.off(framed);}
    public static ResourceLocation stationary(String id){return ScreenTextureLocations.stationary(id);}
    public static void capture(float top,float bottom,float left,float right,float block,float sky){observed=new float[]{top,bottom,left,right,block,sky};}
    private static void need(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static final class Tile extends TileEntityAnimatedScreenSelector {
        int choice,mode;String screen;
        @Override public int getSurfaceTexture(int slot){return choice;}
        @Override public int getEffectiveMode(){return mode;}
        @Override public String getSelectedScreen(){return screen;}
        @Override public int getAnimationSpeedTicks(){return 10;}
    }
    private static java.lang.reflect.Method branch()throws Exception{
        String source="com/vandorlabs/client/TEAnimatedScreenSelector",target="com/vandorlabs/client/SelectorArtworkBranchFixture";
        String hooks=SelectorTextureRoutingChecks.class.getName().replace('.','/'),animation=Animation.class.getName().replace('.','/');
        ClassNode parsed=new ClassNode();
        try(java.io.InputStream input=SelectorTextureRoutingChecks.class.getResourceAsStream("/"+source+".class")){new ClassReader(input).accept(parsed,ClassReader.SKIP_FRAMES);}
        MethodNode render=null;
        for(MethodNode method:parsed.methods)if(method.name.equals("render")&&method.desc.startsWith("(Lcom/vandorlabs/tiles/TileEntityAnimatedScreenSelector;"))render=method;
        need(render!=null,"actual selector render method missing");
        AbstractInsnNode start=null,end=null;
        for(AbstractInsnNode node=render.instructions.getFirst();node!=null;node=node.getNext())if(node instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)node;
            if(start==null&&call.name.equals("getEffectiveMode")){
                start=node.getPrevious();while(start.getOpcode()<0)start=start.getPrevious();need(start.getOpcode()==Opcodes.ALOAD,"artwork branch start changed");
            }else if(start!=null&&call.owner.equals("net/minecraft/client/renderer/Tessellator")&&call.name.equals("getInstance")){end=node;break;}
        }
        need(start!=null&&end!=null,"artwork branch boundaries not found");
        Map<LabelNode,LabelNode> labels=new HashMap<>();for(AbstractInsnNode node:render.instructions.toArray())if(node instanceof LabelNode)labels.put((LabelNode)node,new LabelNode());
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,target,null,"java/lang/Object",null);
        MethodVisitor output=writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"prepare","(Ljava/lang/Object;Lcom/vandorlabs/tiles/TileEntityAnimatedScreenSelector;)V",null,null);output.visitCode();
        for(AbstractInsnNode node=start;node!=end;node=node.getNext()){
            if(node instanceof LineNumberNode||node instanceof FrameNode)continue;
            AbstractInsnNode copy=node.clone(labels);
            if(copy instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)copy;
                if(call.owner.equals(source)&&call.name.equals("animData")){call.owner=hooks;call.name="animation";call.desc="(Ljava/lang/String;)L"+animation+";";}
                else if(call.owner.equals(source)&&call.name.equals("bindSurface")){call.owner=hooks;call.name="configured";}
                else if(call.name.equals("bindTexture")){call.setOpcode(Opcodes.INVOKESTATIC);call.owner=hooks;call.name="bind";call.desc="(Ljava/lang/Object;Lnet/minecraft/util/ResourceLocation;)V";}
                else if(call.owner.equals("net/minecraft/client/renderer/OpenGlHelper")&&call.name.equals("setLightmapTextureCoords")){call.owner=hooks;call.name="light";}
                else if(call.owner.equals("com/vandorlabs/client/ScreenTextureLocations"))call.owner=hooks;
            }else if(copy instanceof FieldInsnNode){FieldInsnNode field=(FieldInsnNode)copy;
                if(field.owner.equals(source+"$AnimData"))field.owner=animation;
                else if(field.owner.equals("net/minecraft/client/renderer/OpenGlHelper")&&field.name.equals("lightmapTexUnit"))copy=new InsnNode(Opcodes.ICONST_0);
            }
            copy.accept(output);
        }
        for(String name:new String[]{"vTop","vBottom","uMin","uMax","lightU","lightV"}){
            int index=-1;for(LocalVariableNode variable:render.localVariables)if(variable.name.equals(name))index=variable.index;
            need(index>=0,"artwork output local missing: "+name);output.visitVarInsn(Opcodes.FLOAD,index);
        }
        output.visitMethodInsn(Opcodes.INVOKESTATIC,hooks,"capture","(FFFFFF)V",false);output.visitInsn(Opcodes.RETURN);output.visitMaxs(0,0);output.visitEnd();writer.visitEnd();
        class Loader extends ClassLoader {Loader(){super(SelectorTextureRoutingChecks.class.getClassLoader());}Class<?> load(byte[] bytes){return defineClass(null,bytes,0,bytes.length);}}
        return new Loader().load(writer.toByteArray()).getMethod("prepare",Object.class,TileEntityAnimatedScreenSelector.class);
    }
    static void run()throws Exception{
        java.lang.reflect.Method branch=branch();NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);world.time=35;
        Tile tile=new Tile();tile.setWorld(world);tile.setPos(new BlockPos(8,80,8));int cases=0;
        for(int mode=0;mode<3;mode++)for(int choice:new int[]{-1,0,1,23,511})for(String screen:new String[]{"engineering_screen","absent_fixture_screen"}){
            tile.mode=mode;tile.choice=choice;tile.screen=screen;legacyLoads=configuredCalls=binds=0;observed=null;lastBound=null;
            branch.invoke(null,null,tile);
            need(observed!=null&&binds==1,"primary artwork bound multiple textures");
            if(choice>=0){
                need(configuredCalls==1&&legacyLoads==0&&lastBound==CONFIGURED,"configured surface loaded/bound hidden legacy animation");
                need(observed[0]==.125F&&observed[1]==.375F&&observed[2]==.25F&&observed[3]==.75F,"configured float UV conversion changed");
            }else{
                need(configuredCalls==0&&legacyLoads==(mode==2?1:0),"legacy static/off/animated fallback changed");
                String nativeId=com.vandorlabs.blocks.ModBlocks.DISPLAY_SCREEN_IDS.contains(screen)?screen:com.vandorlabs.tiles.ScreenHousingTextures.canonicalScreen("engineering_screen");
                float top=mode==2?.375F:0,bottom=mode==2?.5F:1,span=bottom-top;
                boolean inset="engineering_screen".equals(nativeId);
                float expectedTop=inset?top+span/16:top,expectedBottom=inset?bottom-span/16:bottom;
                need(observed[0]==expectedTop&&observed[1]==expectedBottom&&observed[2]==(inset?1F/16:0)&&observed[3]==(inset?15F/16:1),"legacy/fallback inset or animation phase changed id="+nativeId+" mode="+mode+" actual="+Arrays.toString(observed));
            }
            float light=mode==0?128:240;need(observed[4]==light&&observed[5]==light,"configured/native primary lightmap changed");cases++;
        }
        System.out.println("selector-cpu texture routing PASS actual compiled artwork branch cases="+cases+" configured legacy-loads0 binds1, native/static/off/fallback UV and light preserved; recording sinks, no GL upload");
    }
    private SelectorTextureRoutingChecks(){}
}
