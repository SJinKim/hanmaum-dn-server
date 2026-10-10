package com.hanmaum.dn.app.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File

/**
 * The two checked-in realm exports describe the same realm, but Keycloak imports only the first
 * one it reads and skips the other with "Realm 'hanmaum' already exists". A clean start therefore
 * never notices when they drift apart; this test does.
 *
 * Compares realm role names and the group tree (path → realm roles), not ids, which differ
 * between the files by design.
 */
class KeycloakRealmExportsTest {
    private val exportDir = File("infrastructure/docker/keycloak/export")
    private val mapper = JsonMapper.builder().build()

    private data class Realm(
        val roles: Set<String>,
        val groups: Map<String, Set<String>>,
    )

    private fun load(name: String): Realm {
        val root = mapper.readTree(File(exportDir, name))
        val roles =
            root
                .path("roles")
                .path("realm")
                .map { it.path("name").asString() }
                .toSet()
        val groups = mutableMapOf<String, Set<String>>()

        fun walk(nodes: JsonNode) {
            nodes.forEach { group ->
                groups[group.path("path").asString()] = group.path("realmRoles").map { it.asString() }.toSet()
                walk(group.path("subGroups"))
            }
        }
        walk(root.path("groups"))
        return Realm(roles, groups)
    }

    @Test
    fun `both exports carry the same realm roles and group mappings`() {
        val primary = load("hanmaum-realm.json")
        val secondary = load("realm-export.json")

        assertEquals(primary.roles, secondary.roles)
        assertEquals(primary.groups, secondary.groups)
    }

    @Test
    fun `every role a group maps exists in the same export`() {
        listOf("hanmaum-realm.json", "realm-export.json").forEach { name ->
            val realm = load(name)
            val missing =
                realm.groups.values
                    .flatten()
                    .toSet() - realm.roles
            assertTrue(missing.isEmpty(), "$name maps undefined roles: $missing")
        }
    }

    @Test
    fun `the ministry leader and general operations groups map their roles`() {
        val groups = load("hanmaum-realm.json").groups

        assertEquals(setOf("ministry_leader"), groups["/leaders/ministries"])
        assertEquals(setOf("GENERAL_OPERATIONS_VIEWER"), groups["/ministries/general_operations/viewers"])
        assertEquals(
            setOf("GENERAL_OPERATIONS_VIEWER", "GENERAL_OPERATIONS_EDITOR"),
            groups["/ministries/general_operations/editors"],
        )
    }
}
