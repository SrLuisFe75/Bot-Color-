package com.icon.nexus.visualizer

/**
 * ICON CORE colors. Electric blue, deep blue, indigo, violet, luminous cyan,
 * and white highlights. Alert does not use amber.
 */
object CoreLooks {
    const val COUNT = 6
    const val IDLE = 0
    const val LISTENING = 1
    const val THINKING = 2
    const val SPEAKING = 3
    const val SPEAKING_CYAN = 4
    const val ALERT = 5

    private const val AMBER_RGB = 0xC6A36A

    val field = arrayOf(
        intArrayOf(0xFF0A1848.toInt(), 0xFF16124A.toInt(), 0xFF070614.toInt()),
        intArrayOf(0xFF0E2E8C.toInt(), 0xFF0A3A6A.toInt(), 0xFF070614.toInt()),
        intArrayOf(0xFF2A1468.toInt(), 0xFF14103C.toInt(), 0xFF070614.toInt()),
        intArrayOf(0xFF1A1860.toInt(), 0xFF141040.toInt(), 0xFF070614.toInt()),
        intArrayOf(0xFF103888.toInt(), 0xFF0C3058.toInt(), 0xFF070614.toInt()),
        intArrayOf(0xFF240848.toInt(), 0xFF0C2048.toInt(), 0xFF070614.toInt()),
    )

    val nucleus = arrayOf(
        intArrayOf(0xFFF7FBFF.toInt(), 0xFF8A7CFF.toInt(), 0x008A7CFF),
        intArrayOf(0xFFF4FEFF.toInt(), 0xFF5CEFFF.toInt(), 0x005CEFFF),
        intArrayOf(0xFFF6F2FF.toInt(), 0xFF9A6BFF.toInt(), 0x009A6BFF),
        intArrayOf(0xFFF7F4FF.toInt(), 0xFF8B5CFF.toInt(), 0x008B5CFF),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFF5CEFFF.toInt(), 0x005CEFFF),
        intArrayOf(0xFFFFFFFF.toInt(), 0xFF7AF0FF.toInt(), 0x007A4DFF),
    )

    val glow = arrayOf(
        intArrayOf(0x553A3A9A, 0x22101840, 0x00101840),
        intArrayOf(0x665CEFFF, 0x223D7BFF, 0x003D7BFF),
        intArrayOf(0x669A6BFF, 0x222A1B6B, 0x002A1B6B),
        intArrayOf(0x558B5CFF, 0x222A1B6B, 0x002A1B6B),
        intArrayOf(0x665CEFFF, 0x223D7BFF, 0x003D7BFF),
        intArrayOf(0x66B388FF, 0x225CEFFF, 0x005CEFFF),
    )

    val ring = intArrayOf(
        0xFF5A4EC8.toInt(),
        0xFF3D7BFF.toInt(),
        0xFF8B5CFF.toInt(),
        0xFF7A5CFF.toInt(),
        0xFF5CEFFF.toInt(),
        0xFF7AF0FF.toInt(),
    )

    val mote = intArrayOf(
        0xFFB7A6FF.toInt(),
        0xFFB8FBFF.toInt(),
        0xFFC9B0FF.toInt(),
        0xFFC4B0FF.toInt(),
        0xFFD9FBFF.toInt(),
        0xFFF7FBFF.toInt(),
    )

    const val ALERT_RING = 0xFFC9B0FF.toInt()

    fun usesAmber(color: Int): Boolean = (color and 0xFFFFFF) == AMBER_RGB

    fun alertUsesAmber(): Boolean {
        if (slotUsesAmber(ALERT)) return true
        return usesAmber(ALERT_RING)
    }

    fun anyAmber(): Boolean {
        var slot = 0
        while (slot < COUNT) {
            if (slotUsesAmber(slot)) return true
            slot += 1
        }
        return usesAmber(ALERT_RING)
    }

    private fun slotUsesAmber(slot: Int): Boolean {
        if (channelUsesAmber(field[slot])) return true
        if (channelUsesAmber(nucleus[slot])) return true
        if (channelUsesAmber(glow[slot])) return true
        return usesAmber(ring[slot]) || usesAmber(mote[slot])
    }

    private fun channelUsesAmber(colors: IntArray): Boolean {
        var index = 0
        while (index < colors.size) {
            if (usesAmber(colors[index])) return true
            index += 1
        }
        return false
    }
}
