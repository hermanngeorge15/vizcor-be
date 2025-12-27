package com.jh.proj.coroutineviz.models

enum class CoroutineState {
    CREATED,
    ACTIVE,
    SUSPENDED,
    WAITING_FOR_CHILDREN,  // Body finished, but waiting for child coroutines
    COMPLETED,
    CANCELLED,
    FAILED,
}
