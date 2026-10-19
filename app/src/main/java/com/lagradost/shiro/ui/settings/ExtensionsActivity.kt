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
import com.lagradost.shiro.utils.aniyomi.AniyomiManager
import com.lagradost.shiro.utils.mihon.MihonManager
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

        refreshRepos()
        refreshPlugins()
        refreshZangetsu()
        refreshAniyomi()
        refreshMihon()
    }

    // ---------- Aniyomi extensions ----------

    private fun refreshAniyomi() {
        val box = findViewById<LinearLayout>(R.id.aniyomi_container)
        box.removeAllViews()
        val repos = AniyomiManager.getRepos(this)
        if (repos.isEmpty()) box.addView(rowText("No Aniyomi repositories added yet"))
        for (repo in repos) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(rowText(repo.name ?: repo.url))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(rowButton("Browse") { browseAniyomi(repo.url) })
            buttons.addView(rowButton("Remove") {
                AniyomiManager.removeRepo(this, repo.url)
                refreshAniyomi()
            })
            row.addView(buttons)
            box.addView(row)
        }

        val input = EditText(this).apply {
            hint = "Aniyomi repository URL (index.min.json)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
        }
        box.addView(input)
        box.addView(rowButton("Add Aniyomi repository") {
            val raw = input.text.toString().trim()
            if (!raw.startsWith("http")) {
                toast("Enter the full https URL of the repository")
                return@rowButton
            }
            val url = AniyomiManager.normaliseRepoUrl(raw)
            confirmRisk {
                setStatus("Reading repository…")
                thread {
                    val index = AniyomiManager.fetchIndex(url)
                    runOnUiThread {
                        if (index == null) {
                            setStatus("Could not read an Aniyomi repository at that URL")
                            toast("Not a valid Aniyomi repository")
                        } else {
                            AniyomiManager.addRepo(this, AniyomiManager.AyRepo(url, url.substringBeforeLast('/').substringAfterLast('/')))
                            setStatus("")
                            refreshAniyomi()
                        }
                    }
                }
            }
        })

        val installed = AniyomiManager.getInstalled(this)
        box.addView(rowText(if (installed.isEmpty()) "No Aniyomi extensions installed" else "Installed extensions"))
        for (ext in installed) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(rowText("${ext.name.removePrefix("Aniyomi: ")}  v${ext.version}  •  ${ext.lang}"))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Switch(this).apply {
                isChecked = AniyomiManager.isEnabled(this@ExtensionsActivity, ext.pkg)
                text = "Enabled"
                setOnCheckedChangeListener { _, checked -> AniyomiManager.setEnabled(this@ExtensionsActivity, ext.pkg, checked) }
            })
            buttons.addView(rowButton("Report") { reportExtension(ext.repoUrl) })
            buttons.addView(rowButton("Uninstall") {
                AniyomiManager.uninstall(this, ext.pkg)
                refreshAniyomi()
            })
            row.addView(buttons)
            box.addView(row)
        }
    }

    private fun browseAniyomi(repoUrl: String) {
        setStatus("Fetching extensions…")
        thread {
            val index = AniyomiManager.fetchIndex(repoUrl)
            runOnUiThread {
                setStatus("")
                val entries = index.orEmpty().sortedBy { it.name }
                if (entries.isEmpty()) {
                    toast("No extensions found (or repository is offline)")
                    return@runOnUiThread
                }
                val installed = AniyomiManager.getInstalled(this)
                val labels = entries.map { e ->
                    val inst = installed.firstOrNull { it.pkg == e.pkg }
                    val marker = when {
                        inst == null -> ""
                        inst.version != e.version -> "  [update]"
                        else -> "  [installed]"
                    }
                    "${e.name.removePrefix("Aniyomi: ")} (${e.lang})${if (e.nsfw == 1) " 18+" else ""}$marker"
                }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Aniyomi extensions")
                    .setItems(labels) { _, which ->
                        confirmRisk {
                            setStatus("Installing ${entries[which].name}…")
                            thread {
                                val (ok, msg) = AniyomiManager.install(this, repoUrl, entries[which])
                                runOnUiThread {
                                    setStatus(msg)
                                    if (ok) refreshAniyomi()
                                }
                            }
                        }
                    }
                    .show()
            }
        }
    }

    // ---------- Mihon extensions ----------

    private fun refreshMihon() {
        val box = findViewById<LinearLayout>(R.id.mihon_container)
        box.removeAllViews()
        val repos = MihonManager.getRepos(this)
        if (repos.isEmpty()) box.addView(rowText("No Mihon repositories added yet"))
        for (repo in repos) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(rowText(repo.name ?: repo.url))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(rowButton("Browse") { browseMihon(repo.url) })
            buttons.addView(rowButton("Remove") {
                MihonManager.removeRepo(this, repo.url)
                refreshMihon()
            })
            row.addView(buttons)
            box.addView(row)
        }

        val input = EditText(this).apply {
            hint = "Mihon repository URL (index.min.json)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
        }
        box.addView(input)
        box.addView(rowButton("Add Mihon repository") {
            val raw = input.text.toString().trim()
            if (!raw.startsWith("http")) {
                toast("Enter the full https URL of the repository")
                return@rowButton
            }
            val url = MihonManager.normaliseRepoUrl(raw)
            confirmRisk {
                setStatus("Reading repository…")
                thread {
                    val index = MihonManager.fetchIndex(url)
                    runOnUiThread {
                        if (index == null) {
                            setStatus("Could not read an Mihon repository at that URL")
                            toast("Not a valid Mihon repository")
                        } else {
                            MihonManager.addRepo(this, MihonManager.MhRepo(url, url.substringBeforeLast('/').substringAfterLast('/')))
                            setStatus("")
                            refreshMihon()
                        }
                    }
                }
            }
        })

        val installed = MihonManager.getInstalled(this)
        box.addView(rowText(if (installed.isEmpty()) "No Mihon extensions installed" else "Installed extensions"))
        for (ext in installed) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            row.addView(rowText("${ext.name.removePrefix("Mihon: ")}  v${ext.version}  •  ${ext.lang}"))
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Switch(this).apply {
                isChecked = MihonManager.isEnabled(this@ExtensionsActivity, ext.pkg)
                text = "Enabled"
                setOnCheckedChangeListener { _, checked -> MihonManager.setEnabled(this@ExtensionsActivity, ext.pkg, checked) }
            })
            buttons.addView(rowButton("Report") { reportExtension(ext.repoUrl) })
            buttons.addView(rowButton("Uninstall") {
                MihonManager.uninstall(this, ext.pkg)
                refreshMihon()
            })
            row.addView(buttons)
            box.addView(row)
        }
    }

    private fun browseMihon(repoUrl: String) {
        setStatus("Fetching extensions…")
        thread {
            val index = MihonManager.fetchIndex(repoUrl)
            runOnUiThread {
                setStatus("")
                val entries = index.orEmpty().sortedBy { it.name }
                if (entries.isEmpty()) {
                    toast("No extensions found (or repository is offline)")
                    return@runOnUiThread
                }
                val installed = MihonManager.getInstalled(this)
                val labels = entries.map { e ->
                    val inst = installed.firstOrNull { it.pkg == e.pkg }
                    val marker = when {
                        inst == null -> ""
                        inst.version != e.version -> "  [update]"
                        else -> "  [installed]"
                    }
                    "${e.name.removePrefix("Mihon: ")} (${e.lang})${if (e.nsfw == 1) " 18+" else ""}$marker"
                }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Mihon extensions")
                    .setItems(labels) { _, which ->
                        confirmRisk {
                            setStatus("Installing ${entries[which].name}…")
                            thread {
                                val (ok, msg) = MihonManager.install(this, repoUrl, entries[which])
                                runOnUiThread {
                                    setStatus(msg)
                                    if (ok) refreshMihon()
                                }
                            }
                        }
                    }
                    .show()
            }
        }
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
            buttons.addView(rowButton("Report") { reportExtension(src.repoUrl) })
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
        AlertDialog.Builder(this)
            .setTitle("Only install extensions you trust")
            .setMessage(
                "Extensions are third-party code that runs inside this app with the same access " +
                        "it has (network, your stored accounts and settings). Nekomo does not review them. " +
                        "Only add repositories and install extensions from sources you trust."
            )
            .setPositiveButton("Continue") { _, _ -> action() }
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
            buttons.addView(rowButton("Report") { reportExtension(plugin.repoUrl) })
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

    /** Opens the issue tracker of the repository an extension came from (the author, not Nekomo, maintains it). */
    private fun reportExtension(repoUrl: String?) {
        val url = repoUrl ?: run { toast("No repository recorded for this extension"); return }
        val gh = Regex("""https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/""").find(url)
        val target = if (gh != null) "https://github.com/${gh.groupValues[1]}/${gh.groupValues[2]}/issues" else url.substringBefore('/', "").let { _ ->
            Regex("""^(https?://[^/]+)""").find(url)?.groupValues?.get(1) ?: url
        }
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target)))
        } catch (e: Exception) {
            toast("No browser available")
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
