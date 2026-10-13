package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.List;

/** Material boundaries must produce the same physical bend as the accepted wall baseline. */
final class FlatPanelJoinRuntimeChecks {
    static void check(EntityPlayer player) {
        World world=player.world;
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();
        TileEntityProgrammableGlass source=new TileEntityProgrammableGlass();
        source.setPanelFacing(EnumFacing.WEST);source.writeToNBT(saved);
        TileEntityProgrammableGlass restored=new TileEntityProgrammableGlass();restored.readFromNBT(saved);
        require(restored.getPanelFacing(EnumFacing.EAST)==EnumFacing.WEST,"glass facing NBT round trip");
        saved.removeTag("GlassPanelFacing");restored.readFromNBT(saved);
        require(restored.getPanelFacing(EnumFacing.EAST)==EnumFacing.EAST,"legacy glass facing fallback");
        require(source.getPanelFacing(EnumFacing.NORTH)==EnumFacing.NORTH,"glass axis fallback");
        BlockPos root=new BlockPos(player.posX+3,Math.min(250,player.posY+20),player.posZ+3);
        String[] ids={"programmable_wall","programmable_glass","programmable_porthole_wall"};
        int count=0;
        for(int material=0;material<3;material++)for(int other=0;other<3;other++)
            for(int variant=0;variant<4;variant++)for(int order=0;order<2;order++) {
                BlockPos branch=root.offset(variant<2?EnumFacing.NORTH:EnumFacing.SOUTH);
                EnumFacing branchFacing=(variant&1)==0?EnumFacing.WEST:EnumFacing.EAST;
                IBlockState a=state(ids[material],EnumFacing.NORTH,0),b=state(ids[other],branchFacing,0);
                try {
                    world.setBlockState(order==0?root:branch,order==0?a:b,3);
                    world.setBlockState(order==0?branch:root,order==0?b:a,3);
                    if(material==1)((TileEntityProgrammableGlass)world.getTileEntity(root)).setPanelFacing(EnumFacing.NORTH);
                    if(other==1)((TileEntityProgrammableGlass)world.getTileEntity(branch)).setPanelFacing(branchFacing);
                    BlockProgrammableWall.FlatCorner corner=PanelJoins.flatCorner(a,world,root);
                    require(PanelJoins.flatBar(a,world,root)==null,"L became a long-bar ownership junction");
                    require(corner!=null,"missing ordered material L "+material+"/"+other+"/"+variant+"/"+order);
                    require(corner.left()==((variant&1)==0?0:6) && corner.right()==((variant&1)==0?10:16),"material changed accepted L extent");
                    require((variant<2?corner.frontArm:corner.backArm)==6,"wrong material arm plane");
                    List<AxisAlignedBB> boxes=PanelJoins.flatBoxes(a,world,root);
                    require(boxes.size()==2,"L collision omitted its bend");
                    AxisAlignedBB arm=boxes.get(1);
                    require(arm.minX==.375 && arm.maxX==.625 && (variant<2?arm.minZ==0 && arm.maxZ==.375:arm.minZ==.625 && arm.maxZ==1),"L collision occupies wrong arm");
                    if(material==1) {
                        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
                        StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
                        TEAnimatedScreenSelector.renderFlatWall(capture,sprite,sprite,6,corner);
                        StaticSurfaceMesh mesh=capture.finish();
                        net.minecraft.client.renderer.texture.TextureAtlasSprite rail=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/space_doors/medium/double_frame_metal");
                        StaticSurfaceMesh frame=mesh.outlineFlatFrame(rail);
                        require(mesh.vertexCount()>0 && frame.vertexCount()>0,"bent glass lost its pane or frame geometry");
                        require(frame.samplesOnly(rail),"bent glass frame samples outside its metal sprite");
                    }
                    if(other==1) {
                        ((TileEntityProgrammableGlass)world.getTileEntity(branch)).setJoin(false);
                        require(PanelJoins.flatCorner(a,world,root)==null,"disabled glass still bends its neighbor");
                    }
                    if(material==1) {
                        ((TileEntityProgrammableGlass)world.getTileEntity(root)).setJoin(false);
                        require(PanelJoins.flatCorner(a,world,root)==null,"disabled root glass still bends");
                    }
                    if(other==2) {
                        ((TileEntityAnimatedScreenSelector)world.getTileEntity(branch)).setJoinPortholes(false);
                        require(PanelJoins.flatCorner(a,world,root)==null,"disabled porthole still bends its neighbor");
                    }
                    count++;
                } finally {world.setBlockToAir(root);world.setBlockToAir(branch);}
            }
        world.setBlockState(root,state(ids[0],EnumFacing.NORTH,0),3);
        world.setBlockState(root.north(),state(ids[1],EnumFacing.WEST,1),3);
        try {
            BlockProgrammableWall.FlatCorner corner=PanelJoins.flatCorner(world.getBlockState(root),world,root);
            // Glass canonical EAST reverses WEST's near edge; depth1 therefore sits at local x12.
            require(corner!=null && corner.frontArm==12,"offset glass arm ignored its actual plane");
        } finally {world.setBlockToAir(root);world.setBlockToAir(root.north());}
        checkBars(world,root,ids);
        FlatGlassFrameRuntimeChecks.check(world,root,ids);
        checkGlassRotation(world);
        System.out.println("[vandorlabs] flat-panel-material-L PASS "+count+" ordered fixtures");
    }
    private static void put(World world,BlockPos pos,String id,EnumFacing facing) {
        world.setBlockState(pos,state(id,facing,0),3);
        if(world.getTileEntity(pos) instanceof TileEntityProgrammableGlass)
            ((TileEntityProgrammableGlass)world.getTileEntity(pos)).setPanelFacing(facing);
    }
    private static void checkBars(World world,BlockPos root,String[] ids) {
        int count=0;
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int hands=0;hands<4;hands++)for(int tail=0;tail<2;tail++)
            for(int center=0;center<3;center++)for(int front=0;front<3;front++)for(int back=0;back<3;back++) {
                BlockPos fp=root.offset(facing),bp=root.offset(facing.getOpposite());
                EnumFacing left=facing.rotateYCCW(),right=facing.rotateY();
                BlockPos stem=root.offset(tail==0?left:right);
                try {
                    put(world,root,ids[center],facing);put(world,fp,ids[front],(hands&1)==0?left:right);
                    put(world,bp,ids[back],(hands&2)==0?left:right);put(world,stem,ids[0],facing);
                    IBlockState state=world.getBlockState(root);
                    BlockProgrammableWall.FlatCorner bar=PanelJoins.flatBar(state,world,root);
                    require(bar!=null && bar.frontArm==6,"aligned opposing branches did not form T bar");
                    require(PanelJoins.flatBoxes(state,world,root).size()==3,"ownership changed physical T boxes");
                    net.minecraft.tileentity.TileEntity tile=world.getTileEntity(root);
                    FlatPanelBarRenderer.Material[] materials={FlatPanelBarRenderer.material(tile,state),FlatPanelBarRenderer.barMaterial(tile,state,bar.frontArm)};
                    for(int i=0;i<2;i++) {
                        FlatPanelBarRenderer.Material material=materials[i];
                        require(material!=null && material.opaque!=null,"missing center-owned opaque surface/frame");
                        require((material.transparent!=null)==(center!=0),"T center borrowed a neighbor's transparency");
                        int turns=i==0?0:3,part=i==0?0:5;
                        for(StaticSurfaceMesh source:new StaticSurfaceMesh[]{material.opaque,material.transparent})if(source!=null) {
                            double padding=source==material.opaque && material.cap==null?.02:0;
                            StaticSurfaceMesh mesh=FlatPanelBarRenderer.part(source,turns,part,bar.frontArm,material.depth,material.left,material.width,padding);
                            require(mesh.vertexCount()>0,"T lost a center-owned material surface");
                            if(i==0)require(mesh.inside(tail==0?-.02:10,-.02,-.02,tail==0?6:16.02,16.02,16.02),"stem leaked into the T bar or grew an opposite tail");
                            else require(mesh.inside(6-padding,-.02,-.02,10+padding,16.02,16.02),"center bar rotated/clipped outside its full span");
                            if(i==1 && source==material.opaque) {
                                require(mesh.hasPlaneFace(2,-padding/2,-1) && mesh.hasPlaneFace(2,16+padding/2,1),"center-owned bar does not cover both full-width ends");
                            }
                            if(padding>0) {
                                if(i==0)require(!mesh.hasPlaneFace(0,5.99,-1) && !mesh.hasPlaneFace(0,10.01,1),"stem frame leaked along center bar");
                                else require(mesh.hasPlaneFace(0,5.99,-1) && mesh.hasPlaneFace(0,10.01,1),"center glass lost its outward metal frame strips");
                            }
                            require(mesh==FlatPanelBarRenderer.part(source,turns,part,bar.frontArm,material.depth,material.left,material.width,padding),"T geometry rebuilt without changes");
                        }
                        if(i==0 && center!=1) {
                            double cut=tail==0?6:10;
                            StaticSurfaceMesh cap=FlatPanelBarRenderer.stemCap(material,cut,6,tail==0);
                            require(cap!=null && cap.vertexCount()>0 && cap.inside(cut,0,6,cut,16,10),"opaque stem has no correct cap against bar opening");
                        }
                    }
                    if(front==1) {
                        TileEntityProgrammableGlass neighbor=(TileEntityProgrammableGlass)world.getTileEntity(fp);
                        neighbor.setAppearance(0,false);neighbor.setShade(1);
                        FlatPanelBarRenderer.Material independent=FlatPanelBarRenderer.barMaterial(tile,state,bar.frontArm);
                        require(independent.opaque==materials[1].opaque && independent.transparent==materials[1].transparent,"neighbor appearance changed center T bar");
                    }
                    if(center==1) {
                        TileEntityProgrammableGlass glass=(TileEntityProgrammableGlass)tile;
                        glass.setAppearance(0,false);
                        FlatPanelBarRenderer.Material changed=FlatPanelBarRenderer.barMaterial(tile,state,bar.frontArm);
                        require(changed.opaque==null && changed.transparent!=materials[1].transparent,"center frame/tint setting retained stale T appearance");
                        glass.setAppearance(1,true);
                    }
                    if(front==1) {
                        TileEntityProgrammableGlass glass=(TileEntityProgrammableGlass)world.getTileEntity(fp);glass.setJoin(false);
                        require(PanelJoins.flatBar(state,world,root)==null,"Join Off glass still owned T bar");glass.setJoin(true);
                    } else if(front==2) {
                        TileEntityAnimatedScreenSelector porthole=(TileEntityAnimatedScreenSelector)world.getTileEntity(fp);porthole.setJoinPortholes(false);
                        require(PanelJoins.flatBar(state,world,root)==null,"Join Off porthole still owned T bar");porthole.setJoinPortholes(true);
                    }
                    // A fourth arm is a cross, not a long-bar T; remove it before fixture cleanup.
                    BlockPos opposite=root.offset(tail==0?right:left);put(world,opposite,ids[0],facing);
                    require(PanelJoins.flatBar(state,world,root)==null,"cross incorrectly inherited T ownership");
                    world.setBlockToAir(opposite);count++;
                } finally {
                    world.setBlockToAir(root);world.setBlockToAir(fp);world.setBlockToAir(bp);world.setBlockToAir(stem);
                    world.setBlockToAir(root.offset(tail==0?right:left));
                }
            }
        System.out.println("[vandorlabs] flat-panel-material-T PASS "+count+" material/direction/hand/tail fixtures");
    }
    private static void checkGlassRotation(World world) {
        BlockPos pos=new BlockPos(1,0,0);
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();
        tile.setPos(pos);tile.setPanelFacing(EnumFacing.WEST);
        java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(pos,state("programmable_glass",EnumFacing.WEST,1),tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        com.vandorlabs.vehicle.VehicleStructure original=new com.vandorlabs.vehicle.VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1),rotated=original;
        EnumFacing facing=EnumFacing.WEST;
        for(int turn=0;turn<4;turn++) {
            rotated=com.vandorlabs.vehicle.VehicleRotation.rotate(rotated,new com.vandorlabs.vehicle.VehicleWorld(world,rotated),1);
            facing=facing.rotateY();
            for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:rotated.cells)if(cell.state.getBlock() instanceof BlockProgrammableGlass) {
                require(cell.tileData().getInteger("GlassPanelFacing")==facing.getHorizontalIndex(),"craft rotation lost glass placement facing");
                com.vandorlabs.vehicle.VehicleWorld copy=new com.vandorlabs.vehicle.VehicleWorld(world,rotated);
                require(PanelJoins.flatFacing(cell.state,copy,cell.pos)==facing,"vehicle world copy lost glass facing");
            }
        }
        require(original.write().equals(rotated.write()),"four craft turns changed glass state or NBT");
    }
    private static IBlockState state(String id,EnumFacing facing,int depth) {
        Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id));
        if(block==null || block==Blocks.AIR)throw new IllegalStateException(id);
        IBlockState state=block.getDefaultState();
        return block instanceof BlockGlassWall?state.withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X)
                .withProperty(BlockGlassWall.DEPTH,depth):state.withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private FlatPanelJoinRuntimeChecks() {}
}
