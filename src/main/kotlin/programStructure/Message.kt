package programStructure

import java.io.Serializable

abstract class Message : Serializable {

    abstract var receiverThreadId: Long

    abstract var senderThreadId: Long

    abstract var value: Any

    abstract var sendEvent: SendEvent
}
