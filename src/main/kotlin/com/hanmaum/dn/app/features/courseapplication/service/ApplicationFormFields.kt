package com.hanmaum.dn.app.features.courseapplication.service

import com.hanmaum.dn.app.features.courseapplication.client.ExternalCourse
import com.hanmaum.dn.app.features.training.api.v1.dto.CourseFormFieldDto
import com.hanmaum.dn.app.features.training.api.v1.dto.CourseFormFieldOptionDto

/**
 * Translates between the external API's field names (aName, aHandy, …) and the names the app
 * sees, which are the properties of TrainingApplicationRequest.
 *
 * The app never sees an `a…` name: those are the PHP form's column names, and keeping them
 * behind this server means a rename there is a change here, not in every installed app.
 */
object ApplicationFormFields {
    private val EXTERNAL_TO_REQUEST =
        mapOf(
            "aName" to "name",
            "aBirthdate" to "birthDate",
            "aEmail" to "email",
            "aHandy" to "phone",
            "aGender" to "gender",
            "aBaptized" to "baptized",
            "aBaptizeType" to "baptizeType",
            "aResidence" to "residence",
            "aGroup" to "group",
            "aGyogu" to "gyogu",
            "aSoon" to "soon",
            "aChildren" to "children",
            "aHistory" to "history",
            "aWaiting" to "waiting",
            "aRunning" to "running",
            "aComment" to "comment",
        )

    /** Required by the external API for every course, whatever the course lists. */
    private val ALWAYS_REQUIRED = listOf("aName", "aBirthdate", "aEmail", "aHandy")

    /** Set by this server and never asked of the applicant. */
    private val SERVER_SET = setOf("aGroup")

    /** The request name for an external field; an unknown field keeps its external name. */
    fun toRequestName(externalName: String): String = EXTERNAL_TO_REQUEST[externalName] ?: externalName

    /**
     * The form of one course.
     *
     * With the documented contract the course's own field definitions are used, unsupported
     * fields left out. An older deployment only lists `requiredOptionalFields`; each becomes a
     * required field without type or label. Either way the four fields every application needs
     * come first.
     */
    fun of(course: ExternalCourse): List<CourseFormFieldDto> {
        val defined = course.applicationFields
        val fields =
            if (defined != null) {
                defined
                    .filter { it.supported && it.name !in SERVER_SET }
                    .map { field ->
                        CourseFormFieldDto(
                            name = toRequestName(field.name),
                            type = field.type,
                            required = field.required,
                            label = field.label,
                            options = field.options.map { CourseFormFieldOptionDto(value = it.value, label = it.label) },
                        )
                    }
            } else {
                course.requiredOptionalFields
                    .filter { it !in SERVER_SET }
                    .map { CourseFormFieldDto(name = toRequestName(it), type = null, required = true, label = null) }
            }
        val present = fields.map { it.name }.toSet()
        val base =
            ALWAYS_REQUIRED
                .map(::toRequestName)
                .filter { it !in present }
                .map { CourseFormFieldDto(name = it, type = null, required = true, label = null) }
        return base + fields.distinctBy { it.name }
    }
}
