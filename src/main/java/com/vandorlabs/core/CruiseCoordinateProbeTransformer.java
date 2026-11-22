package com.vandorlabs.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** The coordinate probe leaves the actor's real coordinates intact outside chunk/tracking calculations. */
public final class CruiseCoordinateProbeTransformer implements IClassTransformer {
    private static final String HOOK = "com/vandorlabs/vehicle/CruiseCoordinateProbe";
    private static final String MAP = "net.minecraft.server.management.PlayerChunkMap";
    private static final String TRACKER = "net.minecraft.entity.EntityTrackerEntry";
    private static final String WORLD = "net.minecraft.world.World";
    private static final String CHUNK = "net.minecraft.world.chunk.Chunk";
    private static final String CLIENT = "net.minecraft.client.entity.EntityPlayerSP";
    private static final String NETWORK = "net.minecraft.network.NetHandlerPlayServer";

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !MAP.equals(transformedName) && !TRACKER.equals(transformedName)
            && !WORLD.equals(transformedName) && !CHUNK.equals(transformedName) && !CLIENT.equals(transformedName)
            && !NETWORK.equals(transformedName)) return bytes;
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type,0);
        int changed=0;
        for (MethodNode method : type.methods) {
            if (!target(transformedName,method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (NETWORK.equals(transformedName)) {
                    if (instruction instanceof MethodInsnNode && mountedRotation((MethodInsnNode)instruction)) {
                        // The first call in processPlayer is its mounted rotation branch.
                        // The first tick call restores the player position captured before gameplay updates.
                        method.instructions.set(instruction,new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"mountedRotation",
                            "(Lnet/minecraft/entity/player/EntityPlayerMP;DDDFF)V",false));
                        changed++;
                        break;
                    }
                    continue;
                }
                if (!(instruction instanceof FieldInsnNode) || instruction.getOpcode()!=Opcodes.GETFIELD) continue;
                FieldInsnNode field=(FieldInsnNode)instruction;
                String axis=name(field.name,"posX","field_70165_t")?"X":name(field.name,"posZ","field_70161_v")?"Z":null;
                if (axis==null || !"D".equals(field.desc)) continue;
                // World has many physical-coordinate reads. Only the chunk reindex divides by 16.
                if ((WORLD.equals(transformedName) || CHUNK.equals(transformedName)) && !chunkRead(field)) continue;
                method.instructions.set(field,new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"tracking"+axis,
                    "(Lnet/minecraft/entity/Entity;)D",false));
                changed++;
            }
        }
        if (changed==0) throw new IllegalStateException("Cruise coordinate probe could not patch "+transformedName);
        System.out.println("[vandorlabs][cruise-coordinate-probe] patched "+transformedName+" coordinate reads="+changed);
        ClassWriter output=new ClassWriter(0);
        type.accept(output);
        return output.toByteArray();
    }

    private static boolean target(String owner,String method) {
        if (MAP.equals(owner)) return name(method,"addPlayer","func_72683_a") || name(method,"updateMovingPlayer","func_72685_d")
            || name(method,"setPlayerViewRadius","func_152622_a");
        if (TRACKER.equals(owner)) return name(method,"isVisibleTo","func_180233_c");
        if (WORLD.equals(owner)) return name(method,"updateEntityWithOptionalForce","func_72866_a");
        if (CHUNK.equals(owner)) return name(method,"addEntity","func_76612_a");
        if (NETWORK.equals(owner)) return name(method,"processPlayer","func_147347_a") || name(method,"update","func_73660_a");
        return name(method,"onUpdate","func_70071_h_");
    }

    private static boolean chunkRead(FieldInsnNode field) {
        AbstractInsnNode division=field.getNext();
        return division instanceof LdcInsnNode && Double.valueOf(16).equals(((LdcInsnNode)division).cst)
            && division.getNext().getOpcode()==Opcodes.DDIV;
    }

    private static boolean mountedRotation(MethodInsnNode call) {
        return call.getOpcode()==Opcodes.INVOKEVIRTUAL && "(DDDFF)V".equals(call.desc)
            && call.owner.equals("net/minecraft/entity/player/EntityPlayerMP")
            && name(call.name,"setPositionAndRotation","func_70080_a");
    }

    private static boolean name(String actual,String mapped,String srg) {
        return mapped.equals(actual)||srg.equals(actual);
    }
}
