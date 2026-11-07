package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Read-only, sliced flat-area/volume search. The transfer must revalidate its result before writing. */
final class VehicleSafeParkSearch {
    interface Access {
        boolean loaded(BlockPos minimum,BlockPos maximum);
        boolean floor(BlockPos position);
        boolean place(BlockPos position);
        boolean clear(AxisAlignedBB volume);
    }
    private final Access access;private final SafeParkCandidates candidates;private final List<BlockPos> targets,columns;private final List<AxisAlignedBB> boxes;
    private final int minimumX,minimumZ,maximumX,maximumZ,worldHeight,floorOffset;
    private SafeParkCandidates.Candidate candidate;private BlockPos origin,result;private int stage,index,boxIndex;private AxisAlignedBB box;private double sx,sy,sz;private boolean done;
    VehicleSafeParkSearch(Collection<BlockPos> targets,List<AxisAlignedBB> boxes,BlockPos preferred,int radius,int worldHeight,Access access){
        this(targets,boxes,preferred,radius,worldHeight,access,false);
    }
    private VehicleSafeParkSearch(Collection<BlockPos> targets,List<AxisAlignedBB> boxes,BlockPos preferred,int radius,int worldHeight,Access access,boolean exact){
        if(targets.isEmpty()||targets.size()>65536||boxes.isEmpty()||boxes.size()>65536||worldHeight<1||worldHeight>512)throw new IllegalArgumentException("Safe Park volume bounds");
        this.access=Objects.requireNonNull(access);this.targets=new ArrayList<>();int minimumCellY=Integer.MAX_VALUE,maximumCellY=Integer.MIN_VALUE;
        for(BlockPos target:targets){if(target==null||Math.abs((long)target.getX())>2048||Math.abs((long)target.getY())>2048||Math.abs((long)target.getZ())>2048)throw new IllegalArgumentException("Safe Park local target bounds");this.targets.add(target.toImmutable());minimumCellY=Math.min(minimumCellY,target.getY());maximumCellY=Math.max(maximumCellY,target.getY());}
        this.boxes=Collections.unmodifiableList(new ArrayList<>(boxes));this.worldHeight=worldHeight;double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;Set<BlockPos> footprint=new LinkedHashSet<>();long work=0;
        int x0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
        for(AxisAlignedBB shape:boxes){
            for(double value:new double[]{shape.minX,shape.minY,shape.minZ,shape.maxX,shape.maxY,shape.maxZ})if(!Double.isFinite(value)||Math.abs(value)>2048)throw new IllegalArgumentException("Safe Park collision bounds");
            if(shape.minX>=shape.maxX||shape.minY>=shape.maxY||shape.minZ>=shape.maxZ)throw new IllegalArgumentException("Safe Park empty collision");low=Math.min(low,shape.minY);high=Math.max(high,shape.maxY);
            int a=MathHelper.floor(shape.minX+1e-7),b=MathHelper.ceil(shape.maxX-1e-7)-1,c=MathHelper.floor(shape.minZ+1e-7),d=MathHelper.ceil(shape.maxZ-1e-7)-1;
            if((work+=(long)(b-a+1)*(d-c+1))>262144)throw new IllegalArgumentException("Safe Park footprint work budget");
            for(int x=a;x<=b;x++)for(int z=c;z<=d;z++){footprint.add(new BlockPos(x,0,z));if(footprint.size()>32768)throw new IllegalArgumentException("Safe Park footprint size");}
            x0=Math.min(x0,a);x1=Math.max(x1,b);z0=Math.min(z0,c);z1=Math.max(z1,d);
        }
        if(footprint.isEmpty())throw new IllegalArgumentException("Safe Park empty footprint");columns=new ArrayList<>(footprint);minimumX=x0-1;maximumX=x1+1;minimumZ=z0-1;maximumZ=z1+1;floorOffset=MathHelper.floor(low+1e-7);
        int minimumY=Math.max(Math.max(-minimumCellY,MathHelper.ceil(-low)),1-floorOffset),maximumY=Math.min(worldHeight-1-maximumCellY,MathHelper.floor(worldHeight-high));
        if(maximumY<minimumY)throw new IllegalArgumentException("Craft is too tall for a flat parking area");
        if(exact){if(preferred.getY()<minimumY||preferred.getY()>maximumY)throw new IllegalArgumentException("Parking origin is outside the loaded world");minimumY=maximumY=preferred.getY();radius=0;}
        candidates=new SafeParkCandidates(preferred.getX(),preferred.getY(),preferred.getZ(),radius,minimumY,maximumY);
    }
    boolean step(int budget,long deadline){
        if(budget<1||budget>4096)throw new IllegalArgumentException("Safe Park slice budget");
        for(int work=0;!done&&work<budget&&System.nanoTime()<deadline;work++){
            if(candidate==null){candidate=candidates.next((x,z)->access.loaded(new BlockPos(x+minimumX,0,z+minimumZ),new BlockPos(x+maximumX,worldHeight-1,z+maximumZ)),1);if(candidate==null){done=candidates.done();return done;}origin=new BlockPos(candidate.x,candidate.y,candidate.z);stage=index=boxIndex=0;box=null;}
            if(stage==0){BlockPos column=columns.get(index++);if(!access.floor(origin.add(column.getX(),floorOffset-1,column.getZ()))){reject();continue;}if(index==columns.size()){stage=1;index=0;}continue;}
            if(stage==1){if(!access.place(origin.add(targets.get(index++)))){reject();continue;}if(index==targets.size()){stage=2;index=0;}continue;}
            if(box==null){if(boxIndex==boxes.size()){result=origin;done=true;continue;}box=boxes.get(boxIndex++).offset(origin).shrink(1e-6);sx=box.minX;sy=box.minY;sz=box.minZ;}
            double x=Math.min(sx+8,box.maxX),y=Math.min(sy+8,box.maxY),z=Math.min(sz+8,box.maxZ);
            if(!access.clear(new AxisAlignedBB(sx,sy,sz,x,y,z))){reject();continue;}
            sx=x;if(sx>=box.maxX){sx=box.minX;sy=y;if(sy>=box.maxY){sy=box.minY;sz=z;if(sz>=box.maxZ)box=null;}}
        }return done;
    }
    private void reject(){candidate=null;origin=null;box=null;}
    BlockPos result(){return result;}
    VehicleSafeParkSearch recheck(){if(result==null)throw new IllegalStateException("No parking result");return new VehicleSafeParkSearch(targets,boxes,result,0,worldHeight,access,true);}
    static Access access(WorldServer world,EntityGroundVehicle craft,EntityPlayerMP player){
        Set<Entity> allowed=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());allowed.add(craft);allowed.add(player);allowed.addAll(craft.getPassengers());
        return new Access(){
            private void thread(){MinecraftServer server=world.getMinecraftServer();if(server==null||!server.isCallingFromMinecraftThread())throw new IllegalStateException("Safe Park requires the server thread");}
            public boolean loaded(BlockPos min,BlockPos max){thread();return world.getWorldBorder().contains(min)&&world.getWorldBorder().contains(max)&&world.isAreaLoaded(min,max,false);}
            public boolean floor(BlockPos p){thread();if(p.getY()<0||p.getY()>=world.getHeight()||!world.isBlockLoaded(p))return false;IBlockState state=world.getBlockState(p);if(state.getMaterial().isLiquid()||!state.isSideSolid(world,p,EnumFacing.UP))return false;AxisAlignedBB b=state.getCollisionBoundingBox(world,p);return b!=null&&b.minX<=1e-7&&b.minZ<=1e-7&&b.maxX>=1-1e-7&&b.maxZ>=1-1e-7&&Math.abs(b.maxY-1)<1e-7;}
            public boolean place(BlockPos p){thread();if(player.world!=world||craft.world!=world||player.isDead||craft.isDead)return false;try{VehicleService.permission(player,p);}catch(IllegalArgumentException denied){return false;}return world.isAirBlock(p)&&world.getTileEntity(p)==null;}
            public boolean clear(AxisAlignedBB volume){thread();BlockPos min=new BlockPos(volume.minX-.01,Math.max(0,volume.minY-.01),volume.minZ-.01),max=new BlockPos(volume.maxX+.01,Math.min(world.getHeight()-1,volume.maxY+.01),volume.maxZ+.01);if(!world.isAreaLoaded(min,max,false)||!world.getCollisionBoxes(craft,volume).isEmpty())return false;return world.getEntitiesWithinAABB(Entity.class,volume.grow(.01),entity->!entity.isDead&&!allowed.contains(entity)&&(entity instanceof EntityLivingBase||entity instanceof com.vandorlabs.entity.EntityChairSeat&&!entity.getPassengers().isEmpty())).isEmpty();}
        };
    }
}
