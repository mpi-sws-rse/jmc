package programStructure

import java.io.Serializable

data class BlockedRecvEvent(
    override val tid: Int,
    override val type: EventType = EventType.BLOCKED_RECV,
    override val serial: Int,
    var receiveEvent: ReceiveEvent
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return BlockedRecvEvent(
            type = EventType.BLOCKED_RECV,
            tid = copy().tid,
            serial = copy().serial,
            receiveEvent = receiveEvent.deepCopy() as ReceiveEvent
        )
    }
}