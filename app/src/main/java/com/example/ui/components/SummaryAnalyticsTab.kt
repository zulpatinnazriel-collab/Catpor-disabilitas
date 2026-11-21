package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.util.ExcelManager
import com.example.ui.theme.FieldAmber
import com.example.ui.theme.FieldPrimary
import com.example.ui.theme.StatusCompleted
import com.example.ui.theme.StatusCompletedContainer
import com.example.ui.theme.StatusInProgress
import com.example.ui.theme.StatusInProgressContainer
import com.example.ui.theme.StatusIssue
import com.example.ui.theme.StatusIssueContainer
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusPendingContainer
import com.example.ui.viewmodel.ActivityViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun SummaryAnalyticsTab(
    viewModel: ActivityViewModel,
    workerName: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val rawActivitiesState by viewModel.rawActivities.collectAsState()
    val activities = if (rawActivitiesState is UiState.Success) {
        (rawActivitiesState as UiState.Success).data
    } else emptyList()

    val total = activities.size
    val completed = activities.count { it.status == "COMPLETED" }
    val inProgress = activities.count { it.status == "IN_PROGRESS" }
    val issues = activities.count { it.status == "ISSUE" }
    val pending = activities.count { it.status == "PENDING" }

    val completionRate = if (total > 0) ((completed.toFloat() / total) * 100).toInt() else 0

    val currentUser = Firebase.auth.currentUser

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val allWorkers by viewModel.allWorkers.collectAsState()

        // Worker / Admin Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (viewModel.isAdmin) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.isAdmin) Color(0xFFD97706) else FieldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profil Petugas",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = workerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (viewModel.isAdmin) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFD700)
                            ) {
                                Text(
                                    text = "ADMIN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF78350F),
                                        fontSize = 9.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = currentUser?.email ?: "Petugas Lapangan",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = if (viewModel.isAdmin) "👑 Administrator & Supervisor Lapangan" else "Divisi Operasional & Lapangan",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (viewModel.isAdmin) Color(0xFFB45309) else FieldPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Keluar")
                }
            }
        }

        // Summary Metric Cards Grid
        Text(
            text = "Ringkasan Kinerja Lapangan",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Tugas",
                count = total.toString(),
                subtitle = "Semua kegiatan",
                icon = Icons.Default.Summarize,
                color = FieldPrimary,
                bgColor = FieldPrimary.copy(alpha = 0.1f)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Selesai",
                count = "$completed ($completionRate%)",
                subtitle = "Tercapai",
                icon = Icons.Default.AssignmentTurnedIn,
                color = StatusCompleted,
                bgColor = StatusCompletedContainer
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Sedang Berjalan",
                count = inProgress.toString(),
                subtitle = "Dalam progres",
                icon = Icons.Default.PlayCircleFilled,
                color = StatusInProgress,
                bgColor = StatusInProgressContainer
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Ada Kendala",
                count = issues.toString(),
                subtitle = "Perlu tindakan",
                icon = Icons.Default.ErrorOutline,
                color = StatusIssue,
                bgColor = StatusIssueContainer
            )
        }

        // Administrator: Team Monitoring Section
        if (viewModel.isAdmin) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "👥", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Daftar Tim Lapangan Terdaftar",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FieldPrimary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${allWorkers.size} Petugas",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FieldPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (allWorkers.isEmpty()) {
                        Text(
                            text = "Belum ada anggota tim lain terhubung.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    } else {
                        allWorkers.forEach { worker ->
                            val workerActivityCount = activities.count { it.userId == worker.userId }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = worker.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            if (worker.role == "ADMIN") {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(Admin)",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color(0xFFB45309),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${worker.workerId ?: "-"} • ${worker.department ?: "Lapangan"} • ${worker.email}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (workerActivityCount > 0) StatusCompletedContainer else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = "$workerActivityCount Tugas",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (workerActivityCount > 0) StatusCompleted else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Ekspor Laporan Excel
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = FieldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (viewModel.isAdmin) "Ekspor Rekap Laporan Tim (Excel)" else "Ekspor Laporan Kegiatan",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (viewModel.isAdmin) {
                        "Ekspor seluruh rekapitulasi kegiatan tim lapangan (mencakup nama petugas, waktu pengerjaan, lokasi, dan status) langsung ke format Microsoft Excel (.xlsx) atau CSV."
                    } else {
                        "Ekspor seluruh catatan kegiatan harian Anda ke dalam format file Microsoft Excel (.xlsx) atau CSV untuk diserahkan ke atasan/kantor pusat."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Button Export XLSX
                Button(
                    onClick = {
                        if (activities.isEmpty()) {
                            Toast.makeText(context, "Belum ada kegiatan untuk diekspor", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        try {
                            val xlsxFile = ExcelManager.exportToXlsx(context, activities, workerName, "Semua Catatan")
                            val uri = ExcelManager.getFileUri(context, xlsxFile)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Laporan Kegiatan Lapangan - $workerName")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Kirim / Bagikan Laporan Excel (.xlsx)"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal mengekspor Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FieldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("export_xlsx_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ekspor File Excel (.xlsx)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button Export CSV
                OutlinedButton(
                    onClick = {
                        if (activities.isEmpty()) {
                            Toast.makeText(context, "Belum ada kegiatan untuk diekspor", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        try {
                            val csvFile = ExcelManager.exportToCsv(context, activities, workerName)
                            val uri = ExcelManager.getFileUri(context, csvFile)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Laporan Kegiatan Lapangan CSV - $workerName")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Kirim / Bagikan File CSV"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal mengekspor CSV: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("export_csv_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ekspor File CSV (.csv)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    count: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}
