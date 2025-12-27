package com.jh.proj.coroutineviz.models

class RuntimeSnapshot {
    val coroutines: MutableMap<String, CoroutineNode> = mutableMapOf()
}
