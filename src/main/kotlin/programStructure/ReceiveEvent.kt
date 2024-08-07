package programStructure

import java.io.Serializable
import java.util.function.BiFunction

/**
 * ReceiveEvent is a subclass of [ThreadEvent] that represents a receive event in a program.
 * It contains the value that was received, the event that it receives from, and the location of the receive.
 */
data class ReceiveEvent(

    /**
     * @property tid The thread id of the event.
     */
    override val tid: Int,

    /**
     * @property type The type of the event.
     */
    override val type: EventType = EventType.RECEIVE,

    /**
     * @property serial The serial number of the event.
     */
    override var serial: Int = 0,

    /**
     * @property value The value that was read.
     */
    var value: Message? = null,

    /**
     * @property rf The event that this event reads from.
     */
    var rf: ReceivesFrom? = null,

    var senderId: Long = 0,

    var tag: Long? = null,

    var blocking: Boolean = false,

    var predicate: BiFunction<Long, Long, Boolean>? = null
) : ThreadEvent(), Serializable {

    /**
     * Returns a deep copy of this object
     *
     * @return A deep copy of this object
     */
    override fun deepCopy(): Event {
        return ReceiveEvent(
            tid = copy().tid,
            type = EventType.RECEIVE,
            serial = copy().serial,
            value = copy().value,
            rf = deepCopyRf(),
            tag = copy().tag,
            senderId = copy().senderId,
            blocking = copy().blocking,
            predicate = copy().predicate
        )
    }

    /**
     * Returns a deep copy of the ReadsFrom object
     *
     * @return A deep copy of the ReadsFrom object
     */
    private fun deepCopyRf(): ReceivesFrom? {
        return when (this.rf) {
            is SendEvent -> (this.rf as SendEvent).deepCopy() as ReceivesFrom
            is InitializationEvent -> (this.rf as InitializationEvent).deepCopy() as ReceivesFrom
            else -> null
        }
    }
}