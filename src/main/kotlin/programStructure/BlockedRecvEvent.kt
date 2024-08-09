package programStructure

import java.io.Serializable

data class BlockedRecvEvent(
    override val type: EventType,
    override val tid: Int,
    override val serial: Int,
    var receiveEvent: ReceiveEvent
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return BlockedRecvEvent(
            type = EventType.SUSPEND,
            tid = copy().tid,
            serial = copy().serial,
            receiveEvent = receiveEvent.deepCopy() as ReceiveEvent
        )
    }
}