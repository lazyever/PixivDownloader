package com.pixiv.downloader.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.pixiv.downloader.data.api.models.UgoiraFrame
import java.io.*
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object UgoiraSynthesizer {

    /**
     * 解压 Ugoira Zip 文件至指定临时目录
     */
    fun unzipFrames(zipFile: File, targetDir: File): List<String> {
        if (!targetDir.exists()) targetDir.mkdirs()
        val extractedFiles = mutableListOf<String>()

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                if (!entry.isDirectory) {
                    val fileName = File(entry.name).name
                    val outputFile = File(targetDir, fileName)
                    FileOutputStream(outputFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                    extractedFiles.add(fileName)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extractedFiles
    }

    /**
     * 将帧序列与延迟信息合成为标准 MP4 视频
     */
    fun synthesizeMp4(
        frameDir: File,
        frames: List<UgoiraFrame>,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ) {
        if (frames.isEmpty()) {
            throw IllegalArgumentException("Ugoira 帧数据为空，无法合成 MP4")
        }

        // 获取第一张图尺寸，要求必须为偶数宽度和高度（H.264 要求）
        val firstFrameFile = File(frameDir, frames[0].file)
        if (!firstFrameFile.exists()) {
            throw FileNotFoundException("首帧文件不存在: ${firstFrameFile.path}")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(firstFrameFile.absolutePath, options)
        var width = options.outWidth
        var height = options.outHeight
        if (width <= 0 || height <= 0) {
            throw IllegalStateException("无法解析帧图像分辨率")
        }

        if (width % 2 != 0) width -= 1
        if (height % 2 != 0) height -= 1

        val tempMp4 = File(frameDir, "temp_render_${System.currentTimeMillis()}.mp4")
        val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 3.5).toInt().coerceIn(2000000, 12000000))
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: Surface? = null

        try {
            encoder = MediaCodec.createEncoderByType(mimeType)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(tempMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            var presentationTimeUs = 0L
            val srcRect = Rect()
            val dstRect = Rect(0, 0, width, height)

            for (i in frames.indices) {
                val frameMeta = frames[i]
                val file = File(frameDir, frameMeta.file)
                if (!file.exists()) continue

                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                srcRect.set(0, 0, bitmap.width, bitmap.height)

                // 绘制到编码器输入 Surface
                val canvas = inputSurface.lockHardwareCanvas()
                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
                inputSurface.unlockCanvasAndPost(canvas)
                bitmap.recycle()

                // 计算下一帧时间戳（微秒）
                presentationTimeUs += frameMeta.delay * 1000L

                // 轮询输出缓冲
                drainEncoder(encoder, muxer, bufferInfo, false) { index ->
                    trackIndex = index
                    muxerStarted = true
                }

                onProgress((i + 1).toFloat() / frames.size.toFloat() * 0.9f)
            }

            // 发送编码结束信号
            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, true) { index ->
                trackIndex = index
                muxerStarted = true
            }

            muxer.stop()
            muxer.release()
            muxer = null

            encoder.stop()
            encoder.release()
            encoder = null

            // 将生成的 MP4 写入目标输出流
            FileInputStream(tempMp4).use { fis ->
                fis.copyTo(outputStream)
            }
            outputStream.flush()
            tempMp4.delete()
            onProgress(1.0f)
        } catch (e: Exception) {
            tempMp4.delete()
            throw IllegalStateException("合成 MP4 失败: ${e.message}", e)
        } finally {
            try {
                inputSurface?.release()
                encoder?.release()
                muxer?.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormatChanged: (Int) -> Unit
    ) {
        val timeoutUs = 10000L
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                val track = muxer.addTrack(newFormat)
                muxer.start()
                onFormatChanged(track)
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                    ?: throw RuntimeException("无法获取编码器输出 ByteBuffer")

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(0, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    /**
     * 将帧序列合成为高质量 Animated GIF
     */
    fun synthesizeGif(
        frameDir: File,
        frames: List<UgoiraFrame>,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ) {
        if (frames.isEmpty()) {
            throw IllegalArgumentException("Ugoira 帧数据为空，无法合成 GIF")
        }

        val encoder = GifSequenceWriter()
        encoder.start(outputStream)
        encoder.setRepeat(0) // 0 表示无限循环

        for (i in frames.indices) {
            val frameMeta = frames[i]
            val file = File(frameDir, frameMeta.file)
            if (!file.exists()) continue

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
            // 延迟单位为百分之一秒（10ms）
            encoder.setDelay((frameMeta.delay / 10).coerceAtLeast(1))
            encoder.addFrame(bitmap)
            bitmap.recycle()

            onProgress((i + 1).toFloat() / frames.size.toFloat())
        }

        encoder.finish()
        outputStream.flush()
    }
}

/**
 * 纯 Kotlin 实现的标准 GIF89a 动图编码器
 */
private class GifSequenceWriter {
    private var out: OutputStream? = null
    private var delay = 10 // 百分之一秒
    private var repeat = 0
    private var isFirstFrame = true

    fun start(os: OutputStream) {
        out = os
        writeString("GIF89a")
    }

    fun setDelay(d: Int) {
        delay = d
    }

    fun setRepeat(r: Int) {
        repeat = r
    }

    fun addFrame(im: Bitmap): Boolean {
        val os = out ?: return false
        val width = im.width
        val height = im.height

        val pixels = IntArray(width * height)
        im.getPixels(pixels, 0, width, 0, 0, width, height)

        // 生成调色板并量化为 256 色
        val quantizer = NeuQuant(pixels, width, height)
        val colorTab = quantizer.process()
        val indexedPixels = quantizer.getIndexedPixels()

        if (isFirstFrame) {
            // 逻辑屏幕描述符
            writeShort(width)
            writeShort(height)
            os.write(0x80 or 0x70 or 0x07) // 全局调色板标志, 256 色
            os.write(0) // 背景颜色索引
            os.write(0) // 像素宽高比
            os.write(colorTab) // 全局调色板

            // Netscape 循环扩展块
            if (repeat >= 0) {
                os.write(0x21) // 扩展引导
                os.write(0xFF) // 应用扩展标签
                os.write(11)   // 块大小
                writeString("NETSCAPE2.0")
                os.write(3)    // 子块大小
                os.write(1)    // 循环子块
                writeShort(repeat)
                os.write(0)    // 块终结符
            }
            isFirstFrame = false
        }

        // 图形控制扩展块
        os.write(0x21)
        os.write(0xF9)
        os.write(4) // 块大小
        os.write(0) // 无透明度处理
        writeShort(delay)
        os.write(0) // 透明色索引
        os.write(0) // 块终结符

        // 图像描述符
        os.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        os.write(0) // 使用全局调色板

        // LZW 压缩编码
        val lzw = LzwEncoder(width, height, indexedPixels, 8)
        lzw.encode(os)

        return true
    }

    fun finish() {
        out?.write(0x3B) // GIF 文件结束符
        out?.flush()
    }

    private fun writeString(s: String) {
        out?.write(s.toByteArray(Charsets.US_ASCII))
    }

    private fun writeShort(value: Int) {
        out?.write(value and 0xFF)
        out?.write((value shr 8) and 0xFF)
    }
}

/**
 * 图像色彩量化器（基于快速中位切分/采样）
 */
private class NeuQuant(
    private val pixels: IntArray,
    private val width: Int,
    private val height: Int
) {
    private val palette = ByteArray(256 * 3)
    private val indexedPixels = ByteArray(pixels.size)

    fun process(): ByteArray {
        val colorMap = LinkedHashMap<Int, Int>()
        // 采样并建立颜色频率
        val step = (pixels.size / 4000).coerceAtLeast(1)
        for (i in 0 until pixels.size step step) {
            val p = pixels[i]
            val r = (p shr 16) and 0xF8
            val g = (p shr 8) and 0xF8
            val b = p and 0xF8
            val key = (r shl 16) or (g shl 8) or b
            colorMap[key] = (colorMap[key] ?: 0) + 1
        }

        val sortedColors = colorMap.keys.take(256)
        val paletteList = ArrayList<Int>(256)
        paletteList.addAll(sortedColors)
        while (paletteList.size < 256) {
            paletteList.add(0)
        }

        for (i in 0 until 256) {
            val c = paletteList[i]
            palette[i * 3] = ((c shr 16) and 0xFF).toByte()
            palette[i * 3 + 1] = ((c shr 8) and 0xFF).toByte()
            palette[i * 3 + 2] = (c and 0xFF).toByte()
        }

        // 映射每一个像素到调色板索引
        for (i in pixels.indices) {
            val p = pixels[i]
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF

            var bestIndex = 0
            var bestDistance = Int.MAX_VALUE

            for (j in 0 until 256) {
                val cr = palette[j * 3].toInt() and 0xFF
                val cg = palette[j * 3 + 1].toInt() and 0xFF
                val cb = palette[j * 3 + 2].toInt() and 0xFF
                val dr = pr - cr
                val dg = pg - cg
                val db = pb - cb
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestDistance) {
                    bestDistance = dist
                    bestIndex = j
                    if (dist == 0) break
                }
            }
            indexedPixels[i] = bestIndex.toByte()
        }

        return palette
    }

    fun getIndexedPixels(): ByteArray = indexedPixels
}

/**
 * GIF 专用 LZW 算法压缩器
 */
private class LzwEncoder(
    private val width: Int,
    private val height: Int,
    private val pixels: ByteArray,
    private val initCodeSize: Int
) {
    fun encode(os: OutputStream) {
        val clearCode = 1 shl initCodeSize
        val eoiCode = clearCode + 1
        var numBits = initCodeSize + 1
        var maxCode = (1 shl numBits) - 1
        var freeCode = clearCode + 2

        var curAccum = 0
        var curBits = 0

        val htab = IntArray(5003) { -1 }
        val codetab = IntArray(5003)

        fun writeByte(b: Int) {
            curAccum = curAccum or (b shl curBits)
            curBits += 8
            while (curBits >= 8) {
                buffer[bufIdx++] = (curAccum and 0xFF).toByte()
                if (bufIdx >= 254) {
                    os.write(bufIdx)
                    os.write(buffer, 0, bufIdx)
                    bufIdx = 0
                }
                curAccum = curAccum shr 8
                curBits -= 8
            }
        }

        fun writeCode(code: Int) {
            curAccum = curAccum or (code shl curBits)
            curBits += numBits
            while (curBits >= 8) {
                buffer[bufIdx++] = (curAccum and 0xFF).toByte()
                if (bufIdx >= 254) {
                    os.write(bufIdx)
                    os.write(buffer, 0, bufIdx)
                    bufIdx = 0
                }
                curAccum = curAccum shr 8
                curBits -= 8
            }
            if (freeCode > maxCode || clearFlg) {
                if (clearFlg) {
                    numBits = initCodeSize + 1
                    maxCode = (1 shl numBits) - 1
                    clearFlg = false
                } else {
                    numBits++
                    maxCode = if (numBits == 12) 4096 else (1 shl numBits) - 1
                }
            }
            if (code == eoiCode) {
                while (curBits > 0) {
                    buffer[bufIdx++] = (curAccum and 0xFF).toByte()
                    if (bufIdx >= 254) {
                        os.write(bufIdx)
                        os.write(buffer, 0, bufIdx)
                        bufIdx = 0
                    }
                    curAccum = curAccum shr 8
                    curBits -= 8
                }
                if (bufIdx > 0) {
                    os.write(bufIdx)
                    os.write(buffer, 0, bufIdx)
                    bufIdx = 0
                }
            }
        }

        os.write(initCodeSize)
        writeCode(clearCode)

        var ent = pixels[0].toInt() and 0xFF
        val len = width * height
        for (i in 1 until len) {
            val c = pixels[i].toInt() and 0xFF
            val fcode = (c shl 12) + ent
            var h = (c shl 4) xor ent
            if (h >= 5003) h -= 5003

            var disp = if (h == 0) 1 else 5003 - h
            var found = false

            while (htab[h] >= 0) {
                if (htab[h] == fcode) {
                    ent = codetab[h]
                    found = true
                    break
                }
                h -= disp
                if (h < 0) h += 5003
            }

            if (!found) {
                writeCode(ent)
                ent = c
                if (freeCode < 4096) {
                    codetab[h] = freeCode++
                    htab[h] = fcode
                } else {
                    htab.fill(-1)
                    freeCode = clearCode + 2
                    clearFlg = true
                    writeCode(clearCode)
                }
            }
        }

        writeCode(ent)
        writeCode(eoiCode)
        os.write(0) // 终结空块
    }

    private var clearFlg = false
    private val buffer = ByteArray(256)
    private var bufIdx = 0
}
