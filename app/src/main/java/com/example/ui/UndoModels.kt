package com.example.ui

import com.example.data.local.SubstanceLogEntity

sealed interface UndoSnapshot {
    data class Edited(
        val previousEntity: SubstanceLogEntity
    ) : UndoSnapshot

    data class Deleted(
        val deletedEntity: SubstanceLogEntity
    ) : UndoSnapshot
}

data class UndoEvent(
    val eventId: Long = System.nanoTime(),
    val message: String,
    val snapshot: UndoSnapshot
)
