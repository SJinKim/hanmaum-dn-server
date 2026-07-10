package com.hanmaum.dn.app.features.members.api.v1.dto

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterMemberRequestTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Test
    fun `email is trimmed during deserialization`() {
        val json = """{"firstName":"A","lastName":"B","password":"secret","email":"  foo@bar.com  "}"""

        val request = mapper.readValue(json, RegisterMemberRequest::class.java)

        assertEquals("foo@bar.com", request.email)
    }

    @Test
    fun `password keeps surrounding whitespace`() {
        val json = """{"firstName":"A","lastName":"B","password":" secret ","email":"foo@bar.com"}"""

        val request = mapper.readValue(json, RegisterMemberRequest::class.java)

        assertEquals(" secret ", request.password)
    }
}
