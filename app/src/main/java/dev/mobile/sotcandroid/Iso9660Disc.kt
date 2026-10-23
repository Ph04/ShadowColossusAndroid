package dev.mobile.sotcandroid

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Locale

/** Safe ISO-9660 extractor for the USA PS2 disc; never trusts the filename alone. */
object Iso9660Disc {
    private const val SECTOR = 2048L
    private const val MAX_DESCRIPTOR_SECTORS = 256L
    private const val MAX_ENTRIES = 100_000
    private const val MAX_DEPTH = 24
    private const val MAX_DIRECTORY_BYTES = 64L * 1024 * 1024
    private const val MIN_IMAGE_BYTES = 256L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024 * 1024
    private val charset: Charset = Charset.forName("ISO-8859-1")

    data class DiscSummary(
        val fileCount: Int,
        val extractedBytes: Long,
        val bootElfBytes: Long,
        val kernelBytes: Long,
        val managerBytes: Long,
        val gamecoreBytes: Long,
    )

    private data class Entry(val path: String, val lba: Long, val size: Long, val directory: Boolean)
    private data class Volume(val rootLba: Long, val rootSize: Long)

    fun extract(iso: File, destination: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): DiscSummary {
        if (!iso.isFile) throw IOException("A imagem selecionada não é um arquivo.")
        val imageBytes = iso.length()
        if (imageBytes < MIN_IMAGE_BYTES || imageBytes > MAX_IMAGE_BYTES || imageBytes % SECTOR != 0L) {
            throw IOException("A imagem não parece uma ISO de DVD válida (tamanho: " + imageBytes + " bytes).")
        }

        val entries = RandomAccessFile(iso, "r").use { input ->
            val volume = locateVolume(input, imageBytes)
            val output = mutableListOf<Entry>()
            collectDirectory(input, imageBytes, volume.rootLba, volume.rootSize, "", 0, mutableSetOf(), output)
            validateRequiredRootEntries(output)
            output
        }

        if (destination.exists() && destination.listFiles()?.isNotEmpty() == true) {
            throw IOException("A pasta temporária de extração precisa estar vazia.")
        }
        if (!destination.exists() && !destination.mkdirs()) throw IOException("Não foi possível criar a pasta de extração.")

        val files = entries.filterNot { it.directory }
        val totalPayload = files.sumOf { it.size }
        var extracted = 0L
        try {
            RandomAccessFile(iso, "r").use { input ->
                for (entry in entries) {
                    val target = safeTarget(destination, entry.path)
                    if (entry.directory) {
                        if (!target.exists() && !target.mkdirs()) {
                            throw IOException("Não foi possível criar a pasta " + entry.path + ".")
                        }
                        continue
                    }
                    target.parentFile?.let { parent ->
                        if (!parent.exists() && !parent.mkdirs()) throw IOException("Não foi possível criar uma pasta da ISO.")
                    }
                    input.seek(entry.lba * SECTOR)
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = entry.size
                        while (remaining > 0L) {
                            val wanted = minOf(buffer.size.toLong(), remaining).toInt()
                            val count = input.read(buffer, 0, wanted)
                            if (count <= 0) throw IOException("Leitura incompleta do arquivo " + entry.path + ".")
                            output.write(buffer, 0, count)
                            remaining -= count.toLong()
                            extracted += count.toLong()
                            onProgress(extracted, totalPayload)
                        }
                        output.fd.sync()
                    }
                }
            }

            val system = File(destination, "SYSTEM.CNF")
            if (!system.isFile) throw IOException("A imagem não contém SYSTEM.CNF na raiz.")
            val bootLine = Regex("(?im)^\\s*BOOT2\\s*=\\s*cdrom0:\\\\SCUS_974\\.72(?:;1)?\\s*$")
            if (!bootLine.containsMatchIn(system.readText(Charsets.ISO_8859_1))) {
                throw IOException("SYSTEM.CNF não aponta para cdrom0:\\SCUS_974.72; a imagem pode ser outra edição.")
            }

            fun required(name: String): File = File(destination, name).also {
                if (!it.isFile || it.length() <= 0L) throw IOException("Falta o arquivo obrigatório " + name + ".")
            }
            val boot = required("SCUS_974.72")
            val kernel = required("KERNEL.XFF")
            val manager = required("MANAGER.XFF")
            val gamecore = required("GAMECORE.XFF")
            onProgress(totalPayload, totalPayload)
            return DiscSummary(files.size, extracted, boot.length(), kernel.length(), manager.length(), gamecore.length())
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }
    }

    private fun locateVolume(input: RandomAccessFile, imageBytes: Long): Volume {
        val lastSector = minOf(imageBytes / SECTOR, 16L + MAX_DESCRIPTOR_SECTORS)
        val data = ByteArray(SECTOR.toInt())
        var primary: Volume? = null
        var sector = 16L
        while (sector < lastSector) {
            input.seek(sector * SECTOR)
            input.readFully(data)
            if (data.copyOfRange(1, 6).toString(charset) != "CD001" || (data[6].toInt() and 0xFF) != 1) {
                throw IOException("Descritor ISO-9660 inválido no setor " + sector + ".")
            }
            when (data[0].toInt() and 0xFF) {
                1 -> if (primary == null) {
                    val blockSize = u16(data, 128)
                    if (blockSize != SECTOR.toInt()) throw IOException("A ISO usa blocos de " + blockSize + " bytes.")
                    val length = data[156].toInt() and 0xFF
                    if (length < 34 || 156 + length > data.size) throw IOException("Registro do diretório raiz inválido.")
                    val lba = u32(data, 158)
                    val size = u32(data, 166)
                    checkRange(lba, size, imageBytes, "diretório raiz")
                    primary = Volume(lba, size)
                }
                255 -> break
            }
            sector++
        }
        return primary ?: throw IOException("Não encontrei o descritor de volume ISO-9660 (CD001).")
    }

    private fun collectDirectory(
        input: RandomAccessFile,
        imageBytes: Long,
        lba: Long,
        size: Long,
        parent: String,
        depth: Int,
        active: MutableSet<Long>,
        entries: MutableList<Entry>,
    ) {
        if (depth > MAX_DEPTH) throw IOException("A estrutura de diretórios da ISO é profunda demais.")
        if (size < 0L || size > MAX_DIRECTORY_BYTES) throw IOException("Diretório ISO inválido: " + parent + ".")
        checkRange(lba, size, imageBytes, "diretório " + parent)
        if (!active.add(lba)) throw IOException("Ciclo detectado na árvore de diretórios.")
        try {
            val bytes = ByteArray(size.toInt())
            input.seek(lba * SECTOR)
            input.readFully(bytes)
            var offset = 0
            while (offset < bytes.size) {
                val length = bytes[offset].toInt() and 0xFF
                if (length == 0) {
                    offset = ((offset / SECTOR.toInt()) + 1) * SECTOR.toInt()
                    continue
                }
                if (length < 34 || offset + length > bytes.size) throw IOException("Registro de diretório truncado.")
                val identifierLength = bytes[offset + 32].toInt() and 0xFF
                if (33 + identifierLength > length) throw IOException("Identificador ISO-9660 inválido.")
                val specialId = identifierLength == 1 && ((bytes[offset + 33].toInt() and 0xFF) in 0..1)
                if (!specialId) {
                    val identifier = bytes.copyOfRange(offset + 33, offset + 33 + identifierLength).toString(charset)
                    val name = normalizeName(identifier)
                    if (name.isNotEmpty()) {
                        val relative = if (parent.isEmpty()) name else parent + "/" + name
                        if (relative.split('/').any { it == "." || it == ".." || it.isEmpty() }) {
                            throw IOException("Caminho inseguro na ISO: " + relative + ".")
                        }
                        val lbaValue = u32(bytes, offset + 2)
                        val byteSize = u32(bytes, offset + 10)
                        val flags = bytes[offset + 25].toInt() and 0xFF
                        if ((flags and 0x80) != 0) throw IOException("Arquivos ISO multi-extent ainda não são suportados.")
                        val isDirectory = (flags and 0x02) != 0
                        checkRange(lbaValue, byteSize, imageBytes, relative)
                        val entry = Entry(relative, lbaValue, byteSize, isDirectory)
                        entries += entry
                        if (entries.size > MAX_ENTRIES) throw IOException("A ISO possui entradas demais para extrair.")
                        if (isDirectory) {
                            collectDirectory(input, imageBytes, lbaValue, byteSize, relative, depth + 1, active, entries)
                        }
                    }
                }
                offset += length
            }
        } finally {
            active.remove(lba)
        }
    }

    private fun validateRequiredRootEntries(entries: List<Entry>) {
        val names = entries.asSequence()
            .filter { !it.directory && !it.path.contains('/') }
            .map { it.path.uppercase(Locale.ROOT) }
            .toSet()
        val required = listOf("SYSTEM.CNF", "SCUS_974.72", "KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF")
        val missing = required.filterNot(names::contains)
        if (missing.isNotEmpty()) {
            throw IOException("Esta não parece ser a edição USA esperada; faltam na raiz: " + missing.joinToString(", ") + ".")
        }
    }

    private fun normalizeName(value: String): String {
        var name = value.trim().uppercase(Locale.ROOT)
        val semi = name.lastIndexOf(';')
        if (semi >= 0 && semi + 1 < name.length && name.substring(semi + 1).all { it.isDigit() }) {
            name = name.substring(0, semi)
        }
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains(':')) return ""
        return name
    }

    private fun safeTarget(root: File, relative: String): File {
        val base = root.canonicalFile
        val target = File(base, relative).canonicalFile
        if (target != base && !target.path.startsWith(base.path + File.separator)) {
            throw IOException("Caminho de extração fora do destino permitido.")
        }
        return target
    }

    private fun checkRange(lba: Long, bytes: Long, imageBytes: Long, what: String) {
        if (lba < 0L || bytes < 0L || lba > imageBytes / SECTOR ||
            bytes > imageBytes || lba * SECTOR > imageBytes - bytes) {
            throw IOException("Intervalo fora da imagem para " + what + " (LBA=" + lba + ", bytes=" + bytes + ").")
        }
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
}
