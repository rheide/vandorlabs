package com.vandorlabs.gear.client;

import com.vandorlabs.gear.GearBlock;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * One renderer at the assembly root. Meshes are shared; stationary transformed
 * vertices are cached, while moving arms and vehicle wheels remain continuous.
 */
@SideOnly(Side.CLIENT)
public final class GearRenderer extends net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<GearTileEntity> {
    private boolean cachePose;
    private Affine mounting=Affine.identity();
    public static long cachedVertexBytes(){return GearModels.cachedVertexBytes();}
    @Override public void render(GearTileEntity tile,double x,double y,double z,float partial,int stage,float alpha){
        if(!visible(tile))return;
        try(com.vandorlabs.client.TurntableRenderState state=new com.vandorlabs.client.TurntableRenderState()){
            bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableCull();
            BufferBuilder buffer=net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);
            renderTileEntityFast(tile,x,y,z,partial,stage,alpha,buffer);net.minecraft.client.renderer.Tessellator.getInstance().draw();try{ArmPayloadRenderer.render(tile,tile.renderProgress(partial),x,y,z,partial);}catch(RuntimeException | LinkageError failure){tile.quarantinePayload();com.vandorlabs.VandorLabs.logger.warn("Arm attachment rendering unavailable; snapshot retained",failure);}
        }
    }
    /** Bound members are drawn by their operational controller, including compact parts. */
    public static boolean visible(GearTileEntity tile){
        if(tile.getWorld()==null)return false;
        GearTileEntity controller=tile.loadedController();
        if(controller!=null&&controller.isOperational()&&controller!=tile)return false;
        IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        return state.getBlock() instanceof GearBlock&&((GearBlock)state.getBlock()).componentRoot(tile.getPos(),state).equals(tile.getPos());
    }
    @Override public void renderTileEntityFast(GearTileEntity tile, double x, double y, double z, float partialTicks,
                                     int destroyStage, float partial, BufferBuilder buffer) {
        if(!visible(tile))return;
        emit(tile,x,y,z,partialTicks,destroyStage,partial,buffer,true);
    }
    /** Uncached vertex path retained for native equivalence/performance checks. */
    public void renderUncached(GearTileEntity tile,double x,double y,double z,float partialTicks,BufferBuilder buffer){
        emit(tile,x,y,z,partialTicks,0,1,buffer,false);
    }
    private void emit(GearTileEntity tile,double x,double y,double z,float partialTicks,int destroyStage,float partial,BufferBuilder buffer,boolean cache){
        if (tile.getWorld() == null) return;
        mounting=Affine.mount(tile.mountDirection());
        double progress=tile.renderProgress(partialTicks);
        cachePose=cache&&(tile.isFixedMount()||progress==0||progress==1)&&(!(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)||((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).craft==null);

        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof GearBlock) || !state.getValue(GearBlock.ASSEMBLED)&&((GearBlock)state.getBlock()).sizeClass()==1) return;
        GearBlock part=(GearBlock)state.getBlock();if(!part.componentRoot(tile.getPos(),state).equals(tile.getPos()))return;
        // Another mod's FastTESR can leave a buffer translation behind.
        // Coordinates below already include the camera-relative tile offset.
        buffer.setTranslation(0, 0, 0);
        int light = tile.getWorld().getCombinedLight(tile.getPos(), 0);
        GearTopology.Assembly assembly = tile.isOperational() ? tile.assembly() : null;
        if (assembly == null) {
            if (tile.hasLoadedController()) return;
            // A member with an unloaded/missing root must never become an
            // invisible block. Draw its ordinary part until ownership heals.
            GearBlock.Kind kind = ((GearBlock) state.getBlock()).kind();
            String model = kind == GearBlock.Kind.MOUNT ? "fixed_mount" : kind == GearBlock.Kind.EXTENDER ? "extender"
                    : kind == GearBlock.Kind.SWING_ARM ? "swing_arm"
                    : kind == GearBlock.Kind.PLATE ? "plate_0" : kind==GearBlock.Kind.WHEEL?part.wheelModel():"ski_0";
            Affine yaw=Affine.quarterTurn(quarterTurns(state.getValue(GearBlock.FACING)));
            if(part.sizeClass()==2&&kind!=GearBlock.Kind.WHEEL){
                Affine centered=Affine.translation(-.5,0,-.5).compose(Affine.scaleXZ(2));
                draw(kind==GearBlock.Kind.MOUNT?"fixed_mount_compact":"mount",yaw,x,y,z,light,buffer);
                if(kind!=GearBlock.Kind.MOUNT){draw("sleeve",yaw.compose(centered),x,y,z,light,buffer);
                    if(kind==GearBlock.Kind.SWING_ARM){Affine bar=Affine.translation(.5,0,.5).compose(Affine.scaleXZ(2,1).compose(Affine.translation(-.5,0,-.5)));draw("swing_linkage",yaw.compose(bar),x,y,z,light,buffer);draw("swivel",yaw.compose(centered.compose(Affine.translation(0,-1,0))),x,y,z,light,buffer);}
                    else draw("rod",yaw.compose(centered.compose(Affine.scaleY(.5,0))),x,y,z,light,buffer);
                }
            }else draw(model,yaw,x,y,z,light,buffer);
            return;
        }

        GearMotion.Pose pose = tile.pose(tile.renderProgress(partialTicks));
        Affine fixed = Affine.quarterTurn(quarterTurns(state.getValue(GearBlock.FACING)));
        Affine moving = Affine.fromPose(pose, false);
        Affine feet = Affine.fromPose(pose, true);

        for(net.minecraft.util.math.BlockPos column:tile.columns()){
            int width=tile.armSize();double shift=(width-1)*.5;
            IBlockState columnState=tile.getWorld().getBlockState(tile.getPos().add(column));
            if(!(columnState.getBlock() instanceof GearBlock))continue;
            Affine columnFixed=Affine.quarterTurn(quarterTurns(columnState.getValue(GearBlock.FACING)));
            net.minecraft.util.math.BlockPos canonical=com.vandorlabs.gear.ArmOrientation.inverse(tile.mountDirection(),column);
            Affine offset=Affine.translation(canonical.getX(),canonical.getY(),canonical.getZ());
            Affine size=Affine.translation(-shift,0,-shift).compose(Affine.scaleXZ(width));
            Affine barSize=tile.isSwingArmController()?Affine.scaleXZ(tile.direction().axisX()!=0?width:1,tile.direction().axisZ()!=0?width:1):Affine.scaleXZ(width);
            barSize=Affine.translation(.5,0,.5).compose(barSize.compose(Affine.translation(-.5,0,-.5)));
            Affine armPose=Affine.translation(tile.armShiftX(column),0,tile.armShiftZ(column)).compose(moving.compose(barSize));
            Affine footPose=Affine.translation(tile.armShiftX(column),0,tile.armShiftZ(column)).compose(feet.compose(size));
            draw(tile.isFixedMount()?(width==2?"fixed_mount_compact":"fixed_mount"):"mount",offset.compose(columnFixed),x,y,z,light,buffer);
            if(!tile.isFixedMount())draw("sleeve",offset.compose(columnFixed.compose(size)),x,y,z,light,buffer);
            if(tile.isSwingArmController()){
                Affine hingeYaw=Affine.quarterTurn(tile.direction().ordinal());
                draw("swing_linkage",offset.compose(armPose.compose(Affine.stretchArm(assembly.columnHeight()-tile.footMountOffset()).compose(hingeYaw))),x,y,z,light,buffer);
                draw("swivel",offset.compose(footPose.compose(Affine.translation(0,-assembly.columnHeight()+tile.footMountOffset(),0).compose(hingeYaw))),x,y,z,light,buffer);
            }else{
                // The Large fixed wheel socket starts exactly at the solid body's
                // underside. Its fully buried rod would duplicate that visible face.
                if(!tile.isFixedMount()||pose.rodBottomY()+tile.footMountOffset()<-.0001)
                    draw("rod",offset.compose(armPose.compose(Affine.scaleY(pose.rodTopY()-pose.rodBottomY()-tile.footMountOffset(),pose.rodBottomY()+tile.footMountOffset()))),x,y,z,light,buffer);
                // Bury the caps slightly into the rod and foot instead of sharing
                // their exact horizontal planes (notably the ski crossbeam).
                draw("socket",offset.compose(footPose.compose(Affine.translation(0,-assembly.columnHeight()+tile.footMountOffset(),0).compose(Affine.scaleY(1.25,-1/16D)))),x,y,z,light,buffer);
            }
        }

        int turns = assembly.kind() != GearTopology.FootKind.PLATE ? quarterTurns(tile.footFacing()) : 0;
        Affine orientation = Affine.quarterTurn(turns);
        String prefix = assembly.kind() == GearTopology.FootKind.PLATE ? "plate_" : "ski_";
        if(assembly.kind()!=GearTopology.FootKind.NONE)for (GearTopology.Cell foot : assembly.feet()) {
            if(assembly.kind()==GearTopology.FootKind.TRACK){
                net.minecraft.util.math.BlockPos source=tile.localCell(foot);IBlockState ts=tile.getWorld().getBlockState(source);
                if(ts.getBlock() instanceof com.vandorlabs.gear.track.TrackBlock){
                    com.vandorlabs.gear.track.TrackBlock track=(com.vandorlabs.gear.track.TrackBlock)ts.getBlock();
                    com.vandorlabs.gear.track.TrackAppearance appearance=com.vandorlabs.gear.track.TrackBlock.appearance(tile.getWorld(),source);
                    for(com.vandorlabs.gear.track.TrackParts.Part piece:com.vandorlabs.gear.track.TrackParts.forCell(track.style(),tile.footFacing(),appearance)){
                        draw("track/"+piece.model.getResourcePath().substring("block/track/".length()),feet.compose(Affine.translation(foot.x,foot.y,foot.z).compose(Affine.quarterTurn(piece.quarterTurns))),x,y,z,light,buffer);
                    }
                }continue;
            }
            if(assembly.kind()==GearTopology.FootKind.WHEEL){
                net.minecraft.util.math.BlockPos source=tile.localCell(foot);IBlockState ws=tile.getWorld().getBlockState(source);if(!(ws.getBlock() instanceof GearBlock)||((GearBlock)ws.getBlock()).kind()!=GearBlock.Kind.WHEEL)continue;GearBlock wheel=(GearBlock)ws.getBlock();if(!wheel.componentRoot(source,ws).equals(source))continue;
                Affine transform=orientation;com.vandorlabs.vehicle.EntityGroundVehicle craft=tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).craft:null;
                Affine at=feet.compose(Affine.translation(foot.x,foot.y,foot.z));
                if(craft!=null)transform=transform.compose(Affine.yaw(.5,com.vandorlabs.client.VehicleWheelMotion.steer(craft)));
                draw(wheel.wheelModel()+"_bracket",at.compose(transform),x,y,z,light,buffer);
                double center=.5,connectorY=wheel.width()==2?13/16D:9.375/13D;
                GearMotion.Vec pivot=orientationPoint(orientation,center,connectorY,center);
                Affine connector=connectorPose(pose,foot.x+pivot.x,foot.y+pivot.y,foot.z+pivot.z);
                draw(wheel.wheelModel()+"_connector",connector.compose(Affine.translation(foot.x,foot.y,foot.z).compose(transform)),x,y,z,light,buffer);
                if(craft!=null){double distance=com.vandorlabs.client.VehicleWheelMotion.sample(craft,partialTicks);double angle=com.vandorlabs.client.VehicleWheelMotion.roll(distance,wheel.width()==2?14/16D:4.5/13,com.vandorlabs.client.VehicleWheelMotion.alignment(craft.structure.facing,tile.footFacing()));transform=transform.compose(Affine.roll(.5,wheel.width()==2?-2/16D:4.5/13,angle));}
                draw(wheel.wheelModel()+"_tyre",at.compose(transform),x,y,z,light,buffer);continue;
            }
            // Connectivity is measured in world directions. Convert that
            // mask before rotating each mesh inside its own block cell.
            int localMask = localMask(assembly.connectionMask(foot), turns);
            Affine atFoot = feet.compose(Affine.translation(foot.x, foot.y, foot.z).compose(orientation));
            draw(prefix + localMask, atFoot, x, y, z, light, buffer);
        }
    }

    private static GearMotion.Vec orientationPoint(Affine t,double x,double y,double z){return new GearMotion.Vec(t.xx*x+t.xy*y+t.xz*z+t.tx,t.yx*x+t.yy*y+t.yz*z+t.ty,t.zx*x+t.zy*y+t.zz*z+t.tz);}
    private static Affine connectorPose(GearMotion.Pose p,double cx,double cy,double cz){
        GearMotion.Vec o=p.transformConnectorVertex(0,0,0,cx,cy,cz),x=p.transformConnectorVertex(1,0,0,cx,cy,cz),y=p.transformConnectorVertex(0,1,0,cx,cy,cz),z=p.transformConnectorVertex(0,0,1,cx,cy,cz);
        return new Affine(x.x-o.x,y.x-o.x,z.x-o.x,x.y-o.y,y.y-o.y,z.y-o.y,x.z-o.z,y.z-o.z,z.z-o.z,o.x,o.y,o.z);
    }

    private void draw(String name,Affine transform,double x,double y,double z,int light,BufferBuilder buffer){
        GearModels.draw(name,mounting.compose(transform),x,y,z,light,buffer,cachePose);
    }

    static int quarterTurns(EnumFacing facing) {
        switch (facing) {
            case EAST: return 1;
            case SOUTH: return 2;
            case WEST: return 3;
            default: return 0;
        }
    }

    static int localMask(int worldMask, int turns) {
        int result = 0;
        for (int local = 0; local < 4; local++) {
            if ((worldMask & 1 << ((local + turns) & 3)) != 0) result |= 1 << local;
        }
        return result;
    }

    /** Small affine transform, avoiding a Vec allocation for every vertex. */
    static final class Affine {
        final double xx, xy, xz, yx, yy, yz, zx, zy, zz, tx, ty, tz;

        Affine(double xx, double xy, double xz, double yx, double yy, double yz,
               double zx, double zy, double zz, double tx, double ty, double tz) {
            this.xx = xx; this.xy = xy; this.xz = xz;
            this.yx = yx; this.yy = yy; this.yz = yz;
            this.zx = zx; this.zy = zy; this.zz = zz;
            this.tx = tx; this.ty = ty; this.tz = tz;
        }

        @Override public int hashCode(){int h=1;h=31*h+Double.hashCode(xx);h=31*h+Double.hashCode(xy);h=31*h+Double.hashCode(xz);h=31*h+Double.hashCode(yx);h=31*h+Double.hashCode(yy);h=31*h+Double.hashCode(yz);h=31*h+Double.hashCode(zx);h=31*h+Double.hashCode(zy);h=31*h+Double.hashCode(zz);h=31*h+Double.hashCode(tx);h=31*h+Double.hashCode(ty);return 31*h+Double.hashCode(tz);}
        @Override public boolean equals(Object other){if(!(other instanceof Affine))return false;Affine a=(Affine)other;return Double.compare(xx,a.xx)==0&&Double.compare(xy,a.xy)==0&&Double.compare(xz,a.xz)==0&&Double.compare(yx,a.yx)==0&&Double.compare(yy,a.yy)==0&&Double.compare(yz,a.yz)==0&&Double.compare(zx,a.zx)==0&&Double.compare(zy,a.zy)==0&&Double.compare(zz,a.zz)==0&&Double.compare(tx,a.tx)==0&&Double.compare(ty,a.ty)==0&&Double.compare(tz,a.tz)==0;}

        static Affine identity() { return translation(0, 0, 0); }

        static Affine translation(double x, double y, double z) {
            return new Affine(1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z);
        }

        static Affine mount(EnumFacing face){
            net.minecraft.util.math.Vec3d o=com.vandorlabs.gear.ArmOrientation.point(face,0,0,0),a=com.vandorlabs.gear.ArmOrientation.vector(face,1,0,0),b=com.vandorlabs.gear.ArmOrientation.vector(face,0,1,0),c=com.vandorlabs.gear.ArmOrientation.vector(face,0,0,1);
            return new Affine(a.x,b.x,c.x,a.y,b.y,c.y,a.z,b.z,c.z,o.x,o.y,o.z);
        }
        static Affine scaleXZ(double scale){return scaleXZ(scale,scale);}
        static Affine scaleXZ(double sx,double sz){return new Affine(sx,0,0,0,1,0,0,0,sz,0,0,0);}
        static Affine aroundCenter(double width,Affine transform){double shift=(width-1)*.5;return translation(shift,0,shift).compose(transform.compose(translation(-shift,0,-shift)));}
        static Affine stretchArm(double height){double factor=(height+.25)/1.25;return scaleY(factor,GearMotion.PIVOT_Y*(1-factor));}
        static Affine yaw(double center,double angle){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));return new Affine(c,0,-s,0,1,0,s,0,c,center-c*center+s*center,0,center-s*center-c*center);}
        static Affine roll(double center,double y,double angle){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));return new Affine(1,0,0,0,c,-s,0,s,c,0,y-c*y+s*center,center-s*y-c*center);}
        static Affine scaleY(double scale, double offset) {
            return new Affine(1, 0, 0, 0, scale, 0, 0, 0, 1, 0, offset, 0);
        }

        static Affine quarterTurn(int turns) {
            int turn = turns & 3;
            double cosine = turn == 0 ? 1 : turn == 2 ? -1 : 0;
            double sine = turn == 1 ? 1 : turn == 3 ? -1 : 0;
            return new Affine(cosine, 0, -sine, 0, 1, 0, sine, 0, cosine,
                    0.5 - cosine * 0.5 + sine * 0.5, 0, 0.5 - sine * 0.5 - cosine * 0.5);
        }

        static Affine fromPose(GearMotion.Pose pose, boolean feet) {
            GearMotion.Vec origin = transform(pose, feet, 0, 0, 0);
            GearMotion.Vec vx = transform(pose, feet, 1, 0, 0);
            GearMotion.Vec vy = transform(pose, feet, 0, 1, 0);
            GearMotion.Vec vz = transform(pose, feet, 0, 0, 1);
            return new Affine(vx.x - origin.x, vy.x - origin.x, vz.x - origin.x,
                    vx.y - origin.y, vy.y - origin.y, vz.y - origin.y,
                    vx.z - origin.z, vy.z - origin.z, vz.z - origin.z, origin.x, origin.y, origin.z);
        }

        private static GearMotion.Vec transform(GearMotion.Pose pose, boolean feet, double x, double y, double z) {
            return feet ? pose.transformFootVertex(x, y, z) : pose.transformRigidVertex(x, y, z);
        }

        /** this(child(vertex)) */
        Affine compose(Affine child) {
            return new Affine(
                    xx * child.xx + xy * child.yx + xz * child.zx,
                    xx * child.xy + xy * child.yy + xz * child.zy,
                    xx * child.xz + xy * child.yz + xz * child.zz,
                    yx * child.xx + yy * child.yx + yz * child.zx,
                    yx * child.xy + yy * child.yy + yz * child.zy,
                    yx * child.xz + yy * child.yz + yz * child.zz,
                    zx * child.xx + zy * child.yx + zz * child.zx,
                    zx * child.xy + zy * child.yy + zz * child.zy,
                    zx * child.xz + zy * child.yz + zz * child.zz,
                    xx * child.tx + xy * child.ty + xz * child.tz + tx,
                    yx * child.tx + yy * child.ty + yz * child.tz + ty,
                    zx * child.tx + zy * child.ty + zz * child.tz + tz);
        }
    }
}
