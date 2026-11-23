package org.evchargingplatform.station.domain;

/**
 * Great-circle distance and search bounding boxes on a spherical Earth.
 * <p>
 * Haversine is accurate to well under 1% at charging-search distances. It's enough until search
 * moves to PostGIS.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoDistance() {
    }

    public static double km(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * Smallest lat/lng rectangle containing every point within {@code radiusKm} of the centre, used to
     * narrow the database query before the exact distance check. Longitude spans the whole globe when
     * the circle reaches a pole or crosses the antimeridian, rather than wrapping.
     */
    public static BoundingBox boundingBox(double lat, double lng, double radiusKm) {
        double dLat = Math.toDegrees(radiusKm / EARTH_RADIUS_KM);
        double minLat = Math.max(-90, lat - dLat);
        double maxLat = Math.min(90, lat + dLat);
        if (minLat <= -90 || maxLat >= 90) {
            return new BoundingBox(minLat, maxLat, -180, 180);
        }
        double dLng = Math.toDegrees(Math.asin(Math.sin(radiusKm / EARTH_RADIUS_KM) / Math.cos(Math.toRadians(lat))));
        double minLng = lng - dLng;
        double maxLng = lng + dLng;
        if (minLng < -180 || maxLng > 180) {
            return new BoundingBox(minLat, maxLat, -180, 180);
        }
        return new BoundingBox(minLat, maxLat, minLng, maxLng);
    }

    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }
}
