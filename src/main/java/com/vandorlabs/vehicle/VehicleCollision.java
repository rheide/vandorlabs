package com.vandorlabs.vehicle;

import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Cached local collision boxes, coalesced along each axis to reduce hull cost. */
public final class VehicleCollision {
    public final List<AxisAlignedBB> boxes;
    public final AxisAlignedBB bounds;
    public VehicleCollision(VehicleWorld view) {
        pivotX=view.structure.bounds.maxX/2; pivotZ=view.structure.bounds.maxZ/2;
        List<AxisAlignedBB> all=new ArrayList<>();
        AxisAlignedBB query=view.structure.bounds.grow(16);
        for(VehicleStructure.Cell c:view.structure.cells) {
            List<AxisAlignedBB> component=new ArrayList<>();
            try {c.state.addCollisionBoxToList(view,c.pos,query,component,null,false);all.addAll(component);}
            catch(RuntimeException | LinkageError error){
                VehicleCompatibility.warn(c.state.getBlock(),"collision",error);
                all.add(new AxisAlignedBB(c.pos));
            }
        }
        if(all.isEmpty())throw new IllegalArgumentException("The craft has no collision geometry.");
        // Equal adjacent full faces merge without filling empty interiors.
        for(int axis=0;axis<3;axis++)all=merge(all,axis);
        if(all.size()>8192)throw new IllegalArgumentException("Craft collision geometry exceeds 8192 boxes.");
        boxes=Collections.unmodifiableList(all);
        AxisAlignedBB bound=all.get(0);for(AxisAlignedBB box:all)bound=bound.union(box);bounds=bound;
    }
    private static List<AxisAlignedBB> merge(List<AxisAlignedBB> input,int axis) {
        Map<String,List<AxisAlignedBB>> groups=new LinkedHashMap<>();
        for(AxisAlignedBB b:input){String key=axis==0?b.minY+":"+b.maxY+":"+b.minZ+":"+b.maxZ:axis==1?b.minX+":"+b.maxX+":"+b.minZ+":"+b.maxZ:b.minX+":"+b.maxX+":"+b.minY+":"+b.maxY;groups.computeIfAbsent(key,k->new ArrayList<>()).add(b);}
        List<AxisAlignedBB> output=new ArrayList<>();
        for(List<AxisAlignedBB> group:groups.values()) {
            group.sort(Comparator.comparingDouble(b->axis==0?b.minX:axis==1?b.minY:b.minZ));
            AxisAlignedBB current=null;
            for(AxisAlignedBB b:group) {
                double start=axis==0?b.minX:axis==1?b.minY:b.minZ, end=current==null?Double.NEGATIVE_INFINITY:axis==0?current.maxX:axis==1?current.maxY:current.maxZ;
                if(current!=null && start<=end+1e-8)current=current.union(b);
                else {if(current!=null)output.add(current);current=b;}
            }
            if(current!=null)output.add(current);
        }
        return output;
    }
    private float posedYaw=Float.NaN;
    private List<OrientedBox> posed;
    private AxisAlignedBB posedBounds;
    private double pivotX,pivotZ;
    public List<OrientedBox> pose(float yaw) {
        if(posed!=null && yaw==posedYaw)return posed;
        posedYaw=yaw;posed=new ArrayList<>();posedBounds=null;
        double radians=Math.toRadians(yaw),cos=Math.cos(radians),sin=Math.sin(radians);
        for(AxisAlignedBB box:boxes) {
            OrientedBox b=new OrientedBox(box,pivotX,pivotZ,cos,sin);posed.add(b);
            posedBounds=posedBounds==null?b.broad:posedBounds.union(b.broad);
        }
        return posed;
    }
    public AxisAlignedBB bounds(float yaw){pose(yaw);return posedBounds;}
    public boolean loaded(World world,double x,double y,double z,double dx,double dy,double dz){return loaded(world,x,y,z,dx,dy,dz,0);}
    public boolean loaded(World world,double x,double y,double z,double dx,double dy,double dz,float yaw) {
        AxisAlignedBB b=bounds(yaw).offset(x,y,z).expand(dx,dy,dz).grow(1);
        if(b.minY<0 || b.maxY>=world.getHeight())return false;
        if(!world.getWorldBorder().contains(new BlockPos(b.minX,b.minY,b.minZ)) || !world.getWorldBorder().contains(new BlockPos(b.maxX,b.maxY,b.maxZ)))return false;
        for(int cx=MathHelper.floor(b.minX)>>4;cx<=MathHelper.floor(b.maxX)>>4;cx++)
            for(int cz=MathHelper.floor(b.minZ)>>4;cz<=MathHelper.floor(b.maxZ)>>4;cz++)
                if(!world.isBlockLoaded(new BlockPos(cx*16,64,cz*16)))return false;
        return true;
    }
    public float turn(World world,net.minecraft.entity.Entity entity,double x,double y,double z,float from,float change) {
        if(Math.abs(change)<1e-6)return from;
        double radius=Math.hypot(bounds.maxX-pivotX,bounds.maxZ-pivotZ);
        int steps=Math.max(1,(int)Math.ceil(Math.abs(Math.toRadians(change))*radius/.12));
        float accepted=from;
        for(int i=1;i<=steps;i++) {
            float next=from+change*i/steps;
            if(!loaded(world,x,y,z,0,0,0,next))break;
            boolean blocked=false;
            for(OrientedBox box:pose(next)) {
                for(AxisAlignedBB obstacle:world.getCollisionBoxes(entity,box.broad.offset(x,y,z).shrink(1e-6)))
                    if(box.intersects(obstacle.offset(-x,-y,-z))){blocked=true;break;}
                if(blocked)break;
            }
            if(blocked)break;accepted=next;
        }
        return accepted;
    }
    public double clip(World world,net.minecraft.entity.Entity entity,double x,double y,double z,double amount,int axis){return clip(world,entity,x,y,z,amount,axis,0);}
    public double clip(World world,net.minecraft.entity.Entity entity,double x,double y,double z,double amount,int axis,float yaw) {
        if(amount==0)return 0;
        double result=amount;
        for(OrientedBox box:pose(yaw)) {
            AxisAlignedBB swept=box.broad.offset(x,y,z).expand(axis==0?amount:0,axis==1?amount:0,axis==2?amount:0);
            for(AxisAlignedBB obstacle:world.getCollisionBoxes(entity,swept))
                result=box.clip(obstacle.offset(-x,-y,-z),result,axis);
        }
        return result;
    }
    /** Horizontal OBB versus terrain AABB; four separating axes preserve rotated hollow hulls. */
    public static final class OrientedBox {
        public final AxisAlignedBB broad;
        final double cx,cz,hx,hz,cos,sin,minY,maxY;
        OrientedBox(AxisAlignedBB b,double px,double pz,double c,double s) {
            hx=(b.maxX-b.minX)/2;hz=(b.maxZ-b.minZ)/2;cos=c;sin=s;minY=b.minY;maxY=b.maxY;
            double x=(b.minX+b.maxX)/2-px,z=(b.minZ+b.maxZ)/2-pz;
            cx=px+c*x-s*z;cz=pz+s*x+c*z;
            double rx=Math.abs(c)*hx+Math.abs(s)*hz,rz=Math.abs(s)*hx+Math.abs(c)*hz;
            broad=new AxisAlignedBB(cx-rx,minY,cz-rz,cx+rx,maxY,cz+rz);
        }
        private double radius(double x,double z){return hx*Math.abs(cos*x+sin*z)+hz*Math.abs(-sin*x+cos*z);}
        private boolean horizontal(AxisAlignedBB b) {
            double bx=(b.minX+b.maxX)/2,bz=(b.minZ+b.maxZ)/2,bhx=(b.maxX-b.minX)/2,bhz=(b.maxZ-b.minZ)/2;
            double[][] axes={{1,0},{0,1},{cos,sin},{-sin,cos}};
            for(double[] a:axes)if(Math.abs((cx-bx)*a[0]+(cz-bz)*a[1])>=radius(a[0],a[1])+bhx*Math.abs(a[0])+bhz*Math.abs(a[1])-1e-7)return false;
            return true;
        }
        public boolean intersects(AxisAlignedBB b){return maxY>b.minY+1e-7 && minY<b.maxY-1e-7 && horizontal(b);}
        double clip(AxisAlignedBB b,double amount,int axis) {
            if(axis==1) {
                if(!horizontal(b))return amount;
                if(amount>0 && maxY<=b.minY+1e-7)return Math.min(amount,Math.max(0,b.minY-maxY));
                if(amount<0 && minY>=b.maxY-1e-7)return Math.max(amount,Math.min(0,b.maxY-minY));
                return amount;
            }
            if(maxY<=b.minY+1e-7 || minY>=b.maxY-1e-7)return amount;
            double bx=(b.minX+b.maxX)/2,bz=(b.minZ+b.maxZ)/2,bhx=(b.maxX-b.minX)/2,bhz=(b.maxZ-b.minZ)/2;
            double enter=Double.NEGATIVE_INFINITY,exit=Double.POSITIVE_INFINITY;
            double[][] axes={{1,0},{0,1},{cos,sin},{-sin,cos}};
            for(double[] a:axes) {
                double centre=(cx-bx)*a[0]+(cz-bz)*a[1],extent=radius(a[0],a[1])+bhx*Math.abs(a[0])+bhz*Math.abs(a[1]);
                double velocity=amount*(axis==0?a[0]:a[1]);
                if(Math.abs(velocity)<1e-12){if(Math.abs(centre)>=extent-1e-7)return amount;continue;}
                double t1=(-extent-centre)/velocity,t2=(extent-centre)/velocity;
                enter=Math.max(enter,Math.min(t1,t2));exit=Math.min(exit,Math.max(t1,t2));
                if(enter>exit)return amount;
            }
            if(exit<=1e-8 || enter>1 || enter< -1e-6)return amount;
            return amount*Math.max(0,enter-1e-7/Math.max(1e-6,Math.abs(amount)));
        }
    }
}
