package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.render.TrapdoorGeometry;
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
        checkMesh();checkClickPlacement();checkSettings();checkPairs();checkSquares();checkPower();checkCopy();checkPermissions();checkRecipe();
        System.out.println("PASS: Programmable Trapdoor ("+assertions+" assertions; geometry, texture, placement, pairs, all square orders, channels, copying, permissions)");
    }
    private static void checkMesh() {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=new net.minecraft.client.renderer.texture.TextureAtlasSprite("trapdoor_check"){};
        sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(256,256,32,48,false);
        for(int pos=0;pos<3;pos++)for(boolean slide:new boolean[]{false,true})for(int facing=0;facing<4;facing++)for(int step=0;step<=18;step++) {
            double pose=step/18D;
            double[][] vertices=TrapdoorGeometry.corners(pos,slide,facing,pose);
            require(Math.abs(distance(vertices[0],vertices[1])-1)<1e-8,"leaf width changes");
            require(Math.abs(distance(vertices[0],vertices[4])-1)<1e-8,"leaf length changes");
            require(Math.abs(distance(vertices[0],vertices[2])-3/16D)<1e-8,"leaf thickness changes");
            double[] bounds=TrapdoorGeometry.bounds(pos,slide,facing,pose);
            if(slide || step==0){require(Math.abs(bounds[1]-TrapdoorGeometry.low(pos))<1e-8,"slide changes height");require(Math.abs(bounds[4]-bounds[1]-3/16D)<1e-8,"slide changes thickness");}
            if(step==0 && pos==0)require(bounds[1]==1/16D,"bottom inset");
            if(step==0 && pos==2)require(bounds[4]==15/16D,"top inset");
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
    private static void checkClickPlacement() {
        for(EnumFacing side:EnumFacing.values())for(float hit:new float[]{.1F,.5F,.9F}) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            BlockPos p=new BlockPos(10,100,10);ItemStack stack=new ItemStack(item);
            require(item.placeBlockAt(stack,null,world,p,side,.5F,hit,.5F,block.getDefaultState()),"plain placement failed");
            int expected=side==EnumFacing.UP?0:side==EnumFacing.DOWN?2:hit<1/3F?0:hit>2/3F?2:1;
            require(((TileEntityProgrammableTrapdoor)world.getTileEntity(p)).getPosition()==expected,"click band lost");
            require(stack.getSubCompound("BlockEntityTag")==null,"placement changed held stack");
        }
        for(int pos=0;pos<3;pos++)for(boolean sliding:new boolean[]{false,true}) {
            double[] b=TrapdoorGeometry.bounds(pos,sliding,0,1);
            require(b[5]>=1/16D-1e-8,"open leaf disappeared into neighbor");
            if(sliding)require(Math.abs(b[5]-1/16D)<1e-8,"sliding clearance is not one pixel");
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
