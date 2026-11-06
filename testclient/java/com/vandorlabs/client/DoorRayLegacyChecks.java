package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import com.google.gson.*;
/** Calls the released block entry point, including its pad priority and stale-state fallback. */
public final class DoorRayLegacyChecks {
    static void install(NonRenderingChecks.MemoryWorld world,BlockConfigurableSpaceDoor block,BlockPos pos,EnumFacing face,boolean open,boolean left){
        IBlockState base=block.getDefaultState().withProperty(BlockVandorDoor.FACING,face).withProperty(BlockVandorDoor.OPEN,open);world.setBlockState(pos,base.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER),2);world.setBlockState(pos.up(),base.withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH).withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER).withProperty(BlockVandorDoor.HINGE,left?BlockDoor.EnumHingePosition.LEFT:BlockDoor.EnumHingePosition.RIGHT),2);
    }
    static NBTTagCompound tag(boolean sliding,boolean framed,boolean hinges,int depth,int panel,int security){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("SpaceDoorSchema",2);tag.setBoolean("SpaceDoorSliding",sliding);tag.setBoolean("SpaceFramed",framed);tag.setBoolean("SpaceDoorHinges",hinges);tag.setInteger("SpaceDoorPlacementDepth",depth);tag.setBoolean("SpaceDoorPanel",panel!=0);tag.setInteger("DoorPanelMode",panel);tag.setInteger("DoorSecurityMode",security);return tag;}

    static JsonArray vec(Vec3d v){JsonArray a=new JsonArray();a.add(v.x);a.add(v.y);a.add(v.z);return a;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();
        java.util.Random random=new java.util.Random(0x1AABB);int kernel=0;
        for(int i=0;i<20000;i++){
            double x=random.nextDouble()*4-2,y=random.nextDouble()*4-2,z=random.nextDouble()*4-2;
            AxisAlignedBB box=new AxisAlignedBB(x,y,z,x+random.nextDouble()*2,y+random.nextDouble()*2,z+random.nextDouble()*2);
            Vec3d start=new Vec3d(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3),end=new Vec3d(random.nextDouble()*6-3,random.nextDouble()*6-3,random.nextDouble()*6-3);
            RayTraceResult expected=box.calculateIntercept(start,end);com.vandorlabs.render.LegacyBoxTrace.Hit actual=com.vandorlabs.render.LegacyBoxTrace.clip(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,start.x,start.y,start.z,end.x,end.y,end.z);
            if((expected==null)!=(actual==null)||expected!=null&&(actual.face!=expected.sideHit.getIndex()||Double.doubleToLongBits(actual.x)!=Double.doubleToLongBits(expected.hitVec.x)||Double.doubleToLongBits(actual.y)!=Double.doubleToLongBits(expected.hitVec.y)||Double.doubleToLongBits(actual.z)!=Double.doubleToLongBits(expected.hitVec.z)))throw new AssertionError("Scalar kernel differs from Forge case="+i);kernel++;
        }
        System.out.println("Legacy scalar intercept exact Forge PASS cases="+kernel);
        BlockDetailedDoor pair=new BlockDetailedDoor("ray_pair",BlockVandorDoor.DoorMotion.HINGED,false,true,false,1,15,11.24F,90,-90,-15,15);
        BlockConfigurableSpaceDoor block=new BlockConfigurableSpaceDoor("ray_fixture",true,pair);
        java.lang.reflect.Field f=TileEntitySpaceDoor.class.getDeclaredField("RESOLVED_MODELS");f.setAccessible(true);
        java.util.concurrent.atomic.AtomicReferenceArray<BlockSpaceDoor> cache=(java.util.concurrent.atomic.AtomicReferenceArray<BlockSpaceDoor>)f.get(null);
        for(int k=0;k<4;k++)cache.set(k,new BlockSpaceDoor(TileEntitySpaceDoor.modelId(2,(k&2)!=0,(k&1)!=0),(k&2)!=0,(k&1)!=0,pair));
        java.lang.reflect.Method padMethod=BlockConfigurableSpaceDoor.class.getDeclaredMethod("controlPanelBounds",EnumFacing.class,com.vandorlabs.render.SpaceDoorControlPanel.Side.class,double.class,boolean.class,boolean.class);padMethod.setAccessible(true);
        java.io.File file=new java.io.File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();long records=0,priority=0;java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");Gson gson=new GsonBuilder().serializeNulls().create();
        try(java.io.PrintWriter out=new java.io.PrintWriter(file,"UTF-8")){
        for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean sliding:new boolean[]{false,true})for(boolean open:new boolean[]{false,true})for(boolean left:new boolean[]{false,true})for(boolean paired:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true})for(boolean hinges:new boolean[]{false,true})for(int depth=0;depth<3;depth++)for(int mode=0;mode<3;mode++)for(boolean security:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);install(world,block,BlockPos.ORIGIN,face,open,left);if(paired)install(world,block,BlockPos.ORIGIN.offset(left?face.rotateY():face.rotateYCCW()),face,open,!left);TileEntitySpaceDoor tile=(TileEntitySpaceDoor)world.getTileEntity(BlockPos.ORIGIN);tile.readFromNBT(tag(sliding,framed,hinges,depth,mode,security?1:0));
            for(boolean upper:new boolean[]{false,true})for(boolean missing:new boolean[]{false,true}){
                BlockPos pos=upper?BlockPos.ORIGIN.up():BlockPos.ORIGIN;IBlockState state=world.getBlockState(pos);IBlockState saved=world.getBlockState(BlockPos.ORIGIN);
                com.vandorlabs.render.SpaceDoorControlPanel.Side side=BlockConfigurableSpaceDoor.panelSide(world,BlockPos.ORIGIN,block.getActualState(saved,world,BlockPos.ORIGIN));
                if(missing)world.setBlockToAir(BlockPos.ORIGIN);
                IBlockState actual=block.getActualState(state,world,pos);AxisAlignedBB leaf=block.getBoundingBox(state,world,pos);
                java.util.List<Vec3d[]> rays=new java.util.ArrayList<>();
                double cx=(leaf.minX+leaf.maxX)/2,cy=(leaf.minY+leaf.maxY)/2,cz=(leaf.minZ+leaf.maxZ)/2;
                for(double y:new double[]{cy,1.125-(upper?1:0),leaf.minY,leaf.maxY}){
                    rays.add(new Vec3d[]{new Vec3d(-2,y,cz),new Vec3d(3,y,cz)});rays.add(new Vec3d[]{new Vec3d(3,y,cz),new Vec3d(-2,y,cz)});
                    rays.add(new Vec3d[]{new Vec3d(cx,y,-2),new Vec3d(cx,y,3)});rays.add(new Vec3d[]{new Vec3d(cx,y,3),new Vec3d(cx,y,-2)});
                }
                rays.add(new Vec3d[]{new Vec3d(cx,cy,cz),new Vec3d(3,cy,cz)});
                rays.add(new Vec3d[]{new Vec3d(leaf.minX,leaf.minY,leaf.minZ),new Vec3d(leaf.maxX,leaf.maxY,leaf.maxZ)});
                rays.add(new Vec3d[]{new Vec3d(cx,-2,cz),new Vec3d(cx,3,cz)});
                rays.add(new Vec3d[]{new Vec3d(leaf.minX-0.0001,cy,cz),new Vec3d(leaf.minX+0.0001,cy,cz)});
                rays.add(new Vec3d[]{new Vec3d(cx,cy,cz),new Vec3d(cx,cy,cz)});
                if(!missing&&side!=com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE){AxisAlignedBB pad=(AxisAlignedBB)padMethod.invoke(null,face,side,tile.positionOffset(),sliding,depth==2);for(int mirror=0;mirror<(tile.panelMode()==2?2:1);mirror++){AxisAlignedBB pb=(mirror==0?pad:BlockConfigurableSpaceDoor.mirrorPanel(pad,face)).offset(0,upper?-1:0,0);double px=(pb.minX+pb.maxX)/2,py=(pb.minY+pb.maxY)/2,pz=(pb.minZ+pb.maxZ)/2;for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1})rays.add(new Vec3d[]{new Vec3d(px+(axis==0?sign*3:0),py+(axis==1?sign*3:0),pz+(axis==2?sign*3:0)),new Vec3d(px-(axis==0?sign*3:0),py-(axis==1?sign*3:0),pz-(axis==2?sign*3:0))});}}
                for(Vec3d[] ray:rays){Vec3d start=ray[0].addVector(pos.getX(),pos.getY(),pos.getZ()),end=ray[1].addVector(pos.getX(),pos.getY(),pos.getZ());RayTraceResult hit=block.collisionRayTrace(state,world,pos,start,end);
                    RayTraceResult leafHit=leaf.calculateIntercept(ray[0],ray[1]);if(hit!=null&&leafHit!=null&&start.squareDistanceTo(hit.hitVec)>ray[0].squareDistanceTo(leafHit.hitVec)+1E-12)priority++;
                    JsonObject r=new JsonObject();r.addProperty("face",actual.getValue(BlockVandorDoor.FACING).getIndex());r.addProperty("paired",actual.getValue(BlockConnectingDetailedDoor.PAIRED));r.addProperty("sliding",sliding);r.addProperty("open",open);r.addProperty("left",actual.getValue(BlockVandorDoor.HINGE)==BlockDoor.EnumHingePosition.LEFT);r.addProperty("framed",framed);r.addProperty("hinges",hinges);r.addProperty("depth",depth);r.addProperty("mode",mode);r.addProperty("security",security);r.addProperty("upper",upper);r.addProperty("missing",missing);r.addProperty("side",side.name());r.add("start",vec(ray[0]));r.add("end",vec(ray[1]));
                    r.add("hit",hit==null?JsonNull.INSTANCE:vec(hit.hitVec.subtract(pos.getX(),pos.getY(),pos.getZ())));r.addProperty("hitFace",hit==null?-1:hit.sideHit.getIndex());
                    String line=gson.toJson(r);out.println(line);hash.update(line.getBytes("UTF-8"));records++;
                }
                if(missing){world.setBlockState(BlockPos.ORIGIN,saved,2);((TileEntitySpaceDoor)world.getTileEntity(BlockPos.ORIGIN)).readFromNBT(tag(sliding,framed,hinges,depth,mode,security?1:0));}
            }
        }}if(records!=419328||priority!=384)throw new AssertionError("Incomplete trace matrix "+records+" farther priority="+priority);StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02x",b&255));if(!digest.toString().equals("4c7f1a7ff9f4cfc90ec908baf3ee40e5982290999eded8a71db6dc081c95884a"))throw new AssertionError("Released ray behavior changed "+digest);System.out.println("Door rays actual Forge PASS records="+records+" fartherPadPriority="+priority+" SHA256="+digest);
    }
}
