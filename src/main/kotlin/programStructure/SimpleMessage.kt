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
}
