package programStructure

import java.io.Serializable

data class SimpleMessage(
    override var receiverThreadId: Long,
    override var senderThreadId: Long,
    @Transient
    override var value: Any
) : Message(), Serializable {
    var valueString: String = value.toString()
    override lateinit var sendEvent: SendEvent
    var jmcRecvTid: Long = receiverThreadId
    var jmcSendTid: Long = senderThreadId

    override fun toString(): String {
        return "m(s=$jmcSendTid, r=$jmcRecvTid, v=$valueString)"
    }
}
