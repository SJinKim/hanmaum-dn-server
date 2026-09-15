package com.hanmaum.dn.app.features.courseapplication.service

import com.hanmaum.dn.app.features.courseapplication.client.ExternalApplicationField
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCourse
import com.hanmaum.dn.app.features.courseapplication.client.ExternalFieldOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationFormFieldsTest {
    @Test
    fun `a legacy course asks for the four base fields plus its listed ones, in request names`() {
        val course =
            ExternalCourse(id = 3, name = "일대일 제자양육", requiredOptionalFields = listOf("aBaptized", "aHistory", "aGroup"))

        val fields = ApplicationFormFields.of(course)

        assertEquals(listOf("name", "birthDate", "email", "phone", "baptized", "history"), fields.map { it.name })
        assertTrue(fields.all { it.required })
    }

    @Test
    fun `a documented course keeps its definitions and drops unsupported and server-set fields`() {
        val course =
            ExternalCourse(
                id = 12,
                name = "큐베세 여자반",
                applicationFields =
                    listOf(
                        ExternalApplicationField(name = "aName", type = "text", required = true, label = "이름"),
                        ExternalApplicationField(
                            name = "aGender",
                            type = "enum",
                            required = true,
                            label = "성별",
                            options = listOf(ExternalFieldOption("F", "여성"), ExternalFieldOption("M", "남성")),
                        ),
                        ExternalApplicationField(name = "aComment", type = "textarea", required = false, label = "커멘트"),
                        ExternalApplicationField(name = "aPhoto", type = "file", required = true, supported = false),
                        ExternalApplicationField(name = "aGroup", type = "enum", required = true),
                    ),
            )

        val fields = ApplicationFormFields.of(course).associateBy { it.name }

        // The base fields the course did not define are added; its own aName keeps its label.
        assertEquals(setOf("name", "birthDate", "email", "phone", "gender", "comment"), fields.keys)
        assertEquals("이름", fields.getValue("name").label)
        assertEquals(listOf("F", "M"), fields.getValue("gender").options.map { it.value })
        assertEquals(false, fields.getValue("comment").required)
    }

    @Test
    fun `a condition on the server-set group is resolved, one on an applicant field keeps the static value`() {
        val course =
            ExternalCourse(
                id = 26,
                name = "일대일 제자양육",
                applicationFields =
                    listOf(
                        ExternalApplicationField(name = "aGroup", type = "enum", required = true),
                        ExternalApplicationField(
                            name = "aGyogu",
                            type = "enum",
                            required = false,
                            requiredWhen = mapOf("aGroup" to listOf("4", "5")),
                        ),
                        // 장년부 only: the server sends 청년부, so this stays optional.
                        ExternalApplicationField(name = "aChildren", required = false, requiredWhen = mapOf("aGroup" to listOf("5"))),
                        // Depends on what the applicant picks; not decidable here.
                        ExternalApplicationField(name = "aSoon", required = false, requiredWhen = mapOf("aGyogu" to listOf("1"))),
                        ExternalApplicationField(name = "aComment", required = true, requiredWhen = mapOf("aGyogu" to listOf("1"))),
                        // dependsOn alone makes nothing required; here that is simply no requiredWhen.
                        ExternalApplicationField(name = "aResidence", required = false),
                    ),
            )

        val required = ApplicationFormFields.of(course).associate { it.name to it.required }

        assertEquals(true, required["gyogu"])
        assertEquals(false, required["children"])
        assertEquals(false, required["soon"])
        assertEquals(true, required["comment"])
        assertEquals(false, required["residence"])
    }

    @Test
    fun `an unknown external field keeps its name`() {
        assertEquals("aVisionConference", ApplicationFormFields.toRequestName("aVisionConference"))
        assertEquals("phone", ApplicationFormFields.toRequestName("aHandy"))
    }
}
