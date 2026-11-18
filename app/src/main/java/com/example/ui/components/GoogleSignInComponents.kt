package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.GoogleFitRestService

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    label: String = "Sign in with Google"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White.copy(alpha = 0.55f),
            disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("connect_google_fit_button")
    ) {
        GoogleLogoGlyph(modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GoogleLogoGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.21f
        val inset = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -35f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 220f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        val midY = size.height / 2f
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.52f, midY),
            end = Offset(size.width - inset * 0.4f, midY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt
        )
    }
}

/**
 * In-app Google Sign-In sheet powered by Chrome Custom Tabs (RFC 8252 native-app pattern).
 * Avoids embedding android.webkit.WebView inside the Compose window, preventing GPU/rendernode
 * Chromium subprocess crashes on virtualized devices while preserving Google's OAuth security model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleSignInWebSheet(
    authUri: Uri,
    onWebSignInCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val toolbarColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()

    val isValidUri = remember(authUri) {
        authUri.scheme == "https" && authUri.host == "accounts.google.com" && isAllowedGoogleAuthUrl(authUri)
    }
    val isOAuthRequest = remember(authUri) {
        authUri.path?.contains("/o/oauth2/") == true &&
            authUri.getQueryParameter("redirect_uri") == GoogleFitRestService.REDIRECT_URI
    }

    var launchedCustomTab by remember(authUri) { mutableStateOf(false) }
    var launchFailed by remember(authUri) { mutableStateOf(false) }

    LaunchedEffect(authUri) {
        if (isValidUri && !launchedCustomTab) {
            launchedCustomTab = true
            val opened = launchGoogleCustomTabOrBrowser(context, authUri, toolbarColor)
            launchFailed = !opened
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("google_signin_web_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_google_signin_sheet")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Google Sign-In"
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Verified HTTPS connection",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "https://${authUri.host ?: "accounts.google.com"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isValidUri) {
                                val opened = launchGoogleCustomTabOrBrowser(context, authUri, toolbarColor)
                                launchFailed = !opened
                            }
                        },
                        modifier = Modifier.testTag("open_google_signin_external")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Reopen Google Sign-In tab"
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GoogleLogoGlyph(modifier = Modifier.size(40.dp))

                if (!isValidUri) {
                    Text(
                        text = "Invalid Google Sign-In URL.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else if (launchFailed) {
                    Text(
                        text = "No Browser Found for Google Sign-In",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Install or enable Chrome or a web browser on this device to open Google's sign-in page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = if (isOAuthRequest) {
                            "Waiting for Google Authorization"
                        } else {
                            "Google Sign-In Opened in Browser Tab"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isOAuthRequest) {
                            "Sign in and approve Google Fit access in the Custom Tab. You will be returned to Circadia automatically once authorized."
                        } else {
                            "Sign in on Google's secure login page in the Custom Tab, then return here and confirm below to mark your Google account connected."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    if (!isOAuthRequest) {
                        Button(
                            onClick = onWebSignInCompleted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("confirm_google_signin_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("I Finished Signing In")
                        }
                    }

                    GoogleSignInButton(
                        onClick = {
                            val opened = launchGoogleCustomTabOrBrowser(context, authUri, toolbarColor)
                            launchFailed = !opened
                        },
                        label = "Open Google Sign-In Page"
                    )

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("cancel_google_signin_button")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

private fun launchGoogleCustomTabOrBrowser(
    context: Context,
    authUri: Uri,
    toolbarColorArgb: Int
): Boolean {
    if (!isAllowedGoogleAuthUrl(authUri) || authUri.host != "accounts.google.com") return false
    val customTabResult = runCatching {
        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColorArgb)
            .build()
        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorScheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build()
        customTabsIntent.launchUrl(context, authUri)
    }
    if (customTabResult.isSuccess) return true

    val fallbackIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    return runCatching {
        context.startActivity(fallbackIntent)
        true
    }.getOrDefault(false)
}

internal fun isAllowedGoogleAuthUrl(uri: Uri): Boolean {
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase() ?: return false
    return host == "accounts.google.com" ||
        host == "myaccount.google.com" ||
        host == "accounts.youtube.com" ||
        host.endsWith(".google.com") ||
        host.endsWith(".gstatic.com") ||
        host.endsWith(".googleapis.com") ||
        host.endsWith(".googleusercontent.com")
}
