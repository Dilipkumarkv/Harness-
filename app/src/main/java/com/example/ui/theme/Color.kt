package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Reference Palette from uploaded swatch image:
// 1. #0A1931 - Midnight Abyss Navy
// 2. #B3CFE5 - Ice Sky Blue
// 3. #4A7FA7 - Oceanic Steel Blue
// 4. #1A3D63 - Marine Deep Navy
// 5. #F6FAFD - Glacier Ice Frost White

val OceanDark900 = Color(0xFF0A1931) // Primary Root Background (#0A1931)
val OceanDark800 = Color(0xFF132847) // Surface Deep Container
val OceanDark700 = Color(0xFF1A3D63) // Elevated Surface / Card Background (#1A3D63)
val OceanSteel   = Color(0xFF4A7FA7) // Steel Blue Accent / Active Elements (#4A7FA7)
val OceanIceSky  = Color(0xFFB3CFE5) // Ice Sky Blue Primary Brand (#B3CFE5)
val OceanIceWhite= Color(0xFFF6FAFD) // Glacier Frost White Text (#F6FAFD)

val OceanBorder  = Color(0xFF264C72) // High-contrast border between 1A3D63 and 4A7FA7
val OceanMuted   = Color(0xFF8FAAC2) // Readable secondary text & captions

// Semantic & Diff Accents (harmonized with Ocean theme)
val OceanAqua    = Color(0xFF5CE1E6) // Tool actions / diagnostics
val OceanSuccess = Color(0xFF38E1B0) // Success status & additions
val OceanError   = Color(0xFFFB7185) // Error status & removals
val OceanWarning = Color(0xFFFCD34D) // Warnings & doctor alerts

// Diff Colors
val DiffAddedBg = Color(0x3338E1B0)
val DiffAddedText = Color(0xFF5EEAD4)
val DiffRemovedBg = Color(0x33FB7185)
val DiffRemovedText = Color(0xFFFDA4AF)
