package programStructure

data class ReadEvent(
    override val tid: Int,
    override val type: EventType = EventType.READ,
    override var serial: Int = 0,
    var value : Any = Any(),
    var rf : ReadsFrom? = null,
    var loc : Location? = null
) : ThreadEvent(){
    override fun deepCopy(): Event {
        val newRead = ReadEvent(tid = this.copy().tid,
            type = EventType.READ,
            serial = this.copy().serial,
            value = this.copy().value,
            rf = null,
            loc = this.loc?.deepCopy())
        if (this.rf != null){
            if (this.rf is WriteEvent){
                newRead.rf = ((this.rf as WriteEvent).deepCopy()) as ReadsFrom
            }else {
                newRead.rf = ((this.rf as Initialization).deepCopy()) as ReadsFrom
            }
        }
        return newRead
    }

}

