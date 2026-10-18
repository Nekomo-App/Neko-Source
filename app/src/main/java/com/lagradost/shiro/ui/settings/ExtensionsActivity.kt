package com.lagradost.shiro.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.lagradost.shiro.R
import com.lagradost.shiro.utils.cs3.CsPluginManager
import com.lagradost.shiro.utils.zg.ZangetsuManager
import kotlin.concurrent.thread

/**
 * CloudStream extension management: add/remove repositories, browse their
 * plugin catalogues, install/update/enable/disable/uninstall .cs3 extensions.
 */
class ExtensionsActivity : CyaneaAppCompatActivity() {

    private lateinit var repoList: LinearLayout
    private lateinit var pluginList: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extensions)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Extensions"

        repoList = findViewById(R.id.repo_list)
        pluginList = findViewById(R.id.plugin_list)
        statusText = findViewById(R.id.extension_status)

        findViewById<Button>(R.id.add_repo_button).setOnClickListener {
            val input = findViewById<EditText>(R.id.repo_url_input)
            val raw = input.text.toString()
            if (raw.isBlank()) return@setOnClickListener
            confirmRisk { addRepoFromInput(raw, input) }
        }

        findViewById<Button>(R.id.browse_repos_button).setOnClickListener {
            setStatus("Fetching public repository list…")
            thread {
                val known = CsPluginManager.fetchKnownRepos()
                runOnUiThread {
                    setStatus("")
                    if (known.isEmpty()) {
                        toast("Could not fetch repository list")
                        return@runOnUiThread
                    }
                    val labels = known.map { (url, verified) ->
                        url.removePrefix("https://").removePrefix("http://") +
                                (if (verified) " ✓" else "")
                    }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Public repositories")
                        .setItems(labels) { _, which ->
                            val (url, _) = known[which]
                            confirmRisk {
                                CsPluginManager.addRepo(this, CsPluginManager.CsRepo(url))
                                refreshRepos()
                            }
                        }
                        .show()
                }
            }
        }

        refreshRepos()
        refreshPlugins()
        refreshZangetsu()
    }

    // ---------- Zangetsu sources ----------

    private fun refreshZangetsu() {
        val box = findViewById<LinearLayout>(R.id.zangetsu_container)
        box.removeAllViews()
        val repos = ZangetsuManager.getRepos(this)
        if (repos.isEmpty()) box.addView(rowText("No Zangetsu repositories added yet"))
        for (repo in repos) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(rowText(repo.name ?: repo.url))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(rowButton("Browse") { browseZangetsu(repo.url) })
            buttons.addView(rowButton("Remove") {
                ZangetsuManager.removeRepo(this, repo.url)
                refreshZangetsu()
            })
            row.addView(buttons)
            box.addView(row)
        }

        val input = EditText(this).apply {
            hint = "Zangetsu repository URL (index.json)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
        }
        box.addView(input)
        box.addView(rowButton("Add Zangetsu repository") {
            val url = input.text.toString().trim()
            if (!url.startsWith("http")) {
                toast("Enter the full https URL of the repository's index.json")
                return@rowButton
            }
            confirmRisk {
                setStatus("Reading repository…")
                thread {
                    val manifest = ZangetsuManager.fetchManifest(url)
                    runOnUiThread {
                        if (manifest == null) {
                            setStatus("Could not read a Zangetsu manifest at that URL")
                            toast("Not a valid Zangetsu repository")
                        } else {
                            ZangetsuManager.addRepo(this, ZangetsuManager.ZgRepo(url, manifest.name))
                            setStatus("")
                            refreshZangetsu()
                        }
                    }
                }
            }
        })

        val installed = ZangetsuManager.getInstalled(this)
        box.addView(rowText(if (installed.isEmpty()) "No Zangetsu sources installed" else "Installed sources"))
        if (installed.isNotEmpty()) box.addView(rowButton("Update all sources") {
            setStatus("Updating Zangetsu sources…")
            thread {
                val n = ZangetsuManager.updateAll(this)
                runOnUiThread {
                    setStatus("Updated $n source(s)")
                    refreshZangetsu()
                }
            }
        })
        for (src in installed) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(rowText("${src.name}  v${src.version}  •  ${src.type} / ${src.lang}"))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Switch(this).apply {
                isChecked = ZangetsuManager.isEnabled(this@ExtensionsActivity, src.id)
                text = "Enabled"
                setOnCheckedChangeListener { _, checked -> ZangetsuManager.setEnabled(this@ExtensionsActivity, src.id, checked) }
            })
            buttons.addView(rowButton("Uninstall") {
                ZangetsuManager.uninstall(this, src.id)
                refreshZangetsu()
            })
            row.addView(buttons)
            box.addView(row)
        }
    }

    private fun browseZangetsu(repoUrl: String) {
        setStatus("Fetching sources…")
        thread {
            val manifest = ZangetsuManager.fetchManifest(repoUrl)
            runOnUiThread {
                setStatus("")
                val sources = manifest?.sources.orEmpty()
                if (sources.isEmpty()) {
                    toast("No sources found (or repository is offline)")
                    return@runOnUiThread
                }
                val installed = ZangetsuManager.getInstalled(this)
                val labels = sources.map { s ->
                    val inst = installed.firstOrNull { it.id == s.id }
                    val marker = when {
                        inst == null -> ""
                        inst.version != s.version -> "  [update ${inst.version}→${s.version}]"
                        else -> "  [installed]"
                    }
                    "${s.name} (${s.type}, ${s.lang})$marker"
                }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Zangetsu sources")
                    .setItems(labels) { _, which ->
                        confirmRisk {
                            setStatus("Installing ${sources[which].name}…")
                            thread {
                                val (ok, msg) = ZangetsuManager.install(this, repoUrl, sources[which])
                                runOnUiThread {
                                    setStatus(msg)
                                    if (ok) refreshZangetsu()
                                }
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun addRepoFromInput(raw: String, input: EditText) {
        run {
            setStatus("Resolving repository…")
            thread {
                val url = CsPluginManager.parseRepoUrl(raw)
                if (url == null) {
                    setStatus("Could not parse repository: $raw")
                    toast("Invalid repository")
                    return@thread
                }
                val repo = CsPluginManager.fetchRepository(url)
                runOnUiThread {
                    CsPluginManager.addRepo(this, CsPluginManager.CsRepo(url, repo?.name))
                    input.text.clear()
                    setStatus("")
                    refreshRepos()
                }
            }
        }

    }

    // ---------- repositories ----------

    private fun refreshRepos() {
        repoList.removeAllViews()
        val repos = CsPluginManager.getRepos(this)
        if (repos.isEmpty()) {
            repoList.addView(rowText("No repositories added yet"))
        }
        for (repo in repos) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(rowText(repo.name ?: repo.url))
            buttons.addView(rowButton("Browse") { browseRepo(repo.url) })
            buttons.addView(rowButton("Install all") { confirmRisk { installAll(repo.url) } })
            buttons.addView(rowButton("Remove") {
                CsPluginManager.removeRepo(this, repo.url)
                refreshRepos()
            })
            row.addView(buttons)
            repoList.addView(row)
        }
    }

    private fun browseRepo(url: String) {
        setStatus("Fetching plugins…")
        thread {
            val plugins = CsPluginManager.fetchRepoPlugins(url)
            runOnUiThread {
                setStatus("")
                if (plugins.isEmpty()) {
                    toast("No plugins found (or repository is offline)")
                    return@runOnUiThread
                }
                val installed = CsPluginManager.getInstalled(this)
                val labels = plugins.map { p ->
                    val inst = installed.firstOrNull { it.internalName == p.internalName }
                    val marker = when {
                        inst == null -> ""
                        inst.version < p.version -> "  [update v${inst.version}→v${p.version}]"
                        else -> "  [installed]"
                    }
                    "${p.displayName} v${p.version}$marker"
                }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Extensions")
                    .setItems(labels) { _, which ->
                        confirmRisk { installPlugin(url, plugins[which]) }
                    }
                    .show()
            }
        }
    }

    private fun installAll(repoUrl: String) {
        setStatus("Fetching plugins…")
        thread {
            val plugins = CsPluginManager.fetchRepoPlugins(repoUrl)
            if (plugins.isEmpty()) {
                setStatus("")
                toast("No plugins found (or repository is offline)")
                return@thread
            }
            var ok = 0
            plugins.forEachIndexed { i, p ->
                setStatus("Installing ${p.displayName} (${i + 1}/${plugins.size})…")
                if (CsPluginManager.installPlugin(this, repoUrl, p).success) ok++
            }
            runOnUiThread {
                setStatus("Installed $ok of ${plugins.size} extensions")
                refreshPlugins()
            }
        }
    }

    private fun updateAll() {
        val installed = CsPluginManager.getInstalled(this)
        setStatus("Updating ${installed.size} extension(s)…")
        thread {
            var ok = 0
            installed.forEach { if (CsPluginManager.updatePlugin(this, it.internalName).success) ok++ }
            runOnUiThread {
                setStatus("Updated $ok of ${installed.size} extensions")
                refreshPlugins()
            }
        }
    }

    /** One-time warning: extensions are third-party code running inside the app. */
    private fun confirmRisk(action: () -> Unit) {
        val prefs = getSharedPreferences("extensions_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("risk_accepted", false)) return action()
        AlertDialog.Builder(this)
            .setTitle("Only install extensions you trust")
            .setMessage(
                "Extensions are third-party code that runs inside this app with the same access " +
                        "it has (network, your stored accounts and settings). Nekomo does not review them. " +
                        "Only add repositories and install extensions from sources you trust."
            )
            .setPositiveButton("I understand") { _, _ ->
                prefs.edit().putBoolean("risk_accepted", true).apply()
                action()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installPlugin(repoUrl: String, plugin: CsPluginManager.CsSitePlugin) {
        if (plugin.fileHash == null) {
            toast("Warning: this repository provides no file hash, so the download can't be verified")
        }
        setStatus("Installing ${plugin.displayName}…")
        thread {
            val result = CsPluginManager.installPlugin(this, repoUrl, plugin)
            runOnUiThread {
                setStatus(
                    if (result.success)
                        "${result.message} (${result.providerCount} provider(s))"
                    else result.message
                )
                refreshPlugins()
            }
        }
    }

    // ---------- installed plugins ----------

    private fun refreshPlugins() {
        pluginList.removeAllViews()
        val installed = CsPluginManager.getInstalled(this)
        if (installed.isEmpty()) {
            pluginList.addView(rowText("No extensions installed"))
        } else {
            pluginList.addView(rowButton("Update all") { updateAll() })
        }
        for (plugin in installed) {
            val providers = CsPluginManager.loaded()
                .firstOrNull { it.installed.internalName == plugin.internalName }
                ?.apis?.size ?: 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(rowText("${plugin.name}  v${plugin.version}  •  $providers provider(s)"))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            val toggle = Switch(this).apply {
                isChecked = CsPluginManager.isPluginEnabled(this@ExtensionsActivity, plugin.internalName)
                text = "Enabled"
                setOnCheckedChangeListener { _, checked ->
                    CsPluginManager.setPluginEnabled(this@ExtensionsActivity, plugin.internalName, checked)
                    refreshPlugins()
                }
            }
            buttons.addView(toggle)
            buttons.addView(rowButton("Update") {
                setStatus("Updating ${plugin.name}…")
                thread {
                    val result = CsPluginManager.updatePlugin(this@ExtensionsActivity, plugin.internalName)
                    runOnUiThread {
                        setStatus(result.message)
                        refreshPlugins()
                    }
                }
            })
            buttons.addView(rowButton("Uninstall") {
                CsPluginManager.uninstallPlugin(this, plugin.internalName)
                refreshPlugins()
            })
            row.addView(buttons)
            CsPluginManager.loaded()
                .firstOrNull { it.installed.internalName == plugin.internalName }
                ?.apis?.forEach { api ->
                    row.addView(Switch(this).apply {
                        text = "  ${api.name}"
                        isChecked = CsPluginManager.isProviderEnabled(this@ExtensionsActivity, api.name)
                        setOnCheckedChangeListener { _, checked ->
                            CsPluginManager.setProviderEnabled(this@ExtensionsActivity, api.name, checked)
                        }
                    })
                }
            pluginList.addView(row)
        }
    }

    // ---------- helpers ----------

    private fun rowText(text: String, weight: Float = 0f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, 16, 0, 16)
            layoutParams = LinearLayout.LayoutParams(
                if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight
            )
        }

    private fun rowButton(label: String, onClick: (View) -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener(onClick)
        }

    private fun setStatus(msg: String) {
        runOnUiThread { statusText.text = msg }
    }

    private fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
