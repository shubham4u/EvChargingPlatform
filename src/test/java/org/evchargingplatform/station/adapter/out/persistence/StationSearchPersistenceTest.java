package org.evchargingplatform.station.adapter.out.persistence;

import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorOccupancy;
import org.evchargingplatform.station.domain.ConnectorType;
import org.evchargingplatform.station.domain.GeoDistance;
import org.evchargingplatform.station.domain.Station;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for the queries behind station search, against the real Flyway schema (V1–V9) on H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import({StationPersistenceAdapter.class, ConnectorPersistenceAdapter.class, ConnectorOccupancyAdapter.class})
class StationSearchPersistenceTest {

    @Autowired
    private StationPersistenceAdapter stations;
    @Autowired
    private ConnectorPersistenceAdapter connectors;
    @Autowired
    private ConnectorOccupancyAdapter occupancy;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("bounding-box query returns only active stations inside the box")
    void shouldQueryWithinBox() {
        Station euston = stations.save(Station.register("ST-EUS", "Euston", "GB", new BigDecimal("51.528200"), new BigDecimal("-0.133700")));
        stations.save(Station.register("ST-BTN", "Brighton", "GB", new BigDecimal("50.822500"), new BigDecimal("-0.137200")));
        flushAndClear();

        List<Station> found = stations.findActiveWithin(GeoDistance.boundingBox(51.5308, -0.1238, 5));

        assertEquals(List.of(euston.id()), found.stream().map(Station::id).toList());
        assertEquals(2, stations.findActive().size());
    }

    @Test
    @DisplayName("connectors load for several stations at once, and occupancy round-trips and clears")
    void shouldLoadConnectorsAndOccupancy() {
        Station a = stations.save(Station.register("ST-A", "A", "GB", new BigDecimal("51.5"), new BigDecimal("-0.1")));
        Station b = stations.save(Station.register("ST-B", "B", "GB", new BigDecimal("51.6"), new BigDecimal("-0.2")));
        Connector a1 = connectors.save(Connector.add(a.id(), ConnectorType.CCS2, 150, Clock.systemUTC()));
        connectors.save(Connector.add(b.id(), ConnectorType.TYPE2, 22, Clock.systemUTC()));
        UUID session = UUID.randomUUID();
        occupancy.save(new ConnectorOccupancy(a1.id(), a.id(), session, Instant.now(), Instant.now()));
        flushAndClear();

        assertEquals(2, connectors.findByStationIds(List.of(a.id(), b.id())).size());
        assertTrue(connectors.findByStationIds(List.of()).isEmpty());
        assertEquals(session, occupancy.findByConnectorId(a1.id()).orElseThrow().sessionId());
        assertEquals(1, occupancy.findByConnectorIds(List.of(a1.id(), UUID.randomUUID())).size());

        occupancy.deleteByConnectorId(a1.id());
        flushAndClear();

        assertTrue(occupancy.findByConnectorId(a1.id()).isEmpty());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
