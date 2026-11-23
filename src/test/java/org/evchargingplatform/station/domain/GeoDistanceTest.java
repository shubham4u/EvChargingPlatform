package org.evchargingplatform.station.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GeoDistance}, checked against published great-circle distances.
 */
class GeoDistanceTest {

    // King's Cross, London and Gare du Nord, Paris
    private static final double KX_LAT = 51.5308, KX_LNG = -0.1238;
    private static final double GDN_LAT = 48.8809, GDN_LNG = 2.3553;

    @Test
    @DisplayName("London King's Cross to Paris Gare du Nord is about 344 km")
    void shouldMatchKnownDistance() {
        assertEquals(344, GeoDistance.km(KX_LAT, KX_LNG, GDN_LAT, GDN_LNG), 2);
    }

    @Test
    @DisplayName("distance is zero to itself and symmetric")
    void shouldBeZeroAndSymmetric() {
        assertEquals(0, GeoDistance.km(KX_LAT, KX_LNG, KX_LAT, KX_LNG), 1e-9);
        assertEquals(GeoDistance.km(KX_LAT, KX_LNG, GDN_LAT, GDN_LNG), GeoDistance.km(GDN_LAT, GDN_LNG, KX_LAT, KX_LNG), 1e-9);
    }

    @Test
    @DisplayName("bounding box contains every point on the search circle")
    void shouldContainCircle() {
        GeoDistance.BoundingBox box = GeoDistance.boundingBox(KX_LAT, KX_LNG, 10);
        for (int bearing = 0; bearing < 360; bearing += 15) {
            double rad = Math.toRadians(bearing);
            // walk 9.99 km in each direction (small-distance approximation is plenty here)
            double lat = KX_LAT + Math.toDegrees(9.99 / 6371.0088 * Math.cos(rad));
            double lng = KX_LNG + Math.toDegrees(9.99 / 6371.0088 * Math.sin(rad) / Math.cos(Math.toRadians(KX_LAT)));
            assertTrue(lat >= box.minLat() && lat <= box.maxLat() && lng >= box.minLng() && lng <= box.maxLng(),
                    "bearing " + bearing + " falls outside the box");
        }
        assertTrue(box.maxLat() - box.minLat() < 0.2, "box should stay tight around 10 km");
    }

    @Test
    @DisplayName("bounding box spans all longitudes across the antimeridian or near a pole")
    void shouldWidenAtEdges() {
        GeoDistance.BoundingBox fiji = GeoDistance.boundingBox(-17.7, 179.9, 50);
        assertEquals(-180, fiji.minLng());
        assertEquals(180, fiji.maxLng());

        GeoDistance.BoundingBox pole = GeoDistance.boundingBox(89.99, 10, 50);
        assertEquals(90, pole.maxLat());
        assertEquals(-180, pole.minLng());
    }
}
