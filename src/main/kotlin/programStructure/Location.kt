package programStructure

data class Location(var obj: String?, var varName : String?){
    fun deepCopy() : Location{
        return this.copy()
    }
}
