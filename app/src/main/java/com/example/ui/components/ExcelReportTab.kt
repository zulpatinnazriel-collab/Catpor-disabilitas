package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExcelReportItem
import com.example.data.util.ExcelManager
import com.example.data.util.ParsedExcelSheet
import com.example.ui.theme.FieldAmber
import com.example.ui.theme.FieldPrimary
import com.example.ui.theme.StatusCompleted
import com.example.ui.theme.StatusCompletedContainer
import com.example.ui.viewmodel.ActivityViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun ExcelReportTab(
    viewModel: ActivityViewModel,
    workerName: String
) {
    val context = LocalContext.current
    val parsedSheet by viewModel.parsedExcelSheet.collectAsState()
    val excelReportsState by viewModel.excelReports.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var isParsingFile by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableStateOf(0L) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            isParsingFile = true
            try {
                // Query file name and size
                var name = "Laporan_Lapangan.xlsx"
                var size = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx)
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
                selectedFileName = name
                selectedFileSize = size

                val parsed = ExcelManager.parseUploadedFile(
                    context = context,
                    uri = uri,
                    fileName = name,
                    currentUserId = viewModel.currentUserId,
                    currentWorkerName = workerName
                )
                viewModel.setParsedExcelSheet(parsed)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memproses file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isParsingFile = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Upload Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(FieldPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload Excel",
                            tint = FieldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Upload File Excel Laporan Utama",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Unggah file laporan harian/mingguan (.xlsx atau .csv) untuk diproses, dilihat isi tabelnya, dan disinkronkan ke database server.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "*/*"
                                )
                            )
                        },
                        enabled = !isParsingFile,
                        colors = ButtonDefaults.buttonColors(containerColor = FieldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("upload_excel_button")
                    ) {
                        if (isParsingFile) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Membaca File Excel...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pilih File Excel Dari Perangkat")
                        }
                    }
                }
            }
        }

        // Section 2: Spreadsheet Preview & Actions (if a file is parsed)
        if (parsedSheet != null) {
            val sheet = parsedSheet!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = FieldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = sheet.fileName,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Sheet: ${sheet.sheetName} • ${sheet.rows.size} baris data • ${sheet.convertedActivities.size} tugas terdeteksi",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Spreadsheet Grid Table Viewer
                        if (sheet.headers.isNotEmpty() && sheet.rows.isNotEmpty()) {
                            Text(
                                text = "Pratinjau Spreadsheet Excel:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Column {
                                    // Header Row
                                    Row(
                                        modifier = Modifier
                                            .background(FieldPrimary)
                                            .padding(vertical = 8.dp)
                                    ) {
                                        sheet.headers.forEach { header ->
                                            Text(
                                                text = header,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                ),
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .padding(horizontal = 8.dp)
                                            )
                                        }
                                    }

                                    // First 5 Preview Rows
                                    val previewRows = sheet.rows.take(6)
                                    previewRows.forEachIndexed { idx, row ->
                                        Row(
                                            modifier = Modifier
                                                .background(if (idx % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .padding(vertical = 8.dp)
                                        ) {
                                            sheet.headers.indices.forEach { colIdx ->
                                                val cellVal = if (colIdx < row.size) row[colIdx] else ""
                                                Text(
                                                    text = cellVal,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .width(140.dp)
                                                        .padding(horizontal = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (sheet.rows.size > 6) {
                                Text(
                                    text = "... dan ${sheet.rows.size - 6} baris lainnya dalam dokumen.",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action 1: Import rows into Real-time database
                        Button(
                            onClick = {
                                viewModel.importActivitiesFromExcel(sheet) { count ->
                                    Toast.makeText(context, "$count kegiatan berhasil diimpor & tersinkron real-time!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusCompleted),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_excel_rows_button")
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Impor Baris ke Kegiatan Real-time (${sheet.convertedActivities.size} Kegiatan)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action 2: Save as Master Excel Report
                        OutlinedButton(
                            onClick = {
                                viewModel.registerUploadedExcelReport(sheet, selectedFileSize) { success, _ ->
                                    if (success) {
                                        Toast.makeText(context, "Laporan Excel berhasil diarsipkan di server!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isSyncing,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_master_report_button")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simpan Sebagai Laporan Utama di Server")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Cancel preview
                        FilledTonalButton(
                            onClick = { viewModel.setParsedExcelSheet(null) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tutup Pratinjau")
                        }
                    }
                }
            }
        }

        // Section 3: Template Download Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Template Excel Baku",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Unduh contoh format file Excel (.xlsx) dengan kolom standar kegiatan lapangan.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val templateFile = ExcelManager.generateTemplateXlsx(context)
                            val uri = ExcelManager.getFileUri(context, templateFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Template Excel"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FieldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("download_template_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Template")
                    }
                }
            }
        }

        // Section 4: Uploaded Master Excel Reports History
        item {
            Text(
                text = "Histori Laporan Utama Excel Terunggah",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        when (val state = excelReportsState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = FieldPrimary)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is UiState.Success -> {
                val reports = state.data
                if (reports.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Belum Ada Laporan Excel",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "File Excel yang Anda unggah sebagai laporan utama akan tercatat dan tersinkron di sini.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(reports, key = { it.id }) { report ->
                        ExcelReportHistoryCard(
                            report = report,
                            isAdmin = viewModel.isAdmin,
                            onVerify = { viewModel.verifyExcelReport(report.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExcelReportHistoryCard(
    report: ExcelReportItem,
    isAdmin: Boolean = false,
    onVerify: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("excel_report_card_${report.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FieldPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = FieldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.fileName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${report.reportDate} • ${report.totalRows} baris • ${report.fileType}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = "Petugas: ${report.workerName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FieldPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!report.notes.isNullOrBlank()) {
                        Text(
                            text = report.notes,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val isVerified = report.status == "VERIFIED"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isVerified) Color(0xFFDCFCE7) else StatusCompletedContainer
                ) {
                    Text(
                        text = when (report.status) {
                            "VERIFIED" -> "✓ Diverifikasi"
                            "PROCESSED" -> "Diimpor"
                            else -> "Tersimpan"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isVerified) Color(0xFF15803D) else StatusCompleted,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Administrator verification action button
            if (isAdmin && report.status != "VERIFIED") {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onVerify,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_report_${report.id}")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verifikasi Laporan Ini (Administrator)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
