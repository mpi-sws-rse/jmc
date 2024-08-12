package programStructure

import java.io.Serializable

data class BlockingRecvReq(
    override val tid: Int,
    override val type: EventType = EventType.BLOCK_RECV_REQ,
    override val serial: Int,
    var receiveEvent: ReceiveEvent
) : ThreadEvent(), Serializable {

    override fun deepCopy(): Event {
        return BlockingRecvReq(
            type = EventType.BLOCK_RECV_REQ,
            tid = copy().tid,
            serial = copy().serial,
            receiveEvent = receiveEvent.deepCopy() as ReceiveEvent
        )
    }
}
