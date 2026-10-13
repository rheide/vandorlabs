package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual portholed surfaces must connect without filling their sloped aperture. */
final class DiagonalPortholeJoinChecks {
    private static String fixture;
    static void run() {
        TextureAtlasSprite sprite=new TextureAtlasSprite("diagonal_porthole_join_check"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(16,16,0,0,false);
        BlockProgrammableWall[] blocks={new BlockProgrammableWall("porthole_check_wall",BlockProgrammableWall.Shape.DIAGONAL),
                new BlockProgrammableDiagonalGlass(),new BlockProgrammableWall("porthole_check_porthole",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE)};
        BlockPos root=new BlockPos(8,88,8);int count=0;
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})
            for(int variant=0;variant<20;variant++)for(int neighbor=0;neighbor<3;neighbor++) {
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
                IBlockState state=blocks[2].getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
                put(world,root,state);
                EnumFacing left=facing.rotateYCCW(),right=facing.rotateY();
                if(variant<4)put(world,root.offset(variant<2?facing:facing.getOpposite()),state(blocks[neighbor],(variant&1)==0?left:right,inverted));
                else if(variant<8) {
                    int v=variant-4;
                    put(world,root.offset(v<2?facing:facing.getOpposite()),state(blocks[neighbor],(v&1)==0?left:right,inverted));
                    put(world,root.offset(left),state(blocks[neighbor],facing,inverted));put(world,root.offset(right),state(blocks[neighbor],facing,inverted));
                } else {
                    int v=variant<16?variant-8:variant-16;
                    int hands=variant<16?v/2:v;
                    put(world,root.offset(facing),state(blocks[neighbor],(hands&1)==0?left:right,inverted));
                    put(world,root.offset(facing.getOpposite()),state(blocks[neighbor],(hands&2)==0?left:right,inverted));
                    if(variant>=16 || (v&1)==0)put(world,root.offset(left),state(blocks[neighbor],facing,inverted));
                    if(variant>=16 || (v&1)!=0)put(world,root.offset(right),state(blocks[neighbor],facing,inverted));
                }
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(root);
                BlockProgrammableWall.Corner corner=blocks[2].corner(state,world,root);
                require(corner!=null,"full-width porthole has no corner descriptor");
                boolean compound=variant>=8 && variant<16 && ((variant-8)/2==1 || (variant-8)/2==2);
                require(TEAnimatedScreenSelector.solidDiagonalPortholeJunction(tile,state)==compound,"compound solid node policy changed an L or cross");
                PortholeHex.Slice opening=compound?null:new PortholeHex(1,1).slice(0,0);
                StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
                TEAnimatedScreenSelector.drawConfiguredWall(capture,tile,state,sprite,sprite,opening);
                StaticSurfaceMesh opaque=capture.finish();
                capture=StaticSurfaceMesh.capture();
                if(opening!=null)TEAnimatedScreenSelector.drawPortholeGlassGeometry(capture,opening,TEAnimatedScreenSelector.portholeMesh(tile,state));
                StaticSurfaceMesh pane=capture.finish();
                double center=(corner.left(6)+corner.right(6))/2;
                fixture="facing="+facing+", inverted="+inverted+", variant="+variant+", neighbor="+neighbor+", center="+center+", left="+corner.left(6)+", right="+corner.right(6);
                require(probe(opaque,6,8,corner.frontRight!=null?3:13)>0,"porthole lost its actual perpendicular connector face");
                capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawConfiguredDiagonalWall(capture,tile,state,sprite,sprite);
                StaticSurfaceMesh solid=capture.finish();
                if(compound) {
                    require(pane.vertexCount()==0,"compound crossing node retained an internal glass pane");
                    require(DiagonalWallMeshCache.glass(tile,state,sprite,sprite,new PortholeHex(1,1).slice(0,0))==null,"actual render cache retained a compound node pane");
                    require(opaque.vertexCount()==solid.vertexCount(),"compound crossing node omitted its solid union surfaces");
                    require(probe(opaque,corner.straightLeft?2:14,8,6)>0,"compound crossing node lost its solid triangular stem face");
                    if(neighbor==2) {
                        BlockPos tail=root.offset(corner.straightLeft?left:right);
                        TEAnimatedScreenSelector.PortholeGroup group=TEAnimatedScreenSelector.portholeGroup((TileEntityAnimatedScreenSelector)world.getTileEntity(tail),world.getBlockState(tail));
                        require(group.columns==1 && group.rows==1,"solid node consumed its neighbor's complete porthole aperture");
                    }
                    if(neighbor==0) {
                        for(int mode:new int[]{0,2}) {tile.setDiagonalGeometry(mode,0);require(!TEAnimatedScreenSelector.solidDiagonalPortholeJunction(tile,state),"solid node changed another diagonal mode");}
                        tile.setDiagonalGeometry(1,0);tile.setDiagonalJoin(2);
                        require(!TEAnimatedScreenSelector.solidDiagonalPortholeJunction(tile,state),"solid node changed a manual join");tile.setDiagonalJoin(0);
                    }
                    count++;continue;
                }
                require(probe(opaque,center,8,6)==0,"solid connector filled the sloped porthole aperture");
                require(probe(pane,center,8,7)>0,"sloped aperture lost its matching glass pane");
                // An attached front arm makes x6..10 at z6 an internal interface,
                // rather than an exterior face of the solid reference union.
                if(corner.frontRight==null || center<5.9 || center>10.1)
                    require(probe(solid,center,8,6)>0,"aperture probe missed the corresponding solid wall face");
                require(probe(solid.diagonalArms(1,inverted,Double.NaN,Double.NaN),center,8,6)==0,"arm filtering retained a solid center face");
                require(opaque.vertexCount()>0 && pane.vertexCount()>0,"empty porthole render mesh");
                count++;
            }
        DiagonalWallMeshCache.clear();
        System.out.println("PASS: "+count+" full-width diagonal porthole L/T/X apertures, compound solid nodes, panes, isolated groups and connector faces");
    }
    private static IBlockState state(BlockProgrammableWall block,EnumFacing facing,boolean inverted) {
        return block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);
    }
    private static void put(NonRenderingChecks.MemoryWorld world,BlockPos pos,IBlockState state) {
        world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
        tile.setDiagonalGeometry(1,0);tile.setJoinPortholes(true);
    }
    private static int probe(StaticSurfaceMesh mesh,double x,double y,double z) {
        return mesh.box(x-.05,y-.05,z-.05,x+.05,y+.05,z+.05,false).vertexCount();
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message+" ["+fixture+"]");}
    private DiagonalPortholeJoinChecks() { }
}
