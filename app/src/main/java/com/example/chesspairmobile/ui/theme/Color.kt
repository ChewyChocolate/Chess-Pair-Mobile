package com.example.chesspairmobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Chess-inspired palette
val ChessBrown = Color(0xFF5D4037)
val ChessBrownDark = Color(0xFF3E2723)
val ChessBrownLight = Color(0xFF8D6E63)
val ChessTan = Color(0xFFF5E6D3)
val ChessTanDark = Color(0xFFD7CCC8)
val ChessGreen = Color(0xFF2E7D32)
val ChessGreenDark = Color(0xFF1B5E20)
val ChessGreenLight = Color(0xFF66BB6A)
val ChessGold = Color(0xFFFFA000)
val ChessGoldDark = Color(0xFFFF8F00)
val ChessCream = Color(0xFFFFF8E1)
val ChessMaroon = Color(0xFF800000)
val ChessNavy = Color(0xFF1A237E)
val ChessSlate = Color(0xFF37474F)
val ChessSilver = Color(0xFFB0BEC5)

// Result colors
val WinGreen = Color(0xFF2E7D32)
val LossRed = Color(0xFFC62828)
val DrawGray = Color(0xFF546E7A)
val ByeBlue = Color(0xFF1565C0)
// Standings podium colors
val GoldMedal = Color(0xFFFFD700)
val SilverMedal = Color(0xFFC0C0C0)
val BronzeMedal = Color(0xFFCD7F32)

internal val DarkColors = darkColorScheme(
    primary = ChessGold,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF4E342E),
    onPrimaryContainer = ChessTan,
    secondary = ChessTanDark,
    onSecondary = Color.Black,
    secondaryContainer = ChessBrown,
    onSecondaryContainer = ChessTan,
    tertiary = ChessGreenLight,
    onTertiary = Color.Black,
    tertiaryContainer = ChessGreenDark,
    onTertiaryContainer = Color(0xFFC8E6C9),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    surfaceTint = ChessGold,
    surfaceDim = Color(0xFF121212),
    surfaceBright = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF191919),
    surfaceContainer = Color(0xFF202020),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF333333),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFF4D4D4D),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF690005)
)

internal val LightColors = lightColorScheme(
    primary = ChessBrown,
    onPrimary = Color.White,
    primaryContainer = ChessTan,
    onPrimaryContainer = ChessBrownDark,
    secondary = ChessGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = ChessGreenDark,
    tertiary = ChessGoldDark,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFFFFECB3),
    onTertiaryContainer = ChessBrownDark,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    surfaceTint = ChessBrown,
    surfaceDim = Color(0xFFDED8D2),
    surfaceBright = Color(0xFFFFFBF7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF6F2),
    surfaceContainer = Color(0xFFF5F0EB),
    surfaceContainerHigh = Color(0xFFEFEAE5),
    surfaceContainerHighest = Color(0xFFE9E4DF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFD5CBC3),
    error = LossRed,
    onError = Color.White
)
