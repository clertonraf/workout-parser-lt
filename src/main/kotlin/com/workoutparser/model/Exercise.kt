package com.workoutparser.model

data class Exercise(
    val name: String,
    val setsReps: String,
    val technique: String = ""
)
