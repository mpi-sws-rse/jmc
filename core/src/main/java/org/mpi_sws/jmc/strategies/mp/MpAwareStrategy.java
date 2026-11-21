package org.mpi_sws.jmc.strategies.mp;

/**
 * Marker for a strategy that answers the message-passing events ({@code MP_SEND_EVENT},
 * {@code MP_RECV_REQUEST_EVENT}, {@code ND_EVENT}).
 *
 * <p>Strategies in the {@code RandomSchedulingStrategy} family that do <em>not</em> implement this
 * interface throw {@code JmcUnsupportedEventException} on all three events, so a message-passing
 * program can never run silently unchecked under {@code pct} or an estimator. The trust family
 * throws from {@code EventFactory} instead.
 */
public interface MpAwareStrategy {}
