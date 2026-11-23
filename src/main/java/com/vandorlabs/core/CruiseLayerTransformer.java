package com.vandorlabs.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Give the detached biome generator private scratch arrays. Ordinary callers use vanilla's pool. */
public final class CruiseLayerTransformer implements IClassTransformer, Opcodes {
    public static volatile boolean installed;
    public byte[] transform(String name,String target,byte[] bytes) {
        if(bytes==null || !"net.minecraft.world.gen.layer.IntCache".equals(target))return bytes;
        ClassNode node=new ClassNode();
        new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods){
            if((method.access&ACC_STATIC)==0 || !"(I)[I".equals(method.desc))continue;
            LabelNode vanilla=new LabelNode();
            InsnList branch=new InsnList();
            branch.add(new VarInsnNode(ILOAD,0));
            branch.add(new MethodInsnNode(INVOKESTATIC,"com/vandorlabs/navigation/CruiseScratch","allocate","(I)[I",false));
            branch.add(new InsnNode(DUP));
            branch.add(new JumpInsnNode(IFNULL,vanilla));
            branch.add(new InsnNode(ARETURN));
            branch.add(vanilla);
            branch.add(new FrameNode(F_SAME1,0,null,1,new Object[]{"[I"}));
            branch.add(new InsnNode(POP));
            // The original method synchronizes on IntCache.class. Keep that lock only
            // on vanilla callers, so unrelated world generation never waits for atlas allocation.
            MethodNode dispatch=new MethodNode(method.access&~ACC_SYNCHRONIZED,method.name,method.desc,null,null);
            method.name="vandor$vanillaIntCache";
            branch.add(new VarInsnNode(ILOAD,0));
            branch.add(new MethodInsnNode(INVOKESTATIC,node.name,method.name,method.desc,false));
            branch.add(new InsnNode(ARETURN));
            dispatch.instructions.add(branch);
            node.methods.add(dispatch);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            installed=true;
            return writer.toByteArray();
        }
        return bytes;
    }
}
