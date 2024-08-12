package programStructure

import java.io.Serializable

data class UnblockedRecvEvent(
    override val tid: Int,
    override val type: EventType = EventType.UNBLOCKED_RECV,
    override val serial: Int,
    var receiveEvent: ReceiveEvent
) : ThreadEvent(), Serializable {
    override fun deepCopy(): Event {
        return UnblockedRecvEvent(
            type = EventType.UNBLOCKED_RECV,
            tid = copy().tid,
            serial = copy().serial,
            receiveEvent = receiveEvent.deepCopy() as ReceiveEvent
        )
    }

}
