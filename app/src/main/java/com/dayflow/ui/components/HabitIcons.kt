package com.dayflow.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.ui.graphics.vector.ImageVector

object HabitIcons {
    val all: Map<String, ImageVector> = linkedMapOf(
        "check" to Icons.Filled.Check,
        "book" to Icons.AutoMirrored.Filled.MenuBook,
        "school" to Icons.Filled.School,
        "code" to Icons.Filled.Code,
        "run" to Icons.AutoMirrored.Filled.DirectionsRun,
        "gym" to Icons.Filled.FitnessCenter,
        "water" to Icons.Filled.LocalDrink,
        "meditate" to Icons.Filled.SelfImprovement,
        "sleep" to Icons.Filled.Bedtime,
        "sun" to Icons.Filled.WbSunny,
        "food" to Icons.Filled.Restaurant,
        "write" to Icons.Filled.Edit,
        "language" to Icons.Filled.Language,
        "music" to Icons.Filled.MusicNote,
        "art" to Icons.Filled.Brush,
        "heart" to Icons.Filled.Favorite,
        "spa" to Icons.Filled.Spa,
        "money" to Icons.Filled.Savings,
        "clean" to Icons.Filled.CleaningServices,
        "nophone" to Icons.Filled.MobileOff,
    )

    fun get(key: String): ImageVector = all[key] ?: Icons.Filled.Check
}
