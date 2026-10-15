package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Pick the visible column before invoking the real player harvest path. */
final class CanopyTargetedHarvestRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(8,130,8);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("targeted canopy harvest: "+what);}
    static void check(World world,EntityPlayerMP player){
        Vec3d old=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;int cases=0;
        try{
            for(String kind:new String[]{"canopy","long_canopy"})for(EnumFacing facing:EnumFacing.HORIZONTALS)
            for(int width:new int[]{3,9})for(int motion=0;motion<2;motion++)for(int pose=0;pose<2;pose++)
            for(int removed:new int[]{0,width/2,width-1}){
                BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);
                IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);
                String detail=kind+" width="+width+" facing="+facing+" motion="+motion+" pose="+pose+" column="+removed;
                try{
                    for(int column=0;column<width;column++){
                        BlockPos root=BASE.offset(facing.rotateY(),column);
                        for(BlockPos cell:block.cells(root,facing)){world.getChunkFromBlockCoords(cell);world.setBlockToAir(cell);}
                        world.setBlockState(root,state,2);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("CanopyMotion",motion);settings.setInteger("CanopyAngle",block.length==3?3:2);settings.setInteger("CanopyMount",motion);
                        ItemStack configured=new ItemStack(block);configured.setTagCompound(settings);block.onBlockPlacedBy(world,root,state,null,configured);
                    }
                    TileEntityCanopy owner=TileEntityCanopy.at(world,BASE);require(owner.width()==width,"joined fixture "+detail);owner.progress=owner.previous=pose;owner.targetOpen=pose==1;
                    BlockPos expected=BASE.offset(facing.rotateY(),removed);RayTraceResult selected=aim(world,player,owner,removed);
                    require(selected!=null && selected.getBlockPos().equals(expected),"visible ray selects requested constituent "+detail+" actual="+(selected==null?"miss":selected.getBlockPos()));
                    require(player.interactionManager.tryHarvestBlock(selected.getBlockPos()),"real player harvest succeeds "+detail);
                    for(int column=0;column<width;column++)for(BlockPos cell:block.cells(BASE.offset(facing.rotateY(),column),facing)){
                        if(column==removed)require(world.isAirBlock(cell) && TileEntityCanopy.at(world,cell)==null,"target removed completely "+detail+" cell="+cell);
                        else require(world.getBlockState(cell).getBlock()==block && TileEntityCanopy.at(world,cell)!=null,"other constituent preserved "+detail+" cell="+cell);
                    }
                    cases++;
                }finally{for(int column=0;column<width;column++)for(BlockPos cell:block.cells(BASE.offset(facing.rotateY(),column),facing))world.setBlockToAir(cell);}
            }
        }finally{player.setPositionAndUpdate(old.x,old.y,old.z);player.rotationYaw=yaw;player.rotationPitch=pitch;}
        System.out.println("[vandorlabs][reprolab] canopy-targeted-harvest PASS cases="+cases+" depths=2,3 widths=3,9 left-middle-right facings=4 slide-hinge ceiling closed-open visible-ray real-player-harvest");
    }
    static RayTraceResult aim(World world,net.minecraft.entity.player.EntityPlayer player,TileEntityCanopy owner,int column){
        for(CanopyMesh.Face face:owner.faces()){
            if(owner.block().opening() && !face.group.equals("shell"))continue;
            Vec3d center=Vec3d.ZERO;for(Vec3d vertex:face.vertices)center=center.add(vertex);center=center.scale(1D/face.vertices.length);
            // Joined shells select one local column. A hinged front window is one
            // component whose visible faces leave those closed column coordinates.
            if(owner.block().opening() && (center.x<column+.15 || center.x>column+.85))continue;
            Vec3d a=CanopyMesh.rotate(face.vertices[0],owner.facing()),b=null,c=null;
            for(int i=1;i+1<face.vertices.length;i++){Vec3d bb=CanopyMesh.rotate(face.vertices[i],owner.facing()),cc=CanopyMesh.rotate(face.vertices[i+1],owner.facing());if(bb.subtract(a).crossProduct(cc.subtract(a)).lengthSquared()>1e-10){b=bb;c=cc;break;}}
            if(b==null)continue;
            center=CanopyMesh.rotate(center,owner.facing()).add(new Vec3d(owner.getPos()));Vec3d normal=b.subtract(a).crossProduct(c.subtract(a)).normalize();
            for(int side:new int[]{1,-1}){
                Vec3d eye=center.add(normal.scale(2*side)),direction=center.subtract(eye).normalize();
                BlockPos expected=owner.block().opening()?owner.getPos().offset(owner.facing().rotateY(),column):owner.getPos();
                if(eye.addVector(0,1.5-player.getEyeHeight(),0).squareDistanceTo(new Vec3d(expected).addVector(.5,.5,.5))>=35)continue;
                player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);player.rotationYaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));player.rotationPitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
                Vec3d actual=player.getPositionEyes(1),end=actual.add(player.getLookVec().scale(4));RayTraceResult hit=CanopyInteractions.trace(world,actual,end);
                if(hit!=null && hit.hitVec.squareDistanceTo(center)<.01)return hit;
            }
        }
        return null;
    }
    private CanopyTargetedHarvestRuntimeChecks(){}
}
