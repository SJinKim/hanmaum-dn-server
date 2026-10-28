package com.hanmaum.dn.app.features.members.service

import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.event.IIOReadWarningListener
import javax.imageio.stream.FileImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/** A normalized profile picture, ready to store. */
class ProcessedProfileImage(
    val bytes: ByteArray,
    val contentType: String,
)

/**
 * Turns an uploaded profile picture into a 512×512 JPEG (HDN-140).
 *
 * The type comes from the magic bytes, never from the client. Only the centered square is
 * decoded, the EXIF orientation is applied, and the JPEG is written without any metadata, so
 * EXIF (GPS included) never reaches storage. Anything the decoder warns about counts as corrupt.
 */
@Component
class ProfileImageProcessor {
    init {
        // Registers the TwelveMonkeys WebP reader also when ImageIO was first touched by another
        // class loader (Spring Boot fat jar).
        ImageIO.scanForPlugins()
    }

    fun process(input: ByteArray): ProcessedProfileImage = process(ByteArrayInputStream(input))

    /**
     * Spools [input] to a temporary file, at most [MAX_UPLOAD_BYTES], and decodes from there: the
     * upload is never held in the heap, only its first [HEAD_BYTES] for the type and EXIF checks.
     */
    fun process(input: InputStream): ProcessedProfileImage {
        val file = Files.createTempFile("profile-image-", ".upload")
        try {
            spool(input, file)
            val head = Files.newInputStream(file).use { it.readNBytes(HEAD_BYTES) }
            val format = detectFormat(head) ?: throw ProfileImageUnsupportedTypeException()
            val square = scale(decodeCenteredSquare(file.toFile(), format), OUTPUT_SIZE)
            val oriented = if (format == ImageFormat.JPEG) orient(square, readJpegOrientation(head)) else square
            return ProcessedProfileImage(encodeJpeg(oriented), OUTPUT_CONTENT_TYPE)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun spool(
        input: InputStream,
        target: Path,
    ) {
        Files.newOutputStream(target).use { out ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                // Stops at the first byte over the limit instead of reading the rest.
                if (total > MAX_UPLOAD_BYTES) throw ProfileImageTooLargeException()
                out.write(buffer, 0, read)
            }
        }
    }

    private enum class ImageFormat(
        val readerName: String,
    ) {
        JPEG("jpeg"),
        PNG("png"),
        WEBP("webp"),
    }

    private fun detectFormat(bytes: ByteArray): ImageFormat? =
        when {
            bytes.startsWith(JPEG_MAGIC) -> ImageFormat.JPEG
            bytes.startsWith(PNG_MAGIC) -> ImageFormat.PNG
            bytes.startsWith(RIFF_MAGIC) && bytes.startsWith(WEBP_MAGIC, offset = 8) -> ImageFormat.WEBP
            else -> null
        }

    private fun decodeCenteredSquare(
        file: File,
        format: ImageFormat,
    ): BufferedImage {
        val reader =
            ImageIO.getImageReadersByFormatName(format.readerName).asSequence().firstOrNull()
                ?: error("No ImageIO reader registered for ${format.readerName}")
        try {
            FileImageInputStream(file).use { stream ->
                reader.input = stream
                var warned = false
                reader.addIIOReadWarningListener(IIOReadWarningListener { _, _ -> warned = true })
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val maxPixels = if (format == ImageFormat.WEBP) MAX_WEBP_PIXELS else MAX_PIXELS
                if (width <= 0 || height <= 0 || width.toLong() * height > maxPixels) {
                    throw ProfileImageInvalidException()
                }
                val side = minOf(width, height)
                val x = (width - side) / 2
                val y = (height - side) / 2
                if (format == ImageFormat.WEBP) {
                    // The WebP reader ignores sourceRegion and subsampling, so it decodes whole and is cropped here.
                    val full = reader.read(0)
                    if (warned) throw ProfileImageInvalidException()
                    return full.getSubimage(x, y, side, side)
                }
                val step = maxOf(1, side / DECODE_TARGET_SIZE)
                val param = reader.defaultReadParam
                param.sourceRegion = Rectangle(x, y, side, side)
                param.setSourceSubsampling(step, step, 0, 0)
                val image = reader.read(0, param)
                if (warned) throw ProfileImageInvalidException()
                return image
            }
        } catch (e: IOException) {
            throw ProfileImageInvalidException(e)
        } catch (e: IllegalArgumentException) {
            throw ProfileImageInvalidException(e)
        } catch (e: IndexOutOfBoundsException) {
            throw ProfileImageInvalidException(e)
        } finally {
            reader.dispose()
        }
    }

    /** Halves in steps before the final bicubic pass, which keeps large photos from aliasing. */
    private fun scale(
        source: BufferedImage,
        size: Int,
    ): BufferedImage {
        var current = resize(source, source.width, source.height)
        while (current.width / 2 >= size && current.height / 2 >= size) {
            current = resize(current, current.width / 2, current.height / 2)
        }
        return resize(current, size, size)
    }

    /** Draws onto an opaque white canvas: JPEG has no alpha, so transparency becomes white. */
    private fun resize(
        source: BufferedImage,
        width: Int,
        height: Int,
    ): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /**
     * Applies an EXIF orientation to a square image. Cropping the centered square commutes with
     * these transforms, so it is fine to orient after cropping and scaling.
     */
    private fun orient(
        image: BufferedImage,
        orientation: Int,
    ): BufferedImage {
        val n = image.width.toDouble()
        // AffineTransform(m00, m10, m01, m11, m02, m12): x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12
        val transform =
            when (orientation) {
                2 -> AffineTransform(-1.0, 0.0, 0.0, 1.0, n, 0.0) // mirror horizontal
                3 -> AffineTransform(-1.0, 0.0, 0.0, -1.0, n, n) // rotate 180
                4 -> AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, n) // mirror vertical
                5 -> AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0) // transpose
                6 -> AffineTransform(0.0, 1.0, -1.0, 0.0, n, 0.0) // rotate 90 clockwise
                7 -> AffineTransform(0.0, -1.0, -1.0, 0.0, n, n) // transverse
                8 -> AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, n) // rotate 270 clockwise
                else -> return image
            }
        val target = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.drawImage(image, transform, null)
        } finally {
            g.dispose()
        }
        return target
    }

    /** Reads IFD0 Orientation (0x0112) from the first EXIF APP1 segment; 1 (as stored) when absent. */
    private fun readJpegOrientation(bytes: ByteArray): Int {
        var offset = 2
        while (offset + 4 <= bytes.size && bytes[offset] == MARKER_PREFIX) {
            val marker = bytes[offset + 1].toInt() and 0xFF
            if (marker == SOS_MARKER || marker == EOI_MARKER) break
            val length = bytes.u16(offset + 2, bigEndian = true)
            if (length < 2) break
            val segmentEnd = minOf(bytes.size, offset + 2 + length)
            if (marker == APP1_MARKER && bytes.startsWith(EXIF_HEADER, offset = offset + 4)) {
                return orientationFromTiff(bytes, offset + 4 + EXIF_HEADER.size, segmentEnd)
            }
            offset += 2 + length
        }
        return 1
    }

    private fun orientationFromTiff(
        bytes: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        if (start + 8 > end) return 1
        val bigEndian =
            when {
                bytes[start] == 'M'.code.toByte() && bytes[start + 1] == 'M'.code.toByte() -> true
                bytes[start] == 'I'.code.toByte() && bytes[start + 1] == 'I'.code.toByte() -> false
                else -> return 1
            }
        val ifdOffset = bytes.u32(start + 4, bigEndian)
        if (ifdOffset < 8 || start + ifdOffset + 2 > end) return 1
        val ifd = start + ifdOffset.toInt()
        val entries = bytes.u16(ifd, bigEndian)
        for (i in 0 until entries) {
            val entry = ifd + 2 + i * 12
            if (entry + 12 > end) return 1
            if (bytes.u16(entry, bigEndian) == ORIENTATION_TAG) return bytes.u16(entry + 8, bigEndian)
        }
        return 1
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        try {
            MemoryCacheImageOutputStream(output).use { stream ->
                writer.output = stream
                val param =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = JPEG_QUALITY
                    }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return output.toByteArray()
    }

    private fun ByteArray.startsWith(
        prefix: ByteArray,
        offset: Int = 0,
    ): Boolean = size >= offset + prefix.size && prefix.indices.all { this[offset + it] == prefix[it] }

    private fun ByteArray.u16(
        offset: Int,
        bigEndian: Boolean,
    ): Int {
        val a = this[offset].toInt() and 0xFF
        val b = this[offset + 1].toInt() and 0xFF
        return if (bigEndian) (a shl 8) or b else (b shl 8) or a
    }

    private fun ByteArray.u32(
        offset: Int,
        bigEndian: Boolean,
    ): Long {
        val high = u16(if (bigEndian) offset else offset + 2, bigEndian).toLong()
        val low = u16(if (bigEndian) offset + 2 else offset, bigEndian).toLong()
        return (high shl 16) or low
    }

    companion object {
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
        const val OUTPUT_CONTENT_TYPE = "image/jpeg"
        private const val OUTPUT_SIZE = 512
        private const val HEAD_BYTES = 256 * 1024
        private const val COPY_BUFFER_BYTES = 8 * 1024
        private const val DECODE_TARGET_SIZE = 1024
        private const val MAX_PIXELS = 50_000_000L

        // Whole-image decode at 4 bytes per pixel: 16 MP is about 64 MB of heap.
        private const val MAX_WEBP_PIXELS = 16_000_000L
        private const val JPEG_QUALITY = 0.85f
        private const val MARKER_PREFIX = 0xFF.toByte()
        private const val APP1_MARKER = 0xE1
        private const val SOS_MARKER = 0xDA
        private const val EOI_MARKER = 0xD9
        private const val ORIENTATION_TAG = 0x0112
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val RIFF_MAGIC = "RIFF".toByteArray()
        private val WEBP_MAGIC = "WEBP".toByteArray()
        private val EXIF_HEADER = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
    }
}
