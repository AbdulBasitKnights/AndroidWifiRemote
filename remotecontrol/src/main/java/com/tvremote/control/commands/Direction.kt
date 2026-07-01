package com.tvremote.control.commands

enum class Direction(val rawValue: Byte) {
    UNKNOWN_DIRECTION(0),
    START_LONG(1),
    END_LONG(2),
    SHORT(3),
}
