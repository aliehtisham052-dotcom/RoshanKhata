package com.innovation313.roshankhata.screens

/**
 * Where a long form keeps what was typed while its screen is rebuilt (8 Nov 2026 review, M88: 120 of 300 products
 * counted, then dark mode came). The phone gives one that survives a rotation and a killed process; the computer
 * passes none (its windows are not rebuilt). A form writes a flat list of text whenever it changes, and null when it
 * is finished or cancelled; the screen reads it back once when it opens again.
 */
interface DraftKeep {
    fun read(): List<String>?
    fun write(values: List<String>?)
}
