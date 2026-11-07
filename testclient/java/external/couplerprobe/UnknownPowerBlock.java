package external.couplerprobe;
public final class UnknownPowerBlock extends net.minecraft.block.Block {
    public static int calls;
    public UnknownPowerBlock(){super(net.minecraft.block.material.Material.IRON);setRegistryName("coupler_probe:unknown_power");}
    @Override public boolean canProvidePower(net.minecraft.block.state.IBlockState state){return true;}
    @Override public int getWeakPower(net.minecraft.block.state.IBlockState state,net.minecraft.world.IBlockAccess world,net.minecraft.util.math.BlockPos pos,net.minecraft.util.EnumFacing side){calls++;throw new AssertionError("Unknown power callback must never execute");}
}
