package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ActivityItem
import com.example.data.model.ExcelReportItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ActivityRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createActivity_validPayload_createsDocumentAndReturnsId() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = ActivityRepository(firestore)

    val activityId = "act_" + UUID.randomUUID().toString().replace("-", "")
    val item = ActivityItem(
      id = activityId,
      userId = uid,
      workerName = "Alice Pratama",
      workerId = "FLD-001",
      date = "2026-11-05",
      title = "Pemasangan Router Pelanggan",
      category = "Instalasi",
      location = "Jakarta Selatan",
      startTime = "08:30",
      endTime = "10:45",
      status = "COMPLETED",
      notes = "Router terpasang dan online"
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createActivity(item) }
    assertTrue(result.isSuccess)
    val returnedId = result.getOrThrow()
    assertEquals(activityId, returnedId)
  }

  @Test
  fun observeActivities_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = ActivityRepository(firestore)

    val activityId = "act_" + UUID.randomUUID().toString().replace("-", "")
    val item = ActivityItem(
      id = activityId,
      userId = uid,
      workerName = "Alice Pratama",
      date = "2026-11-05",
      title = "Inspeksi Tiang FO",
      category = "Inspeksi",
      location = "Kuningan",
      status = "IN_PROGRESS"
    )
    val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createActivity(item) }
    assertTrue(createResult.isSuccess)

    val emittedActivities = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeActivities(uid, isAdmin = false).first { list -> list.any { it.id == activityId } }
    }
    assertTrue(emittedActivities.any { it.id == activityId })
  }

  @Test
  fun observeActivities_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = ActivityRepository(firestore)

    // Sign out to simulate unauthenticated caller
    auth.signOut()

    val exception = try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeActivities(uid, isAdmin = false).first()
      }
      null
    } catch (e: Exception) {
      e
    }
    assertNotNull(exception)
    val firestoreException = exception?.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun createExcelReport_validPayload_createsDocumentAndReturnsId() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = ActivityRepository(firestore)

    val reportId = "rep_" + UUID.randomUUID().toString().replace("-", "")
    val report = ExcelReportItem(
      id = reportId,
      userId = uid,
      workerName = "Alice Pratama",
      fileName = "Laporan_Mingguan.xlsx",
      fileType = "XLSX",
      reportDate = "2026-11-05",
      totalRows = 25,
      status = "UPLOADED",
      notes = "Rekap aktivitas mingguan",
      fileSize = 120450,
      rawContentSummary = "Sheet1: 25 kegiatan"
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createExcelReport(report) }
    assertTrue(result.isSuccess)
    val returnedId = result.getOrThrow()
    assertEquals(reportId, returnedId)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_field@test.com"
    const val ADMIN_EMAIL = "zulpatinnazriel@gmail.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
