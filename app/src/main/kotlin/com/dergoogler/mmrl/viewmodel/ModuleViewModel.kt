package com.dergoogler.mmrl.viewmodel

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dergoogler.mmrl.R
import com.dergoogler.mmrl.database.entity.Repo
import com.dergoogler.mmrl.database.entity.Repo.Companion.toRepo
import com.dergoogler.mmrl.datastore.UserPreferencesRepository
import com.dergoogler.mmrl.ext.panicString
import com.dergoogler.mmrl.model.json.UpdateJson
import com.dergoogler.mmrl.model.local.LocalModule
import com.dergoogler.mmrl.model.local.State
import com.dergoogler.mmrl.model.online.OnlineModule
import com.dergoogler.mmrl.model.online.OtherSources
import com.dergoogler.mmrl.model.online.TrackJson
import com.dergoogler.mmrl.model.online.VersionItem
import com.dergoogler.mmrl.model.state.OnlineState.Companion.createState
import com.dergoogler.mmrl.platform.PlatformManager
import com.dergoogler.mmrl.platform.model.ModId
import com.dergoogler.mmrl.platform.stub.IModuleOpsCallback
import com.dergoogler.mmrl.repository.LocalRepository
import com.dergoogler.mmrl.repository.ModulesRepository
import com.dergoogler.mmrl.service.DownloadService
import com.dergoogler.mmrl.utils.Utils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@HiltViewModel(assistedFactory = ModuleViewModel.Factory::class)
class ModuleViewModel @AssistedInject constructor(
    @Assisted arguments: Bundle,
    localRepository: LocalRepository,
    modulesRepository: ModulesRepository,
    userPreferencesRepository: UserPreferencesRepository,
    application: Application,
) : MMRLViewModel(
    localRepository = localRepository,
    modulesRepository = modulesRepository,
    userPreferencesRepository = userPreferencesRepository,
    application = application,
) {
    val version: String
        get() = PlatformManager.get("") {
            with(moduleManager) { version }
        }

    val versionCode: Int
        get() = PlatformManager.get(0) {
            with(moduleManager) { versionCode }
        }

    private val moduleId = arguments.panicString("moduleId")
    val repoUrl = arguments.panicString("repoUrl")

    var online: OnlineModule by mutableStateOf(OnlineModule.example())
        private set
    var repo: Repo by mutableStateOf(Repo.example())
        private set
    val lastVersionItem by derivedStateOf {
        versions.firstOrNull()?.second
    }

    val isEmptyAbout
        get() = online.homepage.orEmpty().isBlank()
                && online.track.source.isBlank()
                && online.support.orEmpty().isBlank()

    val isEmptyReadme get() = !online.hasReadme
    val readme get() = online.readme.orEmpty()
    var local: LocalModule? by mutableStateOf(null)
        private set

    private val installed get() = local?.let { it.author == online.author } ?: false
    var notifyUpdates by mutableStateOf(false)
        private set

    val localVersionCode
        get() =
            if (notifyUpdates && installed) local!!.versionCode else Int.MAX_VALUE
    val updatableSize by derivedStateOf {
        versions.count { it.second.versionCode > localVersionCode }
    }

    val otherSources = mutableStateListOf<OtherSources>()
    val versions = mutableStateListOf<Pair<Repo, VersionItem>>()
    val tracks = mutableStateListOf<Pair<Repo, TrackJson>>()

    init {
        Timber.d("ModuleViewModel init: $moduleId")
        loadData()
    }

    private fun loadData() = viewModelScope.launch {
        localRepository.getOnlineByIdAndUrl(moduleId, repoUrl).let {
            online = it
        }

        localRepository.getOnlineAllById(moduleId).let {
            val filtered = it.filter { f -> f.repoUrl != repoUrl }

            otherSources.addAll(filtered.map { module ->
                OtherSources(
                    repo = localRepository.getRepoByUrl(module.repoUrl),
                    online = module,
                    state = module.createState(
                        local = localRepository.getLocalByIdOrNull(module.id),
                        hasUpdatableTag = localRepository.hasUpdatableTag(module.id)
                    )
                )
            })
        }

        localRepository.getRepoByUrl(repoUrl).let {
            repo = it
        }

        localRepository.getLocalByIdOrNull(moduleId)?.let {
            local = it
            notifyUpdates = localRepository.hasUpdatableTag(moduleId)
        }

        localRepository.getVersionByIdAndUrl(moduleId, repoUrl).forEach {
            val repo = localRepository.getRepoByUrl(it.repoUrl)

            val item = repo to it
            val track = repo to localRepository.getOnlineByIdAndUrl(
                id = online.id,
                repoUrl = it.repoUrl
            ).track

            versions.add(item)
            if (track !in tracks) tracks.add(track)
        }

        if (installed) {
            UpdateJson.loadToVersionItem(local!!.updateJson)?.let {
                versions.add(0, "Update Json".toRepo() to it)
            }
        }
    }

    fun setUpdatesTag(updatable: Boolean) {
        viewModelScope.launch {
            notifyUpdates = updatable
            localRepository.insertUpdatableTag(moduleId, updatable)
        }
    }

    fun downloader(
        context: Context,
        item: VersionItem,
        onSuccess: (File) -> Unit,
    ) {
        viewModelScope.launch {
            val downloadPath = File(
                userPreferencesRepository.data
                    .first().downloadPath
            )

            val filename = Utils.getFilename(
                name = online.name,
                version = item.version,
                versionCode = item.versionCode,
                extension = "zip"
            )

            val task = DownloadService.TaskItem(
                key = item.hashCode(),
                url = item.zipUrl,
                filename = filename,
                title = online.name,
                desc = item.versionDisplay
            )

            val listener = object : DownloadService.IDownloadListener {
                override fun getProgress(value: Float) {}
                override fun onFileExists() {
                    Toast.makeText(
                        context,
                        context.getString(R.string.file_already_exists), Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSuccess() {
                    if (downloadPath.exists() && downloadPath.mkdirs()) {
                        Timber.d("Created directory: $downloadPath")
                    }

                    onSuccess(downloadPath.resolve(filename))
                }

                override fun onFailure(e: Throwable) {
                    Timber.d(e)
                }
            }

            DownloadService.start(
                context = context,
                task = task,
                listener = listener
            )
        }
    }

    @Composable
    fun getProgress(item: VersionItem): Float {
        val progress by DownloadService.getProgressByKey(item.hashCode())
            .collectAsStateWithLifecycle(initialValue = 0f)

        return progress
    }

    private val opsTasks = mutableStateListOf<ModId>()
    private val opsCallback = object : IModuleOpsCallback.Stub() {
        override fun onSuccess(id: ModId) {
            viewModelScope.launch {
                modulesRepository.getLocal(id)
                opsTasks.remove(id)
            }
        }

        override fun onFailure(id: ModId, msg: String?) {
            opsTasks.remove(id)
            Timber.w("$id: $msg")
        }
    }

    fun createModuleOps(useShell: Boolean, module: LocalModule) = when (module.state) {
        State.ENABLE -> ModuleOps(
            isOpsRunning = opsTasks.contains(module.id),
            toggle = {
                opsTasks.add(module.id)
                PlatformManager.moduleManager.disable(module.id, useShell, opsCallback)
            },
            change = {
                Timber.d("Pressed ENABLE")
                opsTasks.add(module.id)
                PlatformManager.moduleManager.remove(module.id, useShell, opsCallback)
                local = local?.copy(state = State.REMOVE)
            }
        )

        State.DISABLE -> ModuleOps(
            isOpsRunning = opsTasks.contains(module.id),
            toggle = {
                opsTasks.add(module.id)
                PlatformManager.moduleManager.enable(module.id, useShell, opsCallback)
            },
            change = {
                Timber.d("Pressed DISABLE")
                opsTasks.add(module.id)
                PlatformManager.moduleManager.remove(module.id, useShell, opsCallback)
                local = local?.copy(state = State.REMOVE)
            }
        )

        State.REMOVE -> ModuleOps(
            isOpsRunning = opsTasks.contains(module.id),
            toggle = {},
            change = {
                Timber.d("Pressed REMOVE")
                opsTasks.add(module.id)
                PlatformManager.moduleManager.enable(module.id, useShell, opsCallback)
                local = local?.copy(state = State.ENABLE)
            }
        )

        State.UPDATE -> ModuleOps(
            isOpsRunning = opsTasks.contains(module.id),
            toggle = {},
            change = {}
        )
    }

    data class ModuleOps(
        val isOpsRunning: Boolean,
        val toggle: (Boolean) -> Unit,
        val change: () -> Unit,
    )

    @AssistedFactory
    interface Factory {
        fun create(arguments: Bundle): ModuleViewModel
    }
}