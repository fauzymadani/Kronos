package com.example.kronos.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

    private fun dateRow(parent: LinearLayout, onClick: (TextView) -> Unit): TextView {
        val row = LayoutInflater.from(parent.context).inflate(R.layout.item_editable, parent, false)
        val text = row.findViewById<TextView>(R.id.text)
        row.setOnClickListener { onClick(text) }
        parent.addView(row)
        return text
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
        val start = dateRow(content) { text ->
            pickDate(startDate) { startDate = it; text.text = label(R.string.start_date, it) }
        }
        start.text = label(R.string.start_date, startDate)
        val end = dateRow(content) { text ->
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
        val periods = settings.periods.toMutableList()
        val labels = periods.map { "${it.name}, ${it.start} - ${it.end}" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.configure_periods)
            .setMessage(R.string.configure_periods_hint)
            .setAdapter(editableItems(labels)) { _, which -> editPeriod(settings, periods, which) }
            .setPositiveButton(R.string.add_period) { _, _ -> editPeriod(settings, periods, null) }
            .show()
    }

    private fun editPeriod(settings: SettingsStore, periods: MutableList<ClassPeriod>, index: Int?) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.period)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(index?.let { periods[it].name })
        }
        val start = EditText(requireContext()).apply {
            hint = getString(R.string.start_time)
            inputType = InputType.TYPE_CLASS_DATETIME
            setText(index?.let { periods[it].start.toString() } ?: "08:00")
        }
        val end = EditText(requireContext()).apply {
            hint = getString(R.string.end_time)
            inputType = InputType.TYPE_CLASS_DATETIME
            setText(index?.let { periods[it].end.toString() } ?: "08:45")
        }
        layout.addView(name); layout.addView(start); layout.addView(end)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (index == null) R.string.add_period else R.string.period)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (index != null) dialog.setNeutralButton(R.string.delete) { _, _ ->
            periods.removeAt(index)
            settings.periods = periods
        }
        val shown = dialog.create()
        shown.setOnShowListener {
            shown.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = runCatching {
                    ClassPeriod(
                        name.text.toString().trim().ifEmpty { throw IllegalArgumentException() },
                        LocalTime.parse(start.text.toString()),
                        LocalTime.parse(end.text.toString())
                    )
                }.getOrNull()
                if (value == null || !value.end.isAfter(value.start)) {
                    end.error = "Use HH:mm and an end after start"
                } else {
                    if (index == null) periods.add(value) else periods[index] = value
                    settings.periods = periods
                    shown.dismiss()
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

