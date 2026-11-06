package com.albert.familycontrol.parent

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albert.familycontrol.parent.databinding.ActivityPresetsBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import com.google.firebase.database.ValueEventListener

class PresetsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPresetsBinding
    private var presetListener: ValueEventListener? = null
    private var currentPreset = "custom"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPresetsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Режим дня"

        binding.cardSchool.setOnClickListener { applyPreset("school") }
        binding.cardEvening.setOnClickListener { applyPreset("evening") }
        binding.cardWeekend.setOnClickListener { applyPreset("weekend") }
        binding.cardCustom.setOnClickListener { applyPreset("custom") }

        presetListener = ParentFirebaseManager.listenPreset { preset ->
            currentPreset = preset
            runOnUiThread { updateSelectedCard(preset) }
        }
    }

    private fun applyPreset(preset: String) {
        ParentFirebaseManager.setPreset(preset)
        val label = when (preset) {
            "school" -> "🎓 Школа"
            "evening" -> "🌙 Вечер"
            "weekend" -> "🎉 Выходной"
            else -> "Своё"
        }
        Toast.makeText(this, "Режим: $label", Toast.LENGTH_SHORT).show()
    }

    private fun updateSelectedCard(preset: String) {
        val selectedColor = getColor(R.color.colorPrimary)
        val normalColor = getColor(R.color.colorSurface)
        binding.cardSchool.setCardBackgroundColor(if (preset == "school") selectedColor else normalColor)
        binding.cardEvening.setCardBackgroundColor(if (preset == "evening") selectedColor else normalColor)
        binding.cardWeekend.setCardBackgroundColor(if (preset == "weekend") selectedColor else normalColor)
        binding.cardCustom.setCardBackgroundColor(if (preset == "custom") selectedColor else normalColor)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        presetListener?.let { ParentFirebaseManager.removePresetListener(it) }
    }
}
