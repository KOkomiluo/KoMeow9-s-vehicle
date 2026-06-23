package com.yourname.vehicleframework.data;

import net.minecraft.world.phys.Vec3;

/** Immutable vehicle seat definition in vehicle-local coordinates. */
public record SeatConfig(int index, boolean driver, double x, double y, double z) {

    public static final SeatConfig DEFAULT_DRIVER =
            new SeatConfig(0, true, 0.5, 0.10, -0.2);

    public Vec3 offset() {
        return new Vec3(x, y, z);
    }

    public boolean isValid() {
        return index >= 0
                && Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z);
    }
}
