package koodipardik.nordprice.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import koodipardik.nordprice.R
import koodipardik.nordprice.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

/** Full-screen "no internet" state, the iOS `ConnectionLostView`. */
@Composable
fun ConnectionLostScreen() {
    val context = LocalContext.current
    val colors = AppTheme

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = colors.textOnBackground,
                modifier = Modifier.padding(bottom = 24.dp).size(160.dp),
            )
            Text(
                text = stringResource(R.string.text_connection_lost),
                fontSize = 20.sp,
                color = colors.textOnBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp),
            )
            Box(
                modifier = Modifier
                    .widthIn(min = 160.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.pillBackground)
                    .clickable {
                        // iOS opens its own Settings pane; the closest Android equivalent is this
                        // app's system page, where network and notification toggles live.
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        runCatching { context.startActivity(intent) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.title_open_settings),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.pillText,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}
