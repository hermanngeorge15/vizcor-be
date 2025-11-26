package com.jh.proj

class BaseTestService {
    interface userRepository{
        suspend fun getUser(id: Long):User
    }

    data class User(val id: Long, val name: String, val email: String)

    interface NotificationService{
        suspend fun sendSms(userId: Long)
        suspend fun sendSSE(userId: Long)
        suspend fun sendEmail(userId: Long)
    }
}