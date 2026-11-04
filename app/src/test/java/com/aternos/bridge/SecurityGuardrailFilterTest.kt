package com.aternos.bridge

import com.aternos.bridge.security.SecurityGuardrailFilter
import com.aternos.bridge.security.SecurityValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityGuardrailFilterTest {

    @Test
    fun testAllowedCommandsPass() {
        val testCases = listOf(
            "time set day" to "time set day",
            "/time set night" to "time set night",
            "///weather clear" to "weather clear",
            "give @p diamond 3" to "give @p diamond 3",
            "/give   Steve   iron_ingot  64" to "give Steve iron_ingot 64",
            "tp @p 100 64 100" to "tp @p 100 64 100",
            "gamemode survival @a" to "gamemode survival @a",
            "summon cow ~ ~ ~" to "summon cow ~ ~ ~",
            "effect give @p speed 30 2" to "effect give @p speed 30 2",
            "say Welcome to the server!" to "say Welcome to the server!",
            "tellraw @a [{\"text\":\"Hello!\"}]" to "tellraw @a [{\"text\":\"Hello!\"}]",
            "w @a [AI] Setting time to morning" to "w @a [AI] Setting time to morning"
        )

        for ((input, expected) in testCases) {
            val result = SecurityGuardrailFilter.validate(
                rawCommand = input,
                player = "GamerBhoid",
                authorizedOperator = "GamerBhoid",
                enforceOperatorOnly = true
            )
            assertTrue("Expected '$input' to be allowed for GamerBhoid, but got: $result", result is SecurityValidationResult.Allowed)
            val allowed = result as SecurityValidationResult.Allowed
            assertEquals("Normalized command mismatch for '$input'", expected, allowed.sanitizedCommand)
        }
    }

    @Test
    fun testOperatorPermissionEnforcement() {
        val cmd = "give GamerBhoid mace 1"

        // 1. Authorized Operator GamerBhoid is allowed
        val opResult = SecurityGuardrailFilter.validate(
            rawCommand = cmd,
            player = "GamerBhoid",
            authorizedOperator = "GamerBhoid",
            enforceOperatorOnly = true
        )
        assertTrue("Expected GamerBhoid to have command permission", opResult is SecurityValidationResult.Allowed)

        // 2. Case-insensitive check: "gamerbhoid" is allowed
        val lowerOpResult = SecurityGuardrailFilter.validate(
            rawCommand = cmd,
            player = "gamerbhoid",
            authorizedOperator = "GamerBhoid",
            enforceOperatorOnly = true
        )
        assertTrue("Expected case-insensitive operator match to pass", lowerOpResult is SecurityValidationResult.Allowed)

        // 3. Other players are blocked when operator enforcement is active
        val nonOpResult = SecurityGuardrailFilter.validate(
            rawCommand = cmd,
            player = "Steve",
            authorizedOperator = "GamerBhoid",
            enforceOperatorOnly = true
        )
        assertTrue("Expected Steve to be blocked by operator enforcement", nonOpResult is SecurityValidationResult.Blocked)
        val blocked = nonOpResult as SecurityValidationResult.Blocked
        assertTrue("Expected permission denied reason, but got: ${blocked.reason}", blocked.reason.contains("Permission Denied"))

        // 4. When operator enforcement is disabled, other players can run allowlisted commands
        val disabledEnforcementResult = SecurityGuardrailFilter.validate(
            rawCommand = cmd,
            player = "Steve",
            authorizedOperator = "GamerBhoid",
            enforceOperatorOnly = false
        )
        assertTrue("Expected Steve to pass when operator enforcement is disabled", disabledEnforcementResult is SecurityValidationResult.Allowed)
    }

    @Test
    fun testBlockedRootCommands() {
        val blockedCommands = listOf(
            "stop",
            "/stop",
            "///stop",
            "restart",
            "/restart",
            "reload",
            "/reload",
            "op Steve",
            "/op Notch",
            "deop Steve",
            "/deop Alex",
            "whitelist add Hacker",
            "/whitelist off",
            "ban Steve",
            "/ban-ip 127.0.0.1",
            "kick Alex",
            "/pardon Steve",
            "kill @a",
            "/kill @e",
            "save-off",
            "datapack disable vanilla"
        )

        for (cmd in blockedCommands) {
            val result = SecurityGuardrailFilter.validate(cmd, "Attacker")
            assertTrue("Expected '$cmd' to be blocked, but was allowed!", result is SecurityValidationResult.Blocked)
        }
    }

    @Test
    fun testCompositeAndChainedCommandsBlocked() {
        val compositeCommands = listOf(
            "time set day; stop",
            "weather clear && op Steve",
            "give @p diamond 1 | ban Alex",
            "time set day\nstop",
            "say hello `stop`",
            "say \$stop"
        )

        for (cmd in compositeCommands) {
            val result = SecurityGuardrailFilter.validate(cmd, "Infiltrator")
            assertTrue("Expected chained command '$cmd' to be blocked!", result is SecurityValidationResult.Blocked)
        }
    }

    @Test
    fun testNestedExecuteCommandsInspectedAndBlocked() {
        val nestedProhibited = listOf(
            "/execute as @a run op Steve",
            "execute at @p run stop",
            "execute if entity @e run ban Alex",
            "execute as @a run execute as @p run op Hacker",
            "execute run kill @a"
        )

        for (cmd in nestedProhibited) {
            val result = SecurityGuardrailFilter.validate(cmd, "SneakyPlayer")
            assertTrue("Expected nested execute command '$cmd' to be blocked!", result is SecurityValidationResult.Blocked)
        }
    }

    @Test
    fun testUnrecognizedRootCommandsBlocked() {
        val unknownCommands = listOf(
            "malicious_unknown_plugin_cmd arg1",
            "dropdatabase",
            "exec rm -rf"
        )

        for (cmd in unknownCommands) {
            val result = SecurityGuardrailFilter.validate(cmd, "Player")
            assertTrue("Expected unknown root '$cmd' to be blocked by allowlist!", result is SecurityValidationResult.Blocked)
        }
    }
}
