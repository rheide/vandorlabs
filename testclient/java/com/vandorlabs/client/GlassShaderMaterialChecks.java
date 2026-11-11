package com.vandorlabs.client;

import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.ZipFile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;

/** Exercise the optional private-helper adapter and uniform boundaries without a GL context. */
public final class GlassShaderMaterialChecks {
    private static final String G5="OptiFine_1.12.2_HD_U_G5";
    private static int sink;
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Tile extends TileEntity {
        final int metadata;
        Tile(int metadata){this.metadata=metadata;}
        @Override public Block getBlockType(){return Blocks.STAINED_GLASS;}
        @Override public int getBlockMetadata(){return metadata;}
    }
    // G5's inspected helper resolves (block ID, metadata), falling back to block ID.
    // This fixture supplies mutable pack aliases; it does not simulate shader rendering.
    public static final class Shaders {
        static final int[] aliases=new int[16];
        static int calls;
        static boolean fail;
        private static int getBlockEntityId(TileEntity tile){
            calls++;if(fail)throw new IllegalStateException("optional helper access failed");
            int alias=aliases[tile.getBlockMetadata()];return alias<0?Block.getIdFromBlock(tile.getBlockType()):alias;
        }
        public static int getBlockAliasId(int block,int metadata){return aliases[metadata];}
    }
    private static final class WrongSignature {private static long getBlockEntityId(TileEntity tile){return 0;}}
    private static final class InstanceMethod {private int getBlockEntityId(TileEntity tile){return 0;}}
    private static IBlockState state(int metadata){return Blocks.STAINED_GLASS.getDefaultState().withProperty(BlockColored.COLOR,EnumDyeColor.byMetadata(metadata));}
    private static GlassShaderMaterial resolver(){
        MethodHandle handle=GlassShaderMaterial.find(G5,Shaders.class);need(handle!=null,"verified helper unavailable");return new GlassShaderMaterial(handle);
    }
    private static void partition(){
        Arrays.fill(Shaders.aliases,-1);Shaders.fail=false;GlassShaderMaterial material=resolver();
        TileEntity first=new Tile(0),second=new Tile(1),third=new Tile(2);
        int fallback=Block.getIdFromBlock(Blocks.STAINED_GLASS);
        need(material.key(first)==fallback&&material.key(second)==fallback,"unmapped metadata block-ID fallback");
        need(!GlassShaderMaterial.split(state(0),material.key(first),state(1),material.key(second)),"equal unmapped uniforms split by state");
        Shaders.aliases[0]=Shaders.aliases[1]=500;Shaders.aliases[2]=700;
        need(!GlassShaderMaterial.split(state(0),material.key(first),state(1),material.key(second)),"equal mapped uniforms split by state");
        need(GlassShaderMaterial.split(state(1),material.key(second),state(2),material.key(third)),"different shader aliases merged");
        int oldRuns=0,newRuns=0,previousKey=GlassShaderMaterial.UNAVAILABLE;IBlockState previous=null;
        for(int i=0;i<1024;i++){
            IBlockState state=state(i&1);int key=material.key((i&1)==0?first:second);
            if(previous==null||previous!=state)oldRuns++;
            if(previous==null||GlassShaderMaterial.split(previous,previousKey,state,key))newRuns++;
            previous=state;previousKey=key;
        }
        need(oldRuns==1024&&newRuns==1,"interleaved metadata uniform runs");
        // The real helper is read on every key lookup; a pack reload cannot leave cached IDs.
        int before=material.key(second);Shaders.aliases[1]=900;int after=material.key(second);
        need(before==500&&after==900&&GlassShaderMaterial.split(state(1),before,state(1),after),"alias reload retained old uniform");
        System.out.println("Glass shader material partition panes1024 fullStateRuns="+oldRuns+" equalAliasRuns="+newRuns+" distinct aliases and same-state alias reload preserved");
    }
    private static void fallback(){
        int missing=GlassShaderMaterial.UNAVAILABLE;
        need(GlassShaderMaterial.find("OptiFine_1.12.2_HD_U_F5",Shaders.class)==null,"unknown version used G5 contract");
        need(GlassShaderMaterial.find(G5,Object.class)==null,"missing helper accepted");
        need(GlassShaderMaterial.find(G5,WrongSignature.class)==null,"wrong return type accepted");
        need(GlassShaderMaterial.find(G5,InstanceMethod.class)==null,"instance helper accepted");
        GlassShaderMaterial absent=new GlassShaderMaterial(null);need(absent.key(new Tile(0))==missing,"missing hook fallback");
        need(!GlassShaderMaterial.split(state(0),missing,state(0),missing),"same-state fallback split");
        need(GlassShaderMaterial.split(state(0),missing,state(1),missing),"different-state fallback merged");
        need(GlassShaderMaterial.split(state(0),500,state(0),missing),"failed lookup merged into a known-uniform batch");
        GlassShaderMaterial broken=resolver();int before=Shaders.calls;Shaders.fail=true;
        need(broken.key(new Tile(0))==missing,"lookup failure did not fall back");
        for(int i=0;i<1024;i++)need(broken.key(new Tile(0))==missing,"failed lookup became active again");
        need(Shaders.calls==before+1,"failed optional hook was retried for each pane");Shaders.fail=false;
    }
    private static void allocation(){
        Arrays.fill(Shaders.aliases,500);GlassShaderMaterial material=resolver();TileEntity tile=new Tile(0);
        for(int i=0;i<32768;i++)sink=material.key(tile);
        com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().getId(),start=meter.getThreadAllocatedBytes(thread);int calls=Shaders.calls;
        for(int i=0;i<65536;i++)sink=material.key(tile);
        long bytes=meter.getThreadAllocatedBytes(thread)-start;
        need(bytes==0,"primitive material key lookup allocated "+bytes);need(Shaders.calls==calls+65536,"live alias reads were cached");
        System.out.println("Glass shader material invokeExact calls65536 allocated="+bytes);
    }
    /** Extract only the verified binary's pure ID helper, omitting every GL initializer.
     * Class/method names are mapped to Forge and the alias table is supplied by the fixture. */
    private static void official(File jar)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar.toPath()));StringBuilder hex=new StringBuilder();for(byte b:hash)hex.append(String.format("%02x",b&255));
        need(hex.toString().equals("3b0006797771feb97f2d0d2908ae7c9a78cefb2e730af1c2e85d08a839ba271e"),"unverified OptiFine binary");
        final String tile="net/minecraft/tileentity/TileEntity",block="net/minecraft/block/Block";
        org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES|org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        writer.visit(org.objectweb.asm.Opcodes.V1_8,org.objectweb.asm.Opcodes.ACC_PUBLIC,"com/vandorlabs/client/G5GlassAliasContract",null,"java/lang/Object",null);
        final int[] methods={0};
        try(ZipFile zip=new ZipFile(jar)){
            new org.objectweb.asm.ClassReader(zip.getInputStream(zip.getEntry("net/optifine/shaders/Shaders.class"))).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM5){
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                    if(!name.equals("getBlockEntityId")||!desc.equals("(Lavj;)I"))return null;methods[0]++;
                    org.objectweb.asm.MethodVisitor target=writer.visitMethod(org.objectweb.asm.Opcodes.ACC_PRIVATE|org.objectweb.asm.Opcodes.ACC_STATIC,name,"(L"+tile+";)I",null,null);
                    return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM5,target){
                        @Override public void visitMethodInsn(int opcode,String owner,String called,String descriptor,boolean itf){
                            if(owner.equals("avj")){owner=tile;need(called.equals("x")||called.equals("v"),"unexpected tile helper call");called=called.equals("x")?"getBlockType":"getBlockMetadata";}
                            else if(owner.equals("aow")){owner=block;need(called.equals("a"),"unexpected block helper call");called="getIdFromBlock";}
                            else if(owner.equals("net/optifine/shaders/BlockAliases")){need(called.equals("getBlockAliasId"),"unexpected alias helper call");owner=Shaders.class.getName().replace('.','/');}
                            else throw new AssertionError("Unexpected dependency in pure G5 helper: "+owner);
                            super.visitMethodInsn(opcode,owner,called,descriptor.replace("Lavj;","L"+tile+";").replace("Laow;","L"+block+";"),itf);
                        }
                    };
                }
            },org.objectweb.asm.ClassReader.SKIP_FRAMES|org.objectweb.asm.ClassReader.SKIP_DEBUG);
        }
        writer.visitEnd();need(methods[0]==1,"G5 helper extraction");
        class Loader extends ClassLoader {Loader(){super(GlassShaderMaterialChecks.class.getClassLoader());}Class<?> define(byte[] code){return defineClass(null,code,0,code.length);}}
        Class<?> extracted=new Loader().define(writer.toByteArray());GlassShaderMaterial actual=new GlassShaderMaterial(GlassShaderMaterial.find(G5,extracted));
        int blockId=Block.getIdFromBlock(Blocks.STAINED_GLASS),cases=0;
        for(int phase=0;phase<4;phase++)for(int metadata=0;metadata<16;metadata++){
            int alias=phase==0?-1:phase==1?500:phase==2?500+(metadata&1):900;
            Shaders.aliases[metadata]=alias;int expected=alias<0?blockId:alias;
            need(actual.key(new Tile(metadata))==expected,"official G5 alias metadata="+metadata+" phase="+phase);cases++;
        }
        need(actual.key(null)==-1,"official G5 null tile");
        System.out.println("Official G5 material helper PASS exact-binary SHA256 pure-method cases="+cases+" unmapped/equal/distinct/reloaded aliases without GL");
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();partition();fallback();allocation();
        if(args.length>0)official(new File(args[0]));
        System.out.println("Glass shader material PASS alias partitions/reload/version and ABI fallback/failure retirement/zero warm allocation");
    }
}
