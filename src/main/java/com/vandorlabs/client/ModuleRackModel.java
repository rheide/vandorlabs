package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockModuleRack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Settled racks are ordinary opaque terrain. All racks share finite immutable
 * component geometry, with at most 6561 typed occupancy/visible/charge combinations per
 * facing. getQuads never reads a world, item inventory, animation clock or NBT.
 */
@Mod.EventBusSubscriber(modid = "vandorlabs", value = Side.CLIENT)
public final class ModuleRackModel implements IBakedModel {
    private static final String[] TEXTURES = { "housing", "module", "autopilot_module", "trim", "dark", "indicators", "contacts" };
    private static final ModuleRackModel[] MODELS = new ModuleRackModel[6];
    private final IBakedModel shell;
    private final int facing;
    private int housingChoice = -1;
    private final java.util.LinkedHashMap<Integer, ModuleRackModel> housingModels = new java.util.LinkedHashMap<>(16, .75F, true);

    private final IBakedModel[] modules;
    private final MovingMesh[] moving;
    private final IBakedModel[] autopilotModules;
    private final MovingMesh[] autopilotMoving;
    private final List<BakedQuad>[] autopilotSeated;
    private IBakedModel[][] batteryModules;
    private MovingMesh[][] batteryMoving;
    private List<BakedQuad>[][] batterySeated;
    private final List<BakedQuad>[] seated, empty;
    private final List<BakedQuad>[][] indicators;
    private final AtomicReferenceArray<List<BakedQuad>> combinations = new AtomicReferenceArray<>(6561);
    private final int[] cacheOrder = new int[6561];
    private int cacheRead, cacheWrite, cacheEntries, cacheBytes;
    private static final int COMBINATION_BYTES = 65536;

    private ModuleRackModel(int facing, IBakedModel shell, IBakedModel[] modules, List<BakedQuad>[] seated,
                            List<BakedQuad>[] empty, List<BakedQuad>[][] indicators, IBakedModel[] autopilotModules, List<BakedQuad>[] autopilotSeated) {
        this.facing = facing; this.shell = shell;
        this.autopilotModules = autopilotModules; this.autopilotSeated = autopilotSeated;
        this.autopilotMoving = new MovingMesh[4];
        for(int slot=0;slot<4;slot++)autopilotMoving[slot]=new MovingMesh(autopilotModules[slot]);
        this.modules = modules;
        this.moving = new MovingMesh[4];
        for (int slot = 0; slot < 4; slot++) moving[slot] = new MovingMesh(modules[slot]);
        this.seated = seated;
        this.empty = empty;
        this.indicators = indicators;
    }

    private ModuleRackModel(ModuleRackModel source, int housingChoice, TextureAtlasSprite sprite) {
        this.facing = source.facing; this.housingChoice = housingChoice; shell = new HousingShell(source.shell, sprite, facing);
        batteryModules=source.batteryModules;batteryMoving=source.batteryMoving;batterySeated=source.batterySeated;
        modules = source.modules; moving = source.moving; autopilotModules=source.autopilotModules; autopilotMoving=source.autopilotMoving; autopilotSeated=source.autopilotSeated; seated = source.seated; empty = source.empty; indicators = source.indicators;
    }
    private synchronized ModuleRackModel housing(int choice) {
        ModuleRackModel found = housingModels.get(choice);
        if (found != null) return found;
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(
                com.vandorlabs.tiles.ScreenHousingTextures.texture(choice));
        found = new ModuleRackModel(this, choice, sprite); housingModels.put(choice, found);
        // Every entry shares the same fixed geometry and module meshes. Retain at most sixteen shell finishes per facing.
        if (housingModels.size() > 16) housingModels.remove(housingModels.keySet().iterator().next());
        return found;
    }
    private static final class HousingShell implements IBakedModel {
        private final IBakedModel source; private final TextureAtlasSprite sprite;
        private final List<BakedQuad>[] quads;
        @SuppressWarnings("unchecked") HousingShell(IBakedModel source, TextureAtlasSprite sprite, int facing) {
            this.source = source; this.sprite = sprite; quads = (List<BakedQuad>[])new List<?>[7];
            for (int face = 0; face < 7; face++) {
                List<BakedQuad> built = new ArrayList<>();
                for (BakedQuad quad : source.getQuads(null, face == 6 ? null : EnumFacing.getFront(face), 0))
                    built.add(housingFace(quad, facing)
                            ? RetexturedItemModel.retextureQuad(quad, sprite) : quad);
                quads[face] = Collections.unmodifiableList(built);
            }
        }
        public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long seed) { return quads[side == null ? 6 : side.getIndex()]; }
        public boolean isAmbientOcclusion() { return source.isAmbientOcclusion(); }
        public boolean isGui3d() { return source.isGui3d(); }
        public boolean isBuiltInRenderer() { return source.isBuiltInRenderer(); }
        public TextureAtlasSprite getParticleTexture() { return sprite; }
        public ItemCameraTransforms getItemCameraTransforms() { return source.getItemCameraTransforms(); }
        public ItemOverrideList getOverrides() { return source.getOverrides(); }
    }

    private static boolean housingFace(BakedQuad quad, int facing) {
        int[] data = quad.getVertexData(); int stride = data.length / 4;
        if (stride < 3 || data.length % 4 != 0) return false;
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int vertex=0;vertex<4;vertex++) {
            int offset=vertex*stride; double x=Float.intBitsToFloat(data[offset]),y=Float.intBitsToFloat(data[offset+1]),z=Float.intBitsToFloat(data[offset+2]);
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
        }
        return com.vandorlabs.rack.ModuleRackGeometry.housingFace(facing,quad.getFace().getIndex(),minX,minY,minZ,maxX,maxY,maxZ);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String texture : TEXTURES)
            event.getMap().registerSprite(new ResourceLocation("vandorlabs", "blocks/module_rack/" + texture));
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        for (EnumFacing facing : EnumFacing.values()) {
            int turns = facing == EnumFacing.WEST ? 1 : facing == EnumFacing.NORTH ? 2 : facing == EnumFacing.EAST ? 3 : 0;
            int pitch = facing == EnumFacing.UP ? 90 : facing == EnumFacing.DOWN ? 270 : 0;
            IBakedModel shell = part("shell", pitch, turns);
            IBakedModel[] modules = new IBakedModel[4], autopilotModules = new IBakedModel[4];
            @SuppressWarnings("unchecked") List<BakedQuad>[][] buttons = (List<BakedQuad>[][])new List<?>[4][2];
            @SuppressWarnings("unchecked") List<BakedQuad>[] seated = (List<BakedQuad>[])new List<?>[4];
            @SuppressWarnings("unchecked") List<BakedQuad>[] empty = (List<BakedQuad>[])new List<?>[4];
            @SuppressWarnings("unchecked") List<BakedQuad>[] autopilotSeated = (List<BakedQuad>[])new List<?>[4];
            for (int slot = 0; slot < 4; slot++) {
                autopilotModules[slot] = part("slot_autopilot_"+slot,pitch,turns);
                autopilotSeated[slot] = all(part("slot_seated_autopilot_"+slot,pitch,turns),false);
                modules[slot] = part("slot_" + slot, pitch, turns);
                seated[slot] = all(part("slot_seated_" + slot, pitch, turns), false);
                empty[slot] = all(part("slot_empty_" + slot, pitch, turns), false);
                buttons[slot][0] = all(part("indicator_" + slot + "_off", pitch, turns), true);
                buttons[slot][1] = all(part("indicator_" + slot + "_on", pitch, turns), true);
            }
            ModuleRackModel model = new ModuleRackModel(facing.getIndex(), shell, modules, seated, empty, buttons, autopilotModules, autopilotSeated);
            model.batteryModules=new IBakedModel[4][5];model.batteryMoving=new MovingMesh[4][5];
            @SuppressWarnings("unchecked") List<BakedQuad>[][] battery=(List<BakedQuad>[][])new List<?>[4][5];model.batterySeated=battery;
            for(int slot=0;slot<4;slot++)for(int level=0;level<5;level++){
                model.batteryModules[slot][level]=part("slot_battery_"+slot+"_"+level,pitch,turns);
                model.batteryMoving[slot][level]=new MovingMesh(model.batteryModules[slot][level]);
                model.batterySeated[slot][level]=all(part("slot_seated_battery_"+slot+"_"+level,pitch,turns),false);
            }
            MODELS[facing.getIndex()] = model;
            event.getModelRegistry().putObject(new ModelResourceLocation("vandorlabs:module_rack", "facing=" + facing.getName()), model);
        }
        ModuleRackAnimation.reload();
    }

    private static IBakedModel part(String name, int pitch, int turns) {
        ResourceLocation resource = new ResourceLocation("vandorlabs", "block/module_rack/" + name);
        try {
            IModel source = ModelLoaderRegistry.getModel(resource);
            return source.bake(ModelRotation.getModelRotation(pitch, turns * 90), DefaultVertexFormats.ITEM,
                    texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot bake Module Rack component " + resource, failure);
        }
    }

    private static List<BakedQuad> all(IBakedModel model, boolean lit) {
        List<BakedQuad> result = new ArrayList<>();
        for (BakedQuad quad : model.getQuads(null, null, 0)) result.add(lit ? luminous(quad) : quad);
        for (EnumFacing side : EnumFacing.values())
            for (BakedQuad quad : model.getQuads(null, side, 0)) result.add(lit ? luminous(quad) : quad);
        return Collections.unmodifiableList(result);
    }

    /** Forge's terrain lighter preserves an authored minimum lightmap. Use the
     * actual format's semantic elements, including shader additions, rather than
     * assuming a seven-int vertex or injecting a permanent glowing TESR pass.
     */
    private static BakedQuad luminous(BakedQuad source) {
        VertexFormat format = new VertexFormat(DefaultVertexFormats.BLOCK);
        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(format);
        builder.setTexture(source.getSprite());
        builder.setQuadOrientation(source.getFace());
        builder.setQuadTint(source.getTintIndex());
        builder.setApplyDiffuseLighting(false);
        float[] value = new float[4];
        for (int vertex = 0; vertex < 4; vertex++) for (int element = 0; element < format.getElementCount(); element++) {
            VertexFormatElement target = format.getElement(element);
            if (target.getUsage() == VertexFormatElement.EnumUsage.UV && target.getIndex() == 1) {
                // UV1 is a normalized signed-short pair (240 is maximum block/sky light).
                builder.put(element, 240F / 32767F, 240F / 32767F, 0, 1);
                continue;
            }
            int match = -1;
            for (int candidate = 0; candidate < source.getFormat().getElementCount(); candidate++) {
                VertexFormatElement input = source.getFormat().getElement(candidate);
                if (input.getUsage() == target.getUsage() && input.getIndex() == target.getIndex()) { match = candidate; break; }
            }
            if (match >= 0) {
                LightUtil.unpack(source.getVertexData(), value, source.getFormat(), vertex, match);
                builder.put(element, value);
            } else builder.put(element);
        }
        BakedQuad unpacked = builder.build();
        return new BakedQuad(unpacked.getVertexData(), source.getTintIndex(), source.getFace(), source.getSprite(), false, format);
    }

    public static List<BakedQuad> batteryQuads(EnumFacing facing,int slot,int level){
        ModuleRackModel model=MODELS[facing.getIndex()];
        return model==null||slot<0||slot>=4?Collections.emptyList():model.batterySeated[slot][Math.max(0,Math.min(4,level))];
    }

    static IBakedModel movingModule(EnumFacing facing, int slot) {
        ModuleRackModel model = MODELS[facing.getIndex()];
        return model == null ? null : model.modules[slot];
    }

    static MovingMesh movingMesh(EnumFacing facing, int slot) { return movingMesh(facing,slot,1); }
    static MovingMesh movingMesh(EnumFacing facing, int slot, int kind) {
        ModuleRackModel model = MODELS[facing.getIndex()];
        return model == null ? null : kind==3?model.batteryMoving[slot][0]:kind==2?model.autopilotMoving[slot]:model.moving[slot];
    }

    /** Decode once into format-independent attributes. Animation submits these
     * cached vertices in the current vanilla/shader buffer format without
     * cloning vertex arrays, rebuilding quads or reading item models per frame.
     */
    static final class MovingMesh {
        private final float[] vertices;
        MovingMesh(IBakedModel source) {
            List<BakedQuad> quads = all(source, false);
            vertices = new float[quads.size() * 4 * 9];
            float[] value = new float[4];
            int at = 0;
            for (BakedQuad quad : quads) {
                VertexFormat format = quad.getFormat();
                int position = element(format, VertexFormatElement.EnumUsage.POSITION, 0);
                int color = element(format, VertexFormatElement.EnumUsage.COLOR, 0);
                int uv = element(format, VertexFormatElement.EnumUsage.UV, 0);
                float shade = quad.shouldApplyDiffuseLighting() ? LightUtil.diffuseLight(quad.getFace()) : 1;
                for (int vertex = 0; vertex < 4; vertex++) {
                    LightUtil.unpack(quad.getVertexData(), value, format, vertex, position);
                    for (int axis = 0; axis < 3; axis++) vertices[at++] = value[axis];
                    LightUtil.unpack(quad.getVertexData(), value, format, vertex, color);
                    for (int channel = 0; channel < 3; channel++) vertices[at++] = value[channel] * shade;
                    vertices[at++] = value[3];
                    LightUtil.unpack(quad.getVertexData(), value, format, vertex, uv);
                    vertices[at++] = value[0]; vertices[at++] = value[1];
                }
            }
        }
        private static int element(VertexFormat format, VertexFormatElement.EnumUsage usage, int index) {
            for (int e = 0; e < format.getElementCount(); e++)
                if (format.getElement(e).getUsage() == usage && format.getElement(e).getIndex() == index) return e;
            throw new IllegalStateException("Missing Module Rack mesh attribute " + usage);
        }
        void draw(BufferBuilder buffer, double x, double y, double z, int light) {
            int sky = light >>> 16 & 65535, block = light & 65535;
            for (int at = 0; at < vertices.length; at += 9)
                buffer.pos(vertices[at] + x, vertices[at + 1] + y, vertices[at + 2] + z)
                        .color(vertices[at + 3], vertices[at + 4], vertices[at + 5], vertices[at + 6])
                        .tex(vertices[at + 7], vertices[at + 8]).lightmap(sky, block).endVertex();
        }
    }

    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long seed) {
        if (state instanceof IExtendedBlockState && state.getBlock() instanceof BlockModuleRack) {
            Integer choice = ((IExtendedBlockState)state).getValue(BlockModuleRack.HOUSING);
            if (choice != null && choice >= 0 && choice != housingChoice
                    && com.vandorlabs.tiles.ScreenHousingTextures.validChoice(choice)) return housing(choice).getQuads(state, side, seed);
        }
        // Only the housing reaches block boundaries. Leave its cull faces intact.
        if (side != null) return shell.getQuads(null, side, 0);
        int occupied = 0, visible = 0, kinds = 0, charge = 0;
        if (state instanceof IExtendedBlockState && state.getBlock() instanceof BlockModuleRack) {
            IExtendedBlockState extended = (IExtendedBlockState)state;
            Integer occupiedValue = extended.getValue(BlockModuleRack.OCCUPIED_MASK);
            Integer visibleValue = extended.getValue(BlockModuleRack.MODULE_MASK);
            Integer typed = extended.getValue(BlockModuleRack.MODULE_KINDS);
            Integer levels=extended.getValue(BlockModuleRack.BATTERY_LEVELS);charge=levels==null?0:levels;
            kinds = typed == null ? 0 : typed;
            occupied = occupiedValue == null ? 0 : occupiedValue & 15;
            visible = visibleValue == null ? occupied : visibleValue & occupied;
        }
        int key=0, place=1;
        for(int slot=0;slot<4;slot++,place*=9) {
            int bit=1<<slot,kind=(kinds >>> (slot*2))&3;
            int digit=(occupied&bit)==0?0:(visible&bit)==0?1:kind==3?4+Math.min(4,(charge >>> (slot*3))&7):kind==2?3:2;
            key+=digit*place;
        }
        List<BakedQuad> found = combinations.get(key);
        if (found != null) return found;
        synchronized (combinations) {
            found = combinations.get(key);
            if (found != null) return found;
            List<BakedQuad> result = new ArrayList<>(shell.getQuads(null, null, 0));
            for (int slot = 0; slot < 4; slot++) {
                result.addAll(indicators[slot][(occupied >>> slot) & 1]);
                result.addAll((visible & (1 << slot)) != 0 ? (((kinds >>> (slot*2))&3)==3?batterySeated[slot][Math.min(4,(charge >>> (slot*3))&7)]:((kinds >>> (slot*2))&3)==2?autopilotSeated[slot]:seated[slot]) : empty[slot]);
            }
            found = Collections.unmodifiableList(result);
            int bytes=32+result.size()*8;
            while(cacheEntries>0 && cacheBytes+bytes>COMBINATION_BYTES) {
                int evicted=cacheOrder[cacheRead++]; if(cacheRead==cacheOrder.length)cacheRead=0;
                List<BakedQuad> old=combinations.getAndSet(evicted,null); if(old!=null)cacheBytes-=32+old.size()*8;cacheEntries--;
            }
            combinations.set(key, found); cacheOrder[cacheWrite++]=key;if(cacheWrite==cacheOrder.length)cacheWrite=0;cacheEntries++;cacheBytes+=bytes;
            return found;
        }
    }

    /** Useful for diagnostics: cache capacity depends on appearance states, never world size. */
    int cachedCombinations() {
        int count = 0;
        for (int index = 0; index < combinations.length(); index++) if (combinations.get(index) != null) count++;
        return count;
    }

    @Override public boolean isAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return shell.getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return shell.getItemCameraTransforms(); }
    @Override public ItemOverrideList getOverrides() { return shell.getOverrides(); }
}
