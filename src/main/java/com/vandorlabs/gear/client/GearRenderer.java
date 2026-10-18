package com.vandorlabs.gear.client;

import com.vandorlabs.gear.GearBlock;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.gear.core.GearMotion;
import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.animation.FastTESR;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * One batched renderer at the top extender. It submits cached mesh vertices
 * into Forge's existing BLOCK buffer; it never begins/draws a buffer or
 * changes GL state for each foot.
 */
@SideOnly(Side.CLIENT)
public final class GearRenderer extends net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer<GearTileEntity> {
    @Override public void render(GearTileEntity tile,double x,double y,double z,float partial,int stage,float alpha){
        try(com.vandorlabs.client.TurntableRenderState state=new com.vandorlabs.client.TurntableRenderState()){
            bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableCull();
            BufferBuilder buffer=net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);
            renderTileEntityFast(tile,x,y,z,partial,stage,alpha,buffer);net.minecraft.client.renderer.Tessellator.getInstance().draw();
        }
    }
    public void renderTileEntityFast(GearTileEntity tile, double x, double y, double z, float partialTicks,
                                     int destroyStage, float partial, BufferBuilder buffer) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof GearBlock) || !state.getValue(GearBlock.ASSEMBLED)&&((GearBlock)state.getBlock()).width()==1) return;
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
            String model = kind == GearBlock.Kind.EXTENDER ? "extender"
                    : kind == GearBlock.Kind.SWING_ARM ? "swing_arm"
                    : kind == GearBlock.Kind.PLATE ? "plate_0" : kind==GearBlock.Kind.WHEEL?part.wheelModel():"ski_0";
            if(part.width()==2&&kind==GearBlock.Kind.SWING_ARM){Affine yaw=Affine.quarterTurn(quarterTurns(state.getValue(GearBlock.FACING))),fixedSize=Affine.scaleXZ(2),barSize=Affine.translation(1,0,1).compose(Affine.scaleXZ(2,1).compose(Affine.translation(-.5,0,-.5)));GearModels.draw("mount",yaw.compose(fixedSize),x,y,z,light,buffer);GearModels.draw("sleeve",yaw.compose(fixedSize),x,y,z,light,buffer);GearModels.draw("swing_linkage",yaw.compose(barSize),x,y,z,light,buffer);GearModels.draw("swivel",yaw.compose(fixedSize.compose(Affine.translation(0,-1,0))),x,y,z,light,buffer);return;}
            GearModels.draw(model, kind==GearBlock.Kind.WHEEL?Affine.quarterTurn(quarterTurns(state.getValue(GearBlock.FACING))):Affine.quarterTurn(quarterTurns(state.getValue(GearBlock.FACING))).compose(Affine.scaleXZ(part.width())),
                    x, y, z, light, buffer);
            return;
        }

        GearMotion.Pose pose = GearMotion.pose(tile.mode(), tile.direction(), assembly.columnHeight(),
                tile.renderProgress(partialTicks));
        Affine fixed = Affine.quarterTurn(quarterTurns(state.getValue(GearBlock.FACING)));
        Affine moving = Affine.fromPose(pose, false);
        Affine feet = Affine.fromPose(pose, true);

        for(net.minecraft.util.math.BlockPos column:tile.columns()){
            int width=tile.armWidth();double shift=(width-1)*.5;
            Affine offset=Affine.translation(column.getX(),column.getY(),column.getZ());
            Affine size=Affine.translation(-shift,0,-shift).compose(Affine.scaleXZ(width));
            Affine barSize=tile.isSwingArmController()?Affine.scaleXZ(tile.direction().axisX()!=0?width:1,tile.direction().axisZ()!=0?width:1):Affine.scaleXZ(width);
            barSize=Affine.translation(.5,0,.5).compose(barSize.compose(Affine.translation(-.5,0,-.5)));
            Affine armPose=Affine.translation(tile.armShiftX(),0,tile.armShiftZ()).compose(moving.compose(barSize));
            Affine footPose=Affine.translation(tile.armShiftX(),0,tile.armShiftZ()).compose(feet.compose(size));
            GearModels.draw("mount",offset.compose(fixed.compose(Affine.scaleXZ(width))),x,y,z,light,buffer);
            GearModels.draw("sleeve",offset.compose(fixed.compose(Affine.scaleXZ(width))),x,y,z,light,buffer);
            if(tile.isSwingArmController()){
                Affine hingeYaw=Affine.quarterTurn(tile.direction().ordinal());
                GearModels.draw("swing_linkage",offset.compose(armPose.compose(Affine.stretchArm(assembly.columnHeight()).compose(hingeYaw))),x,y,z,light,buffer);
                GearModels.draw("swivel",offset.compose(footPose.compose(Affine.translation(0,-assembly.columnHeight(),0).compose(hingeYaw))),x,y,z,light,buffer);
            }else{
                GearModels.draw("rod",offset.compose(armPose.compose(Affine.scaleY(pose.rodTopY()-pose.rodBottomY(),pose.rodBottomY()))),x,y,z,light,buffer);
                GearModels.draw("socket",offset.compose(footPose.compose(Affine.translation(0,-assembly.columnHeight(),0))),x,y,z,light,buffer);
            }
        }

        int turns = assembly.kind() != GearTopology.FootKind.PLATE ? quarterTurns(tile.footFacing()) : 0;
        Affine orientation = Affine.quarterTurn(turns);
        String prefix = assembly.kind() == GearTopology.FootKind.PLATE ? "plate_" : "ski_";
        for (GearTopology.Cell foot : assembly.feet()) {
            if(assembly.kind()==GearTopology.FootKind.WHEEL){
                net.minecraft.util.math.BlockPos source=tile.getPos().add(foot.x,foot.y,foot.z);IBlockState ws=tile.getWorld().getBlockState(source);GearBlock wheel=(GearBlock)ws.getBlock();if(!wheel.componentRoot(source,ws).equals(source))continue;
                Affine transform=orientation;com.vandorlabs.vehicle.EntityGroundVehicle craft=tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).craft:null;
                Affine at=feet.compose(Affine.translation(foot.x,foot.y,foot.z));
                if(craft!=null)transform=transform.compose(Affine.yaw(wheel.width()*.5,com.vandorlabs.client.VehicleWheelMotion.steer(craft)));
                GearModels.draw(wheel.wheelModel()+"_bracket",at.compose(transform),x,y,z,light,buffer);
                if(craft!=null){double distance=com.vandorlabs.client.VehicleWheelMotion.sample(craft,partialTicks);double angle=com.vandorlabs.client.VehicleWheelMotion.roll(distance,wheel.width()==2?.35:4.5/13,com.vandorlabs.client.VehicleWheelMotion.alignment(craft.structure.facing,tile.footFacing()));transform=transform.compose(Affine.roll(wheel.width()*.5,wheel.width()==2?.35:4.5/13,angle));}
                GearModels.draw(wheel.wheelModel()+"_tyre",at.compose(transform),x,y,z,light,buffer);continue;
            }
            // Connectivity is measured in world directions. Convert that
            // mask before rotating each mesh inside its own block cell.
            int localMask = localMask(assembly.connectionMask(foot), turns);
            Affine atFoot = feet.compose(Affine.translation(foot.x, foot.y, foot.z).compose(orientation));
            GearModels.draw(prefix + localMask, atFoot, x, y, z, light, buffer);
        }
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

        static Affine identity() { return translation(0, 0, 0); }

        static Affine translation(double x, double y, double z) {
            return new Affine(1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z);
        }

        static Affine scaleXZ(double scale){return scaleXZ(scale,scale);}
        static Affine scaleXZ(double sx,double sz){return new Affine(sx,0,0,0,1,0,0,0,sz,0,0,0);}
        static Affine aroundCenter(double width,Affine transform){double shift=(width-1)*.5;return translation(shift,0,shift).compose(transform.compose(translation(-shift,0,-shift)));}
        static Affine stretchArm(int height){double factor=(height+.25)/1.25;return scaleY(factor,GearMotion.PIVOT_Y*(1-factor));}
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
