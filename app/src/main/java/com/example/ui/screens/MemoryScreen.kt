package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.model.MemoryCategory
import com.example.core.model.MemoryItem
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
fun MemoryScreen(viewModel: JarvisViewModel) {
    val memories by viewModel.memories.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredMemories = memories.filter { item ->
        val matchesSearch = searchQuery.isBlank() ||
                item.key.contains(searchQuery, ignoreCase = true) ||
                item.value.contains(searchQuery, ignoreCase = true)
        val matchesCat = selectedCategory == null || item.category == selectedCategory
        matchesSearch && matchesCat
    }

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
                        text = "EPISODIC MEMORY MATRIX",
                        color = JarvisCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "ON-DEVICE PERSISTENCE & SEMANTIC RETRIEVAL",
                        color = JarvisTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = { viewModel.clearAllMemories() },
                    modifier = Modifier.testTag("memory_clear_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear All Memories",
                        tint = JarvisTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter memory matrix...", color = JarvisTextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = JarvisTextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisSurfaceBorder,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("memory_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isSelected = selectedCategory == null
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) JarvisCyan else JarvisSurface,
                        border = BorderStroke(1.dp, if (isSelected) JarvisCyan else JarvisSurfaceBorder),
                        modifier = Modifier.clickable { selectedCategory = null }
                    ) {
                        Text(
                            text = "ALL (${memories.size})",
                            color = if (isSelected) Color(0xFF030712) else JarvisTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                items(MemoryCategory.values()) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) JarvisCyan else JarvisSurface,
                        border = BorderStroke(1.dp, if (isSelected) JarvisCyan else JarvisSurfaceBorder),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.name.replace("_", " "),
                            color = if (isSelected) Color(0xFF030712) else JarvisTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Memory List
            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching memory records found in vault.",
                        color = JarvisTextMuted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMemories, key = { it.id }) { item ->
                        MemoryItemCard(
                            item = item,
                            onDelete = { viewModel.deleteMemory(item.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // FAB to add memory
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = JarvisCyan,
            contentColor = Color(0xFF030712),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_memory_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Memory")
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { key, value, cat, importance ->
                viewModel.saveMemory(key, value, cat, importance)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MemoryItemCard(
    item: MemoryItem,
    onDelete: () -> Unit
) {
    val categoryColor = when (item.category) {
        MemoryCategory.IDENTITY -> JarvisCyan
        MemoryCategory.USER_PREFERENCE -> JarvisElectricTeal
        MemoryCategory.FACT -> JarvisAmber
        MemoryCategory.PROJECT -> JarvisBlue
        MemoryCategory.TASK -> JarvisSuccessGreen
        MemoryCategory.GENERAL -> JarvisTextSecondary
    }

    GlowingCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = categoryColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, categoryColor)
                ) {
                    Text(
                        text = item.category.name,
                        color = categoryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Importance stars
                    Row {
                        for (i in 1..item.importance) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = JarvisAmber,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Memory",
                            tint = JarvisTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.key,
                color = JarvisCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.value,
                color = JarvisTextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Source: ${item.source}",
                color = JarvisTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, MemoryCategory, Int) -> Unit
) {
    var keyText by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
    var selectedCat by remember { mutableStateOf(MemoryCategory.USER_PREFERENCE) }
    var importance by remember { mutableStateOf(3f) }

    Dialog(onDismissRequest = onDismiss) {
        GlowingCard(borderColor = JarvisCyan, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .background(JarvisSurfaceElevated)
                    .padding(20.dp)
            ) {
                Text(
                    text = "STORE NEW MEMORY VECTOR",
                    color = JarvisCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Memory Key / Descriptor") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("memory_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Memory Content / Fact") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisSurfaceBorder,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .testTag("memory_value_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Importance Weight: ${importance.toInt()} / 5",
                    color = JarvisTextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Slider(
                    value = importance,
                    onValueChange = { importance = it },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = JarvisCyan,
                        activeTrackColor = JarvisCyan,
                        inactiveTrackColor = JarvisSurfaceBorder
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                        onClick = {
                            if (keyText.isNotBlank() && valueText.isNotBlank()) {
                                onSave(keyText, valueText, selectedCat, importance.toInt())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = Color(0xFF030712)
                        ),
                        modifier = Modifier.testTag("memory_save_button")
                    ) {
                        Text("Save to Vault", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
