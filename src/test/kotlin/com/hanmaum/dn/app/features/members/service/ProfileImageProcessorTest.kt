package com.hanmaum.dn.app.features.members.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileImageProcessorTest {
    private val processor = ProfileImageProcessor()

    @Test
    fun `jpeg upload becomes a 512 square jpeg`() {
        val result = processor.process(encode(solid(800, 600, Color.GREEN), "jpg"))

        assertSquareJpeg(result)
    }

    @Test
    fun `png upload becomes a 512 square jpeg`() {
        val result = processor.process(encode(solid(300, 900, Color.GREEN), "png"))

        assertSquareJpeg(result)
    }

    @Test
    fun `webp upload becomes a 512 square jpeg`() {
        val webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==")

        val result = processor.process(webp)

        assertSquareJpeg(result)
    }

    @Test
    fun `transparent png is flattened onto white`() {
        val transparent = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)

        val image = decode(processor.process(encode(transparent, "png")).bytes)

        val pixel = Color(image.getRGB(256, 256))
        assertTrue(pixel.red > 240 && pixel.green > 240 && pixel.blue > 240, "expected white, got $pixel")
    }

    @Test
    fun `upload above 5 MB is rejected`() {
        val tooLarge = ByteArray(ProfileImageProcessor.MAX_UPLOAD_BYTES + 1)
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()).copyInto(tooLarge)

        assertThrows<ProfileImageTooLargeException> { processor.process(tooLarge) }
    }

    @Test
    fun `gif upload is rejected as unsupported`() {
        assertThrows<ProfileImageUnsupportedTypeException> {
            processor.process(encode(solid(10, 10, Color.RED), "gif"))
        }
    }

    @Test
    fun `text disguised as an image is rejected as unsupported`() {
        assertThrows<ProfileImageUnsupportedTypeException> {
            processor.process("not an image at all".toByteArray())
        }
    }

    @Test
    fun `png magic followed by garbage is rejected as invalid`() {
        val garbage = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64) { 0x42 }

        assertThrows<ProfileImageInvalidException> { processor.process(garbage) }
    }

    @Test
    fun `truncated jpeg is rejected as invalid`() {
        val full = encode(gradient(400, 400), "jpg")

        assertThrows<ProfileImageInvalidException> { processor.process(full.copyOf(full.size / 2)) }
    }

    @Test
    fun `png claiming more than 50 megapixels is rejected before decoding`() {
        val png = encode(solid(10, 10, Color.RED), "png")
        // IHDR starts at byte 16: width and height as big-endian ints.
        writeInt(png, 16, 10_000)
        writeInt(png, 20, 10_000)

        assertThrows<ProfileImageInvalidException> { processor.process(png) }
    }

    @Test
    fun `exif orientation is applied and the exif block is stripped`() {
        // Left half red, right half blue. Orientation 6 means "rotate 90 degrees clockwise to
        // display", so red must end up on top and blue at the bottom.
        val source = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        val g = source.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, 100, 100)
        g.color = Color.BLUE
        g.fillRect(100, 0, 100, 100)
        g.dispose()
        val jpeg = withExifOrientation(encode(source, "jpg"), orientation = 6)

        val result = processor.process(jpeg)
        val image = decode(result.bytes)

        assertTrue(isRed(Color(image.getRGB(470, 40))), "top right should be red")
        assertTrue(isBlue(Color(image.getRGB(40, 470))), "bottom left should be blue")
        assertFalse(0xE1 in markersBeforeScan(result.bytes), "APP1 (EXIF) must not survive")
    }

    private fun assertSquareJpeg(result: ProcessedProfileImage) {
        assertEquals("image/jpeg", result.contentType)
        assertEquals(0xFF, result.bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, result.bytes[1].toInt() and 0xFF)
        val image = decode(result.bytes)
        assertEquals(512, image.width)
        assertEquals(512, image.height)
    }

    private fun solid(
        width: Int,
        height: Int,
        color: Color,
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        return image
    }

    private fun gradient(
        width: Int,
        height: Int,
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, Color((x * 7) % 256, (y * 13) % 256, (x * y) % 256).rgb)
            }
        }
        return image
    }

    private fun encode(
        image: BufferedImage,
        format: String,
    ): ByteArray = ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

    private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    private fun isRed(c: Color) = c.red > 200 && c.green < 60 && c.blue < 60

    private fun isBlue(c: Color) = c.blue > 200 && c.red < 60 && c.green < 60

    private fun writeInt(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        for (i in 0 until 4) bytes[offset + i] = (value ushr (24 - 8 * i)).toByte()
    }

    /** Inserts an APP1 EXIF segment holding only IFD0/Orientation right after the JFIF APP0. */
    private fun withExifOrientation(
        jpeg: ByteArray,
        orientation: Int,
    ): ByteArray {
        val tiff =
            byteArrayOf(
                'M'.code.toByte(),
                'M'.code.toByte(),
                0x00,
                0x2A, // big-endian TIFF header
                0x00,
                0x00,
                0x00,
                0x08, // IFD0 offset
                0x00,
                0x01, // one entry
                0x01,
                0x12,
                0x00,
                0x03,
                0x00,
                0x00,
                0x00,
                0x01, // Orientation, SHORT, count 1
                0x00,
                orientation.toByte(),
                0x00,
                0x00, // value
                0x00,
                0x00,
                0x00,
                0x00, // no next IFD
            )
        val payload = "Exif".toByteArray() + byteArrayOf(0x00, 0x00) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        val app0End = 4 + (((jpeg[4].toInt() and 0xFF) shl 8) or (jpeg[5].toInt() and 0xFF))
        return jpeg.copyOfRange(0, app0End) + app1 + jpeg.copyOfRange(app0End, jpeg.size)
    }

    private fun markersBeforeScan(jpeg: ByteArray): List<Int> {
        val markers = mutableListOf<Int>()
        var offset = 2
        while (offset + 4 <= jpeg.size) {
            val marker = jpeg[offset + 1].toInt() and 0xFF
            if (marker == 0xDA) break
            markers += marker
            offset += 2 + (((jpeg[offset + 2].toInt() and 0xFF) shl 8) or (jpeg[offset + 3].toInt() and 0xFF))
        }
        return markers
    }
}
