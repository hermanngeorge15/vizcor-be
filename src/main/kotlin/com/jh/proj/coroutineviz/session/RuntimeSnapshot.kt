package com.jh.proj.coroutineviz.session

class RuntimeSnapshot {
    val coroutines: MutableMap<String, CoroutineNode> = mutableMapOf()
}