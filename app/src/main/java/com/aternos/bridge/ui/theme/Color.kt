package com.aternos.bridge.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Orange Palette (WCAG AA Compliant >= 4.5:1 against light surfaces)
val PrimaryOrange = Color(0xFFEA580C)          // Orange 600 - Action Buttons, Active Tabs, Highlights
val PrimaryOrangeVariant = Color(0xFFF97316)   // Orange 500
val PrimaryOrangeContainer = Color(0xFFFFEDD5) // Orange 100 - Subdued tint containers
val OnPrimaryOrangeContainer = Color(0xFF9A3412) // Orange 800

// Secondary & Status Accents
val SecondaryAmber = Color(0xFFD97706)         // Amber 600 - Warnings & Cloudflare Alerts
val SecondaryAmberContainer = Color(0xFFFEF3C7)// Amber 100
val EmeraldGreen = Color(0xFF059669)           // Emerald 600 - Server Online
val RedError = Color(0xFFDC2626)               // Red 600 - Guardrail Blocks & Server Offline
val RedErrorContainer = Color(0xFFFEE2E2)      // Red 100

// Light Mode Surface & Card Hierarchy
val LightBackground = Color(0xFFF8FAFC)        // Slate 50 - Base screen background
val LightCard = Color(0xFFFFFFFF)              // Pure White - Elevated component cards
val LightSurfaceVariant = Color(0xFFFFF7ED)     // Warm Orange 50 tint for focused callouts
val BorderOutline = Color(0xFFE2E8F0)           // Slate 200 - Clean Card Borders
val OrangeOutline = Color(0xFFFED7AA)           // Orange 200 - Accent borders

// Typography Tokens
val TextPrimary = Color(0xFF0F172A)             // Slate 900 - Headings & High Contrast Text
val TextSecondary = Color(0xFF475569)           // Slate 600 - Body & Label Text
val TextMuted = Color(0xFF94A3B8)               // Slate 400 - Subtitles & Hints

// Dark Slate Terminal Window (High contrast code card for Minecraft Console)
val TerminalBg = Color(0xFF0F172A)              // Slate 900
val TerminalHeader = Color(0xFF1E293B)          // Slate 800
val TerminalText = Color(0xFFF8FAFC)            // Slate 50
val TerminalTimestamp = Color(0xFF94A3B8)       // Slate 400
val TerminalCmdHighlight = Color(0xFFFB923C)    // Orange 400
val TerminalAiPrefix = Color(0xFF38BDF8)        // Sky 400
val TerminalWarn = Color(0xFFFBBF24)            // Amber 400
val TerminalError = Color(0xFFF87171)           // Red 400
val TerminalUser = Color(0xFFA78BFA)            // Violet 400

// Compatibility aliases
val Slate950 = Color(0xFF0B0F19)
val Slate900 = TextPrimary
val Slate850 = Color(0xFF161F33)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = TextSecondary
val Slate500 = Color(0xFF64748B)
val Slate400 = TextMuted
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = BorderOutline
val Slate100 = Color(0xFFF1F5F9)

val Cyan400 = PrimaryOrange
val Cyan500 = PrimaryOrangeVariant
val Cyan600 = PrimaryOrange
val Emerald400 = EmeraldGreen
val Emerald500 = EmeraldGreen
val Emerald600 = EmeraldGreen
val Amber400 = SecondaryAmber
val Amber500 = SecondaryAmber
val Red400 = RedError
val Red500 = RedError
val Red600 = RedError
val Indigo400 = Color(0xFF818CF8)
val Indigo500 = Color(0xFF6366F1)

val ConsoleBg = TerminalBg
val ConsoleText = TerminalText
val ConsoleTimestamp = TerminalTimestamp
val ConsoleAiPrefix = TerminalAiPrefix
val ConsoleCmd = TerminalCmdHighlight
val ConsoleWarn = TerminalWarn
val ConsoleError = TerminalError
val ConsoleUser = TerminalUser
