package com.dergoogler.mmrl.ui.screens.home

import android.os.Build
import android.system.Os
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.datastore.model.WorkingMode.Companion.isNonRoot
import com.dergoogler.mmrl.datastore.model.WorkingMode.Companion.isRoot
import com.dergoogler.mmrl.ext.ModifierScopeUnit
import com.dergoogler.mmrl.ext.managerVersion
import com.dergoogler.mmrl.ext.navigateSingleTopTo
import com.dergoogler.mmrl.ext.none
import com.dergoogler.mmrl.ext.nullable
import com.dergoogler.mmrl.ext.takeTrue
import com.dergoogler.mmrl.model.online.Changelog
import com.dergoogler.mmrl.network.runRequest
import com.dergoogler.mmrl.platform.file.SuFile.Companion.toFormattedFileSize
import com.dergoogler.mmrl.stub.IMMRLApiManager
import com.dergoogler.mmrl.ui.component.SELinuxStatus
import com.dergoogler.mmrl.ui.component.TopAppBar
import com.dergoogler.mmrl.ui.component.TopAppBarEventIcon
import com.dergoogler.mmrl.ui.component.WorkingModeBottomSheet
import com.dergoogler.mmrl.ui.component.card.Card
import com.dergoogler.mmrl.ui.component.listItem.ListItem
import com.dergoogler.mmrl.ui.component.listItem.ListItemDefaults
import com.dergoogler.mmrl.ui.component.listItem.ListProgressBarItem
import com.dergoogler.mmrl.ui.navigation.MainRoute
import com.dergoogler.mmrl.ui.providable.LocalMainNavController
import com.dergoogler.mmrl.ui.providable.LocalNavController
import com.dergoogler.mmrl.ui.providable.LocalUserPreferences
import com.dergoogler.mmrl.ui.screens.home.items.NonRootItem
import com.dergoogler.mmrl.ui.screens.home.items.RebootBottomSheet
import com.dergoogler.mmrl.ui.screens.home.items.RootItem
import com.dergoogler.mmrl.ui.screens.settings.changelogs.items.ChangelogBottomSheet
import com.dergoogler.mmrl.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

val listItemContentPaddingValues: PaddingValues = PaddingValues(vertical = 8.dp, horizontal = 25.dp)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val navController = LocalMainNavController.current
    val userPreferences = LocalUserPreferences.current
    val browser = LocalUriHandler.current

    var workingModeBottomSheet by remember { mutableStateOf(false) }
    if (workingModeBottomSheet) WorkingModeBottomSheet(
        onClose = {
            workingModeBottomSheet = false
        }
    )

    var openRebootSheet by remember { mutableStateOf(false) }
    if (openRebootSheet) {
        RebootBottomSheet(
            onClose = { openRebootSheet = false })
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopBar(
                isProviderAlive = viewModel.isProviderAlive,
                onInfoClick = {
                    navController.navigateSingleTopTo(MainRoute.About.route)
                },
                onHeartClick = {
                    navController.navigateSingleTopTo(MainRoute.ThankYou.route)
                },
                onRebootClick = {
                    openRebootSheet = true
                },
                scrollBehavior = scrollBehavior
            )
        }, contentWindowInsets = WindowInsets.none
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val compressedCardModifier: ModifierScopeUnit = {
                column = Modifier.padding(vertical = 16.dp)
            }

            val compressedCardModifierRow: ModifierScopeUnit = {
                surface = Modifier.weight(1f)
                column = Modifier.padding(vertical = 16.dp)
            }

            when {
                userPreferences.workingMode.isRoot -> RootItem(
                    developerMode = userPreferences.developerMode,
                    viewModel = viewModel,
                    onClick = {
                        workingModeBottomSheet = true
                    }
                )

                userPreferences.workingMode.isNonRoot -> NonRootItem(
                    developerMode = userPreferences.developerMode,
                    onClick = {
                        workingModeBottomSheet = true
                    }
                )
            }

            if (userPreferences.checkAppUpdates) {
                var changelog by remember { mutableStateOf<List<Changelog>?>(null) }
                LaunchedEffect(Unit) {
                    runRequest {
                        withContext(Dispatchers.IO) {
                            val api = IMMRLApiManager.build()
                            return@withContext api.changelog.execute()
                        }
                    }.onSuccess { list ->
                        changelog = list
                    }.onFailure {
                        Timber.e(it, "unable to get changelog")
                    }

                }

                changelog?.let {
                    val latest = it.first()

                    var changelogSheet by remember { mutableStateOf(false) }
                    if (changelogSheet) {
                        ChangelogBottomSheet(
                            changelog = latest,
                            onClose = { changelogSheet = false })
                    }

                    AnimatedVisibility(
                        visible = if (latest.preRelease) {
                            userPreferences.checkAppUpdatesPreReleases && latest.versionCode > context.managerVersion.second
                        } else {
                            latest.versionCode > context.managerVersion.second
                        },
                        enter = fadeIn() + expandVertically(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        changelogSheet = true
                                    }
                            ) {
                                ListItem(
                                    title = stringResource(
                                        R.string.new_version_available, latest.versionName
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = compressedCardModifier
            ) {
                val uname = Os.uname()

                ListItem(
                    contentPaddingValues = listItemContentPaddingValues,
                    icon = R.drawable.cookie_man,
                    title = stringResource(R.string.kernel),
                    desc = uname.release
                )

                ListItem(
                    contentPaddingValues = listItemContentPaddingValues,
                    icon = R.drawable.launcher_outline,
                    title = stringResource(R.string.manager_version),
                    desc = "${context.managerVersion.first} (${context.managerVersion.second})"
                )

                ListItem(
                    contentPaddingValues = listItemContentPaddingValues,
                    icon = R.drawable.fingerprint,
                    title = stringResource(R.string.fingerprint),
                    desc = if (userPreferences.hideFingerprintInHome) {
                        stringResource(id = R.string.hidden)
                    } else {
                        Build.FINGERPRINT
                    }
                )

                ListItem(
                    contentPaddingValues = listItemContentPaddingValues,
                    icon = R.drawable.cpu_2,
                    title = stringResource(R.string.architecture),
                    desc = Os.uname().machine
                )

                SELinuxStatus(
                    contentPaddingValues = listItemContentPaddingValues
                )

                if (viewModel.platform.isKernelSuOrNext) {
                    ListItem(
                        contentPaddingValues = listItemContentPaddingValues,
                        icon = R.drawable.user_outlined,
                        title = "SuperUser Apps",
                        desc = viewModel.superUserCount.toString()
                    )
                }
            }

            viewModel.isProviderAlive.takeTrue {
                userPreferences.developerMode.takeTrue {
                    Card(
                        modifier = compressedCardModifier
                    ) {
                        ListItem(
                            contentPaddingValues = listItemContentPaddingValues,
                            title = stringResource(R.string.home_root_provider_version_name),
                            desc = viewModel.versionName
                        )

                        ListItem(
                            contentPaddingValues = listItemContentPaddingValues,
                            title = stringResource(R.string.home_root_provider_se_linux_context),
                            desc = viewModel.seLinuxContext
                        )
                    }
                }

                viewModel.analytics(context).nullable {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = compressedCardModifierRow
                        ) {
                            ListItem(
                                contentPaddingValues = listItemContentPaddingValues,
                                desc = stringResource(R.string.home_installed_modules),
                                title = it.totalModules.toString()
                            )
                        }

                        Card(
                            modifier = compressedCardModifierRow
                        ) {
                            ListItem(
                                contentPaddingValues = listItemContentPaddingValues,
                                desc = stringResource(R.string.home_updated_modules),
                                title = it.totalUpdated.toString()
                            )
                        }
                    }

                    Card(
                        modifier = compressedCardModifier
                    ) {
                        ListProgressBarItem(
                            contentPaddingValues = listItemContentPaddingValues,
                            progressBarModifier = Modifier
                                .weight(1f),
                            startDesc = it.totalModulesUsageBytes.toFormattedFileSize(),
                            endDesc = it.totalDeviceStorageBytes.toFormattedFileSize(),
                            title = stringResource(id = R.string.home_storage_usage),
                            progress = it.totalStorageUsage,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = compressedCardModifierRow
                        ) {
                            ListItem(
                                contentPaddingValues = listItemContentPaddingValues,
                                desc = stringResource(R.string.home_enabled_modules),
                                title = it.totalEnabled.toString()
                            )

                        }

                        Card(
                            modifier = compressedCardModifierRow
                        ) {
                            ListItem(
                                contentPaddingValues = listItemContentPaddingValues,
                                desc = stringResource(R.string.home_disabled_modules),
                                title = it.totalDisabled.toString()
                            )
                        }
                    }
                }
            }

            Card(
                modifier = compressedCardModifier,
                onClick = {
                    browser.openUri("https://github.com/sponsors/DerGoogler")
                }
            ) {
                ListItem(
                    contentPaddingValues = listItemContentPaddingValues,
                    itemTextStyle = ListItemDefaults.itemStyle.copy(
                        titleTextColor = Color.Unspecified,
                        descTextColor = Color.Unspecified,
                        titleTextStyle = MaterialTheme.typography.bodyLarge,
                        descTextStyle = MaterialTheme.typography.bodyMedium
                    ),
                    title = stringResource(R.string.home_support_title),
                    desc = stringResource(R.string.home_support_content)
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    isProviderAlive: Boolean,
    onRebootClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onHeartClick: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            TopAppBarEventIcon()
        },
        scrollBehavior = scrollBehavior,
        actions = {
            if (isProviderAlive) {
                IconButton(onClick = onRebootClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.refresh),
                        contentDescription = null
                    )
                }
            }

            IconButton(onClick = onHeartClick) {
                Icon(
                    painter = painterResource(id = R.drawable.heart),
                    contentDescription = null
                )
            }

            IconButton(onClick = onInfoClick) {
                Icon(
                    painter = painterResource(id = R.drawable.info_circle),
                    contentDescription = null
                )
            }
        }
    )
}