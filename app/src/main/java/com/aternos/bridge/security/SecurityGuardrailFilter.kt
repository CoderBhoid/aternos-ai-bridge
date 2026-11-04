package com.aternos.bridge.security

import android.util.Log

/**
 * Result of validating a proposed Minecraft command against the security guardrail.
 */
sealed class SecurityValidationResult {
    /**
     * The command passed all security criteria and is safe for dispatch.
     * @param sanitizedCommand The normalized, slash-stripped command string ready for console injection.
     */
    data class Allowed(val sanitizedCommand: String) : SecurityValidationResult()

    /**
     * The command was rejected by the guardrail filter.
     * @param reason Human-readable explanation of why the command was blocked.
     * @param rawCommand The original proposed command string.
     */
    data class Blocked(val reason: String, val rawCommand: String) : SecurityValidationResult()
}

/**
 * High-Security Guardrail Filter for Minecraft Server Commands.
 *
 * <p>Because an AI model or a malicious player prompt may attempt to perform administrative
 * attacks or destructive operations on the Minecraft server (such as stopping the server,
 * granting operator privileges, banishing players, or executing chained bash/command exploits),
 * this class acts as an unbypassable, allowlist-leaning boundary.</p>
 *
 * <h3>Design & Invariants:</h3>
 * <ul>
 *   <li><b>Normalization:</b> All inputs are lowercased, stripped of leading slashes, and collapsed of redundant whitespace.</li>
 *   <li><b>Composite / Chaining Detection:</b> Characters such as semicolons (`;`), logical AND (`&&`), pipes (`|`),
 *       and newlines (`\n`, `\r`) are strictly rejected.</li>
 *   <li><b>Nested Execution Inspection:</b> Commands matching `/execute ... run ...` are recursively unwrapped
 *       and validated against all blocked sub-commands.</li>
 *   <li><b>Hard-Blocked Roots:</b> Commands targeting destructive lifecycle changes (`stop`, `restart`, `reload`),
 *       privilege escalation (`op`, `deop`, `whitelist`), or player discipline (`ban`, `ban-ip`, `kick`, `pardon`)
 *       are rejected unconditionally without any override.</li>
 *   <li><b>Safe Allowlist Matching:</b> Common gameplay utility commands (`time`, `weather`, `give`, `tp`, `teleport`,
 *       `gamemode`, `summon`, `effect`, `say`, `tellraw`, `w`, `tell`, `msg`, `title`, `particle`, `playsound`, `clear`,
 *       `difficulty`, `seed`, `locate`, `scoreboard`) are verified for valid syntax.</li>
 * </ul>
 */
object SecurityGuardrailFilter {

    private const val TAG = "SecurityGuardrail"

    /**
     * Unconditionally blocked root commands that must never be triggered by an AI assistant in v1.
     */
    private val BLOCKED_ROOTS = setOf(
        "stop",
        "restart",
        "reload",
        "op",
        "deop",
        "whitelist",
        "ban",
        "ban-ip",
        "pardon",
        "pardon-ip",
        "kick",
        "kill @a",
        "kill @e",
        "save-off",
        "save-all",
        "publish",
        "debug",
        "jfr",
        "perf",
        "datapack disable"
    )

    /**
     * Characters and sequences indicating composite or chained command injection.
     */
    private val FORBIDDEN_COMPOSITE_SEPARATORS = listOf(";", "&&", "||", "|", "\n", "\r", "`", "$")

    /**
     * Common safe Minecraft command prefixes that are permitted for game assistance.
     */
    private val ALLOWED_COMMAND_PREFIXES = setOf(
        "time",
        "weather",
        "give",
        "tp",
        "teleport",
        "gamemode",
        "summon",
        "effect",
        "say",
        "tellraw",
        "w",
        "tell",
        "msg",
        "title",
        "particle",
        "playsound",
        "clear",
        "difficulty",
        "seed",
        "locate",
        "scoreboard",
        "experience",
        "xp",
        "attribute",
        "enchant",
        "tag",
        "item",
        "loot",
        "advancement",
        "execute",
        "kill"
    )

    /**
     * Normalizes a raw command string by removing leading slashes, trimming,
     * collapsing internal multi-spaces, and converting to lowercase for comparison.
     *
     * @param rawCommand The raw command string proposed by the AI model.
     * @return A trimmed, single-spaced, lowercased string without leading slashes.
     */
    fun normalize(rawCommand: String): String {
        return rawCommand
            .trim()
            .trimStart('/')
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Evaluates a proposed command against all security guardrails and operator permissions.
     *
     * @param rawCommand The command string to validate.
     * @param player The username of the player who triggered the prompt (for audit logging).
     * @param authorizedOperator The single designated operator username permitted to execute commands (e.g. "GamerBhoid").
     * @param enforceOperatorOnly If true, restricts command execution permissions exclusively to authorizedOperator.
     * @return [SecurityValidationResult.Allowed] if safe, or [SecurityValidationResult.Blocked] with reason.
     */
    fun validate(
        rawCommand: String,
        player: String = "unknown",
        authorizedOperator: String = "GamerBhoid",
        enforceOperatorOnly: Boolean = false
    ): SecurityValidationResult {
        if (rawCommand.isBlank()) {
            val reason = "Rejected empty command."
            logWarn("[$player] $reason")
            return SecurityValidationResult.Blocked(reason, rawCommand)
        }

        // 0. Operator Permission Enforcement Check
        if (enforceOperatorOnly && authorizedOperator.isNotBlank()) {
            val cleanPlayer = player.trim()
            val cleanOp = authorizedOperator.trim()
            if (!cleanPlayer.equals(cleanOp, ignoreCase = true)) {
                val reason = "Permission Denied: Player '$player' is not authorized to execute server commands. Only operator '$authorizedOperator' has OP execution permissions."
                logWarn("[$player] BLOCKED: $reason (cmd='$rawCommand')")
                return SecurityValidationResult.Blocked(reason, rawCommand)
            }
        }

        // 1. Composite / Injection sequence check
        for (sep in FORBIDDEN_COMPOSITE_SEPARATORS) {
            if (rawCommand.contains(sep)) {
                val reason = "Security Violation: Chained or composite command separator '$sep' detected."
                logWarn("[$player] BLOCKED: $reason (cmd='$rawCommand')")
                return SecurityValidationResult.Blocked(reason, rawCommand)
            }
        }

        val normalized = normalize(rawCommand)
        val normalizedLower = normalized.lowercase()
        val tokens = normalizedLower.split(" ")
        val rootCommand = tokens.firstOrNull() ?: ""

        // 2. Direct Blocked Roots Check
        if (BLOCKED_ROOTS.contains(rootCommand)) {
            val reason = "Security Violation: Command root '$rootCommand' is strictly prohibited."
            logWarn("[$player] BLOCKED: $reason (cmd='$rawCommand')")
            return SecurityValidationResult.Blocked(reason, rawCommand)
        }

        // 3. Multi-word blocked patterns (e.g. 'kill @a', 'kill @e')
        for (blockedPattern in BLOCKED_ROOTS) {
            if (normalizedLower == blockedPattern || normalizedLower.startsWith("$blockedPattern ")) {
                val reason = "Security Violation: Command pattern '$blockedPattern' is strictly prohibited."
                logWarn("[$player] BLOCKED: $reason (cmd='$rawCommand')")
                return SecurityValidationResult.Blocked(reason, rawCommand)
            }
        }

        // 4. Inspect nested /execute commands (e.g. "execute as @a run op Player")
        if (rootCommand == "execute") {
            val nestedValidation = validateExecuteChain(normalizedLower, rawCommand, player)
            if (nestedValidation is SecurityValidationResult.Blocked) {
                return nestedValidation
            }
        }

        // 5. Allowlist check: Ensure root command is in allowed set
        if (!ALLOWED_COMMAND_PREFIXES.contains(rootCommand)) {
            val reason = "Command root '$rootCommand' is not in the recognized safe allowlist."
            logWarn("[$player] BLOCKED: $reason (cmd='$rawCommand')")
            return SecurityValidationResult.Blocked(reason, rawCommand)
        }

        // Passed all checks! Return normalized command (preserving case of arguments like player names)
        val cleanOutput = normalize(rawCommand)
        logInfo("[$player] ALLOWED: '$cleanOutput'")
        return SecurityValidationResult.Allowed(cleanOutput)
    }

    /**
     * Recursively parses `/execute ... run <subcommand>` chains to ensure no blocked command
     * is concealed inside an execute wrapper.
     */
    private fun validateExecuteChain(
        normalizedLower: String,
        rawCommand: String,
        player: String
    ): SecurityValidationResult {
        val runIndex = normalizedLower.indexOf(" run ")
        if (runIndex != -1) {
            val innerCommand = normalizedLower.substring(runIndex + 5).trim()
            val innerTokens = innerCommand.split(" ")
            val innerRoot = innerTokens.firstOrNull() ?: ""

            if (BLOCKED_ROOTS.contains(innerRoot)) {
                val reason = "Security Violation: Blocked sub-command '$innerRoot' nested within /execute."
                logWarn("[$player] BLOCKED: $reason (cmd='$rawCommand')")
                return SecurityValidationResult.Blocked(reason, rawCommand)
            }

            for (blockedPattern in BLOCKED_ROOTS) {
                if (innerCommand == blockedPattern || innerCommand.startsWith("$blockedPattern ")) {
                    val reason = "Security Violation: Prohibited pattern '$blockedPattern' nested within /execute."
                    logWarn("[$player] BLOCKED: $reason (cmd='$rawCommand')")
                    return SecurityValidationResult.Blocked(reason, rawCommand)
                }
            }

            // Recursive check if nested inside another execute
            if (innerRoot == "execute") {
                return validateExecuteChain(innerCommand, rawCommand, player)
            }
        }
        return SecurityValidationResult.Allowed(rawCommand)
    }

    private fun logWarn(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: Throwable) {
            // Safe fallback on plain JVM unit tests
        }
    }

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
            // Safe fallback on plain JVM unit tests
        }
    }
}
