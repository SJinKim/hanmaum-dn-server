package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import software.amazon.awssdk.core.exception.SdkClientException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class ProfileImageServiceTest {
    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var currentMemberResolver: CurrentMemberResolver

    @Mock private lateinit var processor: ProfileImageProcessor

    @Mock private lateinit var storage: S3StorageService

    private val processed = ProcessedProfileImage(byteArrayOf(1, 2, 3), "image/jpeg")

    private fun service() = ProfileImageService(memberRepository, currentMemberResolver, processor, storage)

    private fun member(key: String? = null) =
        Member(lastName = "Kim", firstName = "Min").apply {
            id = 7L
            profileImageKey = key
        }

    @Test
    fun `upload stores the processed image under the member and bumps the version`() {
        val member = member()
        `when`(currentMemberResolver.requireForUpdate("sub")).thenReturn(member)
        `when`(processor.process(any())).thenReturn(processed)

        service().uploadForMember("sub", byteArrayOf(9))

        val key = argumentCaptor<String>()
        verify(storage).putObject(key.capture(), eq(processed.bytes), eq("image/jpeg"))
        assertTrue(key.firstValue.startsWith("profile-images/${member.publicId}/"))
        assertTrue(key.firstValue.endsWith(".jpg"))
        assertEquals(key.firstValue, member.profileImageKey)
        assertEquals(1L, member.profileImageVersion)
        verify(memberRepository).save(member)
    }

    @Test
    fun `upload deletes the previous object`() {
        val member = member(key = "profile-images/old.jpg")
        `when`(currentMemberResolver.requireForUpdate("sub")).thenReturn(member)
        `when`(processor.process(any())).thenReturn(processed)

        service().uploadForMember("sub", byteArrayOf(9))

        verify(storage).deleteObjects(listOf("profile-images/old.jpg"))
    }

    @Test
    fun `upload keeps the new picture when deleting the old object fails`() {
        val member = member(key = "profile-images/old.jpg")
        `when`(currentMemberResolver.requireForUpdate("sub")).thenReturn(member)
        `when`(processor.process(any())).thenReturn(processed)
        doThrow(SdkClientException.create("down")).`when`(storage).deleteObjects(any())

        service().uploadForMember("sub", byteArrayOf(9))

        assertTrue(member.profileImageKey!!.startsWith("profile-images/${member.publicId}/"))
    }

    @Test
    fun `upload leaves the member untouched when storage is down`() {
        val member = member(key = "profile-images/old.jpg")
        `when`(currentMemberResolver.requireForUpdate("sub")).thenReturn(member)
        `when`(processor.process(any())).thenReturn(processed)
        doThrow(SdkClientException.create("down")).`when`(storage).putObject(any(), any(), any())

        assertThrows<ProfileImageStorageUnavailableException> { service().uploadForMember("sub", byteArrayOf(9)) }

        assertEquals("profile-images/old.jpg", member.profileImageKey)
        assertEquals(0L, member.profileImageVersion)
        verify(memberRepository, never()).save(any())
        verify(storage, never()).deleteObjects(any())
    }

    @Test
    fun `upload rejects an invalid image before touching storage`() {
        `when`(currentMemberResolver.requireForUpdate("sub")).thenReturn(member())
        `when`(processor.process(any())).thenThrow(ProfileImageUnsupportedTypeException())

        assertThrows<ProfileImageUnsupportedTypeException> { service().uploadForMember("sub", byteArrayOf(9)) }

        verifyNoInteractions(storage)
    }

    @Test
    fun `deleteForMember clears the key bumps the version and removes the object`() {
        val member = member(key = "profile-images/old.jpg")
        `when`(currentMemberResolver.requireForUpdate("sub")).thenReturn(member)

        service().deleteForMember("sub")

        assertNull(member.profileImageKey)
        assertEquals(1L, member.profileImageVersion)
        verify(memberRepository).save(member)
        verify(storage).deleteObjects(listOf("profile-images/old.jpg"))
    }

    @Test
    fun `deleteForMember without a picture changes nothing`() {
        `when`(currentMemberResolver.requireForUpdate("sub")).thenReturn(member())

        service().deleteForMember("sub")

        verify(memberRepository, never()).save(any())
        verifyNoInteractions(storage)
    }

    @Test
    fun `deleteByAdmin removes the picture of the member it names`() {
        val member = member(key = "profile-images/old.jpg")
        val publicId = UUID.randomUUID()
        `when`(memberRepository.findForUpdateByPublicIdAndDeletedAtIsNull(publicId)).thenReturn(Optional.of(member))

        service().deleteByAdmin(publicId)

        assertNull(member.profileImageKey)
        verify(storage).deleteObjects(listOf("profile-images/old.jpg"))
    }

    @Test
    fun `deleteByAdmin on an unknown member is not found`() {
        val publicId = UUID.randomUUID()
        `when`(memberRepository.findForUpdateByPublicIdAndDeletedAtIsNull(publicId)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service().deleteByAdmin(publicId) }
    }

    @Test
    fun `urlFor presigns the stored key`() {
        `when`(storage.presignGet("profile-images/a.jpg")).thenReturn("https://s3/a.jpg?sig")

        assertEquals("https://s3/a.jpg?sig", service().urlFor(member(key = "profile-images/a.jpg")))
    }

    @Test
    fun `urlFor is null without a picture`() {
        assertNull(service().urlFor(member()))
        verifyNoInteractions(storage)
    }

    @Test
    fun `urlFor is null when presigning fails so the profile still loads`() {
        `when`(storage.presignGet(any())).thenThrow(SdkClientException.create("no credentials"))

        assertNull(service().urlFor(member(key = "profile-images/a.jpg")))
    }

    @Test
    fun `deleteStoredObject swallows storage failures`() {
        doThrow(SdkClientException.create("down")).`when`(storage).deleteObjects(any())

        service().deleteStoredObject("profile-images/a.jpg")
    }
}
