package com.vandorlabs.blocks;

import com.google.gson.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.*;
import java.util.*;

/** Gear collision uses the same cuboids as its rendered models. */
public class BlockLandingGear extends BlockVandorDirectional {
    protected final List<AxisAlignedBB> parts = new ArrayList<>();
    public BlockLandingGear(String name) {
        this(name,true);
    }
    protected BlockLandingGear(String name,boolean loadModel) {
        super(name);
        setLightOpacity(0);
        if(loadModel)parts.addAll(loadParts("block/" + name + "_small_retracted"));
    }
    protected static List<AxisAlignedBB> loadParts(String model) {return loadParts(model,1);}
    protected static List<AxisAlignedBB> loadParts(String model,int slices) {
        List<AxisAlignedBB> result = new ArrayList<>();
        try {
            for (JsonElement raw : modelElements("vandorlabs:"+model,0)) {
                JsonObject e = raw.getAsJsonObject(); JsonArray a = e.getAsJsonArray("from"), b = e.getAsJsonArray("to");
                AxisAlignedBB bounds=new AxisAlignedBB(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16,
                        b.get(0).getAsDouble()/16,b.get(1).getAsDouble()/16,b.get(2).getAsDouble()/16);
                if(e.has("rotation")){
                    int count=Math.max(1,slices);double step=(bounds.maxY-bounds.minY)/count;
                    for(int n=0;n<count;n++)result.add(rotatedBounds(new AxisAlignedBB(bounds.minX,bounds.minY+n*step,bounds.minZ,bounds.maxX,bounds.minY+(n+1)*step,bounds.maxZ),e.getAsJsonObject("rotation")));
                }else result.add(bounds);
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load gear geometry " + model, e); }
        return result;
    }
    private static AxisAlignedBB rotatedBounds(AxisAlignedBB box,JsonObject rotation){
        JsonArray origin=rotation.getAsJsonArray("origin");double ox=origin.get(0).getAsDouble()/16,oy=origin.get(1).getAsDouble()/16,oz=origin.get(2).getAsDouble()/16;
        double angle=Math.toRadians(rotation.get("angle").getAsDouble()),c=Math.cos(angle),s=Math.sin(angle);String axis=rotation.get("axis").getAsString();
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(double px:new double[]{box.minX,box.maxX})for(double py:new double[]{box.minY,box.maxY})for(double pz:new double[]{box.minZ,box.maxZ}){
            double x=px-ox,y=py-oy,z=pz-oz,rx=x,ry=y,rz=z;
            if("x".equals(axis)){ry=y*c-z*s;rz=y*s+z*c;}else if("y".equals(axis)){rx=x*c+z*s;rz=-x*s+z*c;}else{rx=x*c-y*s;ry=x*s+y*c;}
            rx+=ox;ry+=oy;rz+=oz;minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minY=Math.min(minY,ry);maxY=Math.max(maxY,ry);minZ=Math.min(minZ,rz);maxZ=Math.max(maxZ,rz);
        }return new AxisAlignedBB(minX,minY,minZ,maxX,maxY,maxZ);
    }
    private static JsonArray modelElements(String model,int depth)throws IOException {
        if(depth>16)throw new IOException("Cyclic gear model inheritance: "+model);
        String[] name=model.split(":",2);String domain=name.length==2?name[0]:"minecraft",path=name.length==2?name[1]:name[0];
        InputStream stream=BlockLandingGear.class.getResourceAsStream("/assets/"+domain+"/models/"+path+".json");
        if(stream==null)throw new IOException("Missing gear model "+model);
        try(Reader reader=new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)){
            JsonObject root=new JsonParser().parse(reader).getAsJsonObject();
            if(root.has("elements"))return root.getAsJsonArray("elements");
            return root.has("parent")?modelElements(root.get("parent").getAsString(),depth+1):new JsonArray();
        }
    }
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing facing = placer.getHorizontalFacing().getOpposite();
        if (getRegistryName().getResourcePath().contains("_side_") && side.getAxis().isHorizontal())
            facing = side.rotateYCCW(); // canonical attachment is west; exposed normal is east
        return getDefaultState().withProperty(FACING, facing);
    }
    public boolean isOpaqueCube(IBlockState state) { return false; }
    public boolean isFullCube(IBlockState state) { return false; }
    protected List<AxisAlignedBB> boxes(IBlockState state, IBlockAccess world, BlockPos pos) { return parts; }
    /** Absolute support plane, before clipping multi-cell geometry to individual cells. */
    public double vehicleBottomY(IBlockAccess world, BlockPos pos) {
        double bottom=Double.POSITIVE_INFINITY;
        for(AxisAlignedBB part:parts)bottom=Math.min(bottom,part.minY);
        return pos.getY()+bottom;
    }
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB box = null;
        for (AxisAlignedBB part : boxes(state,world,pos)) box = box == null ? part : box.union(part);
        return PanelPlacement.rotateFromNorth(box == null ? FULL_BLOCK_AABB : box, state.getValue(FACING));
    }
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
            AxisAlignedBB entityBox, List<AxisAlignedBB> boxes, Entity entity, boolean actual) {
        for (AxisAlignedBB part : boxes(state,world,pos))
            addCollisionBoxToList(pos,entityBox,boxes,PanelPlacement.rotateFromNorth(part,state.getValue(FACING)));
    }
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        RayTraceResult nearest = null;
        for (AxisAlignedBB part : boxes(state,world,pos)) {
            RayTraceResult hit = rayTrace(pos,start,end,PanelPlacement.rotateFromNorth(part,state.getValue(FACING)));
            if (hit != null && (nearest == null || start.squareDistanceTo(hit.hitVec) < start.squareDistanceTo(nearest.hitVec))) nearest=hit;
        }
        return nearest;
    }
}
