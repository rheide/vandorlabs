package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Every front/back corner and straight-tail combination, independent of insertion order. */
final class DiagonalJoinMatrixChecks {
    private static final net.minecraft.client.renderer.texture.TextureAtlasSprite SPRITE=sprite();
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(){net.minecraft.client.renderer.texture.TextureAtlasSprite s=new net.minecraft.client.renderer.texture.TextureAtlasSprite("join_matrix"){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(16,16,0,0,false);return s;}
    static void run(){
        BlockProgrammableWall[] blocks={(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,new BlockProgrammableDiagonalGlass()};
        int cases=0;
        for(BlockProgrammableWall block:blocks)for(int mode=0;mode<3;mode++)for(EnumFacing facing:EnumFacing.HORIZONTALS)
            for(boolean inverted:new boolean[]{false,true})for(int front=0;front<3;front++)for(int back=0;back<3;back++)for(int tails=0;tails<4;tails++){
                BlockPos root=new BlockPos(8,88,8);
                List<BlockPos> positions=new ArrayList<>();List<IBlockState> states=new ArrayList<>();
                IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
                positions.add(root);states.add(state);
                for(int side=0;side<2;side++){
                    int turn=side==0?front:back;if(turn==0)continue;
                    positions.add(root.offset(side==0?facing:facing.getOpposite()));
                    states.add(state.withProperty(BlockProgrammableWall.FACING,turn==1?facing.rotateYCCW():facing.rotateY()));
                }
                for(int side=0;side<2;side++)if((tails&(1<<side))!=0){positions.add(root.offset(side==0?facing.rotateYCCW():facing.rotateY()));states.add(state);}
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
                List<String> reference=null;
                for(int order=0;order<positions.size();order++){
                    world.clear();
                    for(int j=0;j<positions.size();j++){
                        int i=(j+order)%positions.size();put(world,positions.get(i),states.get(i),mode);
                    }
                    List<String> shape=shape(world,positions);
                    if(reference==null)reference=shape;else require(reference.equals(shape),"placement order changed join geometry");
                    for(int i=0;i<positions.size();i++){
                        world.setBlockState(positions.get(i),net.minecraft.init.Blocks.AIR.getDefaultState(),2);
                        put(world,positions.get(i),states.get(i),mode);
                        require(reference.equals(shape(world,positions)),"remove/replace changed join geometry");
                    }
                }
                if(mode<2){BlockProgrammableWall.Corner corner=block.corner(state,world,root);
                    require((corner!=null)==(front!=0 || back!=0),"corner detection differs from fixture");
                    if(corner!=null){require(Objects.equals(corner.frontRight,front==0?null:front==2),"front handedness");require(Objects.equals(corner.backRight,back==0?null:back==2),"back handedness");}
                }
                cases++;
            }
        for(int id=0;id<GlassTextures.IDS.length;id++){
            TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.setShade(id);
            TileEntityProgrammableGlass restored=new TileEntityProgrammableGlass();restored.readFromNBT(tile.getUpdateTag());require(restored.getShade()==id,"glass finish did not persist");
        }
        mixed(blocks);
        System.out.println("PASS: "+cases+" diagonal wall/glass join topologies, orientations, slopes, placement rotations and remove/replace cycles; all glass finish IDs persist");
    }
    private static void mixed(BlockProgrammableWall[] diagonals){
        BlockProgrammableWall flatWall=new BlockProgrammableWall("mixed_join_wall",BlockProgrammableWall.Shape.PLAIN);
        BlockProgrammableGlass flatGlass=new BlockProgrammableGlass("mixed_join_glass");
        BlockPos root=new BlockPos(8,88,8);
        for(BlockProgrammableWall diagonal:diagonals)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<2;mode++)for(int depth=0;depth<3;depth++)for(boolean glass:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
            IBlockState state=diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
            put(world,root,state,mode);
            EnumFacing flatFacing=glass?(facing.getAxis()==EnumFacing.Axis.X?EnumFacing.EAST:EnumFacing.NORTH):facing;
            IBlockState flat=glass?flatGlass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth):flatWall.getDefaultState().withProperty(BlockProgrammableWall.FACING,flatFacing).withProperty(BlockProgrammableWall.DEPTH,depth);
            for(boolean upper:new boolean[]{false,true}){
                double expected=com.vandorlabs.render.DiagonalWallGeometry.near(mode,inverted,upper?1:0);
                double near=PanelJoins.near(flat)/16;if(flatFacing!=facing)near=.75-near;
                int offset=(int)Math.round(expected-near);
                BlockPos next=root.up(upper?1:-1).offset(facing.getOpposite(),offset);
                world.setBlockState(next,flat,2);
                double end=BlockProgrammableWall.flatEnd(state,world,root,upper);
                if(Math.abs(expected-near-offset)<=.1250001){
                    require(Math.abs(end-near-offset)<1e-8,"mixed-material endpoint failed to align");
                    require(PanelJoins.connectsFlat(world,next,flat,root),"flat/diagonal connection is asymmetric");
                } else require(Double.isNaN(end),"distant panel folded the diagonal slope");
                int anchor=PanelJoins.placementDepth(world,root,state,upper?EnumFacing.UP:EnumFacing.DOWN);
                require(Math.abs(PanelDepth.start(anchor)/16D-expected)<=.1250001,"placement did not inherit an aligned end");
                world.setBlockState(next,net.minecraft.init.Blocks.AIR.getDefaultState(),2);
            }
            for(int choice=0;choice<12;choice++){
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root);tile.setDiagonalJoin(choice);
                TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(tile.getUpdateTag());require(restored.getDiagonalJoin()==choice,"join override lost in NBT");
                require((diagonal.corner(state,world,root)!=null)==(choice>=2),"join override geometry missing");
            }
        }
    }
    private static void put(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state,int mode){world.setBlockState(pos,state,2);((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).setDiagonalGeometry(mode,0);}
    private static List<String> shape(NonRenderingChecks.MemoryWorld world,List<BlockPos> positions){
        List<String> result=new ArrayList<>();
        for(BlockPos pos:positions){IBlockState state=world.getBlockState(pos);BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();List<AxisAlignedBB> boxes=new ArrayList<>();block.addCollisionBoxToList(state,world,pos,new AxisAlignedBB(pos).grow(3),boxes,null,false);
            DiagonalWallQuadCapture capture=new DiagonalWallQuadCapture(state.getValue(BlockProgrammableWall.FACING),false);
            TEAnimatedScreenSelector.drawConfiguredDiagonalWall(capture,(TileEntityAnimatedScreenSelector)world.getTileEntity(pos),state,SPRITE,SPRITE);
            for(net.minecraft.client.renderer.block.model.BakedQuad quad:capture.finish())result.add(pos+":mesh:"+Arrays.toString(quad.getVertexData()));
            for(AxisAlignedBB box:boxes){require(box.minX<=box.maxX && box.minY<=box.maxY && box.minZ<=box.maxZ,"inverted collision box");result.add(pos+":"+box);}
        }
        Collections.sort(result);return result;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
