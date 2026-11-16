package com.example.wakeonwanremotephone

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wakeonwanremotephone.ui.theme.*

/**
 * "Shared secrets" setup dialog.
 *
 * The helper secret is shared with the Home Phone; the PC secret is the
 * `pc_onoff --status` Command Secret used for direct UDP 9877 power commands.
 */
@Composable
fun SecuritySettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val securitySettings = remember { SecuritySettings(context) }

    var helperSecret by remember { mutableStateOf(securitySettings.getHelperSecret()) }
    var pcSecret by remember { mutableStateOf(securitySettings.getPcSecret()) }
    var helperVisible by remember { mutableStateOf(false) }
    var pcVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderStandard, RoundedCornerShape(16.dp)),
            color = DarkSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(AccentEmeraldDim, RoundedCornerShape(10.dp))
                            .border(1.dp, AccentEmeraldBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AccentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.security_title),
                        color = Slate50,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.security_description),
                    color = Slate300,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                SecretField(
                    label = stringResource(R.string.helper_secret_label),
                    value = helperSecret,
                    onValueChange = { helperSecret = it },
                    visible = helperVisible,
                    onToggleVisibility = { helperVisible = !helperVisible }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallActionButton(
                        text = stringResource(R.string.generate_secret),
                        onClick = {
                            helperSecret = securitySettings.generateHelperSecret()
                            helperVisible = true
                            Toast.makeText(
                                context,
                                context.getString(R.string.secret_generated),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    SmallActionButton(
                        text = stringResource(R.string.copy_secret),
                        onClick = {
                            if (helperSecret.isBlank()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.secret_empty),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                clipboardManager.setText(AnnotatedString(helperSecret))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.secret_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SecretField(
                    label = stringResource(R.string.pc_secret_label),
                    value = pcSecret,
                    onValueChange = { pcSecret = it },
                    visible = pcVisible,
                    onToggleVisibility = { pcVisible = !pcVisible }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SmallActionButton(
                    text = stringResource(R.string.paste_secret),
                    onClick = {
                        val pasted = clipboardManager.getText()?.text?.trim().orEmpty()
                        if (pasted.isBlank()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.clipboard_empty),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            pcSecret = pasted
                            pcVisible = true
                            Toast.makeText(
                                context,
                                context.getString(R.string.secret_pasted),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = Slate400, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            securitySettings.setHelperSecret(helperSecret)
                            securitySettings.setPcSecret(pcSecret)
                            Toast.makeText(
                                context,
                                context.getString(R.string.secrets_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Slate400,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.hide_secret else R.string.show_secret
                        ),
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentEmerald,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = Slate50,
                unfocusedTextColor = Slate50,
                cursorColor = AccentEmerald,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SmallActionButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, BorderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate200),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
