package com.liguyano.mcaimod.context;

public record NearbyEntity(String type, double distance) {
    public NearbyEntity {
        if (type == null || type.isBlank() || !Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Invalid nearby entity");
        }
    }
}
