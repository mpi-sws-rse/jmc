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
 * <p><b>Which strategy supports what.</b>
 * <table>
 *   <caption>Message-passing support by strategy</caption>
 *   <tr><th></th><th>{@code must}</th><th>{@code mp-random}</th><th>other strategies</th></tr>
 *   <tr><td>{@code P2P}, {@code ASYN}</td><td>explored exhaustively</td><td>sampled</td><td>refused</td></tr>
 *   <tr><td>{@code CD}, {@code MBOX}</td><td>refused</td><td>sampled</td><td>refused</td></tr>
 *   <tr><td>{@link org.mpi_sws.jmc.api.mp.Monitors} (notify under {@code CD})</td><td>refused</td>
 *       <td>sampled</td><td>refused</td></tr>
 *   <tr><td>send, blocking and non-blocking receive with guards, {@code Nondet} over several options</td>
 *       <td>yes</td><td>yes</td><td>refused</td></tr>
 *   <tr><td>spawn, create and start, daemons, await, assume</td><td>yes</td><td>yes</td>
 *       <td>ordinary thread events</td></tr>
 *   <tr><td>models mixed in one program</td><td>{@code P2P} and {@code ASYN}</td><td>any</td><td></td></tr>
 *   <tr><td>summary export (one declared model, {@code MpProgram.model()})</td><td>yes</td><td>yes (sampled)</td>
 *       <td>no</td></tr>
 *   <tr><td>online check, graph store, split runs</td><td>yes</td><td>no</td><td>no</td></tr>
 * </table>
 * {@code must} refuses an unsupported model with a fault that names it ("... uses model CD, which must does
 * not support (p2p and asyn only)"); a strategy that is not a message-passing one refuses sends, receives and
 * choices among several options with a {@code JmcUnsupportedEventException}. An export also needs the program's
 * declared roles, its shape and encodable values.
 *
 * <p>The value types — {@code Tid}, {@code MpMessage}, {@code CommunicationModel} — live in
 * {@code org.mpi_sws.jmc.mp.events}; the only runtime-dependent way to ask
 * "who am I" is {@link org.mpi_sws.jmc.api.mp.MpProcess#self()}.
 */
package org.mpi_sws.jmc.api.mp;
