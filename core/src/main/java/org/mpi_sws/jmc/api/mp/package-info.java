/**
 * The user-facing message-passing API of Must (Enea, Giannakopoulou, Kokologiannakis, Majumdar,
 * OOPSLA 2024, §2.1): {@code send^M(t, v)}, blocking and non-blocking selective
 * {@code recv^{M,β}(λx : e)}, {@code nondet(S)}, process spawn/join, and the monitor wrappers of
 * §2.3.6 — for thread-style programs executed under {@code JmcRuntime}.
 *
 * <p><b>This package makes no decisions.</b> Every point
 * at which a program's behaviour could go more than one way — which send a receive reads, what a
 * {@code nondet} returns, whether a blocking receive proceeds — is a {@code JmcRuntimeEvent}
 * answered by the scheduling strategy. This package asks and reports; it holds no communication
 * state and no mutable state of any kind. Keeping these decisions out of the API (for example out
 * of a mailbox class) is what lets a strategy see, and systematically explore, every one of them.
 *
 * <p>Every event that requires an answer is unwrapped with an explicit type check that throws
 * {@code JmcUnsupportedEventException} when the configured strategy did not answer. There is no
 * fallback path.
 *
 * <p>The value types — {@code Tid}, {@code MpMessage}, {@code CommunicationModel} — live in
 * {@code org.mpi_sws.jmc.mp.events}; the only runtime-dependent way to ask
 * "who am I" is {@link org.mpi_sws.jmc.api.mp.MpProcess#self()}.
 */
package org.mpi_sws.jmc.api.mp;
