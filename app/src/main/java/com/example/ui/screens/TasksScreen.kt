package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.AgentTask
import com.example.core.model.SubTask
import com.example.core.model.TaskStatus
import com.example.ui.components.GlowingCard
import com.example.ui.theme.JarvisAlertRed
import com.example.ui.theme.JarvisAmber
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricTeal
import com.example.ui.theme.JarvisGlass
import com.example.ui.theme.JarvisSuccessGreen
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceBorder
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.viewmodel.JarvisViewModel

@Composable
fun TasksScreen(viewModel: JarvisViewModel) {
    val tasks by viewModel.agentTasks.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUTONOMOUS AGENT ORCHESTRATOR",
                        color = JarvisCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "PLANNER -> TOOL SELECTION -> EXECUTION -> VERIFICATION",
                        color = JarvisTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JarvisSurfaceElevated,
                    border = BorderStroke(1.dp, JarvisGlass)
                ) {
                    Text(
                        text = "${tasks.size} TASKS",
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = JarvisTextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Active Agent Tasks",
                            color = JarvisTextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button to dispatch an autonomous goal to JARVIS.",
                            color = JarvisTextMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onCancel = { viewModel.cancelAgentTask(task.id) },
                            onDelete = { viewModel.deleteAgentTask(task.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // FAB to create new task
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = JarvisCyan,
            contentColor = Color(0xFF030712),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_agent_task_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Task")
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            onDismiss = { showCreateDialog = false },
            onSubmit = { goal ->
                viewModel.createAgentTask(goal)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun TaskItemCard(
    task: AgentTask,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(task.status == TaskStatus.RUNNING) }

    val statusColor = when (task.status) {
        TaskStatus.PENDING -> JarvisTextMuted
        TaskStatus.PLANNING -> JarvisAmber
        TaskStatus.RUNNING -> JarvisBlue
        TaskStatus.COMPLETED -> JarvisSuccessGreen
        TaskStatus.FAILED -> JarvisAlertRed
        TaskStatus.CANCELLED -> JarvisTextMuted
    }

    GlowingCard(
        borderColor = if (task.status == TaskStatus.RUNNING) JarvisCyan else JarvisSurfaceBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = task.title,
                        color = JarvisTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = task.status.name,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Objective: ${task.goal}",
                color = JarvisTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { task.progress },
                color = statusColor,
                trackColor = JarvisSurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtasks list toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    border = BorderStroke(1.dp, JarvisSurfaceBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan)
                ) {
                    Text(
                        text = if (isExpanded) "Hide Subtasks (${task.subtasks.size})" else "Inspect Subtasks (${task.subtasks.size})",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row {
                    if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PLANNING) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Stop, contentDescription = "Cancel Task", tint = JarvisAlertRed)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = JarvisTextMuted)
                    }
                }
            }

            // Expanded Subtask breakdown
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    task.subtasks.forEachIndexed { index, subtask ->
                        SubtaskItemRow(index = index + 1, subtask = subtask)
                    }

                    if (task.finalOutput != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = JarvisSurfaceElevated,
                            border = BorderStroke(1.dp, JarvisGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "SYNTHESIS DIGEST",
                                    color = JarvisCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = task.finalOutput,
                                    color = JarvisTextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubtaskItemRow(index: Int, subtask: SubTask) {
    val icon = when (subtask.status) {
        TaskStatus.COMPLETED -> Icons.Default.CheckCircle to JarvisSuccessGreen
        TaskStatus.RUNNING -> Icons.Default.PlayArrow to JarvisBlue
        TaskStatus.FAILED -> Icons.Default.Error to JarvisAlertRed
        else -> Icons.Default.HourglassEmpty to JarvisTextMuted
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = JarvisSurfaceElevated,
        border = BorderStroke(0.5.dp, JarvisSurfaceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon.first,
                contentDescription = null,
                tint = icon.second,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$index. ${subtask.title}",
                    color = JarvisTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtask.toolRequired != null) {
                    Text(
                        text = "Tool: [${subtask.toolRequired.uppercase()}]",
                        color = JarvisCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (subtask.output != null) {
                    Text(
                        text = subtask.output.take(120),
                        color = JarvisTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var goalText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        GlowingCard(borderColor = JarvisCyan, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .background(JarvisSurfaceElevated)
                    .padding(20.dp)
            ) {
                Text(
                    text = "DISPATCH AUTONOMOUS TASK",
                    color = JarvisCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter a multi-step objective for JARVIS to plan, tool-select, and execute.",
                    color = JarvisTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    placeholder = { Text("e.g. Research quantum LLM inference and draft summary memo") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("create_task_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, JarvisSurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { if (goalText.isNotBlank()) onSubmit(goalText) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = Color(0xFF030712)
                        ),
                        modifier = Modifier.testTag("submit_task_button")
                    ) {
                        Text("Initiate Plan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
