package com.vandorlabs.vehicle;

import com.vandorlabs.physics.RigidTransform;

/** One owned client cosmetic constraint result: phases and bodies must be consumed together. */
public final class VehicleArmVisualFrame {
 public final RigidTransform[] bodies;
 public final double[] phases;
 public VehicleArmVisualFrame(RigidTransform[] bodies,double[] phases){this.bodies=bodies;this.phases=phases;}
}
