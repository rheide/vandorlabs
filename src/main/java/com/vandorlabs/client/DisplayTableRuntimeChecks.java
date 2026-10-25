package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockIndustrialTable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Live Forge checks for the connected Industrial Table. */
public final class DisplayTableRuntimeChecks {
    private DisplayTableRuntimeChecks() {}

    public static void run(World world, EntityPlayer player) {
        Block raw = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs", "industrial_table"));
        require(raw instanceof BlockIndustrialTable, "table registration");
        BlockIndustrialTable table = (BlockIndustrialTable) raw;
        ItemStack stack = new ItemStack(table);
        require("Industrial Table".equals(stack.getDisplayName()), "table item name");
        Minecraft mc = Minecraft.getMinecraft();
        require(mc.getRenderItem().getItemModelMesher().getItemModel(stack)
                != mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel(),
                "table item model");

        BlockPos left = new BlockPos(-30, 28, -30);
        BlockPos middle = left.east();
        BlockPos right = middle.east();
        BlockPos[] positions = {left, middle, right};
        IBlockState[] original = new IBlockState[positions.length];
        for (int i = 0; i < positions.length; i++) original[i] = world.getBlockState(positions[i]);
        IBlockState north = table.getDefaultState().withProperty(BlockIndustrialTable.FACING,
                EnumFacing.NORTH);
        try {
            for (BlockPos pos : positions) world.setBlockState(pos, north, 3);
            check(table, world, left, false, true);
            check(table, world, middle, true, true);
            check(table, world, right, true, false);
            world.setBlockState(middle, north.withProperty(BlockIndustrialTable.FACING,
                    EnumFacing.SOUTH), 3);
            check(table, world, left, false, false);
            check(table, world, right, false, false);
            world.setBlockState(middle, north, 3);
            world.setBlockToAir(middle);
            check(table, world, left, false, false);
            check(table, world, right, false, false);
            require(table.isSideSolid(north, world, left, EnumFacing.UP), "tabletop support");
            IBlockState hanging = table.getStateForPlacement(world, middle,
                    EnumFacing.DOWN, 0.5F, 0.5F, 0.5F, 0, player, EnumHand.MAIN_HAND);
            require(hanging.getValue(BlockIndustrialTable.UPSIDE_DOWN), "ceiling placement");
            require(table.getStateFromMeta(table.getMetaFromState(hanging))
                    .getValue(BlockIndustrialTable.UPSIDE_DOWN), "ceiling metadata");
            require(table.isSideSolid(hanging, world, middle, EnumFacing.DOWN),
                    "hanging tabletop support");
            world.setBlockState(middle, hanging, 3);
            check(table, world, left, false, false);
            check(table, world, middle, false, false);
        } finally {
            for (int i = 0; i < positions.length; i++)
                world.setBlockState(positions[i], original[i], 3);
        }
        models(world,player,table);
        System.out.println("[vandorlabs][reprolab] display-table-runtime PASS");
    }

    private static java.util.List<Object> quads(net.minecraft.client.renderer.block.model.IBakedModel model,IBlockState state){
        java.util.List<Object> result=new java.util.ArrayList<>();java.util.List<EnumFacing> sides=new java.util.ArrayList<>();sides.add(null);sides.addAll(java.util.Arrays.asList(EnumFacing.values()));
        for(EnumFacing side:sides)for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(state,side,0)){
            java.util.Map<String,Object> entry=new java.util.LinkedHashMap<>();entry.put("sprite",quad.getSprite().getIconName().replace(":blocks/",":block/"));java.util.List<Object> vertices=new java.util.ArrayList<>();int[] data=quad.getVertexData();int stride=data.length/4;
            for(int i=0;i<4;i++){int at=i*stride;vertices.add(new double[]{Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]),(Float.intBitsToFloat(data[at+4])-quad.getSprite().getMinU())/(quad.getSprite().getMaxU()-quad.getSprite().getMinU()),(Float.intBitsToFloat(data[at+5])-quad.getSprite().getMinV())/(quad.getSprite().getMaxV()-quad.getSprite().getMinV())});}entry.put("vertices",vertices);result.add(entry);
        }return result;
    }
    private static void models(World world,EntityPlayer player,BlockIndustrialTable table){
        BlockPos root=new BlockPos(-30,28,-30);java.util.Map<BlockPos,IBlockState> saved=new java.util.LinkedHashMap<>();saved.put(root,world.getBlockState(root));for(EnumFacing face:EnumFacing.Plane.HORIZONTAL)saved.put(root.offset(face),world.getBlockState(root.offset(face)));
        java.util.List<Object> rows=new java.util.ArrayList<>();EnumFacing[] faces={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
        try{for(int i=0;i<32;i++){
            for(EnumFacing face:EnumFacing.Plane.HORIZONTAL)world.setBlockToAir(root.offset(face));EnumFacing face=faces[i/8];int mask=i%4;IBlockState state=table.getDefaultState().withProperty(BlockIndustrialTable.FACING,face).withProperty(BlockIndustrialTable.UPSIDE_DOWN,i%8>=4);world.setBlockState(root,state,3);
            if((mask&1)!=0)world.setBlockState(root.offset(face.rotateYCCW()),state,3);if((mask&2)!=0)world.setBlockState(root.offset(face.rotateY()),state,3);IBlockState actual=table.getActualState(state,world,root);
            java.util.List<Object> emitted=quads(Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(actual),actual);require(emitted.size()==24-6*Integer.bitCount(mask),"canonical model strut count "+i);java.util.Map<String,Object> row=new java.util.LinkedHashMap<>();row.put("index",i);row.put("quads",emitted);
            java.util.List<net.minecraft.util.math.AxisAlignedBB> boxes=new java.util.ArrayList<>();table.addCollisionBoxToList(state,world,root,new net.minecraft.util.math.AxisAlignedBB(root.getX(),root.getY(),root.getZ(),root.getX()+1,root.getY()+1,root.getZ()+1),boxes,null,false);java.util.List<double[]> collision=new java.util.ArrayList<>();for(net.minecraft.util.math.AxisAlignedBB box:boxes)collision.add(new double[]{box.minX-root.getX(),box.minY-root.getY(),box.minZ-root.getZ(),box.maxX-root.getX(),box.maxY-root.getY(),box.maxZ-root.getZ()});row.put("collision",collision);rows.add(row);
        }}finally{for(java.util.Map.Entry<BlockPos,IBlockState> entry:saved.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),3);}
        java.util.Map<String,Object> output=new java.util.LinkedHashMap<>();output.put("states",rows);output.put("item",quads(Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(new ItemStack(table),world,player),null));
        String folder=System.getenv("VANDOR_LABS_REPRO_OUT");require(folder!=null,"missing reproducible model output directory");try{java.nio.file.Files.write(java.nio.file.Paths.get(folder,"industrial-table-models.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(output).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        System.out.println("[vandorlabs][reprolab] display-table-models PASS 32 actual canonical baked states and item geometry exported");
    }

    private static void check(BlockIndustrialTable table, World world, BlockPos pos,
            boolean left, boolean right) {
        IBlockState actual = table.getActualState(world.getBlockState(pos), world, pos);
        require(actual.getValue(BlockIndustrialTable.LEFT) == left
                        && actual.getValue(BlockIndustrialTable.RIGHT) == right,
                "table connections at " + pos);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("display table: " + message);
    }
}
