package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableWall;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Opt-in horizontal junction fixtures placed through the normal block item API. */
final class ConnectionAcceptance {
    private static final String PREFIX = "gallery_acceptance_horizontal_";
    private static final String PILOT = "gallery_acceptance_junction_";
    private static final EnumFacing[] DIRECTIONS = {
            EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};
    // Base-three assignment digits use the active subset of these fixed WORLD roles.
    private static final EnumFacing[] ROLES = {
            null, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    static List<String> names() {
        List<String> result = new ArrayList<>();
        if (Boolean.getBoolean("vandorlabs.connectionAcceptancePilot")) {
            for (int mode = 0; mode < 3; mode++)
                for (int kind = 0; kind < 4; kind++)
                    result.add(PILOT + mode + "_" + kind);
            validateNames(result, true);
            return result;
        }
        if (diagonalFamily()) {
            int mode=Integer.parseInt(System.getProperty("vandorlabs.connectionAcceptanceMode","0"));
            if(mode<0 || mode>2)throw new IllegalArgumentException("Unknown diagonal acceptance mode: "+mode);
            diagonalNames(result,mode);
            validateNames(result,false);
            System.out.println("[vandorlabs] acceptance fixture coverage PASS 60 diagonal mode="+mode+" L18 T24 X18");
            return result;
        }
        int kind = topologyKind();
        int[] variants = kind == 0 ? new int[]{0, 1, 2, 3} : kind == 1 ? new int[]{0, 2, 4, 5} : new int[]{0};
        for (int variant : variants)
            for (int assignment = 0; assignment < (kind == 0 ? 9 : kind == 1 ? 81 : 243); assignment++)
                for (int order = 0; order < (kind == 0 ? 2 : 1); order++)
                    result.add(PREFIX + "0_0_0_0_" + kind + "_" + variant + "_" + assignment + "_" + order);
        validateNames(result, false);
        return result;
    }

    private static boolean diagonalFamily() {
        String family=System.getProperty("vandorlabs.connectionAcceptanceFamily","flat");
        if(!family.equals("flat") && !family.equals("regular") && !family.equals("diagonal"))throw new IllegalArgumentException("Unknown acceptance family: "+family);
        return family.equals("diagonal");
    }
    private static void diagonalNames(List<String> names,int mode) {
        for(int i=0;i<9;i++)for(int order=0;order<2;order++) {
            int variant=i%4;int[] materials=new int[5];materials[0]=i/3;materials[variant<2?1:2]=i%3;
            addDiagonal(names,mode,0,variant,i,order,materials);
        }
        // Fixed relative roles C,F,B,L,R. Each T row names its active arm subset.
        String[] t={"B0110","D1122","A2012","D0101","B2121","D2200","A1000","C0022",
                "A0202","A1211","C2110","B1022","B2201","A2120","D1011","B1210",
                "C2101","D2212","C2000","C2222","A1111","B1111","C1111","D1111"};
        int[][] roles={{0,1,3,4},{0,2,3,4},{0,1,2,3},{0,1,2,4}};
        int[][] variants={{0,1},{2,3},{4,6,8,10},{5,7,9,11}};int[] next=new int[4];
        for(int i=0;i<t.length;i++) {
            int subset=t[i].charAt(0)-'A';int[] materials=new int[5];
            for(int j=0;j<4;j++)materials[roles[subset][j]]=t[i].charAt(j+1)-'0';
            int variant=variants[subset][next[subset]++%variants[subset].length];
            addDiagonal(names,mode,1,variant,i,0,materials);
        }
        // Fifteen verified covering rows plus homogeneous glass/porthole/wall frame cases.
        String[] cross={"00000","01111","02222","10012","11120","12201","20021","21102",
                "22210","00100","00200","01000","01200","02000","02100","11111","22222","00000"};
        for(int i=0;i<cross.length;i++) {
            int[] materials=new int[5];for(int j=0;j<5;j++)materials[j]=cross[i].charAt(j)-'0';
            addDiagonal(names,mode,2,i%4,i,0,materials);
        }
    }
    private static void addDiagonal(List<String> names,int mode,int kind,int variant,int pose,int order,int[] materials) {
        Scene scene=new Scene();scene.family=1;scene.mode=mode;scene.kind=kind;scene.variant=variant;
        scene.direction=(pose%4+pose/4)%4;scene.inversion=(pose/4)%2;scene.order=order;
        int power=1;
        for(Cell cell:cells(scene,0,0)) {
            scene.assignment+=materials[relativeRole(scene,cell.pos,0,0)]*power;power*=3;
        }
        names.add(PREFIX+scene.family+"_"+scene.mode+"_"+scene.direction+"_"+scene.inversion+"_"
                +scene.kind+"_"+scene.variant+"_"+scene.assignment+"_"+scene.order);
    }
    private static int relativeRole(Scene scene,BlockPos pos,int x,int y) {
        BlockPos root=new BlockPos(x,y+2,-18);EnumFacing f=DIRECTIONS[scene.direction];
        if(pos.equals(root))return 0;if(pos.equals(root.offset(f)))return 1;
        if(pos.equals(root.offset(f.getOpposite())))return 2;if(pos.equals(root.offset(f.rotateYCCW())))return 3;
        if(pos.equals(root.offset(f.rotateY())))return 4;throw new IllegalStateException("Unexpected fixture role: "+pos);
    }

    private static int topologyKind() {
        String topology = System.getProperty("vandorlabs.connectionAcceptanceTopology", "l").toLowerCase(java.util.Locale.ROOT);
        if (topology.equals("l")) return 0;
        if (topology.equals("t")) return 1;
        if (topology.equals("x")) return 2;
        throw new IllegalArgumentException("Unknown connection acceptance topology: " + topology);
    }

    private static void validateNames(List<String> names, boolean pilot) {
        boolean diagonal=!pilot && diagonalFamily();
        int selectedKind = pilot || diagonal ? 0 : topologyKind();
        int expected = pilot ? 12 : diagonal ? 60 : selectedKind == 0 ? 72 : selectedKind == 1 ? 324 : 243;
        if (names.size() != expected || new HashSet<>(names).size() != names.size())
            throw new IllegalStateException("Acceptance scene count/uniqueness differs");
        int[] topologies = new int[3];
        for (String name : names) {
            Scene scene = decode(name);
            List<Cell> cells = cells(scene, 0, 0);
            if (cells.size() != (scene.kind == 0 ? 2 : scene.kind == 1 ? 4 : 5))
                throw new IllegalStateException("Acceptance topology differs: " + name);
            int assignment = scene.assignment;
            for (Cell cell : cells) {
                if (cell.material != assignment % 3 || cell.pos.getY() != 2)
                    throw new IllegalStateException("Acceptance role assignment differs: " + name);
                assignment /= 3;
            }
            if (assignment != 0) throw new IllegalStateException("Acceptance assignment exceeds its roles: " + name);
            topologies[scene.kind]++;
        }
        if(diagonal)validateDiagonalCoverage(names,topologies);
        if (!pilot && !diagonal) for (int kind = 0; kind < topologies.length; kind++)
            if (topologies[kind] != (kind == selectedKind ? expected : 0))
                throw new IllegalStateException("Acceptance topology coverage count differs");
    }

    private static void validateDiagonalCoverage(List<String> names,int[] counts) {
        if(counts[0]!=18 || counts[1]!=24 || counts[2]!=18)throw new IllegalStateException("Diagonal topology count differs");
        for(int kind=0;kind<3;kind++) {
            java.util.Set<String> poses=new HashSet<>(),variants=new HashSet<>(),pairs=new HashSet<>();
            java.util.Map<Integer,java.util.Set<Integer>> handDirections=new java.util.HashMap<>(),handPlacements=new java.util.HashMap<>();
            for(String name:names) {
                Scene scene=decode(name);if(scene.kind!=kind)continue;
                if(scene.family!=1)throw new IllegalStateException("Diagonal fixture family differs");
                poses.add(scene.direction+"/"+scene.inversion);variants.add(Integer.toString(scene.variant));
                handDirections.computeIfAbsent(scene.variant,key->new HashSet<>()).add(scene.direction);
                handPlacements.computeIfAbsent(scene.variant,key->new HashSet<>()).add(scene.inversion);
                List<Cell> cells=cells(scene,0,0);int[] material={-1,-1,-1,-1,-1};
                for(Cell cell:cells)material[relativeRole(scene,cell.pos,0,0)]=cell.material;
                if(kind==0) {
                    int other=scene.variant<2?1:2;pairs.add(material[0]+"/"+material[other]);
                    String counterpart=name.substring(0,name.lastIndexOf('_')+1)+(1-scene.order);
                    if(!names.contains(counterpart))throw new IllegalStateException("Diagonal L lacks reverse placement order");
                } else for(int a=0;a<5;a++)for(int b=a+1;b<5;b++)if(material[a]>=0 && material[b]>=0)
                    pairs.add(a+"/"+b+"/"+material[a]+"/"+material[b]);
            }
            if(kind!=1)for(int hand=0;hand<4;hand++)
                if(handDirections.get(hand).size()<(kind==0?2:4) || handPlacements.get(hand).size()!=2)
                    throw new IllegalStateException("Diagonal handedness remains correlated with direction/placement");
            if(poses.size()!=8 || variants.size()!=(kind==1?12:4) || pairs.size()!=(kind==0?9:90))
                throw new IllegalStateException("Diagonal pose/hand/material-pair coverage differs for kind "+kind);
        }
    }

    private static final class Scene {
        int family, mode, direction, inversion, kind, variant, assignment, order;
        boolean inverted() { return inversion != 0; }
    }

    private static Scene decode(String name) {
        Scene scene = new Scene();
        if (name.startsWith(PILOT)) {
            String[] p = name.substring(PILOT.length()).split("_");
            if (p.length != 2) throw new IllegalArgumentException(name);
            scene.family = 1;
            scene.mode = Integer.parseInt(p[0]);
            int pilotKind = Integer.parseInt(p[1]);
            scene.kind = pilotKind < 2 ? 0 : pilotKind == 2 ? 1 : 2;
            scene.order = pilotKind == 1 ? 1 : 0;
            if (scene.mode < 0 || scene.mode > 2 || pilotKind < 0 || pilotKind > 3)
                throw new IllegalArgumentException(name);
        } else {
            if (!name.startsWith(PREFIX)) throw new IllegalArgumentException(name);
            String[] p = name.substring(PREFIX.length()).split("_");
            if (p.length != 8) throw new IllegalArgumentException(name);
            scene.family = Integer.parseInt(p[0]); scene.mode = Integer.parseInt(p[1]);
            scene.direction = Integer.parseInt(p[2]); scene.inversion = Integer.parseInt(p[3]);
            scene.kind = Integer.parseInt(p[4]); scene.variant = Integer.parseInt(p[5]);
            scene.assignment = Integer.parseInt(p[6]); scene.order = Integer.parseInt(p[7]);
            if (scene.family < 0 || scene.family > 1 || scene.mode < 0 || scene.mode > 2 || scene.family == 0 && scene.mode != 0
                    || scene.direction < 0 || scene.direction > 3
                    || scene.inversion < 0 || scene.inversion > (scene.family == 0 ? 0 : 1)
                    || scene.kind < 0 || scene.kind > 2
                    || scene.variant < 0 || scene.variant >= (scene.kind == 1 ? 12 : 4)
                    || scene.assignment < 0 || scene.assignment >= (scene.kind == 0 ? 9 : scene.kind == 1 ? 81 : 243)
                    || scene.order < 0 || scene.order > (scene.kind == 0 ? 1 : 0))
                throw new IllegalArgumentException(name);
        }
        return scene;
    }

    private static final class Cell {
        final BlockPos pos;
        final EnumFacing facing;
        final int material;
        Cell(BlockPos pos, EnumFacing facing, int material) {
            this.pos = pos; this.facing = facing; this.material = material;
        }
    }

    private static List<Cell> cells(Scene scene, int x, int y) {
        BlockPos root = new BlockPos(x, y + 2, -18);
        EnumFacing f = DIRECTIONS[scene.direction], left = f.rotateYCCW(), right = f.rotateY();
        Map<BlockPos, EnumFacing> layout = new LinkedHashMap<>();
        layout.put(root, f);
        if (scene.kind == 0 || scene.kind == 1 && scene.variant < 4) {
            boolean front = scene.variant < 2, handRight = (scene.variant & 1) != 0;
            layout.put(root.offset(front ? f : f.getOpposite()), handRight ? right : left);
            if (scene.kind == 1) {
                layout.put(root.offset(left), f); layout.put(root.offset(right), f);
            }
        } else {
            int hands = scene.kind == 2 ? scene.variant : (scene.variant - 4) / 2;
            layout.put(root.offset(f), (hands & 1) != 0 ? right : left);
            layout.put(root.offset(f.getOpposite()), (hands & 2) != 0 ? right : left);
            if (scene.kind == 2 || (scene.variant - 4) % 2 == 0) layout.put(root.offset(left), f);
            if (scene.kind == 2 || (scene.variant - 4) % 2 != 0) layout.put(root.offset(right), f);
        }
        List<Cell> cells = new ArrayList<>();
        int assignment = scene.assignment;
        for (EnumFacing role : ROLES) {
            BlockPos pos = role == null ? root : root.offset(role);
            EnumFacing facing = layout.get(pos);
            if (facing != null) {
                cells.add(new Cell(pos, facing, assignment % 3));
                assignment /= 3;
            }
        }
        return cells;
    }

    private static Block block(Scene scene, Cell cell) {
        String[] flat = {"programmable_wall", "programmable_glass", "programmable_porthole_wall"};
        String[] diagonal = {"programmable_diagonal_wall", "programmable_diagonal_glass", "programmable_diagonal_porthole"};
        Block block = Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",
                (scene.family == 0 ? flat : diagonal)[cell.material]));
        if (block == null || block == Blocks.AIR) throw new IllegalStateException("Acceptance block missing");
        return block;
    }

    static void build(World world, String name, int x, int y) {
        if (world.isRemote) throw new IllegalStateException("Acceptance placement requires the server world");
        Scene scene = decode(name);
        List<Cell> cells = cells(scene, x, y);
        EntityPlayerMP player = null;
        for (EntityPlayer candidate : world.playerEntities)
            if (candidate instanceof EntityPlayerMP) { player = (EntityPlayerMP) candidate; break; }
        if (player == null) throw new IllegalStateException("Acceptance server player missing");
        world.setWorldTime(6000);
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        ItemStack previous = player.getHeldItemMainhand();
        float yaw = player.rotationYaw, pitch = player.rotationPitch;
        double px = player.posX, py = player.posY, pz = player.posZ;
        List<Cell> placements = new ArrayList<>(cells);
        if (scene.order == 1) Collections.reverse(placements);
        try {
            player.setPosition(x + 8, y + 2, -18);
            for (Cell cell : placements) place(world, player, scene, cell);
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND, previous);
            player.rotationYaw = yaw; player.rotationPitch = pitch;
            player.setPosition(px, py, pz);
        }
        if (!ready(world, name, x, y))
            throw new IllegalStateException("Acceptance fixture state differs: " + name);
        System.out.println("[vandorlabs] acceptance junction placed " + name + " blocks=" + cells.size());
    }

    private static void place(World world, EntityPlayerMP player, Scene scene, Cell cell) {
        EnumFacing side = scene.inverted() ? EnumFacing.DOWN : EnumFacing.UP;
        BlockPos support = cell.pos.offset(side.getOpposite());
        if (!world.isAirBlock(cell.pos) || !world.isAirBlock(support))
            throw new IllegalStateException("Acceptance placement occupied: " + cell.pos);
        Block block = block(scene, cell);
        ItemStack stack = new ItemStack(block);
        if (!(stack.getItem() instanceof ItemBlock)) throw new IllegalStateException("Acceptance ItemBlock missing");
        NBTTagCompound settings = new NBTTagCompound();
        settings.setBoolean("DiagonalFullWidth", scene.mode != 0);
        settings.setBoolean("DiagonalHalfHeight", scene.mode == 2);
        settings.setInteger("DiagonalFill", 0); settings.setInteger("DiagonalJoin", 0);
        settings.setBoolean("JoinPortholes", true);
        settings.setBoolean("GlassJoin", true);
        stack.setTagInfo("BlockEntityTag", settings);
        player.setHeldItem(EnumHand.MAIN_HAND, stack);
        player.rotationYaw = cell.facing.getOpposite().getHorizontalAngle();
        world.setBlockState(support, Blocks.STONE.getDefaultState(), 3);
        try {
            EnumActionResult result = ((ItemBlock) stack.getItem()).onItemUse(player, world, support,
                    EnumHand.MAIN_HAND, side, .5F, .5F, .5F);
            if (result != EnumActionResult.SUCCESS)
                throw new IllegalStateException("Acceptance ItemBlock failed at " + cell.pos + ": " + result);
        } finally { world.setBlockToAir(support); }
        TileEntity raw = world.getTileEntity(cell.pos);
        if (raw instanceof TileEntityAnimatedScreenSelector) {
            TileEntityAnimatedScreenSelector tile = (TileEntityAnimatedScreenSelector) raw;
            if (scene.family == 1 && (BlockProgrammableWall.geometry(world, cell.pos) != scene.mode
                    || tile.isDiagonalFullWidth() != (scene.mode != 0) || tile.getDiagonalFill() != 0))
                tile.setDiagonalGeometry(scene.mode, 0);
            tile.setGlassShade(12);
            tile.setJoinPortholes(true);
        } else if (raw instanceof TileEntityProgrammableGlass) {
            ((TileEntityProgrammableGlass) raw).setShade(12);
            ((TileEntityProgrammableGlass) raw).setJoin(true);
        }
        if (!matches(world, scene, cell)) throw new IllegalStateException("Acceptance placement differs: " + cell.pos);
    }

    private static boolean matches(World world, Scene scene, Cell cell) {
        IBlockState state = world.getBlockState(cell.pos);
        if (state.getBlock() != block(scene, cell)) return false;
        TileEntity raw = world.getTileEntity(cell.pos);
        if (state.getBlock() instanceof BlockGlassWall)
            return state.getValue(BlockGlassWall.ROTATED) == (cell.facing.getAxis() == EnumFacing.Axis.X)
                    && state.getValue(BlockGlassWall.DEPTH) == 0
                    && raw instanceof TileEntityProgrammableGlass
                    && ((TileEntityProgrammableGlass) raw).getPanelFacing(com.vandorlabs.blocks.PanelJoins.facing(state)) == cell.facing
                    && ((TileEntityProgrammableGlass) raw).getShade() == 12
                    && ((TileEntityProgrammableGlass) raw).isJoin();
        if (!(raw instanceof TileEntityAnimatedScreenSelector)
                || state.getValue(BlockProgrammableWall.FACING) != cell.facing
                || state.getValue(BlockProgrammableWall.DEPTH) != 0
                || state.getValue(BlockProgrammableWall.INVERTED) != (scene.family == 1 && scene.inverted()))
            return false;
        TileEntityAnimatedScreenSelector tile = (TileEntityAnimatedScreenSelector) raw;
        return tile.getGlassShade() == 12 && tile.getDiagonalJoin() == 0
                && tile.isJoinPortholes() && (scene.family == 0 || BlockProgrammableWall.geometry(world, cell.pos) == scene.mode
                && tile.isDiagonalHalfHeight() == (scene.mode == 2) && tile.isDiagonalFullWidth() == (scene.mode != 0)
                && tile.getDiagonalFill() == 0);
    }

    /** Wait for client block states and tile packets before spending the screenshot hold ticks. */
    static boolean ready(World world, String name, int x, int y) {
        Scene scene = decode(name);
        List<Cell> cells = cells(scene, x, y);
        Map<BlockPos, Cell> expected = new LinkedHashMap<>();
        for (Cell cell : cells) {
            if (!matches(world, scene, cell)) return false;
            expected.put(cell.pos, cell);
        }
        // This is the same cleared volume used by ReproLab for acceptance fixtures.
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(x - 2, y + 1, -20),
                new BlockPos(x + 2, y + 3, -16)))
            if (!expected.containsKey(pos) && !world.isAirBlock(pos)) return false;
        return true;
    }

    private ConnectionAcceptance() {}
}
