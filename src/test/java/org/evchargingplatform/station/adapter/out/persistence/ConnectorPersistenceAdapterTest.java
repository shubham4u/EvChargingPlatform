package org.evchargingplatform.station.adapter.out.persistence;

import org.evchargingplatform.station.domain.Connector;
import org.evchargingplatform.station.domain.ConnectorType;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for {@link ConnectorPersistenceAdapter} against the real Flyway schema on H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa-test")
@Import({ConnectorPersistenceAdapter.class, StationPersistenceAdapter.class})
class ConnectorPersistenceAdapterTest {

    @Autowired
    private ConnectorPersistenceAdapter connectors;

    @Autowired
    private StationPersistenceAdapter stations;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("should list a station's connectors and find one only at its own station")
    void shouldScopeConnectorsToStation() {
        Station station = stations.save(Station.register("ST-001", "Central", "GB",
                new BigDecimal("51.507400"), new BigDecimal("-0.127800")));
        Connector ccs = connectors.save(Connector.add(station.id(), ConnectorType.CCS2, 150, Clock.systemUTC()));
        connectors.save(Connector.add(station.id(), ConnectorType.TYPE2, 22, Clock.systemUTC()));
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, connectors.findByStationId(station.id()).size());
        assertEquals(ConnectorType.CCS2, connectors.findByIdAndStationId(ccs.id(), station.id()).orElseThrow().connectorType());
        assertTrue(connectors.findByIdAndStationId(ccs.id(), UUID.randomUUID()).isEmpty());
    }
}
