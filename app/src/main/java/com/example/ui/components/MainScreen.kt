package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.model.ActivityItem
import com.example.ui.theme.FieldPrimary
import com.example.ui.theme.StatusCompleted
import com.example.ui.viewmodel.ActivityViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ActivityViewModel,
    onSignOutRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val userMessage by viewModel.userMessage.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var showFormDialog by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<ActivityItem?>(null) }
    var selectedDetailActivity by remember { mutableStateOf<ActivityItem?>(null) }

    val currentUser = Firebase.auth.currentUser
    val workerDisplayName = currentUser?.displayName ?: currentUser?.email?.substringBefore("@") ?: "Petugas Lapangan"

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kegiatan Lapangan",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = workerDisplayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                )
                                if (viewModel.isAdmin) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                        color = Color(0xFFFFD700)
                                    ) {
                                        Text(
                                            text = "👑 ADMIN",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF78350F),
                                                fontSize = 9.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Cloud Connected",
                        tint = StatusCompleted,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(22.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FieldPrimary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Assignment, contentDescription = "Kegiatan") },
                    label = { Text("Kegiatan") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FieldPrimary,
                        selectedTextColor = FieldPrimary,
                        indicatorColor = FieldPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("tab_activities")
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.TableChart, contentDescription = "Laporan Excel") },
                    label = { Text("Laporan Excel") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FieldPrimary,
                        selectedTextColor = FieldPrimary,
                        indicatorColor = FieldPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("tab_excel")
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "Ringkasan") },
                    label = { Text("Ringkasan") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FieldPrimary,
                        selectedTextColor = FieldPrimary,
                        indicatorColor = FieldPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("tab_summary")
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> ActivityListTab(
                    viewModel = viewModel,
                    workerName = workerDisplayName,
                    onAddActivityClick = {
                        editingActivity = null
                        showFormDialog = true
                    },
                    onEditActivity = { act ->
                        editingActivity = act
                        showFormDialog = true
                    },
                    onViewDetail = { act ->
                        selectedDetailActivity = act
                    }
                )
                1 -> ExcelReportTab(
                    viewModel = viewModel,
                    workerName = workerDisplayName
                )
                2 -> SummaryAnalyticsTab(
                    viewModel = viewModel,
                    workerName = workerDisplayName,
                    onSignOut = {
                        signOutUser(
                            context = context,
                            credentialManager = credentialManager,
                            onSignOutComplete = onSignOutRequested,
                            scope = scope
                        )
                    }
                )
            }
        }
    }

    // Add / Edit Activity Dialog
    if (showFormDialog) {
        ActivityFormDialog(
            initialActivity = editingActivity,
            currentUserId = viewModel.currentUserId,
            currentWorkerName = workerDisplayName,
            onDismiss = {
                showFormDialog = false
                editingActivity = null
            },
            onSave = { item ->
                showFormDialog = false
                if (editingActivity == null) {
                    viewModel.addActivity(item)
                } else {
                    viewModel.updateActivity(item)
                }
                editingActivity = null
            }
        )
    }

    // Detail Activity Dialog
    selectedDetailActivity?.let { activity ->
        ActivityDetailDialog(
            activity = activity,
            onDismiss = { selectedDetailActivity = null },
            onEdit = {
                selectedDetailActivity = null
                editingActivity = activity
                showFormDialog = true
            },
            onDelete = {
                selectedDetailActivity = null
                viewModel.deleteActivity(activity.id)
            },
            onToggleStatus = {
                selectedDetailActivity = null
                viewModel.toggleQuickStatus(activity)
            }
        )
    }
}
