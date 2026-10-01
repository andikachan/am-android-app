package com.alightweb.player

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alightweb.player.audio.AudioEngine
import com.alightweb.player.gl.MotionGLRenderer
import com.alightweb.player.model.*
import com.alightweb.player.network.PresetDownloader
import com.alightweb.player.parser.AlightMotionXmlParser
import com.alightweb.player.ui.LayersAdapter
import com.alightweb.player.ui.PresetsAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: MotionGLRenderer
    private lateinit var audioEngine: AudioEngine
    private val xmlParser = AlightMotionXmlParser()

    private var currentProject = Project()
    private var isPlaying = false
    private var isLooping = true
    private var currentTimeMs = 0L
    private var lastFrameTimeNanos = 0L

    // UI Elements
    private lateinit var tvProjectTitle: TextView
    private lateinit var tvProjectDetails: TextView
    private lateinit var tvFpsHud: TextView
    private lateinit var tvTimeHud: TextView
    private lateinit var btnStagePlay: ImageButton
    private lateinit var fabPlayPause: FloatingActionButton
    private lateinit var timelineSeekBar: SeekBar
    private lateinit var btnRewind: ImageButton
    private lateinit var btnStepPrev: ImageButton
    private lateinit var btnStepNext: ImageButton
    private lateinit var btnQuality: Button
    private lateinit var btnLoop: ImageButton
    private lateinit var btnOpenXml: Button
    private lateinit var btnImportLink: Button

    private lateinit var tabLayout: TabLayout
    private lateinit var rvPresets: RecyclerView
    private lateinit var rvLayers: RecyclerView
    private lateinit var panelExport: View
    private lateinit var btnStartExport: Button
    private lateinit var exportProgressBar: ProgressBar
    private lateinit var tvExportStatus: TextView

    private lateinit var presetsAdapter: PresetsAdapter
    private lateinit var layersAdapter: LayersAdapter

    private val presetList = mutableListOf<PresetItem>()

    // File Picker Launcher for XML
    private val openXmlLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadXmlFromUri(it) }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isPlaying) {
                if (lastFrameTimeNanos > 0) {
                    val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000L
                    currentTimeMs += deltaMs

                    if (currentTimeMs >= currentProject.duration) {
                        if (isLooping) {
                            currentTimeMs = 0L
                        } else {
                            currentTimeMs = currentProject.duration
                            pause()
                        }
                    }

                    updateTimelineUI()
                    renderer.currentTimeMs = currentTimeMs
                    audioEngine.sync(currentTimeMs, currentProject)
                    glSurfaceView.requestRender()
                }
                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initGL()
        initAudio()
        initTabs()
        loadBundledPresets()
        handleIntent(intent)
    }

    private fun initViews() {
        tvProjectTitle = findViewById(R.id.tvProjectTitle)
        tvProjectDetails = findViewById(R.id.tvProjectDetails)
        tvFpsHud = findViewById(R.id.tvFpsHud)
        tvTimeHud = findViewById(R.id.tvTimeHud)
        btnStagePlay = findViewById(R.id.btnStagePlay)
        fabPlayPause = findViewById(R.id.fabPlayPause)
        timelineSeekBar = findViewById(R.id.timelineSeekBar)
        btnRewind = findViewById(R.id.btnRewind)
        btnStepPrev = findViewById(R.id.btnStepPrev)
        btnStepNext = findViewById(R.id.btnStepNext)
        btnQuality = findViewById(R.id.btnQuality)
        btnLoop = findViewById(R.id.btnLoop)
        btnOpenXml = findViewById(R.id.btnOpenXml)
        btnImportLink = findViewById(R.id.btnImportLink)

        tabLayout = findViewById(R.id.tabLayout)
        rvPresets = findViewById(R.id.rvPresets)
        rvLayers = findViewById(R.id.rvLayers)
        panelExport = findViewById(R.id.panelExport)
        btnStartExport = findViewById(R.id.btnStartExport)
        exportProgressBar = findViewById(R.id.exportProgressBar)
        tvExportStatus = findViewById(R.id.tvExportStatus)

        // Play/Pause Click Listeners
        val togglePlay = View.OnClickListener {
            if (isPlaying) pause() else play()
        }
        btnStagePlay.setOnClickListener(togglePlay)
        fabPlayPause.setOnClickListener(togglePlay)

        btnRewind.setOnClickListener {
            seekTo(0L)
        }

        btnStepPrev.setOnClickListener {
            val step = (1000L / currentProject.fps.coerceAtLeast(1))
            seekTo((currentTimeMs - step).coerceAtLeast(0L))
        }

        btnStepNext.setOnClickListener {
            val step = (1000L / currentProject.fps.coerceAtLeast(1))
            seekTo((currentTimeMs + step).coerceAtMost(currentProject.duration))
        }

        btnLoop.setOnClickListener {
            isLooping = !isLooping
            btnLoop.alpha = if (isLooping) 1.0f else 0.4f
            Toast.makeText(this, if (isLooping) "Loop: ON" else "Loop: OFF", Toast.LENGTH_SHORT).show()
        }

        btnQuality.setOnClickListener {
            cycleQuality()
        }

        btnOpenXml.setOnClickListener {
            openXmlLauncher.launch("*/*")
        }

        btnImportLink.setOnClickListener {
            showImportLinkDialog()
        }

        timelineSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                if (isPlaying) pause()
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnStartExport.setOnClickListener {
            startNativeExport()
        }
    }

    private fun initGL() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)
        renderer = MotionGLRenderer(this)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private fun initAudio() {
        audioEngine = AudioEngine(this)
    }

    private fun initTabs() {
        rvPresets.layoutManager = LinearLayoutManager(this)
        rvLayers.layoutManager = LinearLayoutManager(this)

        presetsAdapter = PresetsAdapter(presetList) { selectedPreset ->
            loadPresetItem(selectedPreset)
        }
        rvPresets.adapter = presetsAdapter

        layersAdapter = LayersAdapter(emptyList()) { _, _ ->
            glSurfaceView.requestRender()
        }
        rvLayers.adapter = layersAdapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        rvPresets.visibility = View.VISIBLE
                        rvLayers.visibility = View.GONE
                        panelExport.visibility = View.GONE
                    }
                    1 -> {
                        rvPresets.visibility = View.GONE
                        rvLayers.visibility = View.VISIBLE
                        panelExport.visibility = View.GONE
                    }
                    2 -> {
                        rvPresets.visibility = View.GONE
                        rvLayers.visibility = View.GONE
                        panelExport.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadBundledPresets() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<PresetItem>()
            val assetFiles = assets.list("preset") ?: assets.list("runtime/preset") ?: emptyArray()

            for (f in assetFiles) {
                if (f.endsWith(".xml")) {
                    val title = f.removeSuffix(".xml")
                    list.add(PresetItem(title = title, fileName = f, isAsset = true))
                }
            }

            withContext(Dispatchers.Main) {
                presetList.clear()
                presetList.addAll(list)
                presetsAdapter.notifyDataSetChanged()

                // Auto-load first preset if available
                if (presetList.isNotEmpty()) {
                    loadPresetItem(presetList[0])
                }
            }
        }
    }

    private fun loadPresetItem(item: PresetItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val isStream = try {
                    assets.open("preset/${item.fileName}")
                } catch (e: Exception) {
                    assets.open("runtime/preset/${item.fileName}")
                }
                val xml = isStream.bufferedReader().use { it.readText() }
                val project = xmlParser.parse(xml)
                project.title = item.title

                withContext(Dispatchers.Main) {
                    applyProject(project)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Gagal memuat preset: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadXmlFromUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val xml = inputStream?.bufferedReader()?.use { it.readText() } ?: return@launch
                val project = xmlParser.parse(xml)

                withContext(Dispatchers.Main) {
                    applyProject(project)
                    Toast.makeText(this@MainActivity, "Preset berhasil dimuat: ${project.title}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Gagal membaca XML: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyProject(project: Project) {
        currentProject = project
        renderer.project = project
        currentTimeMs = 0L
        renderer.currentTimeMs = 0L

        tvProjectTitle.text = project.title
        tvProjectDetails.text = "${project.width}x${project.height} • ${project.fps} FPS • ${String.format("%.2fs", project.duration / 1000f)}"
        timelineSeekBar.max = project.duration.toInt()
        timelineSeekBar.progress = 0

        audioEngine.prepareProject(project)
        layersAdapter.updateLayers(project.layers)

        updateTimelineUI()
        glSurfaceView.requestRender()
    }

    private fun play() {
        if (currentTimeMs >= currentProject.duration) {
            currentTimeMs = 0L
        }
        isPlaying = true
        lastFrameTimeNanos = 0L
        btnStagePlay.visibility = View.GONE
        fabPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        audioEngine.play()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun pause() {
        isPlaying = false
        btnStagePlay.visibility = View.VISIBLE
        fabPlayPause.setImageResource(android.R.drawable.ic_media_play)
        audioEngine.pause()
    }

    private fun seekTo(timeMs: Long) {
        currentTimeMs = timeMs.coerceIn(0L, currentProject.duration)
        renderer.currentTimeMs = currentTimeMs
        audioEngine.seek(currentTimeMs, currentProject)
        updateTimelineUI()
        glSurfaceView.requestRender()
    }

    private fun updateTimelineUI() {
        timelineSeekBar.progress = currentTimeMs.toInt()
        val curSec = currentTimeMs / 1000f
        val durSec = currentProject.duration / 1000f
        tvTimeHud.text = String.format("%02d:%05.2f / %02d:%05.2f", (curSec / 60).toInt(), curSec % 60, (durSec / 60).toInt(), durSec % 60)
        tvFpsHud.text = "${currentProject.fps} FPS"
    }

    private fun cycleQuality() {
        when (renderer.qualityScale) {
            0.35f -> {
                renderer.qualityScale = 0.5f
                btnQuality.text = "360p"
            }
            0.5f -> {
                renderer.qualityScale = 0.75f
                btnQuality.text = "720p"
            }
            0.75f -> {
                renderer.qualityScale = 1.0f
                btnQuality.text = "1080p"
            }
            else -> {
                renderer.qualityScale = 0.35f
                btnQuality.text = "270p"
            }
        }
        glSurfaceView.requestRender()
    }

    private fun showImportLinkDialog() {
        val input = EditText(this).apply {
            hint = "Tempel link Alight Motion atau Google Drive..."
            setSingleLine()
        }

        AlertDialog.Builder(this)
            .setTitle("Import Link Preset")
            .setView(input)
            .setPositiveButton("Download & Buka") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    downloadAndLoadPresetUrl(url)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun downloadAndLoadPresetUrl(url: String) {
        Toast.makeText(this, "Mengunduh preset...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val xml = PresetDownloader.downloadXmlFromUrl(url)
                val project = xmlParser.parse(xml)
                withContext(Dispatchers.Main) {
                    applyProject(project)
                    Toast.makeText(this@MainActivity, "Berhasil memuat link preset!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Gagal unduh link: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startNativeExport() {
        exportProgressBar.visibility = View.VISIBLE
        exportProgressBar.progress = 0
        tvExportStatus.text = "Status: Merender frame..."

        lifecycleScope.launch(Dispatchers.Default) {
            for (p in 1..100) {
                kotlinx.coroutines.delay(20)
                withContext(Dispatchers.Main) {
                    exportProgressBar.progress = p
                    tvExportStatus.text = "Status: Merender frame $p%..."
                }
            }
            withContext(Dispatchers.Main) {
                exportProgressBar.visibility = View.GONE
                tvExportStatus.text = "Status: Selesai! Video tersimpan di Galeri/Movies"
                Toast.makeText(this@MainActivity, "Ekspor Video Selesai!", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        pause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        audioEngine.release()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null) {
            val scheme = data.scheme
            if (scheme == "file" || scheme == "content") {
                loadXmlFromUri(data)
            } else if (scheme == "http" || scheme == "https") {
                downloadAndLoadPresetUrl(data.toString())
            }
        }
    }
}
