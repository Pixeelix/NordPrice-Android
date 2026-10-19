package com.koodipardik.nordprice.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koodipardik.nordprice.R
import com.koodipardik.nordprice.core.Region
import com.koodipardik.nordprice.ui.theme.AppTheme

/**
 * Region chooser. iOS presents this as a confirmation dialog anchored to the flag; the Material
 * equivalent is a bottom sheet, which is also easier to hit with a thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPickerSheet(onDismiss: () -> Unit, onSelect: (Region) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.card,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.title_region),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.cardText,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider(color = AppTheme.cardStroke)
            Region.entries.forEach { region ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(region) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Image(
                        painter = painterResource(region.flagRes),
                        contentDescription = null,
                        modifier = Modifier.size(width = 32.dp, height = 22.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = stringResource(region.nameRes),
                        fontSize = 17.sp,
                        color = AppTheme.cardText,
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.button_cancel),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
