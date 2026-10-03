package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.render.TrapdoorGeometry;
import com.vandorlabs.render.DiagonalTrapdoorGeometry;
import com.vandorlabs.persistence.SpaceDoorData;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.registry.*;
import java.util.*;

/** No GL context: actual block/tile/registry/channel/mesh code. */
final class TrapdoorChecks {
    private static BlockProgrammableTrapdoor block;
    private static ItemBlock item;
    private static int assertions;
    static void run() {
        GameRegistry.registerTileEntity(TileEntityProgrammableTrapdoor.class,new ResourceLocation("minecraft:vandorlabs_data_check_trapdoor"));
        block=(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_TRAPDOOR;
        ForgeRegistries.BLOCKS.register(block);
        item=new ItemProgrammableTrapdoor(block);ForgeRegistries.ITEMS.register(item.setRegistryName(block.getRegistryName()));
        checkMesh();checkNextBlockAndLayout();checkCoverGroupSafety();checkClickPlacement();checkSettings();checkPairs();checkSquares();checkRectangles();checkPower();checkCopy();checkOffsetNeighborsAndCopy();checkOpposingCovers();checkPermissions();checkRecipe();
        System.out.println("PASS: Programmable Trapdoor ("+assertions+" assertions; geometry, texture, placement, pairs, all square orders, channels, copying, permissions)");
    }
    private static void checkMesh() {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=new net.minecraft.client.renderer.texture.TextureAtlasSprite("trapdoor_check"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,48,false);
        for(int pos=0;pos<3;pos++)for(boolean slide:new boolean[]{false,true})for(int facing=0;facing<4;facing++)for(int step=0;step<=18;step++) {
            double pose=step/18D;
            double[][] vertices=TrapdoorGeometry.corners(pos,slide,facing,pose);
            require(Math.abs(distance(vertices[0],vertices[1])-(1-2*TrapdoorGeometry.EDGE_CLEARANCE))<1e-8,"leaf width changes");
            require(Math.abs(distance(vertices[0],vertices[4])-(1-2*TrapdoorGeometry.EDGE_CLEARANCE))<1e-8,"leaf length changes");
            require(Math.abs(distance(vertices[0],vertices[2])-3/16D)<1e-8,"leaf thickness changes");
            double[] bounds=TrapdoorGeometry.bounds(pos,slide,facing,pose);
            if(slide || step==0){require(Math.abs(bounds[1]-TrapdoorGeometry.low(pos))<1e-8,"slide changes height");require(Math.abs(bounds[4]-bounds[1]-3/16D)<1e-8,"slide changes thickness");}
            if(pos==0)require(bounds[1]>=TrapdoorGeometry.EDGE_CLEARANCE-1e-8,"moving bottom leaf clips its supporting floor");
            if(pos==2)require(bounds[4]<=1-TrapdoorGeometry.EDGE_CLEARANCE+1e-8,"moving top leaf clips its supporting ceiling");
            if(step==0 && pos==0)require(bounds[1]==TrapdoorGeometry.EDGE_CLEARANCE,"bottom leaf floats above support");
            if(step==0 && pos==2)require(bounds[4]==1-TrapdoorGeometry.EDGE_CLEARANCE,"top leaf floats below support");
            net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(1024);
            net.minecraft.client.renderer.vertex.VertexFormat format=BlockSurfaceFormat.get();buffer.begin(7,format);
            TEProgrammableTrapdoor.drawLeaf(buffer,sprite,pos,slide,facing,pose,(192<<16)|80);buffer.finishDrawing();
            require(buffer.getVertexCount()==24,"leaf must have only six faces");
            java.nio.ByteBuffer data=buffer.getByteBuffer();int stride=format.getNextOffset(),uv=format.getUvOffsetById(0),light=format.getUvOffsetById(1),normal=format.getNormalOffset();
            for(int v=0;v<24;v++) {
                int start=v*stride;double u=data.getFloat(start+uv),vv=data.getFloat(start+uv+4);
                require(u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6 && vv>=sprite.getMinV()-1e-6 && vv<=sprite.getMaxV()+1e-6,"atlas bleed");
                require(data.getShort(start+light)==80 && data.getShort(start+light+2)==192,"lightmap missing");
                double nx=data.get(start+normal)/127D,ny=data.get(start+normal+1)/127D,nz=data.get(start+normal+2)/127D;
                require(Math.abs(nx*nx+ny*ny+nz*nz-1)<.03,"invalid normal");
                if(v%4==0) {
                    double[] a=point(data,start),b=point(data,start+stride),c=point(data,start+2*stride);
                    double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
                    require((uy*vz-uz*vy)*nx+(uz*vx-ux*vz)*ny+(ux*vy-uy*vx)*nz>0,"normal opposes winding");
                }
            }
        }
    }
    private static void checkNextBlockAndLayout() {
        for(int position=0;position<3;position++)for(int turn=0;turn<4;turn++)for(boolean sliding:new boolean[]{false,true}) {
            double[][] closed=TrapdoorGeometry.coverCorners(position,sliding,turn,0),open=TrapdoorGeometry.coverCorners(position,sliding,turn,1);
            double[] closedBounds=DiagonalTrapdoorGeometry.bounds(closed);
            double far=turn==0?closedBounds[2]:turn==1?closedBounds[3]:turn==2?closedBounds[5]:closedBounds[0];
            double expected=turn==0?-1-TrapdoorGeometry.COVER_OVERHANG+TrapdoorGeometry.EDGE_CLEARANCE:turn==3?-1-TrapdoorGeometry.COVER_OVERHANG+TrapdoorGeometry.EDGE_CLEARANCE:2+TrapdoorGeometry.COVER_OVERHANG-TrapdoorGeometry.EDGE_CLEARANCE;
            require(Math.abs(far-expected)<1e-8,"next-block closed leaf must project one pixel past far edge");
            double[] b=DiagonalTrapdoorGeometry.bounds(open);
            require(b[0]>0 && b[3]<1 && b[2]>0 && b[5]<1,"next-block open leaf intrudes into adjacent solid block");
            for(int i=0;i<8;i++)for(int j=i+1;j<8;j++)require(Math.abs(distance(closed[i],closed[j])-distance(open[i],open[j]))<1e-8,"next-block leaf deforms");
            if(!sliding)require(Math.abs(open[0][1]-closed[0][1])>.5,"next-block rotating leaf slides instead");
            double[] normal=TrapdoorGeometry.bounds(position,false,turn,1);
            require(normal[0]>0 && normal[3]<1 && normal[2]>0 && normal[5]<1,"rotated normal leaf shares a neighboring block plane");
            if(position!=1)require(normal[1]>0 && normal[4]<1,"open bottom/top leaf clips into supporting floor or ceiling");
        }
        TileEntityProgrammableTrapdoor tile=new TileEntityProgrammableTrapdoor();require(tile.isTileTexture(),"trapdoors default to Tile");tile.setTileTexture(false);tile.setCover(true);
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityProgrammableTrapdoor restored=new TileEntityProgrammableTrapdoor();restored.readFromNBT(saved);
        require(!restored.isTileTexture() && restored.isCover() && !restored.isSliding(),"Fit and next-block rotation did not save");
        saved.removeTag("TrapdoorTileTexture");restored.readFromNBT(saved);require(restored.isTileTexture(),"old trapdoors do not default to Tile");
    }
    private static void checkCoverGroupSafety() {
        for(int[] size:new int[][]{{2,1},{2,2},{2,4}}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);
            for(int z=0;z<size[1];z++)for(int x=0;x<size[0];x++)place(world,base.add(x,0,z),0,false);
            TileEntityProgrammableTrapdoor root=(TileEntityProgrammableTrapdoor)world.getTileEntity(base);
            java.util.List<TileEntityProgrammableTrapdoor> group=root.group();require(group.size()==size[0]*size[1],"cover safety fixture not joined");
            java.util.List<IBlockState> states=new ArrayList<>();for(TileEntityProgrammableTrapdoor leaf:group)states.add(world.getBlockState(leaf.getPos()));
            root.setCover(true);require(!root.isCover(),"direct Next block dissolved joined trapdoor");
            root.configureGroup(root.getHousingTexture(),0,false,0,0,false,true,true,EnumFacing.NORTH);
            NBTTagCompound copied=new NBTTagCompound();copied.setBoolean(ProgrammableSettings.TRAPDOOR_COVER,true);copied.setInteger(ProgrammableSettings.TRAPDOOR_COVER_FACING,EnumFacing.SOUTH.getHorizontalIndex());
            ProgrammableSettings.apply(world,base,copied);
            for(int i=0;i<group.size();i++) {
                TileEntityProgrammableTrapdoor leaf=group.get(i);
                require(!leaf.isCover() && leaf.group().size()==group.size(),"Next block/copy changed joined membership");
                require(world.getBlockState(leaf.getPos()).getValue(BlockTrapDoor.FACING)==states.get(i).getValue(BlockTrapDoor.FACING),"Next block/copy moved joined hinge");
            }
            // Model the old conversion, then exercise the documented recovery.
            for(TileEntityProgrammableTrapdoor leaf:group){leaf.unpair();leaf.setCover(true);leaf.setCoverFacing(EnumFacing.NORTH);}
            for(TileEntityProgrammableTrapdoor leaf:group)leaf.setCover(false);
            BlockPos replaced=group.get(group.size()-1).getPos();block.breakBlock(world,replaced,world.getBlockState(replaced));world.setBlockToAir(replaced);place(world,replaced,0,false);
            require(((TileEntityProgrammableTrapdoor)world.getTileEntity(base)).group().size()==size[0]*size[1],"restored offset assembly did not rejoin after replacing a member");
        }
        for(int position=0;position<3;position++)for(boolean sliding:new boolean[]{false,true}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=new BlockPos(10,100,10);
            TileEntityProgrammableTrapdoor leaf=place(world,pos,position,sliding);leaf.setCover(true);
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean open:new boolean[]{false,true}) {
                leaf.setCoverFacing(facing);leaf.requestOpen(open);
                AxisAlignedBB box=OffsetTrapdoorInteractions.bounds(leaf);Vec3d center=box.getCenter();
                Vec3d start=center.addVector(0,2,0),end=center.addVector(0,-2,0);
                if(open){start=center.addVector(facing.getFrontOffsetX()*2,0,facing.getFrontOffsetZ()*2);end=center.addVector(-facing.getFrontOffsetX()*2,0,-facing.getFrontOffsetZ()*2);}
                RayTraceResult hit=OffsetTrapdoorInteractions.trace(world,start,end);
                require(hit!=null && pos.equals(hit.getBlockPos()),"Next block leaf cannot be selected after hinge change: "+facing+" open="+open);
                java.util.List<AxisAlignedBB> boxes=new ArrayList<>();AxisAlignedBB query=new AxisAlignedBB(center.x-.05,center.y-.05,center.z-.05,center.x+.05,center.y+.05,center.z+.05);
                OffsetTrapdoorInteractions.addCollisions(world,query,boxes);OffsetTrapdoorInteractions.addCollisions(world,query,boxes);
                require(boxes.size()==1 && boxes.get(0).equals(box),"offset leaf collision missing, misplaced or duplicated");
                if(!open) {
                    Vec3d tip=center.addVector(facing.getFrontOffsetX()*.48,0,facing.getFrontOffsetZ()*.48);
                    RayTraceResult tipHit=OffsetTrapdoorInteractions.trace(world,tip.addVector(0,2,0),tip.addVector(0,-2,0));
                    require(tipHit!=null && pos.equals(tipHit.getBlockPos()),"one-pixel overhang cannot be selected");
                    java.util.List<AxisAlignedBB> tipBoxes=new ArrayList<>();
                    OffsetTrapdoorInteractions.addCollisions(world,new AxisAlignedBB(tip.x-.005,tip.y-.005,tip.z-.005,tip.x+.005,tip.y+.005,tip.z+.005),tipBoxes);
                    require(tipBoxes.size()==1 && tipBoxes.get(0).equals(box),"one-pixel overhang has no collision");
                }
                require(leaf.isCover() && world.getTileEntity(pos)==leaf,"hinge change replaced offset tile");
            }
            leaf.setCover(false);require(!leaf.isCover(),"offset leaf cannot be restored to This block");
        }
    }
    private static void checkClickPlacement() {
        for(EnumFacing side:EnumFacing.values())for(float hit:new float[]{.1F,.5F,.9F}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            BlockPos p=new BlockPos(10,100,10);ItemStack stack=new ItemStack(item);
            EntityPlayer placer=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TrapdoorPlacement")) {
                @Override public boolean isSpectator(){return false;}
                @Override public boolean isCreative(){return true;}
            };
            IBlockState placedState=block.getStateForPlacement(world,p,side,.5F,hit,.5F,0,placer);
            require(placedState.getValue(BlockTrapDoor.FACING)==(side.getAxis().isHorizontal()?side.getOpposite():placer.getHorizontalFacing().getOpposite()),"plain trapdoor hinge faces away from clicked support");
            require(item.placeBlockAt(stack,placer,world,p,side,.5F,hit,.5F,placedState),"plain placement failed");
            int expected=side==EnumFacing.UP?0:side==EnumFacing.DOWN?2:hit<1/3F?0:hit>2/3F?2:1;
            require(((TileEntityProgrammableTrapdoor)world.getTileEntity(p)).getPosition()==expected,"click band lost");
            require(stack.getSubCompound("BlockEntityTag")==null,"placement changed held stack");
        }
        for(int pos=0;pos<3;pos++)for(boolean sliding:new boolean[]{false,true}) {
            double[] b=TrapdoorGeometry.bounds(pos,sliding,0,1);
            require(b[5]>=1/16D-TrapdoorGeometry.EDGE_CLEARANCE-1e-8,"open leaf disappeared into neighbor");
            if(sliding)require(Math.abs(b[5]-(1/16D-TrapdoorGeometry.EDGE_CLEARANCE))<1e-8,"sliding clearance is not one pixel");
        }
    }
    private static void checkSettings() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        BlockPos p=new BlockPos(10,100,10);
        for(int finish=0;finish<ScreenHousingTextures.IDS.length;finish++)for(int position=0;position<3;position++)for(boolean slide:new boolean[]{false,true}) {
            world.clear();TileEntityProgrammableTrapdoor tile=place(world,p,position,slide);
            tile.configure(finish,position,slide,SpaceDoorData.TRIGGER_DISABLED,0);
            tile.requestOpen(true);
            for(EnumFacing facing:EnumFacing.HORIZONTALS) {
                world.setBlockState(p,world.getBlockState(p).withProperty(BlockProgrammableTrapdoor.FACING,facing),2);
                double[] expected=TrapdoorGeometry.bounds(position,slide,BlockProgrammableTrapdoor.quarterTurns(facing),1);
                AxisAlignedBB box=block.getBoundingBox(world.getBlockState(p),world,p);
                require(Math.abs(box.minX-expected[0])+Math.abs(box.minY-expected[1])+Math.abs(box.minZ-expected[2])<1e-8,"collision differs from geometry");
                IBlockState state=world.getBlockState(p);
                require(block.getStateFromMeta(block.getMetaFromState(state)).equals(state),"metadata round trip");
            }
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityProgrammableTrapdoor restored=new TileEntityProgrammableTrapdoor();restored.readFromNBT(saved);
            require(restored.getHousingTexture()==finish && restored.getPosition()==position && restored.isSliding()==slide,"settings save round trip");
            ItemStack pick=block.configuredDrop(tile);
            require(!pick.isEmpty() && !pick.getSubCompound("BlockEntityTag").hasKey("TrapdoorPartner"),"drop includes pair state");
            NonRenderingChecks.MemoryWorld client=new NonRenderingChecks.MemoryWorld(true);
            require(item.placeBlockAt(pick,null,client,p,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState()),"client placement failed");
            TileEntityProgrammableTrapdoor predicted=(TileEntityProgrammableTrapdoor)client.getTileEntity(p);
            require(predicted.getHousingTexture()==finish && predicted.getPosition()==position && predicted.isSliding()==slide,"placement texture flicker or settings lost");
            require(client.getBlockState(p).getValue(BlockTrapDoor.HALF)==(position==2?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM),"placement HALF differs from configured position");
        }
        require(!TileEntityProgrammableTrapdoor.valid(-1,0,1,0) && !TileEntityProgrammableTrapdoor.valid(0,3,1,0)
                && !TileEntityProgrammableTrapdoor.valid(0,0,8,0) && !TileEntityProgrammableTrapdoor.valid(0,0,1,-1),"invalid settings accepted");
        io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();
        com.vandorlabs.network.MessageProgrammableTrapdoor message=new com.vandorlabs.network.MessageProgrammableTrapdoor(p,4,2,true,2,17);
        message.toBytes(bytes);com.vandorlabs.network.MessageProgrammableTrapdoor copy=new com.vandorlabs.network.MessageProgrammableTrapdoor();copy.fromBytes(bytes);
        io.netty.buffer.ByteBuf again=io.netty.buffer.Unpooled.buffer();copy.toBytes(again);
        require(bytes.readerIndex()==bytes.writerIndex() && io.netty.buffer.ByteBufUtil.equals(bytes,0,again,0,again.writerIndex()),"packet round trip");bytes.release();again.release();
    }
    private static void checkPairs() {
        for(int position=0;position<3;position++)for(boolean slide:new boolean[]{false,true})for(EnumFacing direction:EnumFacing.HORIZONTALS) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos a=new BlockPos(10,100,10),b=a.offset(direction);
            TileEntityProgrammableTrapdoor first=place(world,a,position,slide),second=place(world,b,position,slide);
            require(first.mate()==second && second.mate()==first,"adjacent trapdoors did not pair");
            require(world.getBlockState(a).getValue(BlockTrapDoor.FACING)==world.getBlockState(b).getValue(BlockTrapDoor.FACING).getOpposite(),"pair directions not opposite");
            second.requestOpen(true);require(open(world,a) && open(world,b),"pair did not open together");
            first.requestOpen(false);require(!open(world,a) && !open(world,b),"pair did not close together");
            NBTTagCompound s1=first.writeToNBT(new NBTTagCompound()),s2=second.writeToNBT(new NBTTagCompound());
            first.readFromNBT(s1);second.readFromNBT(s2);require(first.mate()==second,"pair reload lost");
            block.breakBlock(world,b,world.getBlockState(b));world.setBlockState(b,Blocks.AIR.getDefaultState(),2);
            require(first.mate()==null && !first.hasPairLink(),"broken half left stale link");first.requestOpen(true);require(open(world,a),"remaining trapdoor unusable");
        }
    }
    private static void checkSquares() {
        List<int[]> orders=new ArrayList<>();permutations(new int[]{0,1,2,3},0,orders);
        for(int position=0;position<3;position++)for(boolean slide:new boolean[]{false,true})for(int[] order:orders) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);
            BlockPos[] cells={base,base.east(),base.south(),base.east().south()};
            for(int i:order)place(world,cells[i],position,slide);
            for(BlockPos cell:cells) {
                TileEntityProgrammableTrapdoor tile=(TileEntityProgrammableTrapdoor)world.getTileEntity(cell);
                require(tile.group().size()==4,"square missed placement order "+Arrays.toString(order));
                require(tile.mate()!=null && world.getBlockState(cell).getValue(BlockTrapDoor.FACING)
                        ==world.getBlockState(tile.mate().getPos()).getValue(BlockTrapDoor.FACING).getOpposite(),"square pair directions not opposite");
                tile.requestOpen(true);for(BlockPos other:cells)require(open(world,other),"square open missed leaf");
                tile.requestOpen(false);for(BlockPos other:cells)require(!open(world,other),"square close missed leaf");
                tile.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
            }
            TileEntityProgrammableTrapdoor remaining=(TileEntityProgrammableTrapdoor)world.getTileEntity(cells[0]);
            block.breakBlock(world,cells[3],world.getBlockState(cells[3]));world.setBlockState(cells[3],Blocks.AIR.getDefaultState(),2);
            require(remaining.group().size()<=2,"broken square remained linked");
            place(world,cells[3],position,slide);require(remaining.group().size()==4,"replacement did not rebuild square");
            // Simulate a missing member from a saved/copy-edited world, bypassing breakBlock.
            world.states.put(cells[3],Blocks.AIR.getDefaultState());world.tiles.remove(cells[3]);
            remaining.repairLinks();place(world,cells[3],position,slide);
            require(remaining.group().size()==4,"stale saved links prevent rebuilding square");
        }
    }
    private static void checkRectangles() {
        for(boolean slide:new boolean[]{false,true})for(int[] size:new int[][]{{2,4},{5,2},{8,8}}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(20,100,20);
            for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++)place(world,base.add(x,0,z),1,slide);
            TileEntityProgrammableTrapdoor first=(TileEntityProgrammableTrapdoor)world.getTileEntity(base);
            require(first.group().size()==size[0]*size[1],"rectangle did not connect "+Arrays.toString(size));
            first.requestOpen(true);
            for(TileEntityProgrammableTrapdoor leaf:first.group()) {
                require(open(world,leaf.getPos()),"rectangle missed leaf");
                leaf.readFromNBT(leaf.writeToNBT(new NBTTagCompound()));
                require(leaf.group().size()==size[0]*size[1],"rectangle save lost");
                require(!leaf.itemSettings().hasKey("TrapdoorAssembly"),"item retained assembly links");
            }
            BlockPos removed=base.add(size[0]-1,0,size[1]-1);
            block.breakBlock(world,removed,world.getBlockState(removed));world.setBlockState(removed,Blocks.AIR.getDefaultState(),2);
            require(first.group().size()<=4,"broken rectangle remained linked");
            place(world,removed,1,slide);require(first.group().size()==size[0]*size[1],"rectangle repair failed");
        }
    }
    private static void checkPower() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos base=new BlockPos(10,100,10);
        BlockPos[] cells={base,base.east(),base.south(),base.east().south()};
        for(BlockPos cell:cells)place(world,cell,0,true);
        TileEntityProgrammableTrapdoor first=(TileEntityProgrammableTrapdoor)world.getTileEntity(base);
        BlockPos power=cells[3].down();world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),2);
        ((TileEntityProgrammableTrapdoor)world.getTileEntity(cells[3])).localInputChanged();
        for(BlockPos cell:cells)require(open(world,cell),"physical power failed to open square");
        world.setBlockState(power,Blocks.AIR.getDefaultState(),2);first.localInputChanged();for(BlockPos cell:cells)require(!open(world,cell),"physical power removal failed");
        for(TileEntityProgrammableTrapdoor leaf:first.group())leaf.setRedstoneChannel(7);
        BlockPos senderPos=base.east(10);TileEntityProgrammableTrapdoor sender=place(world,senderPos,0,false);sender.setRedstoneChannel(7);
        world.setBlockState(senderPos.down(),Blocks.REDSTONE_BLOCK.getDefaultState(),2);sender.localInputChanged();
        for(BlockPos cell:cells)require(open(world,cell),"virtual channel failed to open square");
        world.setBlockState(senderPos.down(),Blocks.AIR.getDefaultState(),2);sender.localInputChanged();for(BlockPos cell:cells)require(!open(world,cell),"virtual channel removal failed");
        for(TileEntityProgrammableTrapdoor leaf:first.group())leaf.configure(3,2,false,SpaceDoorData.TRIGGER_REDSTONE_OFF,7);
        for(BlockPos cell:cells)require(open(world,cell),"inverse redstone trigger failed");
        world.setBlockState(senderPos.down(),Blocks.REDSTONE_BLOCK.getDefaultState(),2);sender.localInputChanged();
        for(BlockPos cell:cells)require(!open(world,cell),"inverse trigger did not close");
        first.configure(3,2,false,SpaceDoorData.TRIGGER_DISABLED,0);first.requestOpen(true);
        NBTTagCompound tag=first.writeToNBT(new NBTTagCompound());first.readFromNBT(tag);first.evaluatePower(false);require(open(world,base),"manual open lost on reload");
        sender.invalidate();
        // With the mate's chunk unavailable, no adjacency/power read may load it.
        NonRenderingChecks.MemoryWorld border=new NonRenderingChecks.MemoryWorld(false);BlockPos edge=new BlockPos(15,100,15);
        TileEntityProgrammableTrapdoor a=place(border,edge,0,true);place(border,edge.east(),0,true);border.chunkLimit=true;
        a.localInputChanged();require(a.group().size()==1,"unloaded mate was visited");
    }
    private static void checkCopy() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos a=new BlockPos(10,100,10),b=a.east();
        TileEntityProgrammableTrapdoor first=place(world,a,0,false);place(world,b,0,false);
        NBTTagCompound settings=new NBTTagCompound();settings.setInteger(ProgrammableSettings.WALL_TEXTURE,4);settings.setInteger(ProgrammableSettings.TRAPDOOR_POSITION,1);
        settings.setBoolean(ProgrammableSettings.DOOR_SLIDING,true);settings.setInteger(ProgrammableSettings.CHANNEL,17);settings.setInteger(ProgrammableSettings.TRIGGER,0);
        require(ProgrammableSettings.apply(world,a,settings),"copy did not apply");
        for(TileEntityProgrammableTrapdoor leaf:first.group())require(leaf.getPosition()==1 && leaf.isSliding() && leaf.getHousingTexture()==4 && leaf.getRedstoneChannel()==17,"group copy lost settings");
        ItemStack crafted=ProgrammableSettings.applyToItem(new ItemStack(item),ProgrammableSettings.capture(world,a));
        require(!crafted.isEmpty() && !crafted.getSubCompound("BlockEntityTag").hasKey("TrapdoorPartner") && !crafted.getSubCompound("BlockEntityTag").hasKey("TrapdoorSquare"),"crafting copied links");
    }
    private static void checkOffsetNeighborsAndCopy() {
        for(boolean sliding:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(EnumFacing adjacent:EnumFacing.HORIZONTALS) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            BlockPos source=new BlockPos(10,100,10),target=source.add(5,0,0);
            TileEntityProgrammableTrapdoor first=place(world,source,2,sliding);first.setCover(true);first.setCoverFacing(facing);
            ItemStack tool=new ItemStack(ModItems.DUPLIFIER);
            require(ItemDuplifier.copyFrom(world,source,tool)!=null,"offset Duplifier capture failed");
            TileEntityProgrammableTrapdoor second=place(world,source.offset(adjacent),2,!sliding);
            require(first.canOffsetClosedLeaf() && first.isCover() && !first.hasPairLink() && !second.hasPairLink(),"neighbor placement joined offset mount");
            first.setCoverFacing(facing.rotateY());
            require(first.coverFacing()==facing.rotateY(),"neighbor placement locked hinge");
            TileEntityProgrammableTrapdoor copied=place(world,target,0,!sliding);
            require(ItemDuplifier.applyTo(world,target,tool,null),"offset Duplifier application failed");
            require(copied.isCover() && copied.isSliding()==sliding && copied.coverFacing()==facing,"offset Duplifier lost movement, hinge or closed leaf");
            place(world,target.offset(adjacent),2,!sliding);
            copied.configureGroup(copied.getHousingTexture(),2,sliding,0,0,false,true,true,facing.rotateY());
            require(copied.canOffsetClosedLeaf() && copied.isCover() && copied.coverFacing()==facing.rotateY(),"copied offset mount hinge locked after neighbor placement");
            ItemStack configured=ProgrammableSettings.applyToItem(new ItemStack(item),tool.getSubCompound(ItemDuplifier.SETTINGS_TAG));
            BlockPos itemPos=source.add(10,0,0);
            require(item.placeBlockAt(configured,null,world,itemPos,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState()),"copied item placement failed");
            TileEntityProgrammableTrapdoor placed=(TileEntityProgrammableTrapdoor)world.getTileEntity(itemPos);
            require(placed.isCover() && placed.isSliding()==sliding && placed.coverFacing()==facing,"copied item lost movement, hinge or closed leaf");
        }
    }
    private static void checkOpposingCovers() {
        BlockPos base=new BlockPos(10,100,10);
        for(int position=0;position<3;position++)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean sliding:new boolean[]{false,true}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            TileEntityProgrammableTrapdoor first=place(world,base,position,sliding);first.setCover(true);first.setCoverFacing(facing);
            BlockPos opposite=base.offset(facing,3);
            TileEntityProgrammableTrapdoor second=place(world,opposite,position,sliding);second.setCover(true);second.setCoverFacing(facing.getOpposite());
            require(first.coverOverhang()==0 && second.coverOverhang()==0,"opposing offset leaves overshoot seam");
            first.requestOpen(false);second.requestOpen(false);
            AxisAlignedBB a=OffsetTrapdoorInteractions.bounds(first),b=OffsetTrapdoorInteractions.bounds(second);
            require(!a.intersects(b),"opposing leaves overlap");
            double seam=facing.getAxis()==EnumFacing.Axis.X?Math.min(Math.abs(a.maxX-b.minX),Math.abs(b.maxX-a.minX)):Math.min(Math.abs(a.maxZ-b.minZ),Math.abs(b.maxZ-a.minZ));
            require(seam<=2*TrapdoorGeometry.EDGE_CLEARANCE+1e-8,"opposing leaves leave visible seam gap");
            for(TileEntityProgrammableTrapdoor leaf:new TileEntityProgrammableTrapdoor[]{first,second}) {
                AxisAlignedBB bounds=OffsetTrapdoorInteractions.bounds(leaf);BlockPos covered=leaf.getPos().offset(leaf.coverFacing());
                require(Math.abs(bounds.minX-covered.getX())<=TrapdoorGeometry.EDGE_CLEARANCE+1e-8 && Math.abs(bounds.maxX-covered.getX()-1)<=TrapdoorGeometry.EDGE_CLEARANCE+1e-8
                        && Math.abs(bounds.minZ-covered.getZ())<=TrapdoorGeometry.EDGE_CLEARANCE+1e-8 && Math.abs(bounds.maxZ-covered.getZ()-1)<=TrapdoorGeometry.EDGE_CLEARANCE+1e-8,"offset leaf leaves one-pixel mount gap");
                leaf.requestOpen(true);AxisAlignedBB retracted=OffsetTrapdoorInteractions.bounds(leaf);
                require(retracted.minX>=leaf.getPos().getX() && retracted.maxX<=leaf.getPos().getX()+1 && retracted.minZ>=leaf.getPos().getZ() && retracted.maxZ<=leaf.getPos().getZ()+1,"opposing leaf did not retract into owning mount");
            }
            block.breakBlock(world,opposite,world.getBlockState(opposite));world.setBlockToAir(opposite);
            require(first.coverOverhang()==TrapdoorGeometry.COVER_OVERHANG,"single cover lost one-pixel protrusion after opposite removal");
            for(boolean client:new boolean[]{false,true}) {
                NonRenderingChecks.MemoryWorld placement=new NonRenderingChecks.MemoryWorld(client);
                TileEntityProgrammableTrapdoor configured=new TileEntityProgrammableTrapdoor();configured.configure(0,position,sliding,0,0);configured.setCover(true);
                NBTTagCompound settings=configured.itemSettings();settings.setInteger("TrapdoorCoverFacing",facing.getHorizontalIndex());
                ItemStack stack=new ItemStack(item);stack.setTagInfo("BlockEntityTag",settings);
                require(item.placeBlockAt(stack,null,placement,base,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState()),"next-block item placement failed");
                TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)placement.getTileEntity(base);
                require(leaf.isCover() && leaf.coverFacing()==facing && leaf.getPosition()==position && leaf.isSliding()==sliding && placement.getBlockState(base).getValue(BlockTrapDoor.OPEN),"Next block placement did not start open with saved mount settings");
                if(!client){leaf.evaluatePower(false);require(open(placement,base),"unchanged initial power immediately closed newly placed cover");leaf.requestOpen(false);require(!open(placement,base),"newly placed cover cannot close manually");}
            }
        }
        System.out.println("PASS: opposing Next block leaves meet without overlap/mount gaps; new offset items place open on both sides");
    }
    private static void checkPermissions() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos a=new BlockPos(10,100,10),b=a.east();
        TileEntityProgrammableTrapdoor first=place(world,a,0,false);place(world,b,0,false);
        EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TrapdoorChecks")) {
            @Override public boolean isSpectator(){return false;}
            @Override public boolean isCreative(){return capabilities.isCreativeMode;}
            @Override public boolean canPlayerEdit(BlockPos p,EnumFacing f,ItemStack tool){return !p.equals(b);}
        };
        player.setPosition(a.getX(),a.getY(),a.getZ());
        block.onBlockActivated(world,a,world.getBlockState(a),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
        require(!open(world,a) && !open(world,b),"manual toggle bypasses mate permissions");
        NBTTagCompound copied=new NBTTagCompound();copied.setInteger(ProgrammableSettings.WALL_TEXTURE,5);
        require(!ProgrammableSettings.apply(world,a,copied,player) && first.getHousingTexture()==0,"copier bypasses mate permissions");
        com.vandorlabs.container.ContainerProgrammableTrapdoor container=new com.vandorlabs.container.ContainerProgrammableTrapdoor(first);
        require(container.canInteractWith(player),"valid container rejected");player.setPosition(1000,100,1000);require(!container.canInteractWith(player),"out-of-range container accepted");
    }
    private static void checkRecipe() {
        ForgeRegistries.ITEMS.register(ModItems.INDUSTRIAL_ALLOY_INGOT);
        ForgeRegistries.ITEMS.register(ModItems.PROGRAMMABLE_MATTER_INGOT);
        try(java.io.InputStream stream=TrapdoorChecks.class.getResourceAsStream("/assets/vandorlabs/recipes/programmable_trapdoor.json")) {
            require(stream!=null,"recipe missing from resources");
            com.google.gson.JsonObject data=new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(stream,"UTF-8")).getAsJsonObject();
            net.minecraft.item.crafting.IRecipe recipe=net.minecraftforge.common.crafting.CraftingHelper.getRecipe(data,new net.minecraftforge.common.crafting.JsonContext("vandorlabs"));
            net.minecraft.inventory.InventoryCrafting grid=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){
                @Override public boolean canInteractWith(EntityPlayer player){return true;}
            },3,3);
            for(int i=0;i<6;i++)grid.setInventorySlotContents(i,new ItemStack(i==4?ModItems.PROGRAMMABLE_MATTER_INGOT:ModItems.INDUSTRIAL_ALLOY_INGOT));
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            require(recipe.matches(grid,world),"trapdoor recipe does not match");
            ItemStack result=recipe.getCraftingResult(grid);require(result.getItem()==item && result.getCount()==2,"recipe yield");
            for(int i=0;i<6;i++){
                ItemStack saved=grid.getStackInSlot(i);grid.setInventorySlotContents(i,ItemStack.EMPTY);
                require(!recipe.matches(grid,world),"recipe accepts missing ingredient");grid.setInventorySlotContents(i,saved);
            }
            grid.setInventorySlotContents(8,new ItemStack(ModItems.INDUSTRIAL_ALLOY_INGOT));
            require(!recipe.matches(grid,world),"recipe accepts extra ingredient");
        } catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static TileEntityProgrammableTrapdoor place(NonRenderingChecks.MemoryWorld world,BlockPos p,int position,boolean sliding) {
        ItemStack stack=new ItemStack(item);TileEntityProgrammableTrapdoor isolated=new TileEntityProgrammableTrapdoor();
        isolated.configure(0,position,sliding,SpaceDoorData.TRIGGER_REDSTONE_ON,0);stack.setTagInfo("BlockEntityTag",isolated.itemSettings());
        require(item.placeBlockAt(stack,null,world,p,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState()),"place failed");
        return (TileEntityProgrammableTrapdoor)world.getTileEntity(p);
    }
    private static boolean open(NonRenderingChecks.MemoryWorld world,BlockPos p){return world.getBlockState(p).getValue(BlockTrapDoor.OPEN);}
    private static double distance(double[] a,double[] b){return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));}
    private static double[] point(java.nio.ByteBuffer b,int start){return new double[]{b.getFloat(start),b.getFloat(start+4),b.getFloat(start+8)};}
    private static void permutations(int[] values,int at,List<int[]> out) {
        if(at==values.length){out.add(values.clone());return;}
        for(int i=at;i<values.length;i++){int t=values[at];values[at]=values[i];values[i]=t;permutations(values,at+1,out);t=values[at];values[at]=values[i];values[i]=t;}
    }
    private static void require(boolean condition,String message){assertions++;if(!condition)throw new IllegalStateException(message);}
}
