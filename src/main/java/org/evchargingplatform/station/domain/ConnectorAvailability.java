package org.evchargingplatform.station.domain;

/**
 * What a driver would find at a connector right now, as seen by the station read side.
 * <p>
 * {@code OCCUPIED} wins over {@code RESERVED}: a driver charging against their reservation is occupying it.
 */
public enum ConnectorAvailability {
    AVAILABLE,
    RESERVED,
    OCCUPIED
}
