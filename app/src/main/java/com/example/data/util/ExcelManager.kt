package com.example.data.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.core.content.FileProvider
import com.example.data.model.ActivityItem
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ParsedExcelSheet(
    val fileName: String,
    val sheetName: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val convertedActivities: List<ActivityItem>
)

object ExcelManager {

    /**
     * Parses an uploaded Excel (.xlsx) or CSV (.csv) file into a structured sheet representation.
     */
    fun parseUploadedFile(context: Context, uri: Uri, fileName: String, currentUserId: String, currentWorkerName: String): ParsedExcelSheet {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".csv") -> parseCsv(context, uri, fileName, currentUserId, currentWorkerName)
            else -> parseXlsx(context, uri, fileName, currentUserId, currentWorkerName)
        }
    }

    private fun parseCsv(context: Context, uri: Uri, fileName: String, userId: String, workerName: String): ParsedExcelSheet {
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Tidak dapat membuka file CSV")
        val lines = inputStream.bufferedReader(StandardCharsets.UTF_8).readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return ParsedExcelSheet(fileName, "Sheet1", emptyList(), emptyList(), emptyList())
        }

        // Detect separator (, or ;)
        val firstLine = lines.first()
        val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','

        val allRows = lines.map { parseCsvLine(it, delimiter) }
        val headers = allRows.firstOrNull() ?: emptyList()
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        val activities = mapRowsToActivities(headers, dataRows, userId, workerName, fileName)
        return ParsedExcelSheet(
            fileName = fileName,
            sheetName = "Sheet1",
            headers = headers,
            rows = dataRows,
            convertedActivities = activities
        )
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    /**
     * Parses OpenXML .xlsx file using standard ZipInputStream and XmlPullParser.
     */
    private fun parseXlsx(context: Context, uri: Uri, fileName: String, userId: String, workerName: String): ParsedExcelSheet {
        val inputStream = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Tidak dapat membuka file Excel")
        val bytes = inputStream.readBytes()
        inputStream.close()

        val sharedStrings = mutableListOf<String>()
        var sheetXmlBytes: ByteArray? = null

        // Pass 1: Extract sharedStrings.xml and sheet1.xml
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") {
                    sharedStrings.addAll(parseSharedStrings(zis.readBytes()))
                } else if (name.endsWith("sheet1.xml") || (name.startsWith("xl/worksheets/sheet") && sheetXmlBytes == null)) {
                    sheetXmlBytes = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (sheetXmlBytes == null) {
            // Fallback: If no standard sheet was parsed, return empty
            return ParsedExcelSheet(fileName, "Sheet1", emptyList(), emptyList(), emptyList())
        }

        val allRows = parseSheetXml(sheetXmlBytes!!, sharedStrings)
        val headers = allRows.firstOrNull() ?: emptyList()
        val dataRows = if (allRows.size > 1) allRows.drop(1) else emptyList()

        val activities = mapRowsToActivities(headers, dataRows, userId, workerName, fileName)
        return ParsedExcelSheet(
            fileName = fileName,
            sheetName = "Sheet1",
            headers = headers,
            rows = dataRows,
            convertedActivities = activities
        )
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        var eventType = parser.eventType
        var currentText = StringBuilder()
        var insideTextTag = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        insideTextTag = true
                        currentText.clear()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideTextTag) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") {
                        insideTextTag = false
                    } else if (parser.name == "si") {
                        strings.add(currentText.toString())
                        currentText.clear()
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseSheetXml(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
        var eventType = parser.eventType

        var currentRowCells = mutableListOf<String>()
        var currentCellType = ""
        var currentCellValue = StringBuilder()
        var insideValueTag = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRowCells = mutableListOf()
                        }
                        "c" -> {
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentCellValue.clear()
                        }
                        "v", "t" -> {
                            insideValueTag = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideValueTag) {
                        currentCellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> {
                            insideValueTag = false
                        }
                        "c" -> {
                            val raw = currentCellValue.toString().trim()
                            val formattedVal = if (currentCellType == "s") {
                                val idx = raw.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else {
                                raw
                            }
                            currentRowCells.add(formattedVal)
                        }
                        "row" -> {
                            if (currentRowCells.any { it.isNotBlank() }) {
                                rows.add(currentRowCells.toList())
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    /**
     * Intelligently maps Excel rows to ActivityItem candidates based on detected column headers.
     */
    private fun mapRowsToActivities(
        headers: List<String>,
        rows: List<List<String>>,
        userId: String,
        defaultWorkerName: String,
        sourceFileName: String
    ): List<ActivityItem> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        var dateIdx = -1
        var titleIdx = -1
        var categoryIdx = -1
        var locationIdx = -1
        var startIdx = -1
        var endIdx = -1
        var statusIdx = -1
        var notesIdx = -1
        var issuesIdx = -1
        var workerIdx = -1
        var nipIdx = -1

        headers.forEachIndexed { i, h ->
            val clean = h.lowercase(Locale.ROOT).replace("_", " ").trim()
            when {
                clean.contains("tanggal") || clean.contains("date") -> dateIdx = i
                clean.contains("judul") || clean.contains("kegiatan") || clean.contains("tugas") || clean.contains("aktivitas") || clean.contains("pekerjaan") -> {
                    if (titleIdx == -1) titleIdx = i
                }
                clean.contains("kategori") || clean.contains("jenis") -> categoryIdx = i
                clean.contains("lokasi") || clean.contains("alamat") || clean.contains("wilayah") || clean.contains("area") -> locationIdx = i
                clean.contains("mulai") || clean.contains("start") -> startIdx = i
                clean.contains("selesai") || clean.contains("end") || clean.contains("akhir") -> endIdx = i
                clean.contains("status") -> statusIdx = i
                clean.contains("catatan") || clean.contains("keterangan") || clean.contains("hasil") || clean.contains("deskripsi") -> notesIdx = i
                clean.contains("kendala") || clean.contains("masalah") || clean.contains("hambatan") -> issuesIdx = i
                clean.contains("nama") || clean.contains("petugas") || clean.contains("karyawan") -> workerIdx = i
                clean.contains("nip") || clean.contains("id karyawan") || clean.contains("nik") -> nipIdx = i
            }
        }

        // Fallback default index assignments if headers were simple / non-matching
        if (titleIdx == -1 && headers.isNotEmpty()) titleIdx = 0
        if (locationIdx == -1 && headers.size > 1) locationIdx = 1

        val activities = mutableListOf<ActivityItem>()
        rows.forEach { row ->
            fun getCol(idx: Int): String? {
                if (idx >= 0 && idx < row.size) {
                    val v = row[idx].trim()
                    return if (v.isNotBlank()) v else null
                }
                return null
            }

            val title = getCol(titleIdx) ?: "Tugas Lapangan"
            val location = getCol(locationIdx) ?: "Area Lapangan"
            val category = normalizeCategory(getCol(categoryIdx))
            val status = normalizeStatus(getCol(statusIdx))
            val date = normalizeDate(getCol(dateIdx)) ?: today
            val worker = getCol(workerIdx) ?: defaultWorkerName
            val workerId = getCol(nipIdx)
            val startTime = getCol(startIdx)
            val endTime = getCol(endIdx)
            val notes = getCol(notesIdx)
            val issues = getCol(issuesIdx)

            activities.add(
                ActivityItem(
                    userId = userId,
                    workerName = worker,
                    workerId = workerId,
                    date = date,
                    title = title,
                    category = category,
                    location = location,
                    startTime = startTime,
                    endTime = endTime,
                    status = status,
                    notes = notes,
                    issues = issues,
                    excelSource = sourceFileName
                )
            )
        }
        return activities
    }

    private fun normalizeCategory(raw: String?): String {
        if (raw == null) return "Lainnya"
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("survey") -> "Survey"
            lower.contains("instal") || lower.contains("pasang") -> "Instalasi"
            lower.contains("mainten") || lower.contains("rawat") || lower.contains("servis") -> "Maintenance"
            lower.contains("kunjung") || lower.contains("visit") -> "Kunjungan"
            lower.contains("inspek") || lower.contains("cek") -> "Inspeksi"
            lower.contains("kirim") || lower.contains("antar") -> "Pengiriman"
            lower.contains("koordinasi") || lower.contains("rapat") -> "Koordinasi"
            else -> "Lainnya"
        }
    }

    private fun normalizeStatus(raw: String?): String {
        if (raw == null) return "COMPLETED"
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("selesai") || lower.contains("done") || lower.contains("complete") -> "COMPLETED"
            lower.contains("jalan") || lower.contains("progress") || lower.contains("proses") -> "IN_PROGRESS"
            lower.contains("kendala") || lower.contains("issue") || lower.contains("masalah") -> "ISSUE"
            lower.contains("pending") || lower.contains("tunda") || lower.contains("menunggu") -> "PENDING"
            else -> "COMPLETED"
        }
    }

    private fun normalizeDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim().replace("/", "-")
        // Check if YYYY-MM-DD
        if (clean.matches(Regex("""\d{4}-\d{1,2}-\d{1,2}"""))) {
            val parts = clean.split("-")
            val y = parts[0]
            val m = parts[1].padStart(2, '0')
            val d = parts[2].padStart(2, '0')
            return "$y-$m-$d"
        }
        // Check if DD-MM-YYYY
        if (clean.matches(Regex("""\d{1,2}-\d{1,2}-\d{4}"""))) {
            val parts = clean.split("-")
            val d = parts[0].padStart(2, '0')
            val m = parts[1].padStart(2, '0')
            val y = parts[2]
            return "$y-$m-$d"
        }
        return null
    }

    /**
     * Exports activity logs into a complete, valid Microsoft Excel (.xlsx) OpenXML workbook.
     */
    fun exportToXlsx(
        context: Context,
        activities: List<ActivityItem>,
        workerName: String,
        filterTitle: String = "Semua Kegiatan"
    ): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Laporan_Kegiatan_Lapangan_$timestampStr.xlsx")

        val sheetXml = buildSheetXml(activities, workerName, filterTitle)
        val stylesXml = buildStylesXml()
        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Laporan Lapangan" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbookRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(file)).use { zos ->
            fun writeEntry(path: String, content: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            writeEntry("[Content_Types].xml", contentTypesXml)
            writeEntry("_rels/.rels", rootRelsXml)
            writeEntry("xl/_rels/workbook.xml.rels", workbookRelsXml)
            writeEntry("xl/workbook.xml", workbookXml)
            writeEntry("xl/styles.xml", stylesXml)
            writeEntry("xl/worksheets/sheet1.xml", sheetXml)
        }

        return file
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="14"/><color rgb="FF1E3A8A"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E3A8A"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/></border>
    <border>
      <left style="thin"><color rgb="FFD1D5DB"/></left>
      <right style="thin"><color rgb="FFD1D5DB"/></right>
      <top style="thin"><color rgb="FFD1D5DB"/></top>
      <bottom style="thin"><color rgb="FFD1D5DB"/></bottom>
    </border>
  </borders>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""
    }

    private fun buildSheetXml(
        activities: List<ActivityItem>,
        workerName: String,
        filterTitle: String
    ): String {
        val total = activities.size
        val completed = activities.count { it.status == "COMPLETED" }
        val inProgress = activities.count { it.status == "IN_PROGRESS" }
        val issue = activities.count { it.status == "ISSUE" }
        val pending = activities.count { it.status == "PENDING" }

        val printDate = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID")).format(Date())

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="6" customWidth="1"/>
    <col min="2" max="2" width="14" customWidth="1"/>
    <col min="3" max="3" width="18" customWidth="1"/>
    <col min="4" max="4" width="16" customWidth="1"/>
    <col min="5" max="5" width="28" customWidth="1"/>
    <col min="6" max="6" width="24" customWidth="1"/>
    <col min="7" max="7" width="12" customWidth="1"/>
    <col min="8" max="8" width="12" customWidth="1"/>
    <col min="9" max="9" width="16" customWidth="1"/>
    <col min="10" max="10" width="35" customWidth="1"/>
    <col min="11" max="11" width="25" customWidth="1"/>
  </cols>
  <sheetData>
""")

        fun escapeXml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun appendCell(col: String, row: Int, value: String, styleIndex: Int = 0) {
            val escaped = escapeXml(value)
            sb.append("""      <c r="$col$row" s="$styleIndex" t="inlineStr"><is><t>$escaped</t></is></c>
""")
        }

        var r = 1
        // Title
        sb.append("""    <row r="$r">
""")
        appendCell("A", r, "LAPORAN KEGIATAN HARIAN LAPANGAN", 1)
        sb.append("    </row>\n")

        r++
        sb.append("""    <row r="$r">
""")
        appendCell("A", r, "Petugas: $workerName | Filter: $filterTitle | Dicetak: $printDate", 0)
        sb.append("    </row>\n")

        r++
        sb.append("""    <row r="$r">
""")
        appendCell("A", r, "Ringkasan: Total $total Kegiatan (Selesai: $completed, Berjalan: $inProgress, Kendala: $issue, Pending: $pending)", 0)
        sb.append("    </row>\n")

        r++ // blank row

        r++
        // Headers
        sb.append("""    <row r="$r">
""")
        val headers = listOf("No", "Tanggal", "Petugas", "Kategori", "Judul Kegiatan", "Lokasi", "Mulai", "Selesai", "Status", "Catatan Hasil Kerja", "Kendala Lapangan")
        val cols = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
        headers.forEachIndexed { i, h ->
            appendCell(cols[i], r, h, 2)
        }
        sb.append("    </row>\n")

        // Rows
        activities.forEachIndexed { index, act ->
            r++
            sb.append("""    <row r="$r">
""")
            val statusDisplay = when (act.status) {
                "COMPLETED" -> "Selesai"
                "IN_PROGRESS" -> "Sedang Berjalan"
                "ISSUE" -> "Ada Kendala"
                else -> "Pending"
            }

            appendCell("A", r, (index + 1).toString(), 3)
            appendCell("B", r, act.date, 3)
            appendCell("C", r, act.workerName, 3)
            appendCell("D", r, act.category, 3)
            appendCell("E", r, act.title, 3)
            appendCell("F", r, act.location, 3)
            appendCell("G", r, act.startTime ?: "-", 3)
            appendCell("H", r, act.endTime ?: "-", 3)
            appendCell("I", r, statusDisplay, 3)
            appendCell("J", r, act.notes ?: "-", 3)
            appendCell("K", r, act.issues ?: "-", 3)
            sb.append("    </row>\n")
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    /**
     * Exports activity logs into a CSV formatted file with UTF-8 BOM.
     */
    fun exportToCsv(
        context: Context,
        activities: List<ActivityItem>,
        workerName: String
    ): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(reportsDir, "Laporan_Kegiatan_Lapangan_$timestampStr.csv")

        file.outputStream().use { fos ->
            // Write UTF-8 BOM so Excel opens it with proper encoding
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val writer = fos.bufferedWriter(StandardCharsets.UTF_8)

            writer.write("\"No\",\"Tanggal\",\"Petugas\",\"ID Karyawan\",\"Kategori\",\"Judul Kegiatan\",\"Lokasi\",\"Jam Mulai\",\"Jam Selesai\",\"Status\",\"Catatan Hasil Kerja\",\"Kendala\",\"Solusi\"\n")

            activities.forEachIndexed { index, act ->
                val statusDisplay = when (act.status) {
                    "COMPLETED" -> "Selesai"
                    "IN_PROGRESS" -> "Sedang Berjalan"
                    "ISSUE" -> "Ada Kendala"
                    else -> "Pending"
                }

                fun esc(s: String?): String = "\"${(s ?: "-").replace("\"", "\"\"")}\""

                writer.write("${index + 1},${esc(act.date)},${esc(act.workerName)},${esc(act.workerId)},${esc(act.category)},${esc(act.title)},${esc(act.location)},${esc(act.startTime)},${esc(act.endTime)},${esc(statusDisplay)},${esc(act.notes)},${esc(act.issues)},${esc(act.resolution)}\n")
            }
            writer.flush()
        }
        return file
    }

    /**
     * Generates a template Excel (.xlsx) file that field workers or supervisors can use as a reference.
     */
    fun generateTemplateXlsx(context: Context): File {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sampleActivities = listOf(
            ActivityItem(
                date = today,
                workerName = "Budi Pratama",
                workerId = "FLD-014",
                category = "Survey",
                title = "Survey Lokasi Penarikan Kabel FO Cluster A",
                location = "Jl. Merdeka Barat No. 10, Jakarta",
                startTime = "08:30",
                endTime = "10:30",
                status = "COMPLETED",
                notes = "Jalur kabel tersedia, koordinasi dengan RT setempat aman.",
                issues = "Cuaca gerimis ringan, tidak menghambat survey."
            ),
            ActivityItem(
                date = today,
                workerName = "Budi Pratama",
                workerId = "FLD-014",
                category = "Instalasi",
                title = "Pemasangan Box ODP dan Splicing Core",
                location = "Tiang No. 12 Sektor Timur",
                startTime = "11:00",
                endTime = "13:30",
                status = "COMPLETED",
                notes = "Box terpasang rapi, redaman optik -18 dBm (standar baik).",
                issues = "-"
            ),
            ActivityItem(
                date = today,
                workerName = "Budi Pratama",
                workerId = "FLD-014",
                category = "Maintenance",
                title = "Pengecekan Power Genset Cadangan BTS",
                location = "BTS Area Cikarang Utama",
                startTime = "14:30",
                endTime = "16:00",
                status = "IN_PROGRESS",
                notes = "Oli mesin diganti, tes running genset selama 30 menit.",
                issues = "Indikator aki agak lemah, perlu penggantian minggu depan."
            )
        )
        return exportToXlsx(context, sampleActivities, "Contoh Template", "Format Baku Kegiatan Lapangan")
    }

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
}
