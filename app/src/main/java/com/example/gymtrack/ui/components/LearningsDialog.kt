package com.example.gymtrack.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningsDialog(
    visible: Boolean,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxSize()
                .clickable { onDismiss() },
            contentAlignment = Alignment.TopCenter
        ) {
            BoxWithConstraints {
                val offset = maxHeight / 3
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(2.dp, Color.LightGray.copy(alpha = 0.2F)),
                    modifier = Modifier
                        .padding(
                            top = offset,
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp
                        )
                        .imePadding()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "Notes",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = onValueChange,
                                placeholder = { Text("Learnings") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = gymTrackOutlinedTextFieldColors(
                                    borderColor = Color.Transparent
                                )
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
