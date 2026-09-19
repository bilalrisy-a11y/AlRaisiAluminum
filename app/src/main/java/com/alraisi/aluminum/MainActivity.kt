package com.alraisi.aluminum

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alraisi.aluminum.data.ServiceLocator
import com.alraisi.aluminum.ui.screens.*
import com.alraisi.aluminum.ui.theme.AlRaisiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        lifecycleScope.launch { ServiceLocator.repo.seedIfNeeded() }
        setContent {
            AlRaisiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val nav = rememberNavController()
                        NavHost(navController = nav, startDestination = "home") {
                            composable("home") { HomeScreen(nav) }
                            composable("newDesign") { NewDesignScreen(nav) }
                            composable("designer/{designId}") { back ->
                                DesignerScreen(nav, back.arguments?.getString("designId").orEmpty())
                            }
                            composable("designs") { DesignsScreen(nav) }
                            composable("customers") { CustomersScreen(nav) }
                            composable("projects") { ProjectsScreen(nav) }
                            composable("profiles") { ProfilesScreen(nav) }
                            composable("prices") { PricesScreen(nav) }
                            composable("costs") { CostsScreen(nav) }
                            composable("settings") { SettingsScreen(nav) }
                            composable("report/{designId}") { back ->
                                ReportScreen(nav, back.arguments?.getString("designId").orEmpty())
                            }
                        }
                    }
                }
            }
        }
    }
}
