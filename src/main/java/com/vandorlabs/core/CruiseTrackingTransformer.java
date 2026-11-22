package com.vandorlabs.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Keep canonical chunk bookkeeping separate from geographic positions for an opted-in aircraft. */
public final class CruiseTrackingTransformer implements IClassTransformer {
    private static final String HOOK = "com/vandorlabs/vehicle/CruiseTracking";
    private static final String MAP = "net.minecraft.server.management.PlayerChunkMap";
    private static final String TRACKER = "net.minecraft.entity.EntityTrackerEntry";
    private static final String WORLD = "net.minecraft.world.World";
    private static final String CHUNK = "net.minecraft.world.chunk.Chunk";
    private static final String CLIENT = "net.minecraft.client.entity.EntityPlayerSP";
    private static final String NETWORK = "net.minecraft.network.NetHandlerPlayServer";
    private static final String PLAYER = "net.minecraft.entity.player.EntityPlayerMP";

    private static final java.util.Set<String> VERIFIED = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static boolean serverReady() {
        return VERIFIED.contains(MAP) && VERIFIED.contains(TRACKER) && VERIFIED.contains(WORLD)
                && VERIFIED.contains(CHUNK) && VERIFIED.contains(NETWORK) && VERIFIED.contains(PLAYER);
    }

    public static boolean clientReady() {
        return VERIFIED.contains(WORLD) && VERIFIED.contains(CHUNK) && VERIFIED.contains(CLIENT);
    }

    private static int expected(String target) {
        if(MAP.equals(target))return 12;
        if(WORLD.equals(target))return 6;
        if(PLAYER.equals(target))return 1;
        return 2;
    }

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !MAP.equals(transformedName) && !TRACKER.equals(transformedName)
            && !WORLD.equals(transformedName) && !CHUNK.equals(transformedName) && !CLIENT.equals(transformedName)
            && !NETWORK.equals(transformedName) && !PLAYER.equals(transformedName)) return bytes;
        ClassNode type = new ClassNode();
        new ClassReader(bytes).accept(type,0);
        int changed=0;
        for (MethodNode method : type.methods) {
            if (!target(transformedName,method.name)) continue;
            if (PLAYER.equals(transformedName)) {
                InsnList save=new InsnList();
                save.add(new VarInsnNode(Opcodes.ALOAD,0));
                save.add(new VarInsnNode(Opcodes.ALOAD,1));
                save.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"writeAnchor",
                    "(Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/nbt/NBTTagCompound;)V",false));
                method.instructions.insert(save);
                changed++;
                continue;
            }
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
                if (WORLD.equals(transformedName) && !chunkRead(field) && !floor(field.getNext())) continue;
                if (CHUNK.equals(transformedName) && !chunkRead(field)) continue;
                if (TRACKER.equals(transformedName)) {
                    String tracked="trackedEntity";
                    for(FieldNode data:type.fields)if(name(data.name,"trackedEntity","field_73132_a")){tracked=data.name;break;}
                    InsnList context=new InsnList();
                    context.add(new VarInsnNode(Opcodes.ALOAD,0));
                    context.add(new FieldInsnNode(Opcodes.GETFIELD,type.name,tracked,"Lnet/minecraft/entity/Entity;"));
                    method.instructions.insertBefore(field,context);
                    method.maxStack=Math.max(method.maxStack,8);
                    method.instructions.set(field,new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"viewer"+axis,
                        "(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)D",false));
                } else {
                    method.instructions.set(field,new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"tracking"+axis,
                        "(Lnet/minecraft/entity/Entity;)D",false));
                }
                changed++;
            }
        }
        if (changed!=expected(transformedName)) {
            VERIFIED.remove(transformedName);
            System.err.println("[vandorlabs] Geographic cruise unavailable: unexpected layout for "+transformedName);
            return bytes;
        }
        ClassWriter output=new ClassWriter(0);
        type.accept(output);
        byte[] result=output.toByteArray();
        VERIFIED.add(transformedName);
        return result;
    }

    private static boolean target(String owner,String method) {
        if (MAP.equals(owner)) return name(method,"addPlayer","func_72683_a") || name(method,"updateMovingPlayer","func_72685_d")
            || name(method,"setPlayerViewRadius","func_152622_a");
        if (TRACKER.equals(owner)) return name(method,"isVisibleTo","func_180233_c");
        if (WORLD.equals(owner)) return name(method,"updateEntityWithOptionalForce","func_72866_a")
            || name(method,"spawnEntity","func_72838_d");
        if (CHUNK.equals(owner)) return name(method,"addEntity","func_76612_a");
        if (NETWORK.equals(owner)) return name(method,"processPlayer","func_147347_a") || name(method,"update","func_73660_a");
        if (PLAYER.equals(owner)) return name(method,"writeEntityToNBT","func_70014_b");
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

    private static boolean floor(AbstractInsnNode instruction) {
        if(!(instruction instanceof MethodInsnNode))return false;
        MethodInsnNode call=(MethodInsnNode)instruction;
        return call.owner.equals("net/minecraft/util/math/MathHelper")&&"(D)I".equals(call.desc)
            && name(call.name,"floor","func_76128_c");
    }

    private static boolean name(String actual,String mapped,String srg) {
        return mapped.equals(actual)||srg.equals(actual);
    }
}
