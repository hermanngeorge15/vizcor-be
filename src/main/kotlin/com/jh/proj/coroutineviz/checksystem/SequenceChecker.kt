package com.jh.proj.coroutineviz.checksystem

class SequenceChecker(
    private val recorder: EventRecorder,
) {

    fun checkSequence(coroutineId: String, expectedKinds: Set<String>): Boolean {
        val events = recorder.forCoroutine(coroutineId)
        val actualKinds = events.map { it.kind }
        val expectedKindsList = expectedKinds.toList()

        var setIndex = 0
        for(item in actualKinds){
            if(setIndex < actualKinds.size && item == expectedKindsList[setIndex]){
                setIndex++
            }
        }
        return setIndex == expectedKindsList.size
    }


}