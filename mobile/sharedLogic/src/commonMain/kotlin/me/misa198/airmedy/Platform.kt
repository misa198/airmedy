package me.misa198.airmedy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform