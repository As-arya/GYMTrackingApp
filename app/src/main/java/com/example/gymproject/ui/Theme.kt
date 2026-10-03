package com.example.gymproject.ui

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import com.example.gymproject.R

private val poppins=FontFamily(Font(R.font.poppins_regular),Font(R.font.poppins_medium,FontWeight.Medium),
    Font(R.font.poppins_semibold,FontWeight.SemiBold),Font(R.font.poppins_bold,FontWeight.Bold))
private fun type(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=poppins,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight,letterSpacing=0.sp)
val gymTypography=Typography(
    displayLarge=type(64,80,FontWeight.Bold),displayMedium=type(42,54,FontWeight.Bold),displaySmall=type(32,42,FontWeight.Bold),
    headlineLarge=type(28,36,FontWeight.Bold),headlineMedium=type(28,36,FontWeight.Bold),headlineSmall=type(20,28,FontWeight.Bold),
    titleLarge=type(20,28,FontWeight.Bold),titleMedium=type(16,24,FontWeight.Medium),titleSmall=type(14,20,FontWeight.Medium),
    bodyLarge=type(16,24),bodyMedium=type(14,20),bodySmall=type(12,18),
    labelLarge=type(14,20,FontWeight.Medium),labelMedium=type(12,18,FontWeight.Medium),labelSmall=type(10,14))

fun gymColors(dark:Boolean)=if(dark) darkColorScheme(
    primary=Color(0xFFAAD2AF),onPrimary=Color(0xFF172E20),primaryContainer=Color(0xFF314C38),onPrimaryContainer=Color(0xFFF1F0E7),
    secondary=Color(0xFFAAD2AF),onSecondary=Color(0xFF172E20),secondaryContainer=Color(0xFF314C38),onSecondaryContainer=Color(0xFFF1F0E7),
    background=Color(0xFF171C18),surface=Color(0xFF222A24),surfaceVariant=Color(0xFF2C362E),
    surfaceDim=Color(0xFF171C18),surfaceBright=Color(0xFF2C362E),surfaceContainerLowest=Color(0xFF171C18),surfaceContainerLow=Color(0xFF222A24),
    surfaceContainer=Color(0xFF222A24),surfaceContainerHigh=Color(0xFF2C362E),surfaceContainerHighest=Color(0xFF2C362E),
    onSurface=Color(0xFFF1F0E7),onBackground=Color(0xFFF1F0E7),onSurfaceVariant=Color(0xFFB6C1B7),
    outline=Color(0xFF3A473D),outlineVariant=Color(0xFF3A473D),error=Color(0xFFF3A79F))
else lightColorScheme(
    primary=Color(0xFF35634B),onPrimary=Color(0xFFFFFCF5),primaryContainer=Color(0xFFDFE8D9),onPrimaryContainer=Color(0xFF24382B),
    secondary=Color(0xFF35634B),onSecondary=Color(0xFFFFFCF5),secondaryContainer=Color(0xFFDFE8D9),onSecondaryContainer=Color(0xFF24382B),
    background=Color(0xFFF6F2E9),surface=Color(0xFFFFFCF5),surfaceVariant=Color(0xFFEEEADF),
    surfaceDim=Color(0xFFEEEADF),surfaceBright=Color(0xFFFFFCF5),surfaceContainerLowest=Color(0xFFFFFCF5),surfaceContainerLow=Color(0xFFFFFCF5),
    surfaceContainer=Color(0xFFFFFCF5),surfaceContainerHigh=Color(0xFFFFFCF5),surfaceContainerHighest=Color(0xFFEEEADF),
    onSurface=Color(0xFF24382B),onBackground=Color(0xFF24382B),onSurfaceVariant=Color(0xFF5F695F),
    outline=Color(0xFFDADDD2),outlineVariant=Color(0xFFDADDD2),error=Color(0xFFA73E35))

fun contributionColors(dark:Boolean)=if(dark) listOf(Color(0xFF304A36),Color(0xFF487352),Color(0xFF70A17B))
    else listOf(Color(0xFFD4E6D3),Color(0xFF9FBF9E),Color(0xFF65956F))
