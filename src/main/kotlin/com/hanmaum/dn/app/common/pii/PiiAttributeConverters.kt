package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.LocalDate

abstract class EncryptedStringConverter(
    private val context: String,
) : AttributeConverter<String?, String?> {
    override fun convertToDatabaseColumn(attribute: String?): String? = PiiCryptoContext.encrypt(attribute, context)

    override fun convertToEntityAttribute(dbData: String?): String? = PiiCryptoContext.decrypt(dbData, context)
}

abstract class EncryptedLocalDateConverter(
    private val context: String,
) : AttributeConverter<LocalDate?, String?> {
    override fun convertToDatabaseColumn(attribute: LocalDate?): String? = PiiCryptoContext.encrypt(attribute?.toString(), context)

    override fun convertToEntityAttribute(dbData: String?): LocalDate? = PiiCryptoContext.decrypt(dbData, context)?.let(LocalDate::parse)
}

abstract class EncryptedGenderConverter(
    private val context: String,
) : AttributeConverter<Gender?, String?> {
    override fun convertToDatabaseColumn(attribute: Gender?): String? = PiiCryptoContext.encrypt(attribute?.name, context)

    override fun convertToEntityAttribute(dbData: String?): Gender? = PiiCryptoContext.decrypt(dbData, context)?.let(Gender::valueOf)
}

abstract class EncryptedBaptismConverter(
    private val context: String,
) : AttributeConverter<Baptism?, String?> {
    override fun convertToDatabaseColumn(attribute: Baptism?): String? = PiiCryptoContext.encrypt(attribute?.name, context)

    override fun convertToEntityAttribute(dbData: String?): Baptism? = PiiCryptoContext.decrypt(dbData, context)?.let(Baptism::valueOf)
}

@Converter
class EncryptedLastNameConverter : EncryptedStringConverter("members.last_name")

@Converter
class EncryptedFirstNameConverter : EncryptedStringConverter("members.first_name")

@Converter
class EncryptedDiscriminatorConverter : EncryptedStringConverter("members.discriminator")

@Converter
class EncryptedGenderFieldConverter : EncryptedGenderConverter("members.gender")

@Converter
class EncryptedBirthDateConverter : EncryptedLocalDateConverter("members.birth_date")

@Converter
class EncryptedPhoneNumberConverter : EncryptedStringConverter("members.phone_number")

@Converter
class EncryptedEmailConverter : EncryptedStringConverter("members.email")

@Converter
class EncryptedStreetConverter : EncryptedStringConverter("members.street")

@Converter
class EncryptedHouseNumberConverter : EncryptedStringConverter("members.house_number")

@Converter
class EncryptedZipCodeConverter : EncryptedStringConverter("members.zip_code")

@Converter
class EncryptedCityConverter : EncryptedStringConverter("members.city")

@Converter
class EncryptedRegistrationDateConverter : EncryptedLocalDateConverter("members.registration_date")

@Converter
class EncryptedChurchRoleConverter : EncryptedStringConverter("members.role")

@Converter
class EncryptedBaptismFieldConverter : EncryptedBaptismConverter("members.baptism")

@Converter
class EncryptedKeycloakIdConverter : EncryptedStringConverter("members.keycloak_id")

@Converter
class EncryptedProfileImageUrlConverter : EncryptedStringConverter("members.profile_image_url")

@Converter
class EncryptedMinistryNoteConverter : EncryptedStringConverter("ministry_registrations.note")

@Converter
class EncryptedPrayerRequestConverter : EncryptedStringConverter("meeting_attendances.prayer_request")

// The mentor (양육자) of a one-to-one discipleship record, written as free text with
// honorifics because they may not be a member. A person's name, so encrypted like the rest.
@Converter
class EncryptedMentorNameConverter : EncryptedStringConverter("user_training.mentor_name_raw")

// Admin-written notes about a member's participation; may name a mentor or another member.
@Converter
class EncryptedUserTrainingNoteConverter : EncryptedStringConverter("user_training.note")

// Graduation notes are written by an admin and may name a spouse or another member.
@Converter
class EncryptedGraduationNoteConverter : EncryptedStringConverter("member_graduations.note")
