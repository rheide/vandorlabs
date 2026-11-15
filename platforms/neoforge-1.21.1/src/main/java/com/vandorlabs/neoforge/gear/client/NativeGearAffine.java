package com.vandorlabs.neoforge.gear.client;
import com.vandorlabs.gear.core.GearMotion;
public final class NativeGearAffine {
        final double xx, xy, xz, yx, yy, yz, zx, zy, zz, tx, ty, tz;

        NativeGearAffine(double xx, double xy, double xz, double yx, double yy, double yz,
               double zx, double zy, double zz, double tx, double ty, double tz) {
            this.xx = xx; this.xy = xy; this.xz = xz;
            this.yx = yx; this.yy = yy; this.yz = yz;
            this.zx = zx; this.zy = zy; this.zz = zz;
            this.tx = tx; this.ty = ty; this.tz = tz;
        }

        @Override public int hashCode(){int h=1;h=31*h+Double.hashCode(xx);h=31*h+Double.hashCode(xy);h=31*h+Double.hashCode(xz);h=31*h+Double.hashCode(yx);h=31*h+Double.hashCode(yy);h=31*h+Double.hashCode(yz);h=31*h+Double.hashCode(zx);h=31*h+Double.hashCode(zy);h=31*h+Double.hashCode(zz);h=31*h+Double.hashCode(tx);h=31*h+Double.hashCode(ty);return 31*h+Double.hashCode(tz);}
        @Override public boolean equals(Object other){if(!(other instanceof NativeGearAffine))return false;NativeGearAffine a=(NativeGearAffine)other;return Double.compare(xx,a.xx)==0&&Double.compare(xy,a.xy)==0&&Double.compare(xz,a.xz)==0&&Double.compare(yx,a.yx)==0&&Double.compare(yy,a.yy)==0&&Double.compare(yz,a.yz)==0&&Double.compare(zx,a.zx)==0&&Double.compare(zy,a.zy)==0&&Double.compare(zz,a.zz)==0&&Double.compare(tx,a.tx)==0&&Double.compare(ty,a.ty)==0&&Double.compare(tz,a.tz)==0;}

        static NativeGearAffine identity() { return translation(0, 0, 0); }

        static NativeGearAffine translation(double x, double y, double z) {
            return new NativeGearAffine(1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z);
        }

        static NativeGearAffine mount(net.minecraft.core.Direction face){
            net.minecraft.world.phys.Vec3 o=com.vandorlabs.neoforge.gear.NativeGearOrientation.point(face,0,0,0),a=com.vandorlabs.neoforge.gear.NativeGearOrientation.vector(face,1,0,0),b=com.vandorlabs.neoforge.gear.NativeGearOrientation.vector(face,0,1,0),c=com.vandorlabs.neoforge.gear.NativeGearOrientation.vector(face,0,0,1);
            return new NativeGearAffine(a.x,b.x,c.x,a.y,b.y,c.y,a.z,b.z,c.z,o.x,o.y,o.z);
        }
        static NativeGearAffine scaleXZ(double scale){return scaleXZ(scale,scale);}
        static NativeGearAffine scaleXZ(double sx,double sz){return new NativeGearAffine(sx,0,0,0,1,0,0,0,sz,0,0,0);}
        static NativeGearAffine aroundCenter(double width,NativeGearAffine transform){double shift=(width-1)*.5;return translation(shift,0,shift).compose(transform.compose(translation(-shift,0,-shift)));}
        static NativeGearAffine stretchArm(double height){double factor=(height+.25)/1.25;return scaleY(factor,GearMotion.PIVOT_Y*(1-factor));}
        static NativeGearAffine yaw(double center,double angle){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));return new NativeGearAffine(c,0,-s,0,1,0,s,0,c,center-c*center+s*center,0,center-s*center-c*center);}
        static NativeGearAffine roll(double center,double y,double angle){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));return new NativeGearAffine(1,0,0,0,c,-s,0,s,c,0,y-c*y+s*center,center-s*y-c*center);}
        static NativeGearAffine scaleY(double scale, double offset) {
            return new NativeGearAffine(1, 0, 0, 0, scale, 0, 0, 0, 1, 0, offset, 0);
        }

        static NativeGearAffine quarterTurn(int turns) {
            int turn = turns & 3;
            double cosine = turn == 0 ? 1 : turn == 2 ? -1 : 0;
            double sine = turn == 1 ? 1 : turn == 3 ? -1 : 0;
            return new NativeGearAffine(cosine, 0, -sine, 0, 1, 0, sine, 0, cosine,
                    0.5 - cosine * 0.5 + sine * 0.5, 0, 0.5 - sine * 0.5 - cosine * 0.5);
        }

        static NativeGearAffine fromUpperPose(GearMotion.Pose pose){
            var o=pose.transformUpperLinkVertex(0,0,0);var x=pose.transformUpperLinkVertex(1,0,0);var y=pose.transformUpperLinkVertex(0,1,0);var z=pose.transformUpperLinkVertex(0,0,1);
            return new NativeGearAffine(x.x-o.x,y.x-o.x,z.x-o.x,x.y-o.y,y.y-o.y,z.y-o.y,x.z-o.z,y.z-o.z,z.z-o.z,o.x,o.y,o.z);
        }
        static NativeGearAffine fromPose(GearMotion.Pose pose, boolean feet) {
            GearMotion.Vec origin = transform(pose, feet, 0, 0, 0);
            GearMotion.Vec vx = transform(pose, feet, 1, 0, 0);
            GearMotion.Vec vy = transform(pose, feet, 0, 1, 0);
            GearMotion.Vec vz = transform(pose, feet, 0, 0, 1);
            return new NativeGearAffine(vx.x - origin.x, vy.x - origin.x, vz.x - origin.x,
                    vx.y - origin.y, vy.y - origin.y, vz.y - origin.y,
                    vx.z - origin.z, vy.z - origin.z, vz.z - origin.z, origin.x, origin.y, origin.z);
        }

        private static GearMotion.Vec transform(GearMotion.Pose pose, boolean feet, double x, double y, double z) {
            return feet ? pose.transformFootVertex(x, y, z) : pose.transformRigidVertex(x, y, z);
        }

        /** this(child(vertex)) */
        NativeGearAffine compose(NativeGearAffine child) {
            return new NativeGearAffine(
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
