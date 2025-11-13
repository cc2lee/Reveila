package com.reveila.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform