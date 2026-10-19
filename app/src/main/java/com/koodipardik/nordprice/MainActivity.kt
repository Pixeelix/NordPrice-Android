package com.koodipardik.nordprice

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.koodipardik.nordprice.ui.AppProviders
import com.koodipardik.nordprice.ui.NordPriceRoot
import com.koodipardik.nordprice.ui.theme.NordPriceTheme

/**
 * The app has a single activity, like the iOS app has a single window group.
 *
 * `AppCompatActivity` rather than `ComponentActivity` for one reason: it is what makes the
 * in-app language picker work on Android 12 and below, where the per-app locales API is
 * backported by AppCompat and requires its activity base class.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NordPriceApplication).container
        setContent {
            NordPriceTheme {
                AppProviders(container) {
                    NordPriceRoot()
                }
            }
        }
    }
}
