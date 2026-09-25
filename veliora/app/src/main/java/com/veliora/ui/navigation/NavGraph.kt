package com.veliora.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.veliora.ui.components.FloatingNavBar
import com.veliora.ui.components.FloatingNavItem
import com.veliora.ui.screens.comms.CommsScreen
import com.veliora.ui.screens.constitution.ConstitutionScreen
import com.veliora.ui.screens.createorg.CreateOrgScreen
import com.veliora.ui.screens.department.DepartmentDetailScreen
import com.veliora.ui.screens.evolution.EvolutionScreen
import com.veliora.ui.screens.evolution.OrgChartDiffScreen
import com.veliora.ui.screens.home.HomeScreen
import com.veliora.ui.screens.investigation.InvestigationScreen
import com.veliora.ui.screens.investigations.InvestigationsScreen
import com.veliora.ui.screens.orgselector.OrgSelectorScreen
import com.veliora.ui.screens.instance.InstanceScreen
import com.veliora.ui.screens.report.ReportScreen
import com.veliora.ui.screens.worker.WorkerDetailScreen
import kotlinx.coroutines.launch

// ── Routes ────────────────────────────────────────────────────────────

object Routes {
    const val ORG_SELECTOR = "org-selector"
    const val HOME = "home/{orgId}"
    const val INVESTIGATIONS = "investigations/{orgId}"
    const val DEPARTMENT = "department/{orgId}/{deptId}"
    const val WORKER = "worker/{orgId}/{workerId}"
    const val INVESTIGATION = "investigation/{orgId}/{investigationId}"
    const val REPORT = "report/{orgId}/{investigationId}"
    const val COMMS = "comms/{orgId}"
    const val EVOLUTION = "evolution/{orgId}"
    const val CONSTITUTION = "constitution/{orgId}"
    const val INSTANCE = "instance"
    const val CREATE_ORG = "create-org"
    const val ORG_CHART_DIFF = "org-chart-diff/{orgId}/{eventId}"

    fun home(orgId: String) = "home/$orgId"
    fun investigations(orgId: String) = "investigations/$orgId"
    fun department(orgId: String, deptId: String) = "department/$orgId/$deptId"
    fun worker(orgId: String, workerId: String) = "worker/$orgId/$workerId"
    fun investigation(orgId: String, invId: String) = "investigation/$orgId/$invId"
    fun report(orgId: String, invId: String) = "report/$orgId/$invId"
    fun comms(orgId: String) = "comms/$orgId"
    fun evolution(orgId: String) = "evolution/$orgId"
    fun constitution(orgId: String) = "constitution/$orgId"
    fun orgChartDiff(orgId: String, eventId: String) = "org-chart-diff/$orgId/$eventId"
}

// ── Bottom Nav Items ─────────────────────────────────────────────────

private val mainNavItems = listOf(
    FloatingNavItem("Home", Icons.Filled.Home, Routes.HOME),
    FloatingNavItem("Investigations", Icons.Filled.Insights, Routes.INVESTIGATIONS),
    FloatingNavItem("Comms", Icons.Filled.Send, Routes.COMMS),
)

// ── Nav Host ──────────────────────────────────────────────────────────

@Composable
fun VelioraNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.ORG_SELECTOR,
    ) {
        // Organization selector
        composable(Routes.ORG_SELECTOR) {
            OrgSelectorScreen(
                onOrgSelected = { orgId ->
                    navController.navigate(Routes.home(orgId)) {
                        popUpTo(Routes.ORG_SELECTOR)
                    }
                },
                onCreateOrg = {
                    navController.navigate(Routes.CREATE_ORG)
                },
                onInstanceClick = {
                    navController.navigate(Routes.INSTANCE)
                },
            )
        }

        // Local instance information
        composable(Routes.INSTANCE) {
            InstanceScreen(
                onOpenOrg = { orgId ->
                    navController.navigate(Routes.home(orgId)) {
                        popUpTo(Routes.ORG_SELECTOR)
                    }
                },
            )
        }

        // Create Organization
        composable(Routes.CREATE_ORG) {
            CreateOrgScreen(
                onLaunch = { orgId ->
                    navController.navigate(Routes.home(orgId)) {
                        popUpTo(Routes.ORG_SELECTOR)
                    }
                },
            )
        }

        // ── Home / Dashboard ───────────────────────────────────────────
        composable(
            route = Routes.HOME,
            arguments = listOf(navArgument("orgId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            MainScaffoldHost(
                navController = navController,
                orgId = orgId,
            ) { padding ->
                HomeScreen(
                    orgId = orgId,
                    onDepartmentClick = { deptId ->
                        navController.navigate(Routes.department(orgId, deptId))
                    },
                    onInvestigationClick = { invId ->
                        navController.navigate(Routes.investigation(orgId, invId))
                    },
                    onCommsClick = { navController.navigate(Routes.comms(orgId)) },
                    onEvolutionClick = { navController.navigate(Routes.evolution(orgId)) },
                    onConstitutionClick = { navController.navigate(Routes.constitution(orgId)) },
                    onOrgSwitch = { newOrgId ->
                        navController.navigate(Routes.home(newOrgId)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    contentPadding = padding,
                )
            }
        }

        // ── Investigations (top-level) ─────────────────────────────────
        composable(
            route = Routes.INVESTIGATIONS,
            arguments = listOf(navArgument("orgId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            MainScaffoldHost(
                navController = navController,
                orgId = orgId,
            ) { padding ->
                InvestigationsScreen(
                    orgId = orgId,
                    onInvestigationClick = { invId ->
                        navController.navigate(Routes.investigation(orgId, invId))
                    },
                    onNewInvestigationCreated = { invId ->
                        navController.navigate(Routes.investigation(orgId, invId))
                    },
                    contentPadding = padding,
                )
            }
        }

        // ── Comms (top-level) ──────────────────────────────────────────
        composable(
            route = Routes.COMMS,
            arguments = listOf(navArgument("orgId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            MainScaffoldHost(
                navController = navController,
                orgId = orgId,
            ) { padding ->
                CommsScreen(
                    orgId = orgId,
                    contentPadding = padding,
                )
            }
        }

        // Department Detail
        composable(
            route = Routes.DEPARTMENT,
            arguments = listOf(
                navArgument("orgId") { type = NavType.StringType },
                navArgument("deptId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            val deptId = backStackEntry.arguments?.getString("deptId") ?: ""
            DepartmentDetailScreen(
                orgId = orgId,
                deptId = deptId,
                onWorkerClick = { workerId ->
                    navController.navigate(Routes.worker(orgId, workerId))
                },
            )
        }

        // Worker Detail
        composable(
            route = Routes.WORKER,
            arguments = listOf(
                navArgument("orgId") { type = NavType.StringType },
                navArgument("workerId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            val workerId = backStackEntry.arguments?.getString("workerId") ?: ""
            WorkerDetailScreen(
                orgId = orgId,
                workerId = workerId,
            )
        }

        // Investigation View
        composable(
            route = Routes.INVESTIGATION,
            arguments = listOf(
                navArgument("orgId") { type = NavType.StringType },
                navArgument("investigationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            val invId = backStackEntry.arguments?.getString("investigationId") ?: ""
            InvestigationScreen(
                orgId = orgId,
                investigationId = invId,
                onReportClick = {
                    navController.navigate(Routes.report(orgId, invId))
                },
            )
        }

        // Report View
        composable(
            route = Routes.REPORT,
            arguments = listOf(
                navArgument("orgId") { type = NavType.StringType },
                navArgument("investigationId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            val invId = backStackEntry.arguments?.getString("investigationId") ?: ""
            val scope = rememberCoroutineScope()
            val app = (LocalContext.current.applicationContext as com.veliora.VelioraApp)
            ReportScreen(
                orgId = orgId,
                investigationId = invId,
                onNewInvestigation = { goal ->
                    scope.launch {
                        val invId2 = app.orgStore.createInvestigation(goal)
                        if (invId2 != null) {
                            navController.navigate(Routes.investigation(orgId, invId2))
                        }
                    }
                },
            )
        }

        // Evolution
        composable(
            route = Routes.EVOLUTION,
            arguments = listOf(navArgument("orgId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            EvolutionScreen(
                orgId = orgId,
                onEventClick = { eventId ->
                    navController.navigate(Routes.orgChartDiff(orgId, eventId))
                },
            )
        }

        // Constitution
        composable(
            route = Routes.CONSTITUTION,
            arguments = listOf(navArgument("orgId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            ConstitutionScreen(
                orgId = orgId,
            )
        }

        // Org chart diff (evolution sub-view)
        composable(
            route = Routes.ORG_CHART_DIFF,
            arguments = listOf(
                navArgument("orgId") { type = NavType.StringType },
                navArgument("eventId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString("orgId") ?: "org1"
            val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
            OrgChartDiffScreen(
                orgId = orgId,
                eventId = eventId,
            )
        }
    }
}

/**
 * Hosts the floating pill nav bar for the three top-level destinations.
 * Extracts [LocalContext] for reachability — kept minimal.
 */
@Composable
private fun MainScaffoldHost(
    navController: NavHostController,
    orgId: String,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.HOME
    val normalized = when {
        currentRoute.startsWith("home/") -> Routes.HOME
        currentRoute.startsWith("investigations/") -> Routes.INVESTIGATIONS
        currentRoute.startsWith("comms/") -> Routes.COMMS
        else -> currentRoute
    }

    // Content fills the screen edge-to-edge; the floating nav bar overlays
    // it at the bottom. Content scrolls behind the pill — the pill's own
    // surface is the only thing that covers what passes underneath.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content(PaddingValues())
        FloatingNavBar(
            items = mainNavItems,
            currentRoute = normalized,
            onNavigate = { route ->
                val target = when (route) {
                    Routes.HOME -> Routes.home(orgId)
                    Routes.INVESTIGATIONS -> Routes.investigations(orgId)
                    Routes.COMMS -> Routes.comms(orgId)
                    else -> route
                }
                navController.navigate(target) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
