package programStructure

import java.io.Serializable

data class TaggedMessage(
    override var receiverThreadId: Long,
    override var senderThreadId: Long,
    override var value: Any,
    var tag: Long
) : Message(), Serializable
