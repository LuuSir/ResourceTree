package com.example.resouretree.data.clipboard

import android.content.Context
import com.example.resouretree.domain.model.ClipboardRule
import com.example.resouretree.domain.model.ActionType
import com.example.resouretree.domain.model.ClipboardMatchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Small local preferences, independent of the user's Room tree and JSON imports. */
class ClipboardRuleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clipboard-rules", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val rules = mutable.asStateFlow()

    fun save(rule: ClipboardRule) {
        val validated = rule.validated()
        require(mutable.value.none { it.id != rule.id && it.prefix == validated.prefix && it.matchType == validated.matchType }) { "该匹配规则已存在，请编辑原规则" }
        val next = if (mutable.value.any { it.id == rule.id }) mutable.value.map { if (it.id == rule.id) validated else it }
            else mutable.value + validated
        write(next)
    }

    fun delete(id: String) = write(mutable.value.filterNot { it.id == id })

    private fun write(rules: List<ClipboardRule>) {
        val json = JSONArray()
        rules.forEach { rule -> json.put(JSONObject().put("id", rule.id).put("prefix", rule.prefix)
            .put("name", rule.name).put("targets", JSONArray(rule.targets)).put("enabled", rule.enabled).put("actionType", rule.actionType.name)
            .put("matchType", rule.matchType.name)) }
        prefs.edit().putString("rules", json.toString()).apply()
        mutable.value = rules
    }

    private fun read(): List<ClipboardRule> {
        val raw = prefs.getString("rules", null) ?: return ClipboardRule.defaults
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).map { index ->
                val rule = json.getJSONObject(index)
                val targets = rule.optJSONArray("targets") ?: JSONArray()
                ClipboardRule(rule.getString("id"), rule.getString("prefix"), rule.optString("name"),
                    (0 until targets.length()).map(targets::getString), rule.optBoolean("enabled", true),
                    ActionType.valueOf(rule.optString("actionType", "COPY_AND_LAUNCH")),
                    ClipboardMatchType.valueOf(rule.optString("matchType", "PREFIX"))).validated()
            }
        }.getOrDefault(emptyList()) // Never silently restore deleted/disabled rules after malformed preferences.
    }
}
