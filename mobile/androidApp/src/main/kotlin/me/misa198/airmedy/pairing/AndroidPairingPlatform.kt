package me.misa198.airmedy.pairing

import java.util.UUID
import me.misa198.airmedy.pairing.PairingClock
import me.misa198.airmedy.pairing.PairingIdGenerator

object AndroidPairingClock : PairingClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

object AndroidPairingIdGenerator : PairingIdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
