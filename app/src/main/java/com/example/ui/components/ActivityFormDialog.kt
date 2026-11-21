package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ActivityItem
import com.example.ui.theme.FieldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityFormDialog(
    initialActivity: ActivityItem? = null,
    currentUserId: String,
    currentWorkerName: String,
    currentWorkerId: String = "FLD-001",
    onDismiss: () -> Unit,
    onSave: (ActivityItem) -> Unit
) {
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    var title by remember { mutableStateOf(initialActivity?.title ?: "") }
    var category by remember { mutableStateOf(initialActivity?.category ?: "Instalasi") }
    var location by remember { mutableStateOf(initialActivity?.location ?: "") }
    var date by remember { mutableStateOf(initialActivity?.date ?: today) }
    var startTime by remember { mutableStateOf(initialActivity?.startTime ?: "08:30") }
    var endTime by remember { mutableStateOf(initialActivity?.endTime ?: "11:30") }
    var status by remember { mutableStateOf(initialActivity?.status ?: "IN_PROGRESS") }
    var workerIdInput by remember { mutableStateOf(initialActivity?.workerId ?: currentWorkerId) }
    var notes by remember { mutableStateOf(initialActivity?.notes ?: "") }
    var issues by remember { mutableStateOf(initialActivity?.issues ?: "") }
    var resolution by remember { mutableStateOf(initialActivity?.resolution ?: "") }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }

    val categories = listOf("Survey", "Instalasi", "Maintenance", "Kunjungan", "Inspeksi", "Pengiriman", "Koordinasi", "Lainnya")
    val statuses = listOf(
        "PENDING" to "Pending (Menunggu)",
        "IN_PROGRESS" to "Sedang Berjalan",
        "COMPLETED" to "Selesai Dikerjakan",
        "ISSUE" to "Ada Kendala Lapangan"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialActivity == null) "Catat Kegiatan Baru" else "Edit Kegiatan Lapangan",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FieldPrimary
                        )
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Judul Kegiatan
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Judul Kegiatan / Tugas *") },
                    placeholder = { Text("Contoh: Pemasangan ODP Tiang No 14") },
                    isError = titleError,
                    supportingText = if (titleError) { { Text("Judul kegiatan wajib diisi") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_activity_title")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category & Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Category Picker
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { categoryDropdownExpanded = true }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Status Picker
                    Box(modifier = Modifier.weight(1f)) {
                        val currentStatusLabel = statuses.firstOrNull { it.first == status }?.second ?: status
                        OutlinedTextField(
                            value = currentStatusLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Status") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { statusDropdownExpanded = true }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { statusDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = statusDropdownExpanded,
                            onDismissRequest = { statusDropdownExpanded = false }
                        ) {
                            statuses.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        status = code
                                        statusDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lokasi Lapangan
                OutlinedTextField(
                    value = location,
                    onValueChange = {
                        location = it
                        if (it.isNotBlank()) locationError = false
                    },
                    label = { Text("Lokasi / Area Lapangan *") },
                    placeholder = { Text("Contoh: Jl. Sudirman Kav. 25, Jaksel") },
                    isError = locationError,
                    supportingText = if (locationError) { { Text("Lokasi wajib diisi") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_activity_location")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tanggal & ID Karyawan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Tanggal (YYYY-MM-DD)") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = workerIdInput,
                        onValueChange = { workerIdInput = it },
                        label = { Text("NIP / ID Petugas") },
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Jam Mulai & Selesai
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Jam Mulai") },
                        placeholder = { Text("08:00") },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Jam Selesai") },
                        placeholder = { Text("11:30") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Catatan Hasil Kerja
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Detail / Hasil Kerja") },
                    placeholder = { Text("Tuliskan deskripsi pekerjaan yang telah dikerjakan...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_activity_notes")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Kendala Lapangan
                OutlinedTextField(
                    value = issues,
                    onValueChange = { issues = it },
                    label = { Text("Kendala Lapangan (Jika ada)") },
                    placeholder = { Text("Cuaca hujan, alat terhambat, izin akses, dll...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (issues.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resolution,
                        onValueChange = { resolution = it },
                        label = { Text("Solusi / Tindak Lanjut Kendala") },
                        placeholder = { Text("Langkah perbaikan atau jadwal ulang...") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            var valid = true
                            if (title.isBlank()) {
                                titleError = true
                                valid = false
                            }
                            if (location.isBlank()) {
                                locationError = true
                                valid = false
                            }
                            if (valid) {
                                val item = ActivityItem(
                                    id = initialActivity?.id ?: "",
                                    userId = currentUserId,
                                    workerName = currentWorkerName,
                                    workerId = workerIdInput.ifBlank { null },
                                    date = date.ifBlank { today },
                                    title = title.trim(),
                                    category = category,
                                    location = location.trim(),
                                    startTime = startTime.ifBlank { null },
                                    endTime = endTime.ifBlank { null },
                                    status = status,
                                    notes = notes.ifBlank { null },
                                    issues = issues.ifBlank { null },
                                    resolution = resolution.ifBlank { null },
                                    excelSource = initialActivity?.excelSource
                                )
                                onSave(item)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FieldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("submit_activity_button")
                    ) {
                        Text(if (initialActivity == null) "Simpan Kegiatan" else "Perbarui")
                    }
                }
            }
        }
    }
}
