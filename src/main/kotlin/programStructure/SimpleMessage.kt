package programStructure

import java.io.Serializable

data class SimpleMessage(
    override var receiverThreadId: Long,
    override var senderThreadId: Long,
    override var value: Any
) : Message(), Serializable
