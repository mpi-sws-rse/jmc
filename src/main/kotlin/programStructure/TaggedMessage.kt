package programStructure

import java.io.Serializable

data class TaggedMessage(
    override var receiverThreadId: Long,
    override var senderThreadId: Long,
    @Transient
    override var value: Any,
    var tag: Long
) : Message(), Serializable {
    var valueString: String = value.toString()
    override lateinit var sendEvent: SendEvent

    var jmcRecvTid: Long? = null
    var jmcSendTid: Long? = null

    override fun toString(): String {
        return "m(s=$jmcSendTid, r=$jmcRecvTid, tag=$tag, v=$valueString)"
    }
}
