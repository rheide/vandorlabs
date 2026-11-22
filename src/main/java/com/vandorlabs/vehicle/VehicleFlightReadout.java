package com.vandorlabs.vehicle;

import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** One controller's preflight measurements and the carried craft's current propulsion. */
public final class VehicleFlightReadout {
    public static final VehicleFlightReadout EMPTY = new VehicleFlightReadout(0, 0, 0, 0, 0, 0, 0, false);
    public final int drives, fuel, capacity;
    public final double mass, effectiveMass, thrust, ratedThrust;
    public final boolean partialMass;

    public VehicleFlightReadout(int drives, int fuel, int capacity, double mass, double effectiveMass, double thrust, double ratedThrust, boolean partialMass) {
        this.drives = drives;
        this.fuel = fuel;
        this.capacity = capacity;
        this.mass = mass;
        this.effectiveMass = effectiveMass;
        this.thrust = thrust;
        this.ratedThrust = ratedThrust;
        this.partialMass = partialMass;
    }

    public double takeoffMass() { return FlightEnvelope.takeoffMass(thrust); }
    public double maximumAltitude() { return FlightEnvelope.maximumAltitude(effectiveMass, thrust); }
    public boolean canFly() { return FlightEnvelope.canFly(effectiveMass, thrust); }

    private static int liftUnits(VehicleStructure structure) {
        int units = 0;
        for (VehicleStructure.Cell cell : structure.liftCells) {
            if (!structure.isActive(cell.pos)) continue;
            String name = cell.state.getBlock().getRegistryName().getResourcePath();
            if (name.startsWith("antigravity_plate")) units++;
            if (name.startsWith("repulsor_array")) units += 2;
        }
        return units;
    }

    public static double effectiveWeight(VehicleStructure structure, double mass) {
        return FlightEnvelope.effectiveMass(mass, liftUnits(structure), 0);
    }

    static final class Sampler {
        private VehicleStructure structure;
        private VehicleWorld view;
        private int liftUnits;
        private VehicleWeight estimate;
        private double mass;
        private boolean partial;
        private long nextMeasure, lastMeasure;

        void inventoryDirty(long now) {
            nextMeasure = Math.min(nextMeasure, Math.max(now, lastMeasure + 20));
        }

        VehicleFlightReadout sample(VehicleWorld current, long now, double scannedMass, boolean scannedPartial) {
            if (current.structure.maxwellCells.isEmpty()) return EMPTY;
            if (current != view || current.structure != structure) {
                boolean worldChanged = current != view;
                boolean topologyChanged = worldChanged || structure.cells.size() != current.structure.cells.size()
                        || !structure.bounds.equals(current.structure.bounds);
                view = current;
                structure = view.structure;
                liftUnits = liftUnits(structure);
                if (worldChanged) {
                    mass = scannedMass;
                    partial = scannedPartial;
                }
                if (topologyChanged) {
                    estimate = null;
                    nextMeasure = now;
                }
            }
            if (!current.parent.isRemote) {
                if (estimate == null && now >= nextMeasure) estimate = new VehicleWeight(structure, view);
                if (estimate != null && estimate.step(1024, System.nanoTime() + 1_000_000L)) {
                    VehicleWeight.Result result = estimate.result();
                    mass = result.units;
                    partial = result.incomplete;
                    estimate = null;
                    lastMeasure = now;
                    // Uncooperative foreign inventories do not always call markDirty.
                    nextMeasure = now + 200;
                }
            }
            int count = 0, fuel = 0, capacity = 0;
            double thrust = 0, ratedThrust = 0;
            for (VehicleStructure.Cell cell : structure.maxwellCells) {
                BlockPos pos=cell.pos;
                if(!structure.isActive(pos))continue;
                TileEntity tile = view.getTileEntity(pos);
                if (!(tile instanceof TileEntityConstructor)) continue;
                TileEntityConstructor drive = (TileEntityConstructor) tile;
                if (!drive.maxwell() || !drive.formed()) continue;
                count++;
                double fullThrust = drive.maxwellThrustUnits();
                ratedThrust += fullThrust;
                fuel += drive.maxwellFuel();
                capacity += drive.maxwellCapacity();
                if (drive.maxwellFuel() > 0) {
                    int throttle=current.parent.isRemote&&drive.screenSettings().redstone?drive.maxwellActiveLevel():drive.maxwellThrustLevel(true);
                    thrust += fullThrust * throttle / 15;
                }
            }
            return new VehicleFlightReadout(count, fuel, capacity, mass, FlightEnvelope.effectiveMass(mass, liftUnits, 0), thrust, ratedThrust, partial);
        }
    }

    public static VehicleFlightReadout preview(VehicleStructure structure, net.minecraft.world.World world, double mass, boolean partial) {
        return structure==null||structure.maxwellCells.isEmpty()?EMPTY:new Sampler().sample(new VehicleWorld(world,structure),0,mass,partial);
    }
}
