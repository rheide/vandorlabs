package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.DuplifierApplyOptions;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.network.MessageProgrammableGlass;
import com.vandorlabs.network.MessageProgrammableWallShade;
import com.vandorlabs.tiles.GlassTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Actual tile, item, copy-mask and packet contracts for configurable glass appearance. */
final class GlassAppearanceRuntimeChecks {
    private GlassAppearanceRuntimeChecks() {}

    static void run(EntityPlayer player) {
        BlockProgrammableGlass regular=(BlockProgrammableGlass)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_glass"));
        Block diagonal=ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS;
        BlockPos pos=new BlockPos(28,4,28);
        int assertions=0;
        for(Block block:new Block[]{regular,diagonal}) {
            player.world.setBlockState(pos,block.getDefaultState(),3);
            for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++)for(boolean frame:new boolean[]{false,true}) {
                if(block==regular) {
                    TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)player.world.getTileEntity(pos);
                    tile.setShade(shade);tile.setAppearance(tint,frame);
                    TileEntityProgrammableGlass loaded=new TileEntityProgrammableGlass();loaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                    require(loaded.getShade()==shade && loaded.getTint()==tint && loaded.isFramed()==frame,"regular NBT");
                    IBlockState actual=regular.getActualState(player.world.getBlockState(pos),player.world,pos);
                    if(!frame)require(!actual.getValue(BlockGlassWall.TOP) && !actual.getValue(BlockGlassWall.INNER_TL),"bare regular frame geometry");
                } else {
                    TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player.world.getTileEntity(pos);
                    tile.setGlassShade(shade);tile.setGlassAppearance(tint,frame);
                    TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                    require(loaded.getGlassShade()==shade && loaded.getGlassTint()==tint && loaded.isGlassFramed()==frame,"diagonal NBT");
                }
                ItemStack picked=block.getPickBlock(player.world.getBlockState(pos),null,player.world,pos,player);
                NBTTagCompound item=picked.getSubCompound(block==regular?"ProgrammableGlassSettings":"BlockEntityTag");
                require(item!=null && item.getInteger(block==regular?"Tint":"GlassTint")==tint && item.getBoolean(block==regular?"Framed":"GlassFramed")==frame,"pick appearance");
                NBTTagCompound capture=ProgrammableSettings.capture(player.world,pos);
                require(capture.getInteger(ProgrammableSettings.GLASS_TINT)==tint && capture.getBoolean(ProgrammableSettings.FRAMED)==frame,"Duplifier capture");
                ItemStack crafted=ProgrammableSettings.applyToItem(new ItemStack(block),capture);
                NBTTagCompound craftedTag=crafted.getSubCompound(block==regular?"ProgrammableGlassSettings":"BlockEntityTag");
                require(craftedTag!=null && craftedTag.getInteger(block==regular?"Tint":"GlassTint")==tint && craftedTag.getBoolean(block==regular?"Framed":"GlassFramed")==frame,"configured crafting");
                assertions++;
            }
        }
        player.world.setBlockToAir(pos);
        TileEntityProgrammableGlass regularLegacy=new TileEntityProgrammableGlass();regularLegacy.readFromNBT(new NBTTagCompound());
        TileEntityAnimatedScreenSelector diagonalLegacy=new TileEntityAnimatedScreenSelector();diagonalLegacy.readFromNBT(new NBTTagCompound());
        require(regularLegacy.getTint()==1 && regularLegacy.isFramed(),"legacy regular appearance");
        require(diagonalLegacy.getGlassTint()==1 && diagonalLegacy.isGlassFramed(),"legacy diagonal appearance");
        NBTTagCompound bare=new NBTTagCompound();bare.setBoolean("GlassFramed",false);diagonalLegacy.readFromNBT(bare);
        require(!diagonalLegacy.isGlassFramed(),"explicit Bare migration");
        NBTTagCompound values=new NBTTagCompound();values.setInteger(ProgrammableSettings.GLASS_TINT,2);values.setBoolean(ProgrammableSettings.FRAMED,true);
        int tintIndex=-1,frameIndex=-1;
        for(int i=0;i<DuplifierApplyOptions.OPTIONS.length;i++) {
            if(DuplifierApplyOptions.OPTIONS[i].key.equals(ProgrammableSettings.GLASS_TINT))tintIndex=i;
            if(DuplifierApplyOptions.OPTIONS[i].key.equals(ProgrammableSettings.FRAMED))frameIndex=i;
        }
        require(tintIndex>=0 && frameIndex>=0,"copy options");
        NBTTagCompound masked=DuplifierApplyOptions.selected(values,DuplifierApplyOptions.ALL&~(1L<<tintIndex));
        require(!masked.hasKey(ProgrammableSettings.GLASS_TINT) && masked.hasKey(ProgrammableSettings.FRAMED),"tint mask independence");
        masked=DuplifierApplyOptions.selected(values,DuplifierApplyOptions.ALL&~(1L<<frameIndex));
        require(masked.hasKey(ProgrammableSettings.GLASS_TINT) && !masked.hasKey(ProgrammableSettings.FRAMED),"frame mask independence");
        packetChecks(pos);
        materialChecks(player,pos);
        insideCornerChecks(player,pos);
        System.out.println("[vandorlabs][reprolab] glass-appearance-live PASS variants="+assertions);
    }
    private static void materialChecks(EntityPlayer player,BlockPos pos){
        Block[] blocks={Block.getBlockFromName("vandorlabs:programmable_glass"),ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS,Block.getBlockFromName("vandorlabs:regular_canopy_glass")};
        for(Block source:blocks)for(Block target:blocks)for(int material:new int[]{-1,0})for(int tint=0;tint<3;tint++){
            player.world.setBlockState(pos,source.getDefaultState(),3);
            player.world.setBlockState(pos.east(6),target.getDefaultState(),3);
            NBTTagCompound values=new NBTTagCompound();values.setInteger(ProgrammableSettings.GLASS_MATERIAL,material);values.setInteger(ProgrammableSettings.GLASS_TINT,tint);
            require(ProgrammableSettings.apply(player.world,pos,values,player),"material source apply");
            NBTTagCompound saved=ProgrammableSettings.capture(player.world,pos);
            require(saved.getInteger(ProgrammableSettings.GLASS_MATERIAL)==material,"material capture");
            require(ProgrammableSettings.apply(player.world,pos.east(6),saved,player),"cross-family material copy");
            NBTTagCompound copied=ProgrammableSettings.capture(player.world,pos.east(6));
            require(copied.getInteger(ProgrammableSettings.GLASS_MATERIAL)==material && copied.getInteger(ProgrammableSettings.GLASS_TINT)==tint,"material and opacity copy");
            if(!(source instanceof com.vandorlabs.blocks.BlockCanopy)){
                ItemStack picked=source.getPickBlock(player.world.getBlockState(pos),null,player.world,pos,player);
                NBTTagCompound item=picked.getSubCompound(source instanceof BlockProgrammableGlass?"ProgrammableGlassSettings":"BlockEntityTag");
                require(item.getInteger(source instanceof BlockProgrammableGlass?"Material":"GlassMaterial")==material,"picked material");
                ItemStack crafted=ProgrammableSettings.applyToItem(picked,values);
                NBTTagCompound craftedData=crafted.getSubCompound(source instanceof BlockProgrammableGlass?"ProgrammableGlassSettings":"BlockEntityTag");
                require(craftedData.getInteger(source instanceof BlockProgrammableGlass?"Material":"GlassMaterial")==material,"configured material crafting");
                net.minecraft.tileentity.TileEntity loaded=source.createTileEntity(player.world,source.getDefaultState());loaded.readFromNBT(player.world.getTileEntity(pos).writeToNBT(new NBTTagCompound()));
                require((loaded instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)loaded).getMaterial():((TileEntityAnimatedScreenSelector)loaded).getGlassMaterial())==material,"material save/load");
            }
            player.world.setBlockToAir(pos);player.world.setBlockToAir(pos.east(6));
        }
        require(com.vandorlabs.tiles.GlassMaterials.opacity(0,0)<com.vandorlabs.tiles.GlassMaterials.opacity(0,1) && com.vandorlabs.tiles.GlassMaterials.opacity(0,2)==1,"strong material tint is opaque");
        System.out.println("[vandorlabs][reprolab] glass-materials PASS cross-family-copy NBT picked-items configured-crafting opacity=35/65/100");
    }

    private static void insideCornerChecks(EntityPlayer player,BlockPos pos) {
        // The ordinary appearance fixture sits directly above superflat ground.
        // Neighbour clipping deliberately splits boxes beside solid supports, so
        // slice-count contracts must run in air rather than count those fragments.
        pos=pos.up(12);
        for(net.minecraft.util.EnumFacing direction:net.minecraft.util.EnumFacing.values())
            require(player.world.isAirBlock(pos.offset(direction)),"isolated corner fixture obstructed: "+pos+" side="+direction+" state="+player.world.getBlockState(pos.offset(direction)));
        int count=0;
        for(Block raw:new Block[]{ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS})
        for(net.minecraft.util.EnumFacing facing:net.minecraft.util.EnumFacing.HORIZONTALS)
        for(boolean inverted:new boolean[]{false,true})for(boolean front:new boolean[]{false,true})for(boolean right:new boolean[]{false,true}) {
            com.vandorlabs.blocks.BlockProgrammableWall block=(com.vandorlabs.blocks.BlockProgrammableWall)raw;
            IBlockState state=block.getDefaultState().withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,facing)
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableWall.INVERTED,inverted);
            BlockPos neighbor=pos.offset(front?facing:facing.getOpposite());
            player.world.setBlockState(pos,state,3);
            player.world.setBlockState(neighbor,state.withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,right?facing.rotateY():facing.rotateYCCW()),3);
            com.vandorlabs.blocks.BlockProgrammableWall.Corner corner=block.corner(state,player.world,pos);
            String settings=" block="+raw.getRegistryName()+" facing="+facing+" inverted="+inverted+" front="+front+" right="+right
                    +" mode="+com.vandorlabs.blocks.BlockProgrammableWall.geometry(player.world,pos)+" fill="+com.vandorlabs.blocks.BlockProgrammableWall.fill(player.world,pos)
                    +" pos="+pos+" neighbor="+player.world.getBlockState(neighbor);
            require(corner!=null,"half-width perpendicular corner not joined"+settings);
            for(double near=-2;near<=6;near+=.25) {
                double left=corner.left(near),end=corner.right(near);
                double arm=corner.armLeft(near,right,front);
                require(left<end && left>=-2 && end<=18,"inside corner has inverted / excessive pane bounds near="+near+" left="+left+" end="+end+settings);
                if(front?near>0:near+4<16)require(left<=arm && end>=arm+4,"inside corner loses part of its connecting arm near="+near+" left="+left+" end="+end+" arm="+arm+settings);
            }
            java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes=new java.util.ArrayList<>();
            block.addCollisionBoxToList(state,player.world,pos,new net.minecraft.util.math.AxisAlignedBB(pos).grow(3),boxes,null,false);
            require(boxes.size()==(front?28:32),"half-width corner collision slice count expected="+(front?28:32)+" actual="+boxes.size()+settings
                    +" clipping="+java.util.Arrays.toString(com.vandorlabs.blocks.DiagonalNeighbourBounds.local(player.world,pos,state,0,1)));
            for(net.minecraft.util.math.AxisAlignedBB box:boxes)require(box.minX<box.maxX && box.minY<box.maxY && box.minZ<box.maxZ,"inside corner degenerate collision box="+box+settings);
            player.world.setBlockToAir(pos);player.world.setBlockToAir(neighbor);count++;
        }
        for(Block raw:new Block[]{ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS})
        for(net.minecraft.util.EnumFacing facing:net.minecraft.util.EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true}) {
            com.vandorlabs.blocks.BlockProgrammableWall block=(com.vandorlabs.blocks.BlockProgrammableWall)raw;
            IBlockState state=block.getDefaultState().withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,facing)
                    .withProperty(com.vandorlabs.blocks.BlockProgrammableWall.INVERTED,inverted);
            BlockPos front=pos.offset(facing),back=pos.offset(facing.getOpposite());
            player.world.setBlockState(pos,state,3);
            player.world.setBlockState(front,state.withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,facing.rotateYCCW()),3);
            player.world.setBlockState(back,state.withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,facing.rotateY()),3);
            com.vandorlabs.blocks.BlockProgrammableWall.Corner bridge=block.corner(state,player.world,pos);
            require(bridge!=null && bridge.left(6)==6 && bridge.right(6)==10,"W bridge retains a tail beyond its corner peak");
            require(bridge.left(3)==3 && bridge.right(3)==13,"W bridge does not terminate at its connecting arms");
            player.world.setBlockToAir(pos);player.world.setBlockToAir(front);player.world.setBlockToAir(back);count++;
        }
        System.out.println("[vandorlabs][reprolab] diagonal-inside-corners-live PASS variants="+count);
    }

    private static void packetChecks(BlockPos pos) {
        for(int tint=0;tint<3;tint++)for(boolean frame:new boolean[]{false,true}) {
            ByteBuf first=Unpooled.buffer(),second=Unpooled.buffer();
            new MessageProgrammableGlass(pos,1,12,true).withAppearance(tint,frame).withMaterial(0).toBytes(first);
            MessageProgrammableGlass decoded=new MessageProgrammableGlass();decoded.fromBytes(first.duplicate());decoded.toBytes(second);
            require(first.equals(second),"regular appearance packet");first.release();second.release();
            first=Unpooled.buffer();second=Unpooled.buffer();
            new MessageProgrammableWallShade(pos,12,false,0).withAppearance(tint,frame).withMaterial(0).toBytes(first);
            MessageProgrammableWallShade wall=new MessageProgrammableWallShade();wall.fromBytes(first.duplicate());wall.toBytes(second);
            require(first.equals(second),"diagonal appearance packet");first.release();second.release();
        }
        ByteBuf old=Unpooled.buffer(),encoded=Unpooled.buffer();
        old.writeLong(pos.toLong()).writeInt(1).writeInt(0).writeBoolean(true);
        MessageProgrammableGlass migrated=new MessageProgrammableGlass();migrated.fromBytes(old);migrated.toBytes(encoded);
        require(encoded.getUnsignedByte(17)==1 && encoded.getBoolean(18),"legacy packet defaults");old.release();encoded.release();
    }

    static void buildGallery(World world) {
        Block regular=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_glass"));
        for(int x=38;x<=52;x++)for(int z=38;z<=46;z++)world.setBlockState(new BlockPos(x,3,z),Blocks.STONE.getDefaultState(),2);
        for(int x=38;x<=52;x++)for(int y=4;y<=8;y++)world.setBlockState(new BlockPos(x,y,46),Blocks.QUARTZ_BLOCK.getDefaultState(),2);
        for(int tint=0;tint<3;tint++)for(int frame=0;frame<2;frame++) {
            int x=40+(tint*2+frame)*2;
            BlockPos flat=new BlockPos(x,5,40),slope=new BlockPos(x,5,43);
            world.setBlockState(flat,regular.getDefaultState(),3);
            TileEntityProgrammableGlass glass=(TileEntityProgrammableGlass)world.getTileEntity(flat);glass.setShade(12);glass.setAppearance(tint,frame==1);
            world.setBlockState(slope,ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState(),3);
            TileEntityAnimatedScreenSelector diagonal=(TileEntityAnimatedScreenSelector)world.getTileEntity(slope);diagonal.setGlassShade(12);diagonal.setGlassAppearance(tint,frame==1);
        }
        BlockPos comparison=new BlockPos(56,6,44);world.setBlockState(comparison,regular.getDefaultState(),3);TileEntityProgrammableGlass comparisonRegular=(TileEntityProgrammableGlass)world.getTileEntity(comparison);comparisonRegular.setShade(12);comparisonRegular.setAppearance(1,true);
        comparison=comparison.east(2);world.setBlockState(comparison,ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState(),3);TileEntityAnimatedScreenSelector comparisonDiagonal=(TileEntityAnimatedScreenSelector)world.getTileEntity(comparison);comparisonDiagonal.setGlassShade(12);comparisonDiagonal.setGlassAppearance(1,true);
        for(int x=54;x<=61;x++)for(int z=42;z<=47;z++)world.setBlockState(new BlockPos(x,4,z),Blocks.STONE.getDefaultState(),2);
        for(int x=54;x<=61;x++)for(int y=5;y<=10;y++)world.setBlockState(new BlockPos(x,y,47),Blocks.QUARTZ_BLOCK.getDefaultState(),2);
        // A joined Bare strip exposes the old coplanar lateral-cap regression.
        for(int x=44;x<=46;x++) {
            BlockPos joined=new BlockPos(x,5,48);
            world.setBlockState(joined,ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState(),3);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(joined);
            tile.setGlassShade(12);tile.setGlassAppearance(1,false);
        }
        for(int material=0;material<2;material++)for(int turn=0;turn<4;turn++) {
            com.vandorlabs.blocks.BlockProgrammableWall block=(com.vandorlabs.blocks.BlockProgrammableWall)(material==0?ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS:ModBlocks.PROGRAMMABLE_DIAGONAL_WALL);
            net.minecraft.util.EnumFacing facing=net.minecraft.util.EnumFacing.getHorizontal(turn);
            BlockPos root=new BlockPos(38+turn*4,5,54+material*5),neighbor=root.offset(facing);
            IBlockState state=block.getDefaultState().withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,facing);
            world.setBlockState(root,state,3);world.setBlockState(neighbor,state.withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,facing.rotateY()),3);
            if(material==0)for(BlockPos at:new BlockPos[]{root,neighbor}) {
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);tile.setGlassShade(12);tile.setGlassAppearance(1,true);
            }
        }
        for(int x=38;x<=52;x++)for(int z=50;z<=68;z++)world.setBlockState(new BlockPos(x,3,z),Blocks.STONE.getDefaultState(),2);
        BlockPos middle=new BlockPos(44,5,64);
        IBlockState glassState=ModBlocks.PROGRAMMABLE_DIAGONAL_GLASS.getDefaultState();
        world.setBlockState(middle,glassState,3);
        world.setBlockState(middle.north(),glassState.withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,net.minecraft.util.EnumFacing.WEST),3);
        world.setBlockState(middle.south(),glassState.withProperty(com.vandorlabs.blocks.BlockProgrammableWall.FACING,net.minecraft.util.EnumFacing.EAST),3);
        for(BlockPos at:new BlockPos[]{middle,middle.north(),middle.south()}) {
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);tile.setGlassShade(12);tile.setGlassAppearance(1,true);
        }
    }
    static void checkRenderMeshes(World world,java.io.File output) throws Exception {
        DiagonalContactChecks.run(world,output);
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(GlassTextures.texture(12,1,1));
        for(int x=44;x<=46;x++) {
            BlockPos pos=new BlockPos(x,5,48);
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
            require(tile!=null,"joined glass fixture not synchronized");
            StaticSurfaceMesh mesh=DiagonalWallMeshCache.get(tile,world.getBlockState(pos),sprite,sprite);
            // withoutPlane returns the same immutable mesh when that plane has no quads.
            require((mesh.withoutPlane(0,0)==mesh)==(x!=44),"left shared cap remains / exposed cap lost");
            require((mesh.withoutPlane(0,16)==mesh)==(x!=46),"right shared cap remains / exposed cap lost");
        }
        System.out.println("[vandorlabs][reprolab] glass-bare-seams-live PASS shared caps absent, exposed caps retained");
        TileEntityAnimatedScreenSelector framed=(TileEntityAnimatedScreenSelector)world.getTileEntity(new BlockPos(42,5,43));
        StaticSurfaceMesh rails=DiagonalWallMeshCache.frame(framed,world.getBlockState(framed.getPos()),sprite);
        require(rails!=null && rails.vertexCount()>0,"framed glass has no face rails");
        net.minecraft.client.renderer.BufferBuilder buffer=new net.minecraft.client.renderer.BufferBuilder(4096);
        net.minecraft.client.renderer.vertex.VertexFormat format=BlockSurfaceFormat.get();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,format);
        rails.draw(buffer,0,0);buffer.finishDrawing();java.nio.ByteBuffer vertices=buffer.getByteBuffer();int stride=format.getNextOffset();
        java.lang.reflect.Field payload=StaticSurfaceMesh.class.getDeclaredField("vertices");payload.setAccessible(true);
        float[] data=(float[])payload.get(rails);int faceRails=0,solidCaps=0;
        net.minecraft.client.renderer.texture.TextureAtlasSprite metal=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
        require(metal!=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite(),"established glass interior texture missing from atlas");
        for(int v=0;v<buffer.getVertexCount();v+=4) {
            if(Math.abs(data[v*8+6])<1E-5 || Math.abs(data[v*8+6])>.99999){solidCaps++;float lowU=Float.POSITIVE_INFINITY,highU=Float.NEGATIVE_INFINITY,lowV=lowU,highV=highU;for(int corner=0;corner<4;corner++){float u=data[(v+corner)*8+3],t=data[(v+corner)*8+4];require(u>=metal.getMinU()-1e-6 && u<=metal.getMaxU()+1e-6 && t>=metal.getMinV()-1e-6 && t<=metal.getMaxV()+1e-6,"rim interior must sample the established metal_side sprite");lowU=Math.min(lowU,u);highU=Math.max(highU,u);lowV=Math.min(lowV,t);highV=Math.max(highV,t);}require((highU-lowU)/(metal.getMaxU()-metal.getMinU())>.1||(highV-lowV)/(metal.getMaxV()-metal.getMinV())>.1,"rim interior collapsed established UV scale to one texel");continue;}
            faceRails++;double width=0;
            for(int axis=0;axis<3;axis++){double difference=vertices.getFloat(v*stride+axis*4)-vertices.getFloat((v+3)*stride+axis*4);width+=difference*difference;}
            require(Math.abs(width-1)<1E-4,"diagonal frame rail differs from regular one-pixel width");
        }
        require(faceRails>0 && solidCaps>=8,"frame lacks closed outer/inner top-bottom-side caps");
        System.out.println("[vandorlabs][reprolab] glass-one-pixel-frame-live PASS faceRails="+faceRails+" solidCaps="+solidCaps);
        TileEntityAnimatedScreenSelector middle=(TileEntityAnimatedScreenSelector)world.getTileEntity(new BlockPos(44,5,64));
        StaticSurfaceMesh bridge=DiagonalWallMeshCache.get(middle,world.getBlockState(middle.getPos()),sprite,sprite);
        require(bridge.withoutPlane(2,0)==bridge && bridge.withoutPlane(2,16)==bridge,"W internal glass end caps remain");
        require(DiagonalWallMeshCache.frame(middle,world.getBlockState(middle.getPos()),sprite).vertexCount()>0,"W outer perimeter frame missing");
        System.out.println("[vandorlabs][reprolab] glass-W-joins-live PASS internal caps absent, perimeter retained");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException("Glass appearance: "+message); }
}
