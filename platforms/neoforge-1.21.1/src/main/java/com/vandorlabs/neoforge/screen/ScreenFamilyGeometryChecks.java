package com.vandorlabs.neoforge.screen;

import com.vandorlabs.render.ScreenFamilyPolicy.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Installed-state comparisons against legacy housing dimensions, including tile-only changes. */
public final class ScreenFamilyGeometryChecks {
    private static final BlockPos POS=new BlockPos(96,80,8);
    private ScreenFamilyGeometryChecks(){}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Modern screen geometry: "+message);}
    private static void bounds(AABB actual,AABB expected,String message){
        require(Math.abs(actual.minX-expected.minX)<1e-8&&Math.abs(actual.minY-expected.minY)<1e-8&&Math.abs(actual.minZ-expected.minZ)<1e-8
            &&Math.abs(actual.maxX-expected.maxX)<1e-8&&Math.abs(actual.maxY-expected.maxY)<1e-8&&Math.abs(actual.maxZ-expected.maxZ)<1e-8,message+" actual="+actual+" expected="+expected);
    }
    // Legacy PanelPlacement rotates horizontal housings about the cell centre; vertical faces retain the authored axes.
    private static AABB rotate(AABB b,Direction face){return switch(face){
        case EAST->new AABB(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);
        case SOUTH->new AABB(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);
        case WEST->new AABB(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);
        default->b;};}
    private static ProgrammableScreenEntity install(ServerLevel level,BlockState state){
        level.setBlock(POS,Blocks.AIR.defaultBlockState(),3);level.setBlock(POS,state,3);
        return (ProgrammableScreenEntity)level.getBlockEntity(POS);
    }
    private static void options(ServerLevel level,ProgrammableScreenEntity tile,boolean ceiling,int ceilingPosition,int wall,boolean small){
        var tag=tile.saveWithoutMetadata(level.registryAccess());tag.putBoolean("CeilingMounted",ceiling);tag.putInt("CeilingPosition",ceilingPosition);tag.putInt("wallPosition",wall);tag.putBoolean("smallInput",small);
        tile.loadWithComponents(tag,level.registryAccess());
    }
    private static AABB inputGolden(boolean full,boolean keyboard,boolean upper,boolean ceiling,int slot,int wall,boolean small){
        double scale=full||!small?1:.7,x=(1-scale)/2;
        if(ceiling){double depth=full?1:.5*scale,z=full?(slot-1)*.25:(1-depth)*slot/2;return new AABB(x,15D/16,z,1-x,1,z+depth);}
        if(keyboard){double y=upper?15D/16:7D/16;return new AABB(x,y,full?0:1-.5*scale,1-x,y+1D/16,1);}
        double height=full?1:.5*scale,y=full?0:wall==0?0:wall==2?1-height:(1-height)/2;
        return new AABB(x,y,15D/16,1-x,y+height,1);
    }
    public static void run(ServerLevel level){
        level.getChunkAt(POS);require(level.getBlockState(POS).isAir(),"owned fixture cell occupied");int cases=0;
        try{
            for(var block:new ProgrammableScreenBlock[]{ScreenRegistration.FULL_INPUT.get(),ScreenRegistration.HALF_INPUT.get()})
                for(Direction face:Direction.Plane.HORIZONTAL)for(boolean keyboard:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true}){
                    var state=block.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,face)
                        .setValue(ScreenFamilyBlocks.KEYBOARD,keyboard).setValue(ScreenFamilyBlocks.UPPER,upper);
                    var tile=install(level,state);boolean full=block.family()==Kind.FULL_INPUT;
                    for(boolean ceiling:new boolean[]{false,true})for(int slot=0;slot<3;slot++)for(int wall=0;wall<3;wall++)for(boolean small:new boolean[]{false,true}){
                        options(level,tile,ceiling,slot,wall,small);
                        var expected=rotate(inputGolden(full,keyboard,upper,ceiling,slot,wall,small),face);
                        // Deliberately invoke BlockState, whose cache must not erase tile-dependent mounts/small size.
                        bounds(level.getBlockState(POS).getCollisionShape(level,POS).bounds(),expected,"input "+block.family()+" "+face+" keyboard="+keyboard+" upper="+upper+" ceiling="+ceiling+" slot="+slot+" wall="+wall+" small="+small);
                        bounds(level.getBlockState(POS).getShape(level,POS).bounds(),expected,"input selection");cases++;
                    }
                }
            var half=ScreenRegistration.HALF_CONSOLE.get();
            for(Direction face:Direction.values()){
                var tile=install(level,half.defaultBlockState().setValue(ProgrammableScreenBlock.FACING,face));
                for(int slot=0;slot<3;slot++){
                    options(level,tile,true,slot,0,false);double offset=(slot-1)*.25;
                    bounds(level.getBlockState(POS).getCollisionShape(level,POS).bounds(),rotate(new AABB(0,0,offset,1,.5,1+offset),face),"ceiling half-console overhang "+face+" "+slot);cases++;
                }
            }
            var diagonal=ScreenRegistration.DIAGONAL.get();
            for(Direction face:Direction.Plane.HORIZONTAL)for(boolean inverted:new boolean[]{false,true}){
                var state=diagonal.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,face).setValue(ScreenFamilyBlocks.INVERTED,inverted);install(level,state);
                for(double x:new double[]{.25,.5,.75})for(double z:new double[]{.125,.375,.625,.875}){
                    var local=rotatePoint(x,z,face);Vec3 start=new Vec3(POS.getX()+local[0],POS.getY()+2,POS.getZ()+local[1]),end=start.add(0,-3,0);
                    var hit=state.getShape(level,POS).clip(start,end,POS);require(hit!=null,"exact diagonal ray missed "+face);
                    // Inverted hull has a flat upper face; the ordinary authored slope rises with local z.
                    double expected=inverted?1:z;
                    require(Math.abs(hit.getLocation().y-POS.getY()-expected)<1e-8,"exact diagonal ray follows slope rather than voxel stairs "+face+" inverted="+inverted+" z="+z+" hit="+hit.getLocation());cases++;
                }
            }
        }finally{level.setBlock(POS,Blocks.AIR.defaultBlockState(),3);}
        System.out.println("[vandorlabs][modern] screen-family-geometry PASS "+cases+" installed mount/small/overhang bounds and exact diagonal rays");
    }
    private static double[] rotatePoint(double x,double z,Direction face){return switch(face){case EAST->new double[]{1-z,x};case SOUTH->new double[]{1-x,1-z};case WEST->new double[]{z,1-x};default->new double[]{x,z};};}
}
