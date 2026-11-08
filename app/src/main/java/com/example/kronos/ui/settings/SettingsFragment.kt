package com.example.kronos.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.kronos.R
import com.example.kronos.data.ClassPeriod
import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.ThemeMode
import com.example.kronos.data.TimeFormat
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.Holiday
import com.example.kronos.databinding.FragmentSettingsBinding
import com.example.kronos.lib.formatDisplayDate
import com.example.kronos.lib.formatDisplayTime
import com.example.kronos.notifications.TaskExamReminderScheduler
import com.example.kronos.widget.TimetableWidgetProvider
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.timepicker.MaterialTimePicker
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var holidays: List<Holiday> = emptyList()

    private data class AccentOption(val key: String, val nameRes: Int, val swatchRes: Int)

    // Fixed order: users learn the strip by position, so this list is not sorted.
    private val accents = listOf(
        AccentOption("purple", R.string.accent_purple, R.color.accent_swatch_purple),
        AccentOption("indigo", R.string.accent_indigo, R.color.accent_swatch_indigo),
        AccentOption("blue", R.string.accent_blue, R.color.accent_swatch_blue),
        AccentOption("teal", R.string.accent_teal, R.color.accent_swatch_teal),
        AccentOption("green", R.string.accent_green, R.color.accent_swatch_green),
        AccentOption("orange", R.string.accent_orange, R.color.accent_swatch_orange),
        AccentOption("rose", R.string.accent_rose, R.color.accent_swatch_rose),
        AccentOption("graphite", R.string.accent_graphite, R.color.accent_swatch_graphite)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = SettingsStore(requireContext())

        val navController = findNavController()
        binding.toolbar.setNavigationOnClickListener {
            // Same as system back: previous tab. Fall back to the start tab when there is none.
            if (!navController.popBackStack()) navController.navigate(R.id.timetableFragment)
        }

        binding.themeModeToggle.check(
            when (settings.themeMode) {
                ThemeMode.LIGHT -> R.id.themeLight
                ThemeMode.DARK -> R.id.themeDark
                ThemeMode.AUTO_BATTERY -> R.id.themeAuto
            }
        )
        binding.pureBlackSwitch.isChecked = settings.pureBlack
        binding.timeFormatToggle.check(
            if (settings.timeFormat == TimeFormat.HOUR_24) R.id.timeFormat24 else R.id.timeFormat12
        )
        binding.firstDayToggle.check(
            if (settings.firstDayOfWeek == FirstDayOfWeek.MONDAY) {
                R.id.firstDayMonday
            } else {
                R.id.firstDaySunday
            }
        )
        renderAccents(settings)
        binding.rotationSlider.value = settings.rotationLength.toFloat()
        binding.reminderOffsetSlider.value = (settings.defaultReminderOffsetMinutes / 5 * 5)
            .coerceIn(
                binding.reminderOffsetSlider.valueFrom.toInt(),
                binding.reminderOffsetSlider.valueTo.toInt()
            ).toFloat()
        renderRotation()
        renderReminderOffset()
        renderExamHour(settings)
        renderSemesterStart(settings)
        binding.classRemindersSwitch.isChecked = settings.classRemindersEnabled
        binding.taskRemindersSwitch.isChecked = settings.taskRemindersEnabled
        binding.examRemindersSwitch.isChecked = settings.examRemindersEnabled
        binding.examDayBeforeSwitch.isChecked = settings.examDayBeforeEnabled
        binding.examHourBeforeSwitch.isChecked = settings.examHourBeforeEnabled

        binding.themeModeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            settings.themeMode = when (checkedId) {
                R.id.themeLight -> ThemeMode.LIGHT
                R.id.themeDark -> ThemeMode.DARK
                else -> ThemeMode.AUTO_BATTERY
            }
            applyNightMode()
        }
        binding.pureBlackSwitch.setOnCheckedChangeListener { _, checked ->
            settings.pureBlack = checked
            TimetableWidgetProvider.requestUpdate(requireContext())
            val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightMask == Configuration.UI_MODE_NIGHT_YES) requireActivity().recreate()
        }
        binding.timeFormatToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            settings.timeFormat =
                if (checkedId == R.id.timeFormat24) TimeFormat.HOUR_24 else TimeFormat.HOUR_12
            TimetableWidgetProvider.requestUpdate(requireContext())
            renderExamHour(settings)
        }
        binding.firstDayToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            settings.firstDayOfWeek =
                if (checkedId == R.id.firstDayMonday) {
                    FirstDayOfWeek.MONDAY
                } else {
                    FirstDayOfWeek.SUNDAY
                }
            TimetableWidgetProvider.requestUpdate(requireContext())
        }
        binding.rotationSlider.addOnChangeListener { _, value, _ ->
            binding.rotationValue.text =
                getString(R.string.settings_rotation_weeks, value.toInt())
            settings.rotationLength = value.toInt()
            TimetableWidgetProvider.requestUpdate(requireContext())
            rescheduleReminders()
        }
        binding.reminderOffsetSlider.addOnChangeListener { _, value, _ ->
            binding.reminderOffsetValue.text =
                getString(R.string.settings_minutes_before, value.toInt())
            settings.defaultReminderOffsetMinutes = value.toInt()
            rescheduleReminders()
        }

        fun bindReminderSwitch(switch: SwitchMaterial, write: (Boolean) -> Unit) {
            switch.setOnCheckedChangeListener { _, checked ->
                write(checked)
                rescheduleReminders()
            }
        }
        bindReminderSwitch(binding.classRemindersSwitch) { settings.classRemindersEnabled = it }
        bindReminderSwitch(binding.taskRemindersSwitch) { settings.taskRemindersEnabled = it }
        bindReminderSwitch(binding.examRemindersSwitch) { settings.examRemindersEnabled = it }
        bindReminderSwitch(binding.examDayBeforeSwitch) { settings.examDayBeforeEnabled = it }
        bindReminderSwitch(binding.examHourBeforeSwitch) { settings.examHourBeforeEnabled = it }

        binding.examHourButton.setOnClickListener {
            MaterialTimePicker.Builder()
                .setTimeFormat(
                    if (settings.timeFormat == TimeFormat.HOUR_12) {
                        com.google.android.material.timepicker.TimeFormat.CLOCK_12H
                    } else {
                        com.google.android.material.timepicker.TimeFormat.CLOCK_24H
                    }
                )
                .setHour(settings.examReminderHour.hour)
                .setMinute(settings.examReminderHour.minute)
                .setTitleText(R.string.exam_reminder_hour)
                .build().apply {
                    addOnPositiveButtonClickListener {
                        settings.examReminderHour = LocalTime.of(hour, minute)
                        renderExamHour(settings)
                        rescheduleReminders()
                    }
                }.show(parentFragmentManager, "examHourPicker")
        }
        binding.semesterStartButton.setOnClickListener {
            val date = settings.semesterStart
            android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                settings.semesterStart = LocalDate.of(year, month + 1, day)
                renderSemesterStart(settings)
                TimetableWidgetProvider.requestUpdate(requireContext())
                rescheduleReminders()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        binding.periodsButton.setOnClickListener { showPeriodsSettings() }
        binding.holidaysButton.setOnClickListener { showHolidayManager() }
        binding.channelButton.setOnClickListener {
            openNotificationSettings()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                DatabaseProvider.get(requireContext())
                    .holidayDao()
                    .observeAll()
                    .collect { holidays = it }
            }
        }
    }

    private fun openNotificationSettings() {
        val packageName = requireContext().packageName
        // No data URI here: the settings filter declares none, so a "package:" data makes it unmatched on some devices.
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun renderRotation() {
        binding.rotationValue.text =
            getString(R.string.settings_rotation_weeks, binding.rotationSlider.value.toInt())
    }

    private fun renderReminderOffset() {
        binding.reminderOffsetValue.text =
            getString(R.string.settings_minutes_before, binding.reminderOffsetSlider.value.toInt())
    }

    private fun renderExamHour(settings: SettingsStore) {
        binding.examHourButton.text =
            formatDisplayTime(settings.examReminderHour, settings.timeFormat)
    }

    private fun renderSemesterStart(settings: SettingsStore) {
        binding.semesterStartButton.text = formatDisplayDate(settings.semesterStart)
    }

    // Plain Views instead of drawables or a RecyclerView: eight dots, no scrolling, no adapter.
    // shortcut: 32dp tap targets (not 48dp) so all eight fit one 360dp-wide row; revisit if the strip ever scrolls.
    private fun renderAccents(settings: SettingsStore) {
        val size = (32 * resources.displayMetrics.density).toInt()
        val spacing = (8 * resources.displayMetrics.density).toInt()
        val ringWidth = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
        val ringColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        accents.forEach { accent ->
            val selected = accent.key == settings.accentColor
            val swatch = View(requireContext())
            swatch.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = spacing
            }
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(requireContext(), accent.swatchRes))
                // A ring marks the selection: scaling the view instead blurs the oval edges.
                if (selected) setStroke(ringWidth, ringColor)
            }
            swatch.contentDescription = getString(accent.nameRes)
            swatch.setOnClickListener {
                if (!selected) {
                    settings.accentColor = accent.key
                    requireActivity().recreate()
                }
            }
            binding.accentSwatches.addView(swatch)
        }
    }

    private fun rescheduleReminders() {
        viewLifecycleOwner.lifecycleScope.launch {
            TaskExamReminderScheduler(requireContext()).rescheduleAll()
        }
    }

    private fun applyNightMode() {
        val mode = when (SettingsStore(requireContext()).themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.AUTO_BATTERY -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        }
        val modeChanged = AppCompatDelegate.getDefaultNightMode() != mode
        AppCompatDelegate.setDefaultNightMode(mode)
        if (!modeChanged) requireActivity().recreate()
    }

    // Rows carry a chevron and a ripple so it is obvious they open an editor.
    private fun editableItems(labels: List<String>) = ArrayAdapter(
        requireContext(), R.layout.item_editable, R.id.text, labels
    )

    private fun itemRow(parent: LinearLayout, onClick: (TextView) -> Unit): TextView {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.item_editable, parent, false)
        val text = row.findViewById<TextView>(R.id.text)
        row.setOnClickListener { onClick(text) }
        parent.addView(row)
        return text
    }

    // Same clock-face picker the class editor uses, so period times are picked the same way.
    private fun pickTime(initial: LocalTime, title: Int, onPicked: (LocalTime) -> Unit) {
        MaterialTimePicker.Builder()
            .setTimeFormat(
                if (SettingsStore(requireContext()).timeFormat == TimeFormat.HOUR_12) {
                    com.google.android.material.timepicker.TimeFormat.CLOCK_12H
                } else {
                    com.google.android.material.timepicker.TimeFormat.CLOCK_24H
                }
            )
            .setHour(initial.hour)
            .setMinute(initial.minute)
            .setTitleText(title)
            .build().apply {
                addOnPositiveButtonClickListener { onPicked(LocalTime.of(hour, minute)) }
            }.show(parentFragmentManager, "periodTimePicker")
    }

    private fun showHolidayManager() {
        val labels = holidays.map { "${it.name}: ${it.startDate} – ${it.endDate}" }.toMutableList()
        labels += getString(R.string.add_holiday)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.holidays)
            .setAdapter(editableItems(labels)) { _, which ->
                if (which == holidays.size) editHoliday(null) else editHoliday(holidays[which])
            }
            .show()
    }

    private fun editHoliday(existing: Holiday?) {
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.holiday_name)
            setText(existing?.name)
        }
        content.addView(name)
        var startDate = existing?.startDate ?: LocalDate.now()
        var endDate = existing?.endDate ?: startDate
        fun label(labelRes: Int, date: LocalDate) = "${getString(labelRes)}: $date"
        fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) =
            android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                onPicked(LocalDate.of(year, month + 1, day))
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        val start = itemRow(content) { text ->
            pickDate(startDate) { startDate = it; text.text = label(R.string.start_date, it) }
        }
        start.text = label(R.string.start_date, startDate)
        val end = itemRow(content) { text ->
            pickDate(endDate) { endDate = it; text.text = label(R.string.end_date, it) }
        }
        end.text = label(R.string.end_date, endDate)
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_holiday else R.string.edit_holiday)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete_holiday) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    DatabaseProvider.get(requireContext()).holidayDao().delete(existing)
                    TimetableWidgetProvider.requestUpdate(requireContext())
                    TaskExamReminderScheduler(requireContext()).rescheduleAll()
                }
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val trimmed = name.text.toString().trim()
                if (trimmed.isEmpty() || endDate.isBefore(startDate)) {
                    if (endDate.isBefore(startDate)) {
                        name.error = getString(R.string.holiday_invalid_range)
                    }
                    return@setOnClickListener
                }
                val holiday = Holiday(existing?.id ?: 0L, trimmed, startDate, endDate)
                viewLifecycleOwner.lifecycleScope.launch {
                    val dao = DatabaseProvider.get(requireContext()).holidayDao()
                    if (existing == null) dao.insert(holiday) else dao.update(holiday)
                    TimetableWidgetProvider.requestUpdate(requireContext())
                    TaskExamReminderScheduler(requireContext()).rescheduleAll()
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showPeriodsSettings() {
        val settings = SettingsStore(requireContext())
        val periods = settings.periods
        val labels = periods.map {
            "${it.name}, ${formatDisplayTime(it.start, settings.timeFormat)} - " +
                formatDisplayTime(it.end, settings.timeFormat)
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.configure_periods)
            .setAdapter(editableItems(labels)) { _, which -> editPeriod(periods[which]) }
            .setPositiveButton(R.string.add_period) { _, _ -> editPeriod(null) }
        // A message and a list in the same dialog compete for the content panel.
        if (labels.isEmpty()) builder.setMessage(R.string.configure_periods_hint)
        builder.show()
    }

    private fun editPeriod(existing: ClassPeriod?) {
        val settings = SettingsStore(requireContext())
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.period)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.name)
        }
        layout.addView(name)
        var startTime = existing?.start ?: LocalTime.of(8, 0)
        var endTime = existing?.end ?: LocalTime.of(8, 45)
        fun timeLabel(labelRes: Int, time: LocalTime) =
            "${getString(labelRes)}: ${formatDisplayTime(time, settings.timeFormat)}"
        val start = itemRow(layout) { text ->
            pickTime(startTime, R.string.start_time) {
                startTime = it
                text.text = timeLabel(R.string.start_time, it)
            }
        }
        start.text = timeLabel(R.string.start_time, startTime)
        val end = itemRow(layout) { text ->
            pickTime(endTime, R.string.end_time) {
                endTime = it
                text.text = timeLabel(R.string.end_time, it)
            }
        }
        end.text = timeLabel(R.string.end_time, endTime)
        // Always write a freshly read list: the caller's copy is stale once a save happened.
        fun persist(mutate: (MutableList<ClassPeriod>) -> Unit) {
            val periods = settings.periods.toMutableList()
            mutate(periods)
            settings.periods = periods
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_period else R.string.period)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (existing != null) dialog.setNeutralButton(R.string.delete) { _, _ ->
            persist { periods -> periods.removeAll { it == existing } }
            showPeriodsSettings()
        }
        val shown = dialog.create()
        shown.setOnShowListener {
            shown.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val periodName = name.text.toString().trim()
                when {
                    periodName.isEmpty() -> name.error = getString(R.string.required)
                    !endTime.isAfter(startTime) -> name.error = "End time must be after start time"
                    else -> {
                        val value = ClassPeriod(periodName, startTime, endTime)
                        persist { periods ->
                            val at = periods.indexOfFirst { it == existing }
                            if (at >= 0) periods[at] = value else periods.add(value)
                        }
                        shown.dismiss()
                        showPeriodsSettings()
                    }
                }
            }
        }
        shown.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

