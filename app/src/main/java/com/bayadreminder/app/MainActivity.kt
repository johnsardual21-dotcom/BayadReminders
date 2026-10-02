package com.bayadreminder.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {

    private val prefsName = "bayad_data"
    private val debtorsKey = "debtors"
    private val monthlyAmount = 4099.0

    private lateinit var listLayout: LinearLayout
    private lateinit var incomeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 24, 24, 24)

        val title = TextView(this)
        title.text = "💰 Bayad Reminder"
        title.textSize = 28f

        val subtitle = TextView(this)
        subtitle.text = "Monthly payment: ₱4,099 bawat may utang"
        subtitle.textSize = 16f

        incomeText = TextView(this)
        incomeText.textSize = 18f

        val addButton = Button(this)
        addButton.text = "➕ Magdagdag ng may utang"
        addButton.setOnClickListener { addDebtor() }

        val voiceButton = Button(this)
        voiceButton.text = "🎙️ Voice Command"
        voiceButton.setOnClickListener { voiceCommand() }

        val scroll = ScrollView(this)
        listLayout = LinearLayout(this)
        listLayout.orientation = LinearLayout.VERTICAL
        scroll.addView(listLayout)

        root.addView(title)
        root.addView(subtitle)
        root.addView(incomeText)
        root.addView(addButton)
        root.addView(voiceButton)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
        refreshList()
    }

    private fun addDebtor() {
        val input = EditText(this)
        input.hint = "Pangalan"

        AlertDialog.Builder(this)
            .setTitle("Magdagdag ng may utang")
            .setMessage("₱4,099 ang regular na bayaran.")
            .setView(input)
            .setPositiveButton("Idagdag") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    addPerson(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addPerson(name: String) {
        val data = loadData()

        data.put(JSONObject().apply {
            put("name", name)
            put("paid", false)
            put("amount", monthlyAmount)
            put("extra", 0.0)
        })

        saveData(data)
        refreshList()
    }

    private fun refreshList() {
        listLayout.removeAllViews()

        val data = loadData()
        var extraIncome = 0.0

        for (i in 0 until data.length()) {
            val person = data.getJSONObject(i)
            val name = person.getString("name")
            val paid = person.getBoolean("paid")
            val amount = person.optDouble("amount", monthlyAmount)

            if (amount > monthlyAmount) {
                extraIncome += amount - monthlyAmount
            }

            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(12, 18, 12, 18)

            val status = if (paid) "✅ PAID" else "⏰ HINDI PAID"

            val text = TextView(this)
            text.text = "$name\n$status\nBayad: ₱${"%.2f".format(amount)}"
            text.textSize = 18f

            val paidButton = Button(this)
            paidButton.text =
                if (paid) "I-record na HINDI PAID"
                else "Mark as PAID"

            paidButton.setOnClickListener {
                person.put("paid", !paid)
                saveData(data)
                refreshList()
            }

            row.addView(text)
            row.addView(paidButton)
            listLayout.addView(row)
        }

        incomeText.text =
            "💵 Extra Income: ₱${"%.2f".format(extraIncome)}"
    }

    private fun voiceCommand() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "fil-PH"
        )
        intent.putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            "Halimbawa: Nakabayad na si Juan"
        )

        try {
            startActivityForResult(intent, 200)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Hindi available ang voice command sa device.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 200 && resultCode == RESULT_OK) {
            val results =
                data?.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )

            val command = results?.firstOrNull()?.lowercase(Locale.getDefault())
                ?: return

            processVoiceCommand(command)
        }
    }

    private fun processVoiceCommand(command: String) {
        val data = loadData()

        for (i in 0 until data.length()) {
            val person = data.getJSONObject(i)
            val name = person.getString("name").lowercase(Locale.getDefault())

            if (command.contains(name)) {
                if (command.contains("bayad") ||
                    command.contains("paid") ||
                    command.contains("nakabayad")) {

                    person.put("paid", true)
                    saveData(data)
                    refreshList()

                    Toast.makeText(
                        this,
                        "${person.getString("name")} ay recorded na PAID.",
                        Toast.LENGTH_LONG
                    ).show()

                    return
                }
            }
        }

        Toast.makeText(
            this,
            "Hindi ko nakita ang pangalan sa listahan.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun loadData(): JSONArray {
        val raw = getSharedPreferences(
            prefsName,
            MODE_PRIVATE
        ).getString(debtorsKey, "[]") ?: "[]"

        return JSONArray(raw)
    }

    private fun saveData(data: JSONArray) {
        getSharedPreferences(
            prefsName,
            MODE_PRIVATE
        ).edit()
            .putString(debtorsKey, data.toString())
            .apply()
    }
}
