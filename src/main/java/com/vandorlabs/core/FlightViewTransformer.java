package com.vandorlabs.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Adjust only the observer in map drawing, and distinguish deliberate teleports from sneaking out of a seat. */
public final class FlightViewTransformer implements IClassTransformer, Opcodes {
    private static final String GEO = "com/vandorlabs/client/CruiseMapView";
    private static final String TELEPORT = "com/vandorlabs/vehicle/VehicleTeleport";

    public byte[] transform(String name, String target, byte[] bytes) {
        if (bytes == null || target == null) return bytes;
        boolean dismount = target.equals("net.minecraft.command.CommandTP")
                || target.equals("net.minecraft.command.server.CommandTeleport")
                || target.equals("journeymap.common.feature.JourneyMapTeleport");
        boolean map = target.equals("journeymap.client.ui.minimap.MiniMap") || target.equals("journeymap.client.ui.fullscreen.Fullscreen");
        boolean editor = target.equals("journeymap.client.ui.waypoint.WaypointEditor");
        boolean newWaypoint = target.equals("journeymap.client.model.Waypoint");
        boolean distances = target.equals("journeymap.client.ui.waypoint.WaypointManagerItem");
        boolean beacon = target.equals("journeymap.client.render.ingame.RenderWaypointBeacon");
        if (!dismount && !map && !beacon && !editor && !newWaypoint && !distances) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(ASM5,writer) {
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access,method,descriptor,signature,exceptions);
                return new MethodVisitor(ASM5,delegate) {
                    @Override public void visitMethodInsn(int op,String owner,String id,String desc,boolean itf) {
                        if (dismount && op==INVOKEVIRTUAL && desc.equals("()V") && owner.startsWith("net/minecraft/entity/")
                                && (id.equals("func_184210_p") || id.equals("dismountRidingEntity"))) {
                            super.visitMethodInsn(INVOKESTATIC,TELEPORT,"detach","(Lnet/minecraft/entity/Entity;)V",false);
                            return;
                        }
                        if(beacon && method.equals("doRender") && op==INVOKESTATIC && owner.equals("java/lang/Math")
                                && id.equals("abs") && desc.equals("(D)D")){
                            super.visitMethodInsn(INVOKESTATIC,GEO,"beaconAngleDifference",desc,false);
                            return;
                        }
                        super.visitMethodInsn(op,owner,id,desc,itf);
                        if(beacon && method.equals("doRender") && Boolean.getBoolean("vandorlabs.cruisePhysicalChecks")
                                && owner.equals("java/lang/String") && id.equals("format") && desc.equals("(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")){
                            super.visitInsn(DUP);
                            super.visitMethodInsn(INVOKESTATIC,"com/vandorlabs/client/CruiseWaypointChecks","formatted","(Ljava/lang/String;)V",false);
                        }
                        if(beacon && method.equals("doRender") && owner.equals("net/minecraft/entity/Entity")
                                && (id.equals("func_70079_am") || id.equals("getRotationYawHead")) && desc.equals("()F"))
                            super.visitMethodInsn(INVOKESTATIC,GEO,"beaconYaw","(F)F",false);
                        if ((beacon && method.equals("doRender") && owner.equals("net/minecraft/entity/Entity")
                                || distances && method.equals("getDistanceTo") && owner.equals("net/minecraft/entity/player/EntityPlayer"))
                                && (id.equals("func_174791_d") || id.equals("getPositionVector")))
                            super.visitMethodInsn(INVOKESTATIC,GEO,beacon?"beaconOrigin":"viewPosition","(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",false);
                    }
                    @Override public void visitFieldInsn(int op,String owner,String id,String desc) {
                        super.visitFieldInsn(op,owner,id,desc);
                        if (op!=GETFIELD || !desc.equals("D")) return;
                        String axis=null;
                        if ((map || editor) && owner.equals("net/minecraft/client/entity/EntityPlayerSP")
                                || newWaypoint && method.equals("of") && owner.equals("net/minecraft/entity/player/EntityPlayer")) {
                            if (id.equals("field_70165_t") || id.equals("posX")) axis="x";
                            if (id.equals("field_70163_u") || id.equals("posY")) axis="y";
                            if (id.equals("field_70161_v") || id.equals("posZ")) axis="z";
                        } else if ((editor || map && method.equals("getLocation")) && owner.equals("net/minecraft/util/math/AxisAlignedBB")
                                && (id.equals("field_72338_b") || id.equals("minY"))) axis="y";
                        else if (beacon && method.equals("doRender") && owner.equals("net/minecraft/client/renderer/entity/RenderManager")) {
                            if (id.equals("field_78730_l") || id.equals("viewerPosX")) axis="x";
                            if (id.equals("field_78731_m") || id.equals("viewerPosY")) axis="y";
                            if (id.equals("field_78728_n") || id.equals("viewerPosZ")) axis="z";
                        }
                        if (axis!=null) {
                            if (newWaypoint) {
                                super.visitVarInsn(ALOAD,0);
                                super.visitMethodInsn(INVOKESTATIC,GEO,axis,"(DLnet/minecraft/entity/player/EntityPlayer;)D",false);
                            } else super.visitMethodInsn(INVOKESTATIC,GEO,axis,"(D)D",false);
                        }
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }
}
