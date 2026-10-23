package com.vandorlabs.validation;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Reject shared bytecode references to APIs stripped by Forge's server SideTransformer. */
public final class DedicatedServerLinkageChecks {
    private final Map<String,ClassNode> classes=new HashMap<>();
    private final Set<String> absent=new HashSet<>();
    private final Set<String> clientMethods=new HashSet<>();
    private final Set<String> findings=new TreeSet<>();
    private int references,sharedClasses;

    private ClassNode type(String name)throws IOException {
        if(name==null||absent.contains(name))return null;
        if(classes.containsKey(name))return classes.get(name);
        try(InputStream in=getClass().getClassLoader().getResourceAsStream(name+".class")){
            if(in==null){absent.add(name);return null;}
            ClassNode node=new ClassNode();new ClassReader(in).accept(node,0);classes.put(name,node);return node;
        }
    }
    private static boolean client(List<AnnotationNode> annotations){
        if(annotations!=null)for(AnnotationNode a:annotations)
            if(a.desc.equals("Lnet/minecraftforge/fml/relauncher/SideOnly;")&&a.values!=null)
                for(Object value:a.values)if(value instanceof String[]&&((String[])value)[1].equals("CLIENT"))return true;
        return false;
    }
    private boolean clientClass(String name)throws IOException {
        if(name==null)return false;
        if(name.contains("/client/")||name.equals("com/vandorlabs/ClientProxy")||name.startsWith("com/vandorlabs/ClientProxy$"))return true;
        ClassNode c=type(name);
        return c!=null&&(client(c.visibleAnnotations)||client(c.invisibleAnnotations)
                ||(c.outerClass!=null&&clientClass(c.outerClass)));
    }
    private String stripped(String owner,String name,String desc,boolean field,Set<String> seen)throws IOException {
        if(owner==null||!seen.add(owner))return null;
        if(clientClass(owner))return owner+" (client class)";
        ClassNode c=type(owner);if(c==null)return null;
        if(field){
            for(FieldNode f:c.fields)if(f.name.equals(name)&&f.desc.equals(desc))
                return client(f.visibleAnnotations)||client(f.invisibleAnnotations)?owner+"."+name+desc:null;
        }else{
            for(MethodNode m:c.methods)if(m.name.equals(name)&&m.desc.equals(desc))
                return client(m.visibleAnnotations)||client(m.invisibleAnnotations)?owner+"."+name+desc:null;
        }
        if(name.equals("<init>"))return null;
        String result=stripped(c.superName,name,desc,field,seen);if(result!=null)return result;
        for(String parent:c.interfaces){result=stripped(parent,name,desc,field,seen);if(result!=null)return result;}
        return null;
    }
    private String stripped(String owner,String name,String desc,boolean field)throws IOException {
        return stripped(owner,name,desc,field,new HashSet<String>());
    }
    private static String key(String owner,MethodNode m){return owner+"."+m.name+m.desc;}
    private boolean clientCallback(ClassNode c,MethodNode m)throws IOException {
        if(client(m.visibleAnnotations)||client(m.invisibleAnnotations))return true;
        // Overrides of Minecraft's stripped callbacks (rendering, tooltips, client placement,
        // interpolation, light sampling) are only called by the physical client.
        if(!m.name.startsWith("<")&&stripped(c.superName,m.name,m.desc,false)!=null)return true;
        // Forge's IGuiHandler client entry point and tile update packets are client callbacks.
        if(c.name.equals("com/vandorlabs/GuiHandler")&&m.name.equals("getClientGuiElement"))return true;
        if(m.name.equals("onDataPacket")&&m.desc.equals("(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/network/play/server/SPacketUpdateTileEntity;)V"))return true;
        // PacketHandler registers these handlers only for Side.CLIENT. They must remain
        // loadable on the server for SimpleNetworkWrapper's registration, not SideOnly classes.
        if(c.name.equals("com/vandorlabs/network/MessageCanopySettingsResult$Handler")
                ||c.name.equals("com/vandorlabs/network/MessageTurntableCarry$Handler"))return true;
        return false;
    }
    private boolean reviewedRenderReference(ClassNode c,MethodNode m,String owner,String name){
        // Immutable mesh/light snapshots are built by getExtendedState/rendering only.
        if(owner.equals("net/minecraft/world/IBlockAccess")&&name.equals("getCombinedLight"))
            return c.name.equals("com/vandorlabs/blocks/DiagonalWallState")&&m.name.equals("<init>")
                ||c.name.equals("com/vandorlabs/blocks/ProgrammableHousingState")&&m.name.equals("neighborLight");
        // This join sampler is behind tile.getWorld().isRemote; the rest of extend is shared.
        if(c.name.equals("com/vandorlabs/blocks/ProgrammableHousingState")&&m.name.equals("extend")
                &&owner.equals("com/vandorlabs/client/JoinedHousingFaces")&&name.equals("sample"))return true;
        // The common interaction cancellation runs on both sides; sampling is inside isRemote.
        return c.name.equals("com/vandorlabs/items/ArmorTextureSampling")&&m.name.equals("onBlock")
                &&owner.equals("com/vandorlabs/client/ArmorTextureSampler")&&name.equals("sample");
    }
    private void descriptor(ClassNode c,MethodNode m,int line,Type t)throws IOException {
        if(t.getSort()==Type.METHOD){for(Type arg:t.getArgumentTypes())descriptor(c,m,line,arg);descriptor(c,m,line,t.getReturnType());}
        else if(t.getSort()==Type.ARRAY)descriptor(c,m,line,t.getElementType());
        else if(t.getSort()==Type.OBJECT&&clientClass(t.getInternalName()))report(c,m,line,t.getInternalName()+" (client descriptor)");
    }
    private void report(ClassNode c,MethodNode m,int line,String target){
        if(target!=null)findings.add(c.name+":"+line+" "+m.name+m.desc+" -> "+target);
    }
    private void reference(ClassNode c,MethodNode m,int line,String owner,String name,String desc,boolean field)throws IOException {
        references++;
        if(!reviewedRenderReference(c,m,owner,name))report(c,m,line,stripped(owner,name,desc,field));
    }
    private void handle(ClassNode c,MethodNode m,int line,Handle h)throws IOException {
        reference(c,m,line,h.getOwner(),h.getName(),h.getDesc(),h.getTag()<=Opcodes.H_PUTSTATIC);
    }
    private void scan(ClassNode c,MethodNode m)throws IOException {
        descriptor(c,m,0,Type.getMethodType(m.desc));
        int line=0;
        for(AbstractInsnNode ins:m.instructions.toArray()){
            if(ins instanceof LineNumberNode)line=((LineNumberNode)ins).line;
            if(ins instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)ins;reference(c,m,line,x.owner,x.name,x.desc,false);}
            else if(ins instanceof FieldInsnNode){FieldInsnNode x=(FieldInsnNode)ins;reference(c,m,line,x.owner,x.name,x.desc,true);}
            else if(ins instanceof TypeInsnNode){String name=((TypeInsnNode)ins).desc;if(clientClass(name))report(c,m,line,name+" (client type)");}
            else if(ins instanceof InvokeDynamicInsnNode){InvokeDynamicInsnNode x=(InvokeDynamicInsnNode)ins;handle(c,m,line,x.bsm);for(Object arg:x.bsmArgs)if(arg instanceof Handle)handle(c,m,line,(Handle)arg);}
            else if(ins instanceof LdcInsnNode){Object x=((LdcInsnNode)ins).cst;if(x instanceof Handle)handle(c,m,line,(Handle)x);else if(x instanceof Type)descriptor(c,m,line,(Type)x);}
        }
    }
    private void controls()throws IOException {
        String box="net/minecraft/util/math/AxisAlignedBB";
        ClassNode fixture=new ClassNode();fixture.name="linkage/NegativeControl";
        MethodNode method=new MethodNode();method.name="badBounds";method.desc="()V";
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,box,"<init>","(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)V",false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,box,"getCenter","()Lnet/minecraft/util/math/Vec3d;",false));
        scan(fixture,method);
        if(findings.size()!=2||stripped(box,"<init>","(DDDDDD)V",false)!=null)
            throw new IllegalStateException("Side annotation controls failed; check mapped Minecraft classpath");
        findings.clear();references=0;
    }
    private void run(Path root)throws IOException {
        controls();
        List<ClassNode> mod=new ArrayList<>();
        try(java.util.stream.Stream<Path> paths=Files.walk(root)){
            for(Path p:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".class"))::iterator){
                ClassNode c=new ClassNode();new ClassReader(Files.readAllBytes(p)).accept(c,0);classes.put(c.name,c);mod.add(c);
            }
        }
        // Seed actual client methods, then follow their synthetic lambda implementation handles.
        for(ClassNode c:mod)if(!clientClass(c.name))for(MethodNode m:c.methods)
            if(clientCallback(c,m))clientMethods.add(key(c.name,m));
        boolean changed;
        do{
            changed=false;
            for(ClassNode c:mod)for(MethodNode m:c.methods)if(clientMethods.contains(key(c.name,m)))
                for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof InvokeDynamicInsnNode)
                    for(Object arg:((InvokeDynamicInsnNode)ins).bsmArgs)if(arg instanceof Handle){
                        Handle h=(Handle)arg;
                        // Only compiler-generated bodies inherit the side of the enclosing method.
                        if(h.getName().startsWith("lambda$")&&clientMethods.add(h.getOwner()+"."+h.getName()+h.getDesc()))changed=true;
                    }
        }while(changed);
        // A mod may deliberately reuse an unannotated render-bounds override in collision.
        // Such a method is shared despite the side of the vanilla method it overrides.
        do{
            changed=false;
            for(ClassNode c:mod)if(!clientClass(c.name))for(MethodNode m:c.methods)
                if(!clientMethods.contains(key(c.name,m)))for(AbstractInsnNode ins:m.instructions.toArray()){
                    if(!(ins instanceof MethodInsnNode))continue;
                    MethodInsnNode call=(MethodInsnNode)ins;ClassNode target=type(call.owner);
                    if(target!=null&&!clientClass(target.name))for(MethodNode method:target.methods)
                        if(method.name.equals(call.name)&&method.desc.equals(call.desc)
                                &&!client(method.visibleAnnotations)&&!client(method.invisibleAnnotations)
                                &&clientMethods.remove(key(target.name,method)))changed=true;
                }
        }while(changed);
        for(ClassNode c:mod){
            if(clientClass(c.name))continue;sharedClasses++;
            for(FieldNode f:c.fields)if(!client(f.visibleAnnotations)&&!client(f.invisibleAnnotations)){
                MethodNode field=new MethodNode();field.name="field "+f.name;field.desc=f.desc;
                descriptor(c,field,0,Type.getType(f.desc));
            }
            for(MethodNode m:c.methods)if(!clientMethods.contains(key(c.name,m)))scan(c,m);
        }
        for(String finding:findings)System.err.println(finding);
        if(!findings.isEmpty())throw new IllegalStateException("Dedicated-server linkage: "+findings.size()+" unreviewed client API references");
        System.out.println("Dedicated-server linkage PASS shared-classes="+sharedClasses+" references="+references+" known-bad-controls=2");
    }
    public static void main(String[] args)throws Exception {new DedicatedServerLinkageChecks().run(Paths.get(args[0]));}
}
